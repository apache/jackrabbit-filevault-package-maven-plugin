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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests failing the build when the Oak administrative principals contain everyone or the anonymous principal.
 */
@ExtendWith(ProjectBuilderExtension.class)
class AdministrativePrincipalsCheckIT {

    private static final String AUTHORIZATION = "org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl";
    private static final String WITH_ANONYMOUS = "jcr_root/apps/example/config/" + AUTHORIZATION + ".cfg.json";
    private static final String WITH_EVERYONE = "jcr_root/apps/example/config.publish/" + AUTHORIZATION + ".config";
    private static final String ADMINISTRATORS_ONLY = "jcr_root/apps/example/config.author/" + AUTHORIZATION + ".cfg.json";

    private static List<String> checkErrors(ProjectBuilder projectBuilder) throws IOException {
        return projectBuilder.getBuildOutput().stream()
                .filter(line -> line.startsWith("[ERROR]") && line.contains("in 'administrativePrincipals'"))
                .collect(Collectors.toList());
    }

    @Test
    void test_build_fails_for_anonymous_and_everyone_when_enabled(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/administrative-principals")
                .setProperty("vault.failOnUnsafeAdministrativePrincipals", "true")
                .setBuildExpectedToFail(true)
                .build();

        List<String> errors = checkErrors(projectBuilder);
        // one logged error per unsafe file
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(projectBuilder.getBuildOutput().stream().anyMatch(line -> line.contains("2 OSGi configuration(s) make 'everyone'")),
                "the build failure names the number of unsafe files");
        assertTrue(errors.stream().anyMatch(error -> error.contains(WITH_ANONYMOUS) && error.contains("'anonymous'")), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains(WITH_EVERYONE) && error.contains("'everyone'")), errors.toString());
        assertTrue(errors.stream().noneMatch(error -> error.contains(ADMINISTRATORS_ONLY)), errors.toString());
    }

    @Test
    void test_build_succeeds_by_default(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/administrative-principals")
                .build();

        assertTrue(checkErrors(projectBuilder).isEmpty(), "the check must be disabled by default");
    }

    @Test
    void test_anonymous_id_is_taken_from_the_user_configuration(ProjectBuilder projectBuilder) throws Exception {
        // the package renames the anonymous user to "guest", so "anonymous" in the administrative principals is allowed
        projectBuilder.setTestProjectDir("osgi-configuration-tests/administrative-principals-custom-anonymous-id")
                .setProperty("vault.failOnUnsafeAdministrativePrincipals", "true")
                .build();

        assertTrue(checkErrors(projectBuilder).isEmpty(), checkErrors(projectBuilder).toString());
    }
}
