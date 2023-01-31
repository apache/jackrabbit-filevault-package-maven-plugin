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

import java.nio.file.Paths;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProjectBuilderExtension.class)
class ValidateFilesIT {

    @Test
    void testInvalidProject(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir("/validator-projects/invalid-project")
            .setTestGoals("clean", "package") // make sure the validate-files mojo is not skipped
            .setBuildExpectedToFail(true)
            .build()
            .verifyExpectedLogLines(Paths.get("META-INF","vault","filter.xml").toString());
    }

    @Test
    void testValidProjectWithZip(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
            .setTestProjectDir("/validator-projects/valid-project-with-zip")
            .setTestGoals("clean", "package") // make sure the validate-files mojo is not skipped
            .build();
    }
}
