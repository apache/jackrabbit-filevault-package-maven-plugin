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
import java.nio.file.Paths;

import org.apache.maven.it.VerificationException;
import org.junit.Test;

public class ValidatePackageIT {

    private static final String TEST_PROJECT_NAME = "/validator-projects/";

    private ProjectBuilder verify(String projectName) throws VerificationException, IOException {
        return new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + projectName)
                .setBuildExpectedToFail(true)
                .build()
                .verifyExpectedLogLines(Paths.get("META-INF", "vault", "filter.xml").toString());
    }

    @Test
    public void testInvalidProject() throws Exception {
        verify("invalid-project");
    }
}
