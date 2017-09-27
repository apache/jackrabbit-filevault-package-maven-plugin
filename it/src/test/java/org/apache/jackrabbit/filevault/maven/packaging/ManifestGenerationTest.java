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
package org.apache.jackrabbit.filevault.maven.packaging;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import org.apache.maven.it.util.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class ManifestGenerationTest extends PackageTestBase {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"/manifest-generation/simple", true},
                {"/manifest-generation/with-bundles", true},
                {"/manifest-generation/with-code", true},
                {"/manifest-generation/with-unused-dependencies", true}
        });
    }

    private final String projectName;

    private final boolean expectedToPass;

    public ManifestGenerationTest(String projectName, boolean expectedToPass) {
        this.projectName = projectName;
        this.expectedToPass = expectedToPass;
    }

    protected File getProjectDirectory() {
        return new File(TEST_PROJECTS_ROOT + projectName);
    }

    @Test
    public void manifest_generation_is_correct() throws Exception {
        File testPackageFile = buildProject(getDefaultProperties());
        String expected = FileUtils.fileRead(new File(testProjectDir, "expected-manifest.txt"));
        verifyManifest(testPackageFile, expected);
    }
}
