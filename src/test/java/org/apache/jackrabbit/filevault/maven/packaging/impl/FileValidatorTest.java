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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.InputStream;

import org.apache.jackrabbit.filevault.maven.packaging.impl.FileValidator;
import org.junit.Test;

public class FileValidatorTest {

    private static final String RESOURCE_DIR = "/oak-index/";

    private InputStream load(String path) {
        return getClass().getResourceAsStream(RESOURCE_DIR + path);
    }

    @Test
    public void test_filter() throws Exception {
        FileValidator validator = new FileValidator();
        String path = "META-INF/vault/filter.xml";

        validator.lookupIndexDefinitionInArtifact(load(path), path);

        assertEquals(2, validator.indexPaths.size());
        assertTrue(validator.indexPaths.contains("/oak:index/ccProfile"));
        assertTrue(validator.indexPaths.contains("/apps/project/oak:index/indexDef"));
    }

    @Test
    public void test_index_at_root() throws Exception {
        FileValidator validator = new FileValidator();
        String path = "jcr_root/_oak_index/testindex/.content.xml";

        validator.lookupIndexDefinitionInArtifact(load(path), path);

        assertEquals(1, validator.foundIndexes.size());
        assertEquals(path, validator.foundIndexes.get("/oak:index/testindex"));
    }

    @Test
    public void test_index_at_deep_path() throws Exception {
        FileValidator validator = new FileValidator();
        String path = "jcr_root/apps/project/_oak_index/.content.xml";

        validator.lookupIndexDefinitionInArtifact(load(path), path);

        assertEquals(1, validator.foundIndexes.size());
        assertEquals(path, validator.foundIndexes.get("/apps/project/oak:index/indexDef"));
    }
}
