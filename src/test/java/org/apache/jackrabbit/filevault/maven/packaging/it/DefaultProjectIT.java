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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.maven.it.VerificationException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.number.OrderingComparison;
import org.junit.Assert;
import org.junit.Test;

public class DefaultProjectIT {

    private static final String TEST_PROJECT_NAME = "/default-test-projects/";

    private ProjectBuilder verify(String projectName) throws VerificationException, IOException {
        return new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + projectName)
                .build()
                .verifyExpectedFiles()
                .verifyExpectedFilesOrder()
                .verifyExpectedManifest();

    }

    @Test
    public void generic_project_package_contains_correct_files() throws Exception {
        // the created date is fixed in the pom, as this is a reproducible build
        String createdDate = verify("generic").getPackageProperty("created");
        Calendar expectedDate = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        DateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssXXX" );
        expectedDate.setTime(df.parse("2019-10-02T08:04:00Z"));
        expectedDate.setLenient(false);
        Calendar date = ISO8601.parse(createdDate);
        assertNotNull("The created date is not compliant to the ISO8601 profile defined in https://www.w3.org/TR/NOTE-datetime", date);
        // check actual value
        assertEquals(expectedDate, date);
    }

    @Test
    public void generic_project_is_reproducible() throws Exception {
        ProjectBuilder builder = verify("generic");
        // MD5
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(builder.getTestPackageFile());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            IOUtils.copy(dis, new NullOutputStream());
        }
        byte[] digest1 = md.digest();
        builder = verify("generic");
        // MD5
        md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(builder.getTestPackageFile());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            IOUtils.copy(dis, new NullOutputStream());
        }
        byte[] digest2 = md.digest();
        Assert.assertArrayEquals(digest1, digest2);
    }
 
    @Test
    public void generic_project_package_with_metainf_contains_correct_files() throws Exception {
        verify("generic-with-metainf");
    }

    @Test
    public void generic_with_builtcd_project_package_contains_correct_files() throws Exception {
        verify("generic-with-builtcd");
    }

    @Test
    public void resource_project_package_contains_correct_files() throws Exception {
        verify("resource");
    }

    @Test
    public void unusual_jcr_root_package_contains_correct_files() throws Exception {
        verify("generic-unusal-jcrroot");
    }

    @Test
    public void generic_empty_directories() throws Exception {
        Calendar dateBeforeRun = Calendar.getInstance();
        String createdDate = new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "generic-empty-directories")
                .build()
                .verifyExpectedFiles().getPackageProperty("created");
        Calendar dateAfterRun = Calendar.getInstance();
        Calendar date = ISO8601.parse(createdDate);
        assertNotNull("The created date is not compliant to the ISO8601 profile defined in https://www.w3.org/TR/NOTE-datetime", date);
        // check actual value
        MatcherAssert.assertThat(date, OrderingComparison.greaterThan(dateBeforeRun));
        MatcherAssert.assertThat(date, OrderingComparison.lessThan(dateAfterRun));
    
    }

    @Test
    public void resource_empty_directories() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "resource-empty-directories")
                .build()
                .verifyExpectedFiles();
    }

    @Test
    public void htl_validation() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "htl-validation")
                .build()
                .verifyExpectedFiles()
                .verifyExpectedManifest();
    }

    @Test
    public void overwritten_embed() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "overwritten-embed")
                .setBuildExpectedToFail(true)
                .build();
    }

    @Test
    public void overwritten_embed_not_failing() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "overwritten-embed-not-failing")
                .build()
                .verifyExpectedFilesChecksum();
    }

    @Test
    public void empty_package() throws VerificationException, IOException {
        new ProjectBuilder()
        .setTestProjectDir(TEST_PROJECT_NAME + "empty")
        .build();
    }

    @Test
    public void additional_metainf_files() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "additional-metainf-files")
                .build()
                .verifyExpectedFiles()
                .verifyExpectedManifest();
    }

    @Test
    public void complex_package_properties() throws Exception {
        new ProjectBuilder()
        .setTestProjectDir(TEST_PROJECT_NAME + "complex-properties")
        .build()
        .verifyPackageProperty(PackageProperties.NAME_SUB_PACKAGE_HANDLING, "*:package1;ignore,group1:*;force_install,group1:package1");
    }
}
