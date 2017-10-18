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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.FileUtils;
import org.apache.maven.it.util.StringUtils;
import org.junit.Before;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public abstract class PackageTestBase {

    private static final Set<String> IGNORED_MANIFEST_ENTRIES = new HashSet<String>(Arrays.asList("Build-Jdk", "Built-By"));

    static final String TEST_PROJECTS_ROOT = "target/test-classes/test-projects";

    static final String TEST_PACKAGE_DEFAULT_NAME = "target/package-plugin-test-pkg-1.0.0-SNAPSHOT.zip";

    File testProjectDir;

    @Before
    public void setup() {
        testProjectDir = getProjectDirectory();
    }

    protected abstract File getProjectDirectory();

    File getTestPackageFile() {
        return getTestPackageFile(testProjectDir);
    }

    File getTestPackageFile(File projectDir) {
        return new File(projectDir, TEST_PACKAGE_DEFAULT_NAME);
    }

    Properties getDefaultProperties() {
        final Properties props = new Properties();
        props.put("plugin.version", System.getProperty("plugin.version"));
        props.put("testcontent.directory", new File("target/test-classes/test-content").getAbsolutePath());
        return props;
    }

    File buildProject(Properties props) throws VerificationException {
        return buildProject(testProjectDir, props);
    }

    File buildProject(File projectDir, Properties props) throws VerificationException {
        Verifier verifier = new Verifier(projectDir.getAbsolutePath());
        verifier.setSystemProperties(props);
        verifier.setDebug(true);
        verifier.setAutoclean(true);
        verifier.executeGoal("package");

        File testPackageFile = getTestPackageFile(projectDir);
        assertThat(testPackageFile.exists(), is(true));
        return testPackageFile;
    }

    void verifyManifest(File testPackageFile, String expected) throws IOException {
        JarFile jar = new JarFile(testPackageFile);

        List<String> entries = new ArrayList<String>();
        for (Map.Entry<Object, Object> e: jar.getManifest().getMainAttributes().entrySet()) {
            String key = e.getKey().toString();
            if (IGNORED_MANIFEST_ENTRIES.contains(key)) {
                continue;
            }
            if ("Import-Package".equals(key)) {
                // split export package so that we have a sorted set
                Parameters params = new Parameters(e.getValue().toString());
                for (Map.Entry<String, Attrs> entry : params.entrySet()) {
                    entries.add(key + ":" + entry.getKey() + ";" + entry.getValue());
                }
                continue;
            }
            entries.add(e.getKey() + ":" + e.getValue());
        }
        Collections.sort(entries);
        String result = StringUtils.join(entries.iterator(),"\n");
        assertEquals("Manifest", expected, result);
    }
}
