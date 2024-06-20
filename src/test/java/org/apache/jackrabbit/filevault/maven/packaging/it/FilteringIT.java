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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.maven.it.VerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests the behaviour of Maven filtering in packages.
 */
@ExtendWith(ProjectBuilderExtension.class)
class FilteringIT {

    private static void verify(ProjectBuilder projectBuilder, String projectName, boolean enableJcrRootFiltering, boolean enableMetaInfFiltering, String filteredFilePatterns, Map<String,String> properties) throws VerificationException, IOException {
        projectBuilder
                .setTestProjectDir("filtering-tests/" + projectName)
                .setProperty("vault.enableMetaInfFiltering", Boolean.toString(enableMetaInfFiltering))
                .setProperty("vault.enableJcrRootFiltering", Boolean.toString(enableJcrRootFiltering));
        if (StringUtils.isNotBlank(filteredFilePatterns)) {
            projectBuilder.setProperty("vault.filteredFilePatterns", filteredFilePatterns);
        }
        if (properties != null) {
            properties.entrySet().stream().forEach(e -> projectBuilder.setProperty(e.getKey(), e.getValue()));
        }
        projectBuilder
                .build();
    }

    @Test
    void test_simple_filter_with_filtering_enabled(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "simple-filter", true, true, null, null);
            // cannot check checksum of properties.xml as that one has platform dependent new lines!
            //.verifyExpectedFileChecksum("META-INF/vault/properties.xml", "295fb69e")
        projectBuilder.verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "10791371")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "7563f01d")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/child/.content.xml", "d9b1ad2");
    }

    @Test
    void test_simple_filter_with_filtering_enabled_and_complex_values(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "simple-filter", true, true, null, Collections.singletonMap("customKey1", "<>&test"));
        // check for correct escaping
        projectBuilder.verifyExpectedFileChecksum("jcr_root/apps/bar/escaping/.content.xml", "7022c3d3");
    }

    @Test
    void test_simple_filter_with_filtering_disabled(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "simple-filter", false, false, null, null);
            // cannot check checksum of properties.xml as that one has platform dependent new lines!
            //.verifyExpectedFileChecksum("META-INF/vault/properties.xml", "5953911b")
        projectBuilder.verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "34e5a01d")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "a41ae6f8")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/child/.content.xml", "f8aee8df");
    }

    @Test
    void test_simple_filter_with_filtering_enabled_on_jcrroot(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "simple-filter", true, false, null, null);
            // cannot check checksum of properties.xml as that one has platform dependent new lines!
            //.verifyExpectedFileChecksum("META-INF/vault/properties.xml", "5953911b")
        projectBuilder.verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "10791371")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "7563f01d")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/child/.content.xml", "d9b1ad2");
    }

    @Test
    void test_simple_filter_with_filtering_enabled_on_metainf(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "simple-filter", false, true, null, null);
            // cannot check checksum of properties.xml as that one has platform dependent new lines!
            //.verifyExpectedFileChecksum("META-INF/vault/properties.xml", "295fb69e")
        projectBuilder.verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "34e5a01d")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "a41ae6f8")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/child/.content.xml", "f8aee8df");
    }

    @Test
    void test_simple_filter_with_filtering_partially_enabled_on_jcrroot(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "simple-filter", true, false, "**/child/*.xml", null);
            // cannot check checksum of properties.xml as that one has platform dependent new lines!
            //.verifyExpectedFileChecksum("META-INF/vault/properties.xml", "5953911b")
        projectBuilder.verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "34e5a01d")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "a41ae6f8")
            .verifyExpectedFileChecksum("jcr_root/apps/bar/.content.xml", "f8aee8df")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/child/.content.xml", "d9b1ad2");
    }
}
