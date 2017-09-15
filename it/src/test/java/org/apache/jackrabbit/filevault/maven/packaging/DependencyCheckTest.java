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

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class DependencyCheckTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"fail-missing-deps", true},
                {"fail-no-maven-deps", true},
                {"fail-no-contains", true},
                {"fail-no-cover", true},
                {"no-errors", false},
                {"repo-structure-pkg", false}
        });
    }

    private final String projectName;

    private final boolean expectToFail;

    public DependencyCheckTest(String projectName, boolean expectToFail) {
        this.projectName = projectName;
        this.expectToFail = expectToFail;
    }

    private void verify(File projectDir) throws VerificationException, IOException {
        final Properties props = new Properties();
        props.put("plugin.version", System.getProperty("plugin.version"));
        props.put("testcontent.directory", new File("target/test-classes/test-content").getAbsolutePath());

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
    }


    @Test
    public void test_dependency_checks() throws Exception {
        final File projectDir = new File("target/test-classes/test-projects/validate-deps-projects/" + projectName);
        verify(projectDir);
    }
}
