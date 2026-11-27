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

import static org.apache.jackrabbit.filevault.maven.packaging.impl.AnonymousUserCheck.AUTHENTICATOR_PID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class AnonymousUserCheckTest {

    private static final String JSON_FILE = "jcr_root/apps/example/config/" + AUTHENTICATOR_PID + ".cfg.json";
    private static final String CONFIG_FILE = "jcr_root/apps/example/config.publish/" + AUTHENTICATOR_PID + ".config";

    /** The package files of one test, sorted by path like in the mojo. */
    private final Map<String, byte[]> files = new TreeMap<>();

    private void addFile(String name, String content) {
        files.put(name, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testAdminIsReportedWithoutThePassword() {
        addFile(JSON_FILE, "{ \"sling.auth.anonymous.user\": \"admin\", \"sling.auth.anonymous.password\": \"example-password\" }");

        List<String> errors = AnonymousUserCheck.check(files);

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains(JSON_FILE), errors.get(0));
        assertTrue(errors.get(0).contains("'sling.auth.anonymous.user' to 'admin'"), errors.get(0));
        assertFalse(errors.get(0).contains("example-password"), "the password must never be logged: " + errors.get(0));
    }

    @Test
    void testAdminIsReportedInBothFormats() {
        addFile(JSON_FILE, "{ \"sling.auth.anonymous.user\": \"admin\" }");
        addFile(CONFIG_FILE, "sling.auth.anonymous.user=\"admin\"\n");

        assertEquals(2, AnonymousUserCheck.check(files).size());
    }

    @Test
    void testCaseAndSurroundingSpacesAreIgnored() {
        // user IDs are looked up case-insensitively, so all of these log in as admin
        for (String user : new String[] {"Admin", "ADMIN", " admin ", "\tadmin"}) {
            assertTrue(AnonymousUserCheck.isForbiddenUser(user), "'" + user + "'");
        }
    }

    @Test
    void testOtherUsersAreNotReported() {
        for (Object user : new Object[] {"anonymous", "administrator", "admin2", "ad min", "", null, Boolean.TRUE}) {
            assertFalse(AnonymousUserCheck.isForbiddenUser(user), "'" + user + "'");
        }
        addFile(JSON_FILE, "{ \"sling.auth.anonymous.user\": \"anonymous\" }");
        addFile(CONFIG_FILE, "auth.annonymous=B\"false\"\n"); // property not set

        assertEquals(0, AnonymousUserCheck.check(files).size());
    }

    @Test
    void testOtherFilesAreIgnored() {
        addFile("jcr_root/apps/example/config/com.example.Other.cfg.json", "{ \"sling.auth.anonymous.user\": \"admin\" }");
        addFile("jcr_root/apps/example/config/" + AUTHENTICATOR_PID + ".cfg", "sling.auth.anonymous.user=admin");
        addFile("jcr_root/apps/example/.content.xml", "<jcr:root/>");

        assertEquals(0, AnonymousUserCheck.check(files).size());
    }

    @Test
    void testUnparsableConfigurationIsSkipped() {
        addFile(JSON_FILE, "{ not json \"admin\"");

        assertEquals(0, AnonymousUserCheck.check(files).size());
    }
}
