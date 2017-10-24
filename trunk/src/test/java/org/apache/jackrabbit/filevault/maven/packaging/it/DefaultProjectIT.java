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

import org.apache.maven.it.VerificationException;
import org.junit.Test;

public class DefaultProjectIT {

    private static final String TEST_PROJECT_NAME = "/default-test-projects/";

    private void verify(String projectName) throws VerificationException, IOException {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + projectName)
                .build()
                .verifyExpectedFiles()
                .verifyExpectedFilesOrder()
                .verifyExpectedManifest();

    }

    @Test
    public void generic_project_package_contains_correct_files() throws Exception {
        verify("generic");
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
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "generic-empty-directories")
                .build()
                .verifyExpectedFiles();
    }

    @Test
    public void resource_empty_directories() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + "resource-empty-directories")
                .build()
                .verifyExpectedFiles();
    }
}
