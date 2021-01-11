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
package org.apache.jackrabbit.filevault.maven.packaging;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;


public class AbstractMetadataPackageMojoTest {

    @Test
    public void testGetRelativePath() {
        // absolute and relative path
        Path base = Paths.get(".");
        Path path = FileSystems.getDefault().getPath("").toAbsolutePath();
        Assert.assertEquals("'" + FileSystems.getDefault().getPath("").toAbsolutePath() + "'", AbstractMetadataPackageMojo.getRelativePath(base, path));

        // one path is parent of the other
        base = Paths.get("my", "base");
        path = Paths.get("my", "base", "child");
        Assert.assertEquals("'child'", AbstractMetadataPackageMojo.getRelativePath(base, path));

        // no common parent
        base = Paths.get("my", "base");
        path = Paths.get("other", "file");
        Assert.assertEquals("'" +path.toString() + "'", AbstractMetadataPackageMojo.getRelativePath(base, path));
 
        // paths have common parent (but one does not start with the other)
        base = Paths.get("my", "base");
        path = Paths.get("my", "other");
        Assert.assertEquals("'" + path + "'", AbstractMetadataPackageMojo.getRelativePath(base, path));
    }
}
