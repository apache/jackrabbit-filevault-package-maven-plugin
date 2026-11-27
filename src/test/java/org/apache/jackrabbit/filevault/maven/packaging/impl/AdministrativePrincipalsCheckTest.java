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
package org.apache.jackrabbit.filevault.maven.packaging.impl;

import static org.apache.jackrabbit.filevault.maven.packaging.impl.AdministrativePrincipalsCheck.AUTHORIZATION_PID;
import static org.apache.jackrabbit.filevault.maven.packaging.impl.AdministrativePrincipalsCheck.USER_PID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class AdministrativePrincipalsCheckTest {

    private static final String AUTHORIZATION_JSON = "jcr_root/apps/example/config/" + AUTHORIZATION_PID + ".cfg.json";
    private static final String AUTHORIZATION_CONFIG = "jcr_root/apps/example/config.author/" + AUTHORIZATION_PID + ".config";
    private static final String USER_JSON = "jcr_root/apps/example/config/" + USER_PID + ".cfg.json";
    private static final String USER_CONFIG = "jcr_root/apps/example/config.publish/" + USER_PID + ".config";

    /** The package files of one test, sorted by path like in the mojo. */
    private final Map<String, byte[]> files = new TreeMap<>();

    private void addFile(String name, String content) {
        files.put(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addAuthorizationJson(String principalsJsonArray) {
        addFile(AUTHORIZATION_JSON, "{ \"administrativePrincipals\": " + principalsJsonArray + ", \"importBehavior\": \"besteffort\" }");
    }

    private static LinkedHashSet<String> setOf(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    @Test
    void testAnonymousIsReportedWithTheFileName() {
        addAuthorizationJson("[\"administrators\", \"anonymous\"]");

        List<String> errors = AdministrativePrincipalsCheck.check(files);

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains(AUTHORIZATION_JSON), errors.get(0));
        assertTrue(errors.get(0).contains("'anonymous'"), errors.get(0));
        assertTrue(!errors.get(0).contains("'administrators'"), "only forbidden principals are named: " + errors.get(0));
    }

    @Test
    void testEveryoneIsReported() {
        addAuthorizationJson("[\"administrators\", \"everyone\"]");

        List<String> errors = AdministrativePrincipalsCheck.check(files);

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("'everyone'"), errors.get(0));
    }

    @Test
    void testEveryoneAndAnonymousInOneFileGiveOneErrorNamingBoth() {
        addAuthorizationJson("[\"everyone\", \"anonymous\", \"anonymous\"]");

        List<String> errors = AdministrativePrincipalsCheck.check(files);

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("'everyone', 'anonymous'"), errors.get(0));
    }

    @Test
    void testBothFormatsAndAllValueShapesAreChecked() {
        // array in .cfg.json, collection "( ... )" and single String in .config
        addAuthorizationJson("[\"anonymous\"]");
        addFile("jcr_root/apps/example/config.author/" + AUTHORIZATION_PID + ".config",
                "administrativePrincipals=( \"administrators\", \"anonymous\" )\n");
        addFile("jcr_root/apps/example/config.publish/" + AUTHORIZATION_PID + ".config",
                "administrativePrincipals=\"everyone\"\n");

        List<String> errors = AdministrativePrincipalsCheck.check(files);

        assertEquals(3, errors.size(), errors.toString());
    }

    @Test
    void testSafeConfigurationsAreNotReported() {
        // Oak compares names exactly, so these do not grant anything to anonymous or everyone
        addAuthorizationJson("[\"administrators\", \"Anonymous\", \" anonymous\", \"anonymous-users\", \"Everyone\"]");
        addFile(AUTHORIZATION_CONFIG, "importBehavior=\"besteffort\"\n"); // property not set
        addFile("jcr_root/apps/example/config/com.example.Other.cfg.json", "{ \"administrativePrincipals\": [\"anonymous\"] }");
        addFile("jcr_root/apps/example/config/" + AUTHORIZATION_PID + ".cfg", "administrativePrincipals=anonymous");
        addFile("jcr_root/apps/example/.content.xml", "<jcr:root/>");

        assertEquals(0, AdministrativePrincipalsCheck.check(files).size());
    }

    @Test
    void testUnparsableConfigurationIsSkipped() {
        addFile(AUTHORIZATION_JSON, "{ not json \"anonymous\"");
        addFile(USER_JSON, "{ not json");

        assertEquals(0, AdministrativePrincipalsCheck.check(files).size());
        // a broken user configuration is not applied, so Oak uses the default anonymous ID
        assertEquals(setOf("everyone", "anonymous"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
    }

    @Test
    void testDefaultAnonymousIdWithoutUserConfiguration() {
        assertEquals(setOf("everyone", "anonymous"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
    }

    @Test
    void testUserConfigurationWithoutAnonymousIdUsesTheDefault() {
        addFile(USER_JSON, "{ \"defaultDepth\": 2 }");

        assertEquals(setOf("everyone", "anonymous"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
    }

    @Test
    void testConfiguredAnonymousIdReplacesTheDefault() {
        addFile(USER_JSON, "{ \"anonymousId\": \"guest\" }");
        addAuthorizationJson("[\"administrators\", \"anonymous\"]");

        assertEquals(setOf("everyone", "guest"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
        // "anonymous" is just an ordinary principal name now
        assertEquals(0, AdministrativePrincipalsCheck.check(files).size());

        addAuthorizationJson("[\"administrators\", \"guest\"]");
        List<String> errors = AdministrativePrincipalsCheck.check(files);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("'guest'"), errors.get(0));
    }

    @Test
    void testConfiguredAnonymousIdIsReadFromTheConfigFormat() {
        addFile(USER_CONFIG, "anonymousId=\"guest\"\n");

        assertEquals(setOf("everyone", "guest"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
    }

    @Test
    void testEveryAnonymousIdOfSeveralUserConfigurationsIsForbidden() {
        // e.g. one user configuration per run mode: each of them could become active
        addFile("jcr_root/apps/example/config.author/" + USER_PID + ".cfg.json", "{ \"anonymousId\": \"guest\" }");
        addFile(USER_CONFIG, "anonymousId=\"visitor\"\n");

        assertEquals(setOf("everyone", "guest", "visitor"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));

        // one of them without anonymousId brings back the default
        addFile(USER_JSON, "{ }");
        assertEquals(setOf("everyone", "guest", "visitor", "anonymous"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
    }

    @Test
    void testEmptyAnonymousIdUsesTheDefault() {
        addFile(USER_JSON, "{ \"anonymousId\": \"\" }");

        assertEquals(setOf("everyone", "anonymous"), AdministrativePrincipalsCheck.getForbiddenPrincipals(files));
    }
}
