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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds Apache Sling authenticator configurations which run requests without credentials as {@code admin}.
 * <p>
 * The Sling authenticator ({@value #AUTHENTICATOR_PID}) runs requests without credentials as the user named in
 * {@value #ANONYMOUS_USER_PROPERTY}. If that user is {@code admin}, every anonymous visitor gets administrator
 * privileges.
 * <p>
 * The user name is compared ignoring case and surrounding spaces, because user IDs are looked up
 * case-insensitively. The value of {@value #ANONYMOUS_PASSWORD_PROPERTY} is never read into a message.
 * <p>
 * Only the formats {@code <pid>.cfg.json} and {@code <pid>.config} are read. Files which cannot be parsed are
 * skipped, because the OSGi installer does not apply them either.
 */
public final class AnonymousUserCheck {

    /** PID of the Sling authenticator configuration. */
    public static final String AUTHENTICATOR_PID = "org.apache.sling.engine.impl.auth.SlingAuthenticator";

    /** Name of the user that requests without credentials are executed as. */
    public static final String ANONYMOUS_USER_PROPERTY = "sling.auth.anonymous.user";

    /** Password used to log the anonymous user in. Never logged. */
    public static final String ANONYMOUS_PASSWORD_PROPERTY = "sling.auth.anonymous.password";

    /** The user that must never be used for requests without credentials, in lower case. */
    static final String FORBIDDEN_ANONYMOUS_USER = "admin";

    private AnonymousUserCheck() {
        // only static methods
    }

    /**
     * Checks the OSGi configurations of a package.
     *
     * @param files all files of the package below {@code jcr_root}: the key is the path in the package, the value
     *            the file content. Files which are not OSGi configurations are ignored.
     * @return one error message per authenticator configuration that uses {@code admin} as anonymous user; empty if
     *         the configurations are fine
     */
    public static @NotNull List<String> check(@NotNull Map<String, byte[]> files) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String fileName = file.getKey();
            if (!AUTHENTICATOR_PID.equals(OsgiConfigurationFiles.getPid(fileName))) {
                continue;
            }
            Dictionary<String, Object> properties = readOrNull(fileName, file.getValue());
            if (properties == null) {
                continue;
            }
            Object anonymousUser = properties.get(ANONYMOUS_USER_PROPERTY);
            if (isForbiddenUser(anonymousUser)) {
                // only the user name is part of the message, never the password
                errors.add("OSGi configuration " + fileName + " sets '" + ANONYMOUS_USER_PROPERTY + "' to '" + anonymousUser
                        + "'. Requests without credentials would get administrator privileges. Remove '"
                        + ANONYMOUS_USER_PROPERTY + "' and '" + ANONYMOUS_PASSWORD_PROPERTY + "' or use a user without "
                        + "administrator privileges.");
            }
        }
        return errors;
    }

    /**
     * @param value the configured anonymous user, a String
     * @return {@code true} if it names the forbidden user, ignoring case and surrounding spaces
     */
    static boolean isForbiddenUser(@Nullable Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String userName = ((String) value).trim().toLowerCase(Locale.ENGLISH);
        return FORBIDDEN_ANONYMOUS_USER.equals(userName);
    }

    /** Parses a configuration file, or returns {@code null} if it cannot be parsed. */
    private static @Nullable Dictionary<String, Object> readOrNull(String fileName, byte[] content) {
        try {
            return OsgiConfigurationFiles.read(fileName, content);
        } catch (IOException | RuntimeException e) {
            return null; // the OSGi installer does not apply it either; other validators report broken files
        }
    }
}
