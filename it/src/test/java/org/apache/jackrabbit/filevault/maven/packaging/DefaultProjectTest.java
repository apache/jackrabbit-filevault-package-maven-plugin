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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.util.FileUtils;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class DefaultProjectTest extends PackageTestBase {

    private static final String TEST_PROJECT_NAME = "/default-test-projects";

    protected File getProjectDirectory() {
        return new File(TEST_PROJECTS_ROOT + TEST_PROJECT_NAME);
    }


    private void verify(String projectName) throws VerificationException, IOException {
        // this is a bit awkward. todo: make better testing framework
        final File projectDir = new File(getProjectDirectory(), projectName);

        File testPackageFile = buildProject(projectDir, getDefaultProperties());
        assertThat(testPackageFile.exists(), is(true));

        List<String> expectedEntries = Files.readAllLines(new File(projectDir, "expected-files.txt").toPath(), StandardCharsets.UTF_8);
        List<String> expectedEntriesInOrder= Files.readAllLines(new File(projectDir, "expected-file-order.txt").toPath(), StandardCharsets.UTF_8);

        List<String> entries = new ArrayList<String>();
        try (JarFile jar = new JarFile(testPackageFile)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                entries.add(e.nextElement().getName());
            }
        }

        // ensure that MANIFEST.MF is first entry
        String first = entries.get(0);
        if ("META-INF/".equals(first)) {
            first = entries.get(1);
        }
        assertEquals("MANIFEST.MF must be first entry", "META-INF/MANIFEST.MF", first);

        // first check that only the expected entries are there in the package (regardless of the order)
        assertThat("Package contains the expected entry names", entries, Matchers.containsInAnyOrder(expectedEntries.toArray()));

        // then check order of some of the entries
        assertThat("Order of entries within package", entries, Matchers.containsInRelativeOrder(expectedEntriesInOrder.toArray()));

        String expectedManifest = FileUtils.fileRead(new File(projectDir, "expected-manifest.txt"));
        verifyManifest(testPackageFile, expectedManifest);

    }

    @Test
    public void generic_project_package_contains_correct_files() throws Exception {
        verify("generic");
    }

    @Test
    public void resource_project_package_contains_correct_files() throws Exception {
        verify("resource");
    }
}
