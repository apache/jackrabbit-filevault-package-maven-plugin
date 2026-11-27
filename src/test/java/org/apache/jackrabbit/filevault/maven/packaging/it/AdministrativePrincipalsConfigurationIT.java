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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Tests removing the anonymous principal from the Oak administrative principals while packaging.
 */
@ExtendWith(ProjectBuilderExtension.class)
class AdministrativePrincipalsConfigurationIT {

    private static final String AUTHORIZATION = "org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl";
    private static final String ANONYMOUS_JSON = "jcr_root/apps/example/config/" + AUTHORIZATION + ".cfg.json";
    private static final String ANONYMOUS_CONFIG = "jcr_root/apps/example/config.publish/" + AUTHORIZATION + ".config";
    private static final String ADMINISTRATORS_ONLY_JSON = "jcr_root/apps/example/config.author/" + AUTHORIZATION + ".cfg.json";

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

    private static String[] packagedPrincipals(ProjectBuilder projectBuilder, String name) throws IOException {
        return (String[]) OsgiConfigurationFiles.read(name, readEntry(projectBuilder, name)).get("administrativePrincipals");
    }

    private static List<String> adjustmentWarnings(ProjectBuilder projectBuilder) throws IOException {
        return projectBuilder.getBuildOutput().stream()
                .filter(line -> line.startsWith("[WARNING]") && line.contains("'administrativePrincipals'"))
                .collect(Collectors.toList());
    }

    @Test
    void test_anonymous_removed_from_administrative_principals_when_enabled(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/administrative-principals")
                .setProperty("vault.removeAnonymousFromAdministrativePrincipals", "true")
                .build();

        assertArrayEquals(new String[] {"administrators"}, packagedPrincipals(projectBuilder, ANONYMOUS_JSON));
        assertArrayEquals(new String[] {"administrators"}, packagedPrincipals(projectBuilder, ANONYMOUS_CONFIG));
        // unrelated property in the same file is kept
        assertEquals("besteffort",
                OsgiConfigurationFiles.read(ANONYMOUS_JSON, readEntry(projectBuilder, ANONYMOUS_JSON)).get("importBehavior"));
        // a configuration without anonymous is copied unchanged
        assertArrayEquals(readSource(projectBuilder, ADMINISTRATORS_ONLY_JSON), readEntry(projectBuilder, ADMINISTRATORS_ONLY_JSON));
        // the source files are not modified
        assertTrue(new String(readSource(projectBuilder, ANONYMOUS_JSON), StandardCharsets.UTF_8).contains("\"anonymous\""));

        List<String> warnings = adjustmentWarnings(projectBuilder);
        assertEquals(2, warnings.size(), warnings.toString());
    }

    @Test
    void test_anonymous_kept_by_default(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/administrative-principals")
                .build();

        for (String name : new String[] {ANONYMOUS_JSON, ANONYMOUS_CONFIG, ADMINISTRATORS_ONLY_JSON}) {
            assertArrayEquals(readSource(projectBuilder, name), readEntry(projectBuilder, name), name);
        }
        assertTrue(adjustmentWarnings(projectBuilder).isEmpty(), "no adjustment warning expected when the parameter is not set");
    }
}
