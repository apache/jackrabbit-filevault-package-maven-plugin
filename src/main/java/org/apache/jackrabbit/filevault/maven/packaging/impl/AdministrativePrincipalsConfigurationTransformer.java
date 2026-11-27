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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.components.io.functions.InputStreamTransformer;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.util.IOUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Removes the anonymous principal from the administrative principals of the Apache Jackrabbit Oak
 * authorization configuration while the file is added to the content package.
 * <p>
 * Oak grants full permissions on the whole repository to every principal listed in
 * {@value #ADMINISTRATIVE_PRINCIPALS_PROPERTY} of {@value #AUTHORIZATION_PID}. If
 * {@value #ANONYMOUS_PRINCIPAL} is listed there, every request without credentials gets those
 * permissions.
 * <p>
 * When a {@code .cfg.json} or {@code .config} file of that PID lists {@value #ANONYMOUS_PRINCIPAL},
 * this transformer removes every such entry, keeps the other entries in their order and logs a
 * warning. The match is exact, like Oak's own check, which compares principal names with
 * {@code Set.contains}: {@code "Anonymous"} or {@code " anonymous"} do not grant anything to the
 * anonymous principal and are left alone.
 * <p>
 * Oak accepts the value as an array (the usual case), as a collection (the {@code ( ... )} syntax
 * of the {@code .config} format) or as a single String. A single String {@value #ANONYMOUS_PRINCIPAL}
 * removes the property, because nothing is left in it.
 * <p>
 * All other files and values pass through unchanged. The source file on disk is never modified,
 * only the copy in the package.
 */
public class AdministrativePrincipalsConfigurationTransformer implements InputStreamTransformer {

    /** PID of the Oak authorization configuration. */
    public static final String AUTHORIZATION_PID = "org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl";

    /** Principals that are granted full permissions on the complete repository. */
    public static final String ADMINISTRATIVE_PRINCIPALS_PROPERTY = "administrativePrincipals";

    /** Name of the principal of requests without credentials. */
    public static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private final Log log;

    /** The archiver may read the same file several times (e.g. for its checksum), so each warning is only logged once. */
    private final Set<String> loggedWarnings = new HashSet<>();

    /**
     * @param log the Maven log, used for the warning when a configuration is changed
     */
    public AdministrativePrincipalsConfigurationTransformer(@NotNull Log log) {
        this.log = log;
    }

    @Override
    public @NotNull InputStream transform(@NotNull PlexusIoResource resource, @NotNull InputStream inputStream) throws IOException {
        String fileName = resource.getName();
        if (!AUTHORIZATION_PID.equals(OsgiConfigurationFiles.getPid(fileName))) {
            return inputStream; // not the authorization configuration: leave the stream untouched
        }

        // configuration files are small, so reading them fully into memory is fine
        byte[] content = IOUtil.toByteArray(inputStream);
        byte[] adjustedContent = adjust(fileName, content);
        return new ByteArrayInputStream(adjustedContent);
    }

    /**
     * Returns the content with the anonymous principal removed from the administrative principals,
     * or the original content if nothing had to change (or the file cannot be parsed).
     *
     * @param fileName the configuration file name, used to pick the format and in the warning
     * @param content the original file content
     * @return the content to put into the package
     * @throws IOException if the adjusted configuration cannot be serialized
     */
    byte[] adjust(@NotNull String fileName, @NotNull byte[] content) throws IOException {
        Dictionary<String, Object> properties;
        try {
            properties = OsgiConfigurationFiles.read(fileName, content);
        } catch (IOException | RuntimeException e) {
            // an unparsable file is not applied by Sling either; leave it to the validators
            warnOnce("Could not parse OSGi configuration " + fileName + ", leaving it unchanged: " + e.getMessage());
            return content;
        }

        Object value = properties.get(ADMINISTRATIVE_PRINCIPALS_PROPERTY);
        if (value instanceof Object[]) {
            List<Object> principals = removeAnonymous(Arrays.asList((Object[]) value));
            if (principals.size() == ((Object[]) value).length) {
                return content; // anonymous was not listed
            }
            properties.put(ADMINISTRATIVE_PRINCIPALS_PROPERTY, principals.toArray(new String[0]));
        } else if (value instanceof Collection) {
            List<Object> principals = removeAnonymous((Collection<?>) value);
            if (principals.size() == ((Collection<?>) value).size()) {
                return content; // anonymous was not listed
            }
            properties.put(ADMINISTRATIVE_PRINCIPALS_PROPERTY, principals);
        } else if (ANONYMOUS_PRINCIPAL.equals(value)) {
            properties.remove(ADMINISTRATIVE_PRINCIPALS_PROPERTY);
        } else {
            return content;
        }

        warnOnce("OSGi configuration " + fileName + ": principal '" + ANONYMOUS_PRINCIPAL + "' must not be listed in '"
                + ADMINISTRATIVE_PRINCIPALS_PROPERTY + "', because requests without credentials would get full permissions "
                + "on the repository. Removed it from the packaged file.");
        return OsgiConfigurationFiles.write(fileName, properties);
    }

    /** Returns a copy of the principals without the anonymous principal, keeping the order of the rest. */
    private static List<Object> removeAnonymous(Collection<?> principals) {
        List<Object> kept = new ArrayList<>();
        for (Object principal : principals) {
            if (!ANONYMOUS_PRINCIPAL.equals(principal)) {
                kept.add(principal);
            }
        }
        return kept;
    }

    private void warnOnce(String message) {
        if (loggedWarnings.add(message)) {
            log.warn(message);
        }
    }
}
