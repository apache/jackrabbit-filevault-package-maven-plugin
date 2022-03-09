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

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.maven.it.VerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(ProjectBuilderExtension.class)
class FormattingIT {

    @TempDir
    File tmpFolder;

    private void verify(ProjectBuilder projectBuilder, String project, String... formattedFiles) throws VerificationException, IOException {
        // copy project to tmp folder as the test modifies the project (allows re-execution without clean/recompile)
        File sourceDirectory = new File(ProjectBuilder.TEST_PROJECTS_ROOT + "/format-xml-tests/" + project);
        FileUtils.copyDirectory(sourceDirectory, tmpFolder);
        projectBuilder
                .setTestProjectDir(tmpFolder)
                .setTestGoals("filevault-package:format-xml")
                .setBuildExpectedToFail(false)
                .setVerifyPackageContents(false)
                .build();

        for (String formattedFile : formattedFiles) {
            // use uppercase drive letter on Windows
            String path = new File(projectBuilder.getTestProjectDir(), formattedFile).getCanonicalPath();
            projectBuilder.verifyExpectedLogLines(path);
        }
    }

    @Test
    void test_format_xml_in_single_module(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "singlemodule", "src/main/content/jcr_root/.content.xml");
    }

    @Test
    void test_format_xml_in_reactor(ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, "multimodule", "a/src/main/content/jcr_root/.content.xml", "b/src/main/content/jcr_root/.content.xml");
    }
}
