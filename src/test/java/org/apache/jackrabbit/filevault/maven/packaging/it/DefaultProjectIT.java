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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.maven.shared.verifier.VerificationException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProjectBuilderExtension.class)
class DefaultProjectIT {

    private static final String TEST_PROJECT_NAME = "/default-test-projects/";

    @Test
    void generic_project_package_contains_correct_files(ProjectBuilder projectBuilder) throws Exception {
        // the created date is fixed in the pom, as this is a reproducible build
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "generic")
            .setTestPackageFile("target/package-plugin-test-pkg-1.0.0.zip")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedFilesOrder()
            .verifyExpectedManifest();
        String createdDate = projectBuilder.getPackageProperty("created");
        Calendar expectedDate = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        DateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssXXX" );
        expectedDate.setTime(df.parse("2019-10-02T08:04:00Z"));
        expectedDate.setLenient(false);
        Calendar date = ISO8601.parse(createdDate);
        assertNotNull(date, "The created date is not compliant to the ISO8601 profile defined in https://www.w3.org/TR/NOTE-datetime");
        // check actual value
        assertEquals(expectedDate, date);
    }

    @Test
    void generic_project_is_reproducible(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "generic")
            .setTestPackageFile("target/package-plugin-test-pkg-1.0.0.zip")
            .build();
        // MD5
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(projectBuilder.getTestPackageFile());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            IOUtils.copy(dis, new NullOutputStream());
        }
        byte[] digest1 = md.digest();
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "generic")
            .setTestPackageFile("target/package-plugin-test-pkg-1.0.0.zip")
            .build();
        // MD5
        md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(projectBuilder.getTestPackageFile());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            IOUtils.copy(dis, new NullOutputStream());
        }
        byte[] digest2 = md.digest();
        assertArrayEquals(digest1, digest2);
    }
 
    @Test
    void generic_project_package_with_metainf_contains_correct_files(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "generic-with-metainf")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedFilesOrder()
            .verifyExpectedManifest();
    }

    @Test
    void generic_with_builtcd_project_package_contains_correct_files(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "generic-with-builtcd")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedFilesOrder()
            .verifyExpectedManifest();
    }

    @Test
    void resource_project_package_contains_correct_files(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "resource")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedFilesOrder()
            .verifyExpectedManifest();
    }

    @Test
    void unusual_jcr_root_package_contains_correct_files(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "generic-unusal-jcrroot")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedFilesOrder()
            .verifyExpectedManifest();
    }

    @Test
    void generic_empty_directories(ProjectBuilder projectBuilder) throws Exception {
        Calendar dateBeforeRun = Calendar.getInstance();
        String createdDate = projectBuilder
                .setTestProjectDir(TEST_PROJECT_NAME + "generic-empty-directories")
                .build()
                .verifyExpectedFiles().getPackageProperty("created");
        Calendar dateAfterRun = Calendar.getInstance();
        Calendar date = ISO8601.parse(createdDate);
        assertNotNull(date, "The created date is not compliant to the ISO8601 profile defined in https://www.w3.org/TR/NOTE-datetime");
        // check actual value
        MatcherAssert.assertThat(date, OrderingComparison.greaterThan(dateBeforeRun));
        MatcherAssert.assertThat(date, OrderingComparison.lessThan(dateAfterRun));
    
    }

    @Test
    void resource_empty_directories(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "resource-empty-directories")
            .build()
            .verifyExpectedFiles();
    }

    @Test
    void htl_validation(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "htl-validation")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedManifest();
    }

    @Test
    void overwritten_embed(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "overwritten-embed")
            .setBuildExpectedToFail(true)
            .build();
    }

    @Test
    void overwritten_embed_not_failing(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "overwritten-embed-not-failing")
            .build()
            .verifyExpectedFilesChecksum();
    }

    @Test
    void empty_package(ProjectBuilder projectBuilder) throws VerificationException, IOException {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "empty")
            .build();
    }

    @Test
    void additional_metainf_files(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "additional-metainf-files")
            .build()
            .verifyExpectedFiles()
            .verifyExpectedManifest();
    }

    @Test
    void complex_package_properties(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir(TEST_PROJECT_NAME + "complex-properties")
            .build()
            .verifyPackageProperty(PackageProperties.NAME_SUB_PACKAGE_HANDLING, "*:package1;ignore,group1:*;force_install,group1:package1");
    }
}
