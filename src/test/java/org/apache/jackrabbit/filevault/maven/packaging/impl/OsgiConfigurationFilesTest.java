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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;

import org.junit.jupiter.api.Test;

class OsgiConfigurationFilesTest {

    @Test
    void testGetPid() {
        assertEquals("my.Service", OsgiConfigurationFiles.getPid("my.Service.cfg.json"));
        assertEquals("my.Service", OsgiConfigurationFiles.getPid("apps/example/config.author/my.Service.cfg.json"));
        assertEquals("my.Service", OsgiConfigurationFiles.getPid("apps\\example\\config\\my.Service.config"));
        assertNull(OsgiConfigurationFiles.getPid("apps/example/config/my.Service.cfg"));
        assertNull(OsgiConfigurationFiles.getPid("apps/example/config/my.Service.xml"));
        assertNull(OsgiConfigurationFiles.getPid("apps/example/config/.content.xml"));
    }

    @Test
    void testGetConfigurationPath() {
        assertEquals("jcr_root/apps/example/config/my.Service.cfg.json",
                OsgiConfigurationFiles.getConfigurationPath("jcr_root/apps/example/config/my.Service.cfg.json"));
        // archive entry names use the platform separator, e.g. on Windows
        assertEquals("jcr_root/apps/example/config.author/my.Service.config",
                OsgiConfigurationFiles.getConfigurationPath("jcr_root\\apps\\example\\config.author\\my.Service.config"));
        assertEquals("jcr_root/apps/example/config/my.Service.cfg.json",
                OsgiConfigurationFiles.getConfigurationPath("jcr_root/apps/example\\config\\my.Service.cfg.json"));
        // not below jcr_root, or not an OSGi configuration format
        assertNull(OsgiConfigurationFiles.getConfigurationPath("META-INF/vault/my.Service.cfg.json"));
        assertNull(OsgiConfigurationFiles.getConfigurationPath("jcr_root_other/my.Service.cfg.json"));
        assertNull(OsgiConfigurationFiles.getConfigurationPath("jcr_root\\apps\\example\\config\\my.Service.cfg"));
        assertNull(OsgiConfigurationFiles.getConfigurationPath("jcr_root/apps/example/.content.xml"));
    }

    @Test
    void testReadJsonWithCommentsAndTypeHints() throws IOException {
        String json = "{\n"
                + "  // a comment\n"
                + "  \"text\": \"value\",\n"
                + "  \"list:String[]\": [\"a\", \"b\"],\n"
                + "  \"number:Integer\": 5,\n"
                + "  \"flag\": true\n"
                + "}";
        Dictionary<String, Object> properties = OsgiConfigurationFiles.read("my.Service.cfg.json", json.getBytes(StandardCharsets.UTF_8));
        assertEquals("value", properties.get("text"));
        assertArrayEquals(new String[] {"a", "b"}, (String[]) properties.get("list"));
        assertEquals(5, properties.get("number"));
        assertEquals(Boolean.TRUE, properties.get("flag"));
        assertEquals(4, properties.size());
    }

    @Test
    void testReadConfigFormat() throws IOException {
        String config = "text=\"value\"\nlist=[\"a\",\"b\"]\nnumber=I\"5\"\n";
        Dictionary<String, Object> properties = OsgiConfigurationFiles.read("my.Service.config", config.getBytes(StandardCharsets.UTF_8));
        assertEquals("value", properties.get("text"));
        assertArrayEquals(new String[] {"a", "b"}, (String[]) properties.get("list"));
        assertEquals(5, properties.get("number"));
    }

    @Test
    void testInvalidJsonThrowsIOException() {
        assertThrows(IOException.class,
                () -> OsgiConfigurationFiles.read("my.Service.cfg.json", "{ not json".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testUnsupportedFileNameThrowsIllegalArgumentException() {
        byte[] content = "a=b".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> OsgiConfigurationFiles.read("my.Service.cfg", content));
    }
}
