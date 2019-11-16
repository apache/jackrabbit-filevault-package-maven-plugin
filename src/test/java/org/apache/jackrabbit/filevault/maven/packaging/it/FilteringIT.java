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

/**
 * Tests the behaviour of Maven filtering in packages.
 */
public class FilteringIT {

    private ProjectBuilder verify(String projectName, String expectedFilesWithChecksumsFile, boolean enableJcrRootFiltering, boolean enableMetaInfFiltering, String ... goals) throws VerificationException, IOException {
        return new ProjectBuilder()
                .setTestProjectDir("filtering-tests/" + projectName)
                .setTestGoals(goals)
                .setProperty("vault.enableMetaInfFiltering", Boolean.toString(enableMetaInfFiltering))
                .setProperty("vault.enableJcrRootFiltering", Boolean.toString(enableJcrRootFiltering))
                .setExpectedFilesWithChecksumsFile(expectedFilesWithChecksumsFile)
                .build() 
                .verifyExpectedFilesChecksum();
    }

    /**
     * Tests if a pom with no filter definition at all fails.
     */
    @Test
    public void test_simple_filter_with_filtering_enabled() throws Exception {
        verify("simple-filter", "expected-files-with-checksums-filtered.txt", true, true);
    }

    /**
     * Tests if a pom with no filter definition at all fails.
     */
    @Test
    public void test_simple_filter_with_filtering_disabled() throws Exception {
        verify("simple-filter", "expected-files-with-checksums-unfiltered.txt", false, false);
    }
}
