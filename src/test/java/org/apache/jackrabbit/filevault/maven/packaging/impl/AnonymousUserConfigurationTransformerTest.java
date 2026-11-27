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

import static org.apache.jackrabbit.filevault.maven.packaging.impl.AnonymousUserConfigurationTransformer.ANONYMOUS_PASSWORD_PROPERTY;
import static org.apache.jackrabbit.filevault.maven.packaging.impl.AnonymousUserConfigurationTransformer.ANONYMOUS_USER_PROPERTY;
import static org.apache.jackrabbit.filevault.maven.packaging.impl.AnonymousUserConfigurationTransformer.AUTHENTICATOR_PID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Hashtable;

import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnonymousUserConfigurationTransformerTest {

    private static final String JSON_FILE = "apps/example/config/" + AUTHENTICATOR_PID + ".cfg.json";
    private static final String CONFIG_FILE = "apps/example/config.publish/" + AUTHENTICATOR_PID + ".config";

    private CapturingLog log;
    private AnonymousUserConfigurationTransformer transformer;

    @BeforeEach
    void setUp() {
        log = new CapturingLog();
        transformer = new AnonymousUserConfigurationTransformer(log);
    }

    /** Builds an authenticator configuration file with the given anonymous user and a password. */
    private static byte[] authenticatorConfiguration(String fileName, String anonymousUser) throws IOException {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(ANONYMOUS_USER_PROPERTY, anonymousUser);
        properties.put(ANONYMOUS_PASSWORD_PROPERTY, "secret-password");
        properties.put("auth.annonymous", Boolean.FALSE); // unrelated ("annonymous" is Sling's spelling)
        return OsgiConfigurationFiles.write(fileName, properties);
    }

    private byte[] transform(String fileName, byte[] content) throws IOException {
        try (InputStream result = transformer.transform(CapturingLog.resourceNamed(fileName), new ByteArrayInputStream(content))) {
            return IOUtil.toByteArray(result);
        }
    }

    @Test
    void testAdminIsRemovedWithItsPasswordInBothFormats() throws IOException {
        for (String fileName : new String[] {JSON_FILE, CONFIG_FILE}) {
            log.warnings.clear();
            byte[] result = transform(fileName, authenticatorConfiguration(fileName, "admin"));

            Dictionary<String, Object> properties = OsgiConfigurationFiles.read(fileName, result);
            assertNull(properties.get(ANONYMOUS_USER_PROPERTY), fileName);
            assertNull(properties.get(ANONYMOUS_PASSWORD_PROPERTY), fileName);
            assertEquals(Boolean.FALSE, properties.get("auth.annonymous"), "unrelated property is kept in " + fileName);
            assertEquals(1, properties.size(), fileName);

            assertEquals(1, log.warnings.size(), fileName);
            String warning = log.warnings.get(0);
            assertTrue(warning.contains(fileName), warning);
            assertTrue(warning.contains("'" + ANONYMOUS_USER_PROPERTY + "'"), warning);
            assertTrue(warning.contains("'admin'"), warning);
            assertFalse(warning.contains("secret-password"), "the password must never be logged: " + warning);
        }
    }

    @Test
    void testAdminIsDetectedIgnoringCaseAndSurroundingSpaces() throws IOException {
        for (String user : new String[] {"Admin", "ADMIN", " admin ", "\tadmin\n"}) {
            byte[] result = transform(JSON_FILE, authenticatorConfiguration(JSON_FILE, user));
            assertNull(OsgiConfigurationFiles.read(JSON_FILE, result).get(ANONYMOUS_USER_PROPERTY), user);
        }
    }

    @Test
    void testOtherAnonymousUsersAreKeptByteForByte() throws IOException {
        for (String user : new String[] {"anonymous", "my-public-user", "administrator", "admin2", ""}) {
            byte[] original = authenticatorConfiguration(JSON_FILE, user);
            assertArrayEquals(original, transform(JSON_FILE, original), user);
        }
        assertTrue(log.warnings.isEmpty(), log.warnings.toString());
    }

    @Test
    void testConfigurationWithoutAnonymousUserIsKept() throws IOException {
        byte[] original = "{ \"auth.annonymous\": false }".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(original, transform(JSON_FILE, original));
        assertTrue(log.warnings.isEmpty());
    }

    @Test
    void testOtherFilesAreNotEvenRead() throws IOException {
        // other PIDs, other formats of the same PID and non-configuration files
        String[] otherFiles = {
            "apps/example/config/com.example.Other.cfg.json",
            "apps/example/config/" + AUTHENTICATOR_PID + ".cfg",
            "apps/example/config/" + AUTHENTICATOR_PID + ".xml",
            "apps/example/.content.xml"
        };
        for (String fileName : otherFiles) {
            InputStream original = new ByteArrayInputStream("{ \"sling.auth.anonymous.user\": \"admin\" }".getBytes(StandardCharsets.UTF_8));
            assertSame(original, transformer.transform(CapturingLog.resourceNamed(fileName), original), fileName);
        }
        assertTrue(log.warnings.isEmpty());
    }

    @Test
    void testUnparsableConfigurationIsKeptWithAWarning() throws IOException {
        byte[] original = "{ not json".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(original, transform(JSON_FILE, original));
        assertEquals(1, log.warnings.size());
        assertTrue(log.warnings.get(0).contains("Could not parse"), log.warnings.get(0));
    }

    @Test
    void testSameFileReadSeveralTimesIsWarnedOnce() throws IOException {
        // the archiver reads each file more than once (e.g. for its checksum and when writing it)
        byte[] original = authenticatorConfiguration(JSON_FILE, "admin");
        byte[] first = transform(JSON_FILE, original);
        byte[] second = transform(JSON_FILE, original);
        assertArrayEquals(first, second);
        assertEquals(1, log.warnings.size(), log.warnings.toString());
    }

    @Test
    void testTransformingTwiceChangesNothingMore() throws IOException {
        byte[] once = transform(JSON_FILE, authenticatorConfiguration(JSON_FILE, "admin"));
        byte[] twice = transform(JSON_FILE, once);
        assertArrayEquals(once, twice);
        assertEquals(1, log.warnings.size());
    }
}
