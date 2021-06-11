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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

public class ValidateFilesMojoTest {

    @Test
    public void testSortAndEnrichFilesAndFolders() {
        SortedSet<Path> expectedPaths = new TreeSet<>();
        expectedPaths.add(Paths.get("apps"));
        expectedPaths.add(Paths.get("apps", ".content.xml"));
        expectedPaths.add(Paths.get("apps", "file"));
        expectedPaths.add(Paths.get("apps", "huhu"));
        expectedPaths.add(Paths.get("apps", "test"));
        expectedPaths.add(Paths.get("apps", "test", ".content.xml"));
        expectedPaths.add(Paths.get("apps", "test", "huhu"));
        Assert.assertEquals(expectedPaths, ValidateFilesMojo.sortAndEnrichFilesAndDirectories(Paths.get("base"), new String[] { 
                "apps" + File.separatorChar + "huhu",
                "apps" + File.separatorChar + "file",
                "apps" + File.separatorChar + "test" + File.separatorChar + "huhu", 
                "apps" + File.separatorChar + "test" + File.separatorChar + ".content.xml",
                "apps" + File.separatorChar + "test" + File.separatorChar + ".content.xml", // add the same value two times to check that equality (derived from comparator) works correctly
                "apps" + File.separatorChar + ".content.xml",
            }, 
            new String[]{ 
                    "apps",
                    "apps" + File.separatorChar + "test"
            }));
    }
}
