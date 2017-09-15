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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.it.util.FileUtils;
import org.apache.maven.it.util.StringUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class DefaultProjectTest extends PackageTestBase {

    private static final String TEST_PROJECT_NAME = "/default-test-project";

    protected File getProjectDirectory() {
        return new File(TEST_PROJECTS_ROOT + TEST_PROJECT_NAME);
    }

    @Test
    public void package_contains_correct_files() throws Exception {
        File testPackageFile = buildProject(getDefaultProperties());
        assertThat(testPackageFile.exists(), is(true));

        String expected = FileUtils.fileRead(new File(testProjectDir, "expected-files.txt"));

        List<String> entries = new ArrayList<String>();
        JarFile jar = new JarFile(testPackageFile);
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            entries.add(e.nextElement().getName());
        }

        // ensure that MANIFEST.MF is first entry
        String first = entries.get(0);
        if ("META-INF/".equals(first)) {
            first = entries.get(1);
        }
        assertEquals("MANIFEST.MF must be first entry", "META-INF/MANIFEST.MF", first);

        // (we don't sort the files so that we can test if the filter order is respected)
        String result = StringUtils.join(entries.iterator(),"\n");
        assertEquals("File List", expected, result);

        String expectedManifest = FileUtils.fileRead(new File(testProjectDir, "expected-manifest.txt"));
        verifyManifest(testPackageFile, expectedManifest);

    }
}
