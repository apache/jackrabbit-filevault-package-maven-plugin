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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.util.FileUtils;
import org.apache.maven.it.util.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EmptyDirectoriesTest extends PackageTestBase {

    private static final String TEST_PROJECT_NAME = "/empty-directories";

    protected File getProjectDirectory() {
        return new File(TEST_PROJECTS_ROOT + TEST_PROJECT_NAME);
    }

    private File buildProject(boolean includeEmptyDirs) throws VerificationException {
        final Properties props = getDefaultProperties();
        props.put("test.includeEmptyDirs", String.valueOf(includeEmptyDirs));
        return buildProject(props);
    }

    @Test
    public void package_contains_no_empty_dirs() throws Exception {
        File testPackageFile = buildProject(false);

        String expected = FileUtils.fileRead(new File(testProjectDir, "expected-files.txt"));

        List<String> entries = new ArrayList<String>();
        JarFile jar = new JarFile(testPackageFile);
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            entries.add(e.nextElement().getName());
        }

        Collections.sort(entries);
        String result = StringUtils.join(entries.iterator(),"\n");
        assertEquals("File List", expected, result);
    }

    @Test
    public void package_contains_empty_dirs() throws Exception {
        File testPackageFile = buildProject(true);

        String expected = FileUtils.fileRead(new File(testProjectDir, "expected-empty-files.txt"));

        List<String> entries = new ArrayList<String>();
        JarFile jar = new JarFile(testPackageFile);
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            entries.add(e.nextElement().getName());
        }

        Collections.sort(entries);
        String result = StringUtils.join(entries.iterator(),"\n");
        assertEquals("File List", expected, result);
    }
}
