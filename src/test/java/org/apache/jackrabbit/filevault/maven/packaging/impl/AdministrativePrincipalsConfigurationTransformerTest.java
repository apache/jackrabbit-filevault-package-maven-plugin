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

import static org.apache.jackrabbit.filevault.maven.packaging.impl.AdministrativePrincipalsConfigurationTransformer.ADMINISTRATIVE_PRINCIPALS_PROPERTY;
import static org.apache.jackrabbit.filevault.maven.packaging.impl.AdministrativePrincipalsConfigurationTransformer.AUTHORIZATION_PID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.codehaus.plexus.util.IOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdministrativePrincipalsConfigurationTransformerTest {

    private static final String JSON_FILE = "apps/example/config/" + AUTHORIZATION_PID + ".cfg.json";
    private static final String CONFIG_FILE = "apps/example/config.author/" + AUTHORIZATION_PID + ".config";

    private CapturingLog log;
    private AdministrativePrincipalsConfigurationTransformer transformer;

    @BeforeEach
    void setUp() {
        log = new CapturingLog();
        transformer = new AdministrativePrincipalsConfigurationTransformer(log);
    }

    /** Builds an authorization configuration file with the given administrativePrincipals value. */
    private static byte[] authorizationConfiguration(String fileName, Object administrativePrincipals) throws IOException {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(ADMINISTRATIVE_PRINCIPALS_PROPERTY, administrativePrincipals);
        properties.put("importBehavior", "besteffort"); // unrelated
        return OsgiConfigurationFiles.write(fileName, properties);
    }

    private byte[] transform(String fileName, byte[] content) throws IOException {
        try (InputStream result = transformer.transform(CapturingLog.resourceNamed(fileName), new ByteArrayInputStream(content))) {
            return IOUtil.toByteArray(result);
        }
    }

    private static Object administrativePrincipals(String fileName, byte[] content) throws IOException {
        return OsgiConfigurationFiles.read(fileName, content).get(ADMINISTRATIVE_PRINCIPALS_PROPERTY);
    }

    @Test
    void testAnonymousIsRemovedAndTheOthersKeptInOrderInBothFormats() throws IOException {
        for (String fileName : new String[] {JSON_FILE, CONFIG_FILE}) {
            log.warnings.clear();
            byte[] result = transform(fileName, authorizationConfiguration(fileName,
                    new String[] {"administrators", "anonymous", "${env.ADMIN_GROUP}"}));

            assertArrayEquals(new String[] {"administrators", "${env.ADMIN_GROUP}"},
                    (String[]) administrativePrincipals(fileName, result), fileName);
            assertEquals("besteffort", OsgiConfigurationFiles.read(fileName, result).get("importBehavior"), fileName);

            assertEquals(1, log.warnings.size(), fileName);
            String warning = log.warnings.get(0);
            assertTrue(warning.contains(fileName), warning);
            assertTrue(warning.contains("'anonymous'"), warning);
            assertTrue(warning.contains("'" + ADMINISTRATIVE_PRINCIPALS_PROPERTY + "'"), warning);
        }
    }

    @Test
    void testEveryOccurrenceOfAnonymousIsRemoved() throws IOException {
        byte[] result = transform(JSON_FILE, authorizationConfiguration(JSON_FILE,
                new String[] {"anonymous", "administrators", "anonymous"}));
        assertArrayEquals(new String[] {"administrators"}, (String[]) administrativePrincipals(JSON_FILE, result));
    }

    @Test
    void testArrayWithOnlyAnonymousBecomesEmpty() throws IOException {
        byte[] result = transform(JSON_FILE, authorizationConfiguration(JSON_FILE, new String[] {"anonymous"}));
        assertArrayEquals(new String[0], (String[]) administrativePrincipals(JSON_FILE, result));
    }

    @Test
    void testSingleStringAnonymousRemovesTheProperty() throws IOException {
        // Oak also accepts a single String; "anonymous" alone would make anonymous an administrator
        for (String fileName : new String[] {JSON_FILE, CONFIG_FILE}) {
            byte[] result = transform(fileName, authorizationConfiguration(fileName, "anonymous"));
            assertNull(administrativePrincipals(fileName, result), fileName);
            assertEquals("besteffort", OsgiConfigurationFiles.read(fileName, result).get("importBehavior"), fileName);
        }
    }

    @Test
    void testCollectionInConfigFormatIsAdjusted() throws IOException {
        // the .config format writes a collection as ( ... ), which Oak accepts as well
        byte[] original = "administrativePrincipals=( \"administrators\", \"anonymous\" )\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(administrativePrincipals(CONFIG_FILE, original) instanceof Collection, "test input must be a collection");

        byte[] result = transform(CONFIG_FILE, original);

        Collection<?> principals = (Collection<?>) administrativePrincipals(CONFIG_FILE, result);
        assertEquals(1, principals.size(), principals.toString());
        assertEquals("administrators", principals.iterator().next());
        assertEquals(1, log.warnings.size());
    }

    @Test
    void testPrincipalsWithoutExactAnonymousAreKeptByteForByte() throws IOException {
        // Oak compares names exactly, so these do not grant anything to the anonymous principal
        Object[] values = {
            new String[] {"administrators", "Anonymous", " anonymous", "anonymous-users", "everyone"},
            "administrators",
            new String[0]
        };
        for (Object value : values) {
            byte[] original = authorizationConfiguration(JSON_FILE, value);
            assertArrayEquals(original, transform(JSON_FILE, original));
        }
        byte[] withoutProperty = "{ \"importBehavior\": \"besteffort\" }".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(withoutProperty, transform(JSON_FILE, withoutProperty));
        assertTrue(log.warnings.isEmpty(), log.warnings.toString());
    }

    @Test
    void testOtherFilesAreNotEvenRead() throws IOException {
        String[] otherFiles = {
            "apps/example/config/com.example.Other.cfg.json",
            "apps/example/config/" + AUTHORIZATION_PID + ".cfg",
            "apps/example/config/" + AUTHORIZATION_PID + ".xml",
            "apps/example/.content.xml"
        };
        for (String fileName : otherFiles) {
            InputStream original = new ByteArrayInputStream("{ \"administrativePrincipals\": [\"anonymous\"] }".getBytes(StandardCharsets.UTF_8));
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
        byte[] original = authorizationConfiguration(JSON_FILE, new String[] {"administrators", "anonymous"});
        byte[] first = transform(JSON_FILE, original);
        byte[] second = transform(JSON_FILE, original);
        assertArrayEquals(first, second);
        assertEquals(1, log.warnings.size(), log.warnings.toString());
    }

    @Test
    void testTransformingTwiceChangesNothingMore() throws IOException {
        byte[] once = transform(JSON_FILE, authorizationConfiguration(JSON_FILE, new String[] {"administrators", "anonymous"}));
        byte[] twice = transform(JSON_FILE, once);
        assertArrayEquals(once, twice);
        assertEquals(1, log.warnings.size());
    }
}
