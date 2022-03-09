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

import java.io.IOException;
import java.util.stream.Stream;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.maven.it.VerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(ProjectBuilderExtension.class)
class PackageTypesIT {

    private static void verify(ProjectBuilder projectBuilder, String projectName, String type) throws VerificationException, IOException {
        projectBuilder
                .setTestProjectDir("package-types/" + projectName)
                .build()
                .verifyPackageProperty("packageType", type);
    }


    @Test
    void test_package_type_for_invalid(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setBuildExpectedToFail(true);
        verify(projectBuilder, "invalid", "invalid");
    }
 
    @ParameterizedTest
    @ValueSource(strings = {"application", "content", "mixed", "container"})
    void test_package_type_explicit(String type, ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setProperty("test.packageType", type);
        verify(projectBuilder, "from-property", type);
    }

    private static Stream<Arguments> test_package_type_autodetect() {
        return Stream.of(Arguments.of("application", "application"), Arguments.of("application-sourced", "application"), Arguments.of("application-cleanup", "application"),
                Arguments.of("content", "content"), Arguments.of("mixed", "mixed"), Arguments.of("container", "container"));
    }

    @ParameterizedTest
    @MethodSource
    void test_package_type_autodetect(String projectName, String type, ProjectBuilder projectBuilder) throws Exception {
        verify(projectBuilder, projectName, type);
    }
}
