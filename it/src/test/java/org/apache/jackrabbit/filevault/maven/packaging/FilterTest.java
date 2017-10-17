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
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.FileUtils;
import org.apache.maven.it.util.IOUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class FilterTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"no-filter-fails", true},
                {"no-filter-with-prop-ok", false},
                {"implicit-filter", false},
        });
    }

    private final String projectName;

    private final boolean expectToFail;

    public FilterTest(String projectName, boolean expectToFail) {
        this.projectName = projectName;
        this.expectToFail = expectToFail;
    }

    private void verify(File projectDir) throws VerificationException, IOException {
        final Properties props = new Properties();
        props.put("plugin.version", System.getProperty("plugin.version"));

        Verifier verifier = new Verifier(projectDir.getAbsolutePath());
        verifier.setSystemProperties(props);
        try {
            verifier.executeGoals(Arrays.asList("clean", "package"));
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

        // this is a bit a hack, but it is the only test that doesn't have a filter.xml
        assertNotNull("package has a filter.xml", entry);

        String result = IOUtil.toString(zip.getInputStream(entry), "utf-8");
        String expected = FileUtils.fileRead(new File(projectDir.getAbsolutePath(), "expected-filter.xml"));
        assertEquals("filter.xml is correct", expected, result);
    }


    @Test
    public void test_filter_checks() throws Exception {
        final File projectDir = new File("target/test-classes/test-projects/filter-tests/" + projectName);
        verify(projectDir);
    }
}
