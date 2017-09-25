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
import org.apache.maven.it.util.StringUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class PackageTypesTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"application", false},
                {"application-sourced", false},
                {"application-cleanup", false},
                {"content", false},
                {"mixed", false},
                {"container", false},
                {"invalid", true}});
    }

    private final String type;

    private final String projectName;

    private final boolean expectToFail;

    public PackageTypesTest(String type, boolean expectToFail) {
        this.projectName = type;
        this.type = StringUtils.chomp(type, "-");
        this.expectToFail = expectToFail;
    }

    private void verify(File projectDir) throws VerificationException, IOException {
        final Properties props = new Properties();
        props.put("plugin.version", System.getProperty("plugin.version"));
        props.put("test.packageType", type);

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
        assertThat(packageFile.exists(), is(true));

        ZipFile zip = new ZipFile(packageFile);
        ZipEntry propertiesFile = zip.getEntry("META-INF/vault/properties.xml");
        assertThat(propertiesFile, notNullValue());

        Properties properties = new Properties();
        properties.loadFromXML(zip.getInputStream(propertiesFile));
        assertThat(properties.getProperty("packageType"), equalTo(type));    }


    @Test
    public void test_package_type() throws Exception {
        final File projectDir = new File("target/test-classes/test-projects/package-types");
        verify(projectDir);
    }

    @Test
    public void test_package_type_autodetect() throws Exception {
        // ignore invalid test
        Assume.assumeTrue(!expectToFail);

        final File projectDir = new File("target/test-classes/test-projects/package-type-auto-" + projectName);
        verify(projectDir);
    }
}
