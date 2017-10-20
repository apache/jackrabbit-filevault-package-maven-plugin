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

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.util.StringUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PackageTypesIT {

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

    public PackageTypesIT(String type, boolean expectToFail) {
        this.projectName = type;
        this.type = StringUtils.chomp(type, "-");
        this.expectToFail = expectToFail;
    }

    private void verify(File projectDir) throws VerificationException, IOException {
        new ProjectBuilder()
                .setTestProjectDir(projectDir)
                .setProperty("test.packageType", type)
                .setBuildExpectedToFail(expectToFail)
                .build()
                .verifyPackageProperty("packageType", type);
    }


    @Test
    public void test_package_type() throws Exception {
        final File projectDir = new File("target/test-classes/test-projects/package-types");
        verify(projectDir);
    }

    @Test
    public void test_package_type_autodetect() throws Exception {
        // ignore invalid test
        Assume.assumeTrue(!expectToFail);

        final File projectDir = new File("target/test-classes/test-projects/package-type-auto/" + projectName);
        verify(projectDir);
    }
}
