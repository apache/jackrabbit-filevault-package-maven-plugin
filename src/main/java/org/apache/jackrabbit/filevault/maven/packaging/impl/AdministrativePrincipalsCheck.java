/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.filevault.maven.packaging.impl;

import static org.apache.jackrabbit.oak.spi.security.user.UserConstants.DEFAULT_ANONYMOUS_ID;
import static org.apache.jackrabbit.oak.spi.security.user.UserConstants.PARAM_ANONYMOUS_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds Apache Jackrabbit Oak authorization configurations which make the anonymous principal or the
 * everyone principal an administrator.
 * <p>
 * Oak grants full permissions on the whole repository to every principal listed in
 * {@value #ADMINISTRATIVE_PRINCIPALS_PROPERTY} of {@value #AUTHORIZATION_PID}. Two principals must never be
 * listed there:
 * <ul>
 * <li>{@value EveryonePrincipal#NAME}: every session, including the one of requests without credentials, has this
 * principal.</li>
 * <li>The anonymous principal: the session of requests without credentials. Its name is the value of
 * {@value org.apache.jackrabbit.oak.spi.security.user.UserConstants#PARAM_ANONYMOUS_ID} in {@value #USER_PID}, or
 * {@value org.apache.jackrabbit.oak.spi.security.user.UserConstants#DEFAULT_ANONYMOUS_ID} if that is not set.</li>
 * </ul>
 * The package may contain several user configurations (e.g. one per run mode folder). Each of them may use a
 * different anonymous ID, and a user configuration without {@code anonymousId} uses the default one. So every
 * anonymous ID which could become active is checked: the configured ones, plus the default if no user configuration
 * exists or one of them does not set the ID.
 * <p>
 * The match is exact, like Oak's own check, which compares principal names with {@code Set.contains}:
 * {@code "Anonymous"} or {@code " everyone"} do not grant anything and are not reported.
 * <p>
 * Only the formats {@code <pid>.cfg.json} and {@code <pid>.config} are read. Files which cannot be parsed are
 * skipped, because the OSGi installer does not apply them either.
 */
public final class AdministrativePrincipalsCheck {

    /** PID of the Oak authorization configuration. */
    public static final String AUTHORIZATION_PID = "org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl";

    /** Principals that are granted full permissions on the complete repository. */
    public static final String ADMINISTRATIVE_PRINCIPALS_PROPERTY = "administrativePrincipals";

    /** PID of the Oak user configuration, which may change the name of the anonymous user. */
    public static final String USER_PID = "org.apache.jackrabbit.oak.security.user.UserConfigurationImpl";

    private AdministrativePrincipalsCheck() {
        // only static methods
    }

    /**
     * Checks the OSGi configurations of a package.
     *
     * @param files all files of the package below {@code jcr_root}: the key is the path in the package, the value
     *            the file content. Files which are not OSGi configurations are ignored.
     * @return one error message per authorization configuration that lists a forbidden principal; empty if the
     *         configurations are fine
     */
    public static @NotNull List<String> check(@NotNull Map<String, byte[]> files) {
        Set<String> forbiddenPrincipals = getForbiddenPrincipals(files);

        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String fileName = file.getKey();
            if (!AUTHORIZATION_PID.equals(OsgiConfigurationFiles.getPid(fileName))) {
                continue;
            }
            Dictionary<String, Object> properties = readOrNull(fileName, file.getValue());
            if (properties == null) {
                continue;
            }
            Set<String> found = new LinkedHashSet<>(toStrings(properties.get(ADMINISTRATIVE_PRINCIPALS_PROPERTY)));
            found.retainAll(forbiddenPrincipals);
            if (!found.isEmpty()) {
                errors.add("OSGi configuration " + fileName + " lists the principal(s) " + quote(found) + " in '"
                        + ADMINISTRATIVE_PRINCIPALS_PROPERTY + "'. This would give full permissions on the whole repository "
                        + "to requests without credentials. Remove them from '" + ADMINISTRATIVE_PRINCIPALS_PROPERTY + "'.");
            }
        }
        return errors;
    }

    /**
     * Returns the principals which must not be administrators: everyone and every anonymous ID which could be
     * active with the user configurations of the package.
     *
     * @param files the package files, as for {@link #check(Map)}
     * @return the forbidden principal names, in a stable order
     */
    static @NotNull Set<String> getForbiddenPrincipals(@NotNull Map<String, byte[]> files) {
        Set<String> forbidden = new LinkedHashSet<>();
        forbidden.add(EveryonePrincipal.NAME);

        boolean defaultAnonymousIdPossible = true; // true as long as no user configuration was found
        boolean userConfigurationFound = false;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (!USER_PID.equals(OsgiConfigurationFiles.getPid(file.getKey()))) {
                continue;
            }
            Dictionary<String, Object> properties = readOrNull(file.getKey(), file.getValue());
            if (properties == null) {
                continue;
            }
            if (!userConfigurationFound) {
                userConfigurationFound = true;
                defaultAnonymousIdPossible = false; // only possible again if a user configuration does not set the ID
            }
            Object anonymousId = properties.get(PARAM_ANONYMOUS_ID);
            if (anonymousId instanceof String && !((String) anonymousId).isEmpty()) {
                forbidden.add((String) anonymousId);
            } else {
                // Oak falls back to the default when the property is missing; an empty value cannot name a
                // principal, so check the default as well to be on the safe side
                defaultAnonymousIdPossible = true;
            }
        }
        if (defaultAnonymousIdPossible) {
            forbidden.add(DEFAULT_ANONYMOUS_ID);
        }
        return forbidden;
    }

    /** Parses a configuration file, or returns {@code null} if it cannot be parsed. */
    private static @Nullable Dictionary<String, Object> readOrNull(String fileName, byte[] content) {
        try {
            return OsgiConfigurationFiles.read(fileName, content);
        } catch (IOException | RuntimeException e) {
            return null; // the OSGi installer does not apply it either; other validators report broken files
        }
    }

    /**
     * Oak accepts the value as an array (the usual case), as a collection (the {@code ( ... )} syntax of the
     * {@code .config} format) or as a single String.
     */
    private static Collection<String> toStrings(@Nullable Object value) {
        Collection<?> values;
        if (value instanceof Object[]) {
            values = Arrays.asList((Object[]) value);
        } else if (value instanceof Collection) {
            values = (Collection<?>) value;
        } else if (value instanceof String) {
            values = Collections.singleton(value);
        } else {
            return Collections.emptyList();
        }
        List<String> strings = new ArrayList<>();
        for (Object element : values) {
            if (element instanceof String) {
                strings.add((String) element);
            }
        }
        return strings;
    }

    private static String quote(Collection<String> names) {
        List<String> quoted = new ArrayList<>();
        for (String name : names) {
            quoted.add("'" + name + "'");
        }
        return String.join(", ", quoted);
    }
}
