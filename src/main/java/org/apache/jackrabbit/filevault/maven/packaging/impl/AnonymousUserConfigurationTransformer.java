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
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.components.io.functions.InputStreamTransformer;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.util.IOUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Removes {@code admin} as the anonymous user from the Apache Sling authenticator configuration
 * while the file is added to the content package.
 * <p>
 * The Sling authenticator ({@value #AUTHENTICATOR_PID}) runs requests without credentials as the
 * user named in {@value #ANONYMOUS_USER_PROPERTY}. If that user is {@code admin}, every anonymous
 * visitor gets administrator privileges.
 * <p>
 * When a {@code .cfg.json} or {@code .config} file of that PID sets the user to {@code admin}
 * (ignoring case and surrounding spaces, because user IDs are looked up case-insensitively), this
 * transformer removes {@value #ANONYMOUS_USER_PROPERTY} and {@value #ANONYMOUS_PASSWORD_PROPERTY}
 * and logs a warning. Sling then uses its default anonymous user. All other files and values pass
 * through unchanged. The source file on disk is never modified, only the copy in the package.
 */
public class AnonymousUserConfigurationTransformer implements InputStreamTransformer {

    /** PID of the Sling authenticator configuration. */
    public static final String AUTHENTICATOR_PID = "org.apache.sling.engine.impl.auth.SlingAuthenticator";

    /** Name of the user that anonymous requests are executed as. */
    public static final String ANONYMOUS_USER_PROPERTY = "sling.auth.anonymous.user";

    /** Password used to log the anonymous user in. Never logged. */
    public static final String ANONYMOUS_PASSWORD_PROPERTY = "sling.auth.anonymous.password";

    /** The user that must never be used for anonymous requests, in lower case. */
    static final String FORBIDDEN_ANONYMOUS_USER = "admin";

    private final Log log;

    /** The archiver may read the same file several times (e.g. for its checksum), so each warning is only logged once. */
    private final Set<String> loggedWarnings = new HashSet<>();

    /**
     * @param log the Maven log, used for the warning when a configuration is changed
     */
    public AnonymousUserConfigurationTransformer(@NotNull Log log) {
        this.log = log;
    }

    @Override
    public @NotNull InputStream transform(@NotNull PlexusIoResource resource, @NotNull InputStream inputStream) throws IOException {
        String fileName = resource.getName();
        if (!AUTHENTICATOR_PID.equals(OsgiConfigurationFiles.getPid(fileName))) {
            return inputStream; // not the authenticator configuration: leave the stream untouched
        }

        // configuration files are small, so reading them fully into memory is fine
        byte[] content = IOUtil.toByteArray(inputStream);
        byte[] adjustedContent = adjust(fileName, content);
        return new ByteArrayInputStream(adjustedContent);
    }

    /**
     * Returns the content with the forbidden anonymous user removed, or the original content if
     * nothing had to change (or the file cannot be parsed).
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

        Object anonymousUser = properties.get(ANONYMOUS_USER_PROPERTY);
        if (!isForbiddenUser(anonymousUser)) {
            return content;
        }

        properties.remove(ANONYMOUS_USER_PROPERTY);
        properties.remove(ANONYMOUS_PASSWORD_PROPERTY);
        // the password value is never logged
        warnOnce("OSGi configuration " + fileName + ": property '" + ANONYMOUS_USER_PROPERTY + "' must not be set to '"
                + anonymousUser + "', because anonymous requests would get administrator privileges. Removed '"
                + ANONYMOUS_USER_PROPERTY + "' and '" + ANONYMOUS_PASSWORD_PROPERTY
                + "' from the packaged file, so anonymous requests use the default anonymous user.");
        return OsgiConfigurationFiles.write(fileName, properties);
    }

    private void warnOnce(String message) {
        if (loggedWarnings.add(message)) {
            log.warn(message);
        }
    }

    /**
     * @param value the configured anonymous user (a String)
     * @return {@code true} if it names the forbidden user, ignoring case and surrounding spaces
     */
    static boolean isForbiddenUser(Object value) {
        if (value == null) {
            return false;
        }
        String userName = String.valueOf(value).trim().toLowerCase(Locale.ENGLISH);
        return FORBIDDEN_ANONYMOUS_USER.equals(userName);
    }
}
