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
package org.apache.jackrabbit.filevault.maven.packaging.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Dictionary;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.jackrabbit.filevault.maven.packaging.impl.OsgiConfigurationFiles;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests the OSGi configuration adjustments applied while packaging.
 */
@ExtendWith(ProjectBuilderExtension.class)
class OsgiConfigurationIT {

    private static final String AUTHENTICATOR = "org.apache.sling.engine.impl.auth.SlingAuthenticator";
    private static final String ADMIN_JSON = "jcr_root/apps/example/config/" + AUTHENTICATOR + ".cfg.json";
    private static final String ADMIN_CONFIG = "jcr_root/apps/example/config.publish/" + AUTHENTICATOR + ".config";
    private static final String ANONYMOUS_JSON = "jcr_root/apps/example/config.author/" + AUTHENTICATOR + ".cfg.json";

    private static byte[] readEntry(ProjectBuilder projectBuilder, String name) throws IOException {
        try (JarFile packageFile = new JarFile(projectBuilder.getTestPackageFile())) {
            JarEntry entry = packageFile.getJarEntry(name);
            assertNotNull(entry, "Missing entry " + name);
            try (InputStream input = packageFile.getInputStream(entry)) {
                return IOUtil.toByteArray(input);
            }
        }
    }

    private static byte[] readSource(ProjectBuilder projectBuilder, String name) throws IOException {
        return Files.readAllBytes(new File(projectBuilder.getTestProjectDir(), name).toPath());
    }

    private static List<String> warningsAbout(ProjectBuilder projectBuilder, String property) throws IOException {
        return projectBuilder.getBuildOutput().stream()
                .filter(line -> line.startsWith("[WARNING]") && line.contains("'" + property + "'"))
                .collect(Collectors.toList());
    }

    @Test
    void test_admin_anonymous_user_removed_when_enabled(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/anonymous-user")
                .setProperty("vault.removeAdminAsAnonymousUser", "true")
                .build();

        for (String name : new String[] {ADMIN_JSON, ADMIN_CONFIG}) {
            Dictionary<String, Object> properties = OsgiConfigurationFiles.read(name, readEntry(projectBuilder, name));
            assertNull(properties.get("sling.auth.anonymous.user"), name);
            assertNull(properties.get("sling.auth.anonymous.password"), name);
        }
        // unrelated property in the same file is kept
        assertEquals(Boolean.FALSE,
                OsgiConfigurationFiles.read(ADMIN_JSON, readEntry(projectBuilder, ADMIN_JSON)).get("auth.annonymous"));
        // a configuration with another anonymous user is copied unchanged
        assertArrayEquals(readSource(projectBuilder, ANONYMOUS_JSON), readEntry(projectBuilder, ANONYMOUS_JSON));
        // the source files are not modified
        assertTrue(new String(readSource(projectBuilder, ADMIN_JSON), StandardCharsets.UTF_8).contains("\"admin\""));

        List<String> warnings = warningsAbout(projectBuilder, "sling.auth.anonymous.user");
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().noneMatch(line -> line.contains("example-password")), warnings.toString());
    }

    @Test
    void test_admin_anonymous_user_kept_by_default(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/anonymous-user")
                .build();

        for (String name : new String[] {ADMIN_JSON, ADMIN_CONFIG, ANONYMOUS_JSON}) {
            assertArrayEquals(readSource(projectBuilder, name), readEntry(projectBuilder, name), name);
        }
        assertTrue(warningsAbout(projectBuilder, "sling.auth.anonymous.user").isEmpty(),
                "no adjustment warning expected when the parameter is not set");
    }
}
