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

import org.junit.Test;

public class GenerateMetadataMultiModuleIT {

    /**
     * Tests that the generate-manifest goal generates the expected filter.xml when run on
     * inter-module dependencies in a multi-module setup for clean + test goals.
     */
    @Test
    public void multi_module_build_clean_test() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir("/generate-metadata-multimodule")
                .setTestGoals("clean", "test")
                .setVerifyPackageContents(false)
                .build()
                .verifyExpectedFilterInWorkDirectory("container/target/vault-work");
    }

    /**
     * Tests that the generate-manifest goal generates the expected filter.xml when run on
     * inter-module dependencies in a multi-module setup for clean + test goals.
     */
    @Test
    public void multi_module_build_clean_package() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir("/generate-metadata-multimodule")
                .setTestPackageFile("container/" + ProjectBuilder.TEST_PACKAGE_DEFAULT_NAME)
                .setTestGoals("clean", "package")
                .setVerifyPackageContents(false)
                .build()
                .verifyExpectedFilter();
    }
}
