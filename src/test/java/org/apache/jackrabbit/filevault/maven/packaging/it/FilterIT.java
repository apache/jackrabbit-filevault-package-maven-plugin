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
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.FileUtils;
import org.apache.maven.it.util.IOUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the behaviour of package filters.
 */
public class FilterIT {

    private void verify(String projectName, boolean expectToFail, String ... goals) throws VerificationException, IOException {
        final File projectDir = new File("target/test-classes/test-projects/filter-tests/" + projectName);
        final Properties props = new Properties();
        props.put("plugin.version", System.getProperty("plugin.version"));

        Verifier verifier = new Verifier(projectDir.getAbsolutePath());
        verifier.setSystemProperties(props);
        verifier.setAutoclean(false);
        if (goals == null || goals.length == 0) {
            goals = new String[]{"clean", "package"};
        }
        try {
            verifier.executeGoals(Arrays.asList(goals));
        } catch (VerificationException e) {
            if (expectToFail) {
                return;
            }
            throw e;
        }
        if (expectToFail) {
            fail("Invalid package type must fail.");
        }

        final File packageFile = new File(projectDir, "target/package-plugin-test-pkg-1.0.0-SNAPSHOT.zip");
        assertTrue(packageFile.exists());

        ZipFile zip = new ZipFile(packageFile);
        ZipEntry entry = zip.getEntry("META-INF/vault/filter.xml");
        assertNotNull("package has a filter.xml", entry);
        String result = IOUtil.toString(zip.getInputStream(entry), "utf-8");
        String expected = FileUtils.fileRead(new File(projectDir.getAbsolutePath(), "expected-filter.xml"));
        assertEquals("filter.xml is correct", expected, result);
    }

    /**
     * Tests if a pom with no filter definition at all fails.
     */
    @Test
    public void test_no_filter_fails() throws Exception {
        verify("no-filter-fails", true);
    }

    /**
     * Tests if a pom with no filter definition but with a @{code failOnEmptyFilter} set to {@code false} works.
     */
    @Test
    public void test_no_filter_with_prop_ok() throws Exception {
        verify("no-filter-with-prop-ok", false);
    }

    /**
     * Tests if a project with an implicit filter defined in META-INF/vault/filter.xml is correctly built
     */
    @Test
    public void test_implicit_filter() throws Exception {
        verify("implicit-filter", false);
    }

    /**
     * Tests if a project with an inline filter properly generates the filter.xml
     */
    @Test
    public void test_inline_filter() throws Exception {
        verify("inline-filter", false);
    }

    /**
     * Tests if a project with an inline filter and a filter source properly generates the merged filter.xml
     */
    @Test
    public void test_merge_inline_filter() throws Exception {
        verify("merge-inline-filter", false);
    }

    /**
     * Tests if a project with an filter source and no inline filters keeps the filter comments.
     */
    @Test
    public void test_retain_filter_source() throws Exception {
        verify("retain-filter-source", false);
    }

    /**
     * Tests if a project with no filter but a prefix creates the default root
     */
    @Test
    public void test_no_filter_with_prefix() throws Exception {
        verify("no-filter-with-prefix", false);
    }

    /**
     * Tests if a project with an inline filter and an implicit filter correctly uses the inline filters.
     */
    @Test
    public void test_inline_and_implicit_filter_fails() throws Exception {
        verify("inline-and-implicit-fails", true);
    }

    /**
     * Tests if a project with an inline filter executed twice works w/o clean
     */
    @Test
    public void test_inline_filter_twice() throws Exception {
        // first execute with default goals
        final File projectDir = new File("target/test-classes/test-projects/filter-tests/inline-filter-twice");
        FileUtils.copyFile(new File(projectDir, "pom1.xml"), new File(projectDir, "pom.xml"));
        FileUtils.copyFile(new File(projectDir, "expected-filter1.xml"), new File(projectDir, "expected-filter.xml"));
        verify("inline-filter-twice", false);

        // copy marker to 'target' to ensure that clean is not executed
        File marker = new File(projectDir, "target/marker.xml");
        FileUtils.copyFile(new File(projectDir, "pom1.xml"), marker);

        FileUtils.copyFile(new File(projectDir, "pom2.xml"), new File(projectDir, "pom.xml"));
        FileUtils.copyFile(new File(projectDir, "expected-filter2.xml"), new File(projectDir, "expected-filter.xml"));
        verify("inline-filter-twice", false, "package");

        assertTrue("Marker file still exists.", marker.exists());
    }
}
