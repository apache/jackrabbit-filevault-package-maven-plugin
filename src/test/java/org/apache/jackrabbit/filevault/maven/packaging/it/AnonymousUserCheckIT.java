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
 * Tests failing the build when the Sling anonymous user is admin.
 */
@ExtendWith(ProjectBuilderExtension.class)
class AnonymousUserCheckIT {

    private static final String AUTHENTICATOR = "org.apache.sling.engine.impl.auth.SlingAuthenticator";
    private static final String ADMIN_JSON = "jcr_root/apps/example/config/" + AUTHENTICATOR + ".cfg.json";
    private static final String ADMIN_CONFIG = "jcr_root/apps/example/config.publish/" + AUTHENTICATOR + ".config";
    private static final String ANONYMOUS_JSON = "jcr_root/apps/example/config.author/" + AUTHENTICATOR + ".cfg.json";

    private static List<String> checkErrors(ProjectBuilder projectBuilder) throws IOException {
        return projectBuilder.getBuildOutput().stream()
                .filter(line -> line.startsWith("[ERROR]") && line.contains("'sling.auth.anonymous.user'"))
                .collect(Collectors.toList());
    }

    @Test
    void test_build_fails_for_admin_anonymous_user_when_enabled(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/anonymous-user")
                .setProperty("vault.failOnAdminAnonymousUser", "true")
                .setBuildExpectedToFail(true)
                .build();

        List<String> errors = checkErrors(projectBuilder);
        // one logged error per unsafe file
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains(ADMIN_JSON) && error.contains("'admin'")), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains(ADMIN_CONFIG) && error.contains("'Admin'")), errors.toString());
        assertTrue(errors.stream().noneMatch(error -> error.contains(ANONYMOUS_JSON)), errors.toString());
        assertTrue(projectBuilder.getBuildOutput().stream().noneMatch(line -> line.contains("example-password")),
                "the password must never be logged");
        assertTrue(projectBuilder.getBuildOutput().stream().anyMatch(line -> line.contains("2 OSGi configuration(s) use 'admin'")),
                "the build failure names the number of unsafe files");
    }

    @Test
    void test_build_succeeds_by_default(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir("osgi-configuration-tests/anonymous-user")
                .build();

        assertTrue(checkErrors(projectBuilder).isEmpty(), "the check must be disabled by default");
    }
}
