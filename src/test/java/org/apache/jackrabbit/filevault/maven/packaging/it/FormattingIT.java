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

import org.apache.maven.it.VerificationException;
import org.junit.Test;

public class FormattingIT {

    private void verify(String project, String... formattedFiles) throws VerificationException, IOException {
        ProjectBuilder builder = new ProjectBuilder()
                .setTestProjectDir("format-xml-tests/" + project)
                .setTestGoals("filevault-package:format-xml")
                .setBuildExpectedToFail(false)
                .setVerifyPackageContents(false)
                .build();

        for (String formattedFile : formattedFiles) {
            String path = new File(builder.getTestProjectDir(), formattedFile).getAbsolutePath();
            builder.verifyExpectedLogLines(path);
        }
    }

    @Test
    public void test_format_xml_in_single_module() throws Exception {
        verify("singlemodule", "src/main/content/jcr_root/.content.xml");
    }

    @Test
    public void test_format_xml_in_reactor() throws Exception {
        verify("multimodule", "a/src/main/content/jcr_root/.content.xml", "b/src/main/content/jcr_root/.content.xml");
    }
}
