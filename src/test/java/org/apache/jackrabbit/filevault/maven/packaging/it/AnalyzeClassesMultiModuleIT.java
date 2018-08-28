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

public class AnalyzeClassesMultiModuleIT {

    /**
     * Tests that the analyze classes module succeeds when run on
     * inter-module dependencies in a multi-module setup.
     */
    @Test
    public void multi_module_build_succeeds() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir("/analyze-classes-multimodule")
                .setTestGoals("clean", "test")
                .setVerifyPackageContents(false)
                .build();
    }
}
