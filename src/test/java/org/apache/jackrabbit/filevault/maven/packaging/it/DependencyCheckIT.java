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
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DependencyCheckIT {

    @Parameterized.Parameters(name="{index}: project {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"fail-missing-deps", true},
                {"fail-missing-deps-implicit", true},
                {"fail-no-maven-deps", true},
                {"fail-no-contains", true},
                {"fail-no-cover", true},
                {"fail-repo-structure-pkg-subtree", true},
                {"no-errors", false},
                {"repo-structure-pkg", false},
                {"no-error-cleanup", false},
                {"no-error-cleanup-filter", false},
                {"no-error-unknown-dependency", false}
        });
    }

    private final String projectName;

    private final boolean expectToFail;

    public DependencyCheckIT(String projectName, boolean expectToFail) {
        this.projectName = projectName;
        this.expectToFail = expectToFail;
    }

    @Test
    public void test_dependency_checks() throws Exception {
        new ProjectBuilder()
                .setTestProjectsRoot(new File("target/test-classes/test-projects/validate-deps-projects"))
                .setTestProjectDir(projectName)
                .setBuildExpectedToFail(expectToFail)
                .build();
    }
}
