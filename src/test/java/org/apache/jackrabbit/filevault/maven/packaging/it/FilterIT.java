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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.maven.shared.verifier.VerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests the behaviour of package filters.
 */
@ExtendWith(ProjectBuilderExtension.class)
class FilterIT {

    private static void verify(ProjectBuilder projectBuilder, String projectName, boolean expectToFail, String ... goals) throws VerificationException, IOException {
        projectBuilder
                .setTestProjectDir("filter-tests/" + projectName)
                .setTestGoals(goals)
                .setBuildExpectedToFail(expectToFail)
                .build()
                .verifyExpectedFilter();
    }

    /**
     * Tests if a pom with no filter definition at all fails.
     */
    @Test
    void test_no_filter_fails(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "no-filter-fails", true);
    }

    /**
     * Tests if a project with an implicit filter defined with a resource based META-INF/vault/filter.xml is correctly built
     */
    @Test
    void test_implicit_filter(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "implicit-filter", false);
    }

    /**
     * Tests if a project with an implicit filter defined in META-INF/vault/filter.xml is correctly built
     */
    @Test
    void test_implicit_filter_via_metainf(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "implicit-filter-via-metainf", false);
    }

    /**
     * Tests if a project with an inline filter properly generates the filter.xml
     */
    @Test
    void test_inline_filter(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "inline-filter", false);
    }

    /**
     * Tests if a project with an inline filter and a filter source properly generates the merged filter.xml
     */
    @Test
    void test_merge_inline_filter(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "merge-inline-filter", false);
    }

    /**
     * Tests if a project with an inline filter and a filter source properly generates the merged filter.xml
     */
    @Test
    void test_merge_inline_filter_with_metainf(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "merge-inline-filter-metainf", false);
    }

    /**
     * Tests if a project with an filter source and no inline filters keeps the filter comments.
     */
    @Test
    void test_retain_filter_source(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "retain-filter-source", false);
    }

    /**
     * Tests if a project with no filter but a prefix creates the default root
     */
    @Test
    void test_no_filter_with_prefix(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "no-filter-with-prefix", false);
    }

    /**
     * Tests if a project with an inline filter and an implicit filter correctly uses the inline filters.
     */
    @Test
    void test_inline_and_implicit_filter_fails(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "inline-and-implicit-fails", true);
    }

    /**
     * Tests if a project with no filter file or inline filters and only a single embedded and sub package marked as filter entry
     * is creating a filter
     */
    @Test
    void test_no_filter_container(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "no-filter-container", false);
    }

    /**
     * Tests if a project with no filter file or inline filters and only a single embedded and sub package marked as filter entry
     * is creating a filter but inside an execution's configuration
     */
    @Test
    void test_no_filter_container_in_execution(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "no-filter-container-in-execution", false);
    }

    /**
     * Tests if a project with an inline filter executed twice works w/o clean
     */
    @Test
    void test_inline_filter_twice(ProjectBuilder projectBuilder) throws Exception {
        // first execute with default goals
        final File projectDir = new File("target/test-classes/test-projects/filter-tests/inline-filter-twice");
        Files.copy(projectDir.toPath().resolve("pom1.xml"), projectDir.toPath().resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(projectDir.toPath().resolve("expected-filter1.xml"), projectDir.toPath().resolve("expected-filter.xml"), StandardCopyOption.REPLACE_EXISTING);
        verify(projectBuilder, "inline-filter-twice", false);

        // copy marker to 'target' to ensure that clean is not executed
        Path marker = projectDir.toPath().resolve("target/marker.xml");
        Files.copy(projectDir.toPath().resolve("pom1.xml"), marker);

        Files.copy(projectDir.toPath().resolve("pom2.xml"), projectDir.toPath().resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(projectDir.toPath().resolve("expected-filter2.xml"), projectDir.toPath().resolve("expected-filter.xml"), StandardCopyOption.REPLACE_EXISTING);
        verify(projectBuilder, "inline-filter-twice", false, "package");

        assertTrue(Files.exists(marker), "Marker file still exists.");
    }

    @Test 
    void test_filter_not_covering_all_files(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "filter-not-covering-all-files", true);
        projectBuilder.verifyExpectedLogLines(new File("jcr_root/apps/.content.xml").toString());
    }
}
