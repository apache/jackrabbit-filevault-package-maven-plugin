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

    private ProjectBuilder verify(String projectName, boolean enableJcrRootFiltering, boolean enableMetaInfFiltering, String ... goals) throws VerificationException, IOException {
        return new ProjectBuilder()
                .setTestProjectDir("filtering-tests/" + projectName)
                .setTestGoals(goals)
                .setProperty("vault.enableMetaInfFiltering", Boolean.toString(enableMetaInfFiltering))
                .setProperty("vault.enableJcrRootFiltering", Boolean.toString(enableJcrRootFiltering))
                .build()
                .verifyExpectedFiles();
    }

    @Test
    public void test_simple_filter_with_filtering_enabled() throws Exception {
        verify("simple-filter", true, true)
            .verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "10791371")
            .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "7563f01d");
    }

    @Test
    public void test_simple_filter_with_filtering_disabled() throws Exception {
        verify("simple-filter", false, false)
        .verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "34e5a01d")
        .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "a41ae6f8");
    }

    @Test
    public void test_simple_filter_with_filtering_partially_enabled() throws Exception {
        verify("simple-filter", true, false)
        .verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "10791371")
        .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "7563f01d");
    }

    @Test
    public void test_simple_filter_with_filtering_partially_enabled2() throws Exception {
        verify("simple-filter", false, true)
        .verifyExpectedFileChecksum("jcr_root/apps/bar/test1.properties", "34e5a01d")
        .verifyExpectedFileChecksum("jcr_root/apps/foo/test2.properties", "a41ae6f8");
    }
}
