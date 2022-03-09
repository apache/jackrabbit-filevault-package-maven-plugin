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
import java.util.stream.Stream;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(ProjectBuilderExtension.class)
public class DependencyCheckIT {

    private static Stream<Arguments> test_dependency_checks() { 
        return Stream.of(
            Arguments.of("fail-missing-deps", true),
            Arguments.of("fail-missing-deps-implicit", true),
            Arguments.of("fail-no-maven-deps", true),
            Arguments.of("fail-no-contains", true),
            Arguments.of("fail-no-cover", true),
            Arguments.of("fail-undeclared-dependency", true),
            Arguments.of("fail-repo-structure-pkg-subtree", true),
            Arguments.of("no-errors", false),
            Arguments.of("repo-structure-pkg", false),
            Arguments.of("no-error-cleanup", false),
            Arguments.of("no-error-cleanup-filter", false),
            Arguments.of("no-error-unknown-dependency", false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource()
    public void test_dependency_checks(String projectName, boolean expectToFail, ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
                .setTestProjectsRoot(new File("target/test-classes/test-projects/validate-deps-projects"))
                .setTestProjectDir(projectName)
                .setBuildExpectedToFail(expectToFail)
                .build();
    }
}
