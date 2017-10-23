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

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.it.VerificationException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManifestGenerationIT {

    private ProjectBuilder verify(String projectName) throws IOException, VerificationException {
        return new ProjectBuilder()
                .setTestProjectDir("manifest-generation/" + projectName)
                .build()
                .verifyExpectedManifest();
    }

    @Test
    public void simple_manifest_generation() throws Exception {
        verify("simple");
    }

    @Test
    public void bundle_manifest_generation() throws Exception {
        verify("with-bundles");
    }

    @Test
    public void code_manifest_generation() throws Exception {
        verify("with-code");
    }

    @Test
    public void unused_manifest_generation() throws Exception {
        verify("with-unused-dependencies");
    }

    /**
     * Tests if a unused bundle dependency define it a parent pom doesn't show up in the unused bundles report.
     * see JCRVLT-214
     */
    @Test
    public void unused_parent_manifest_generation() throws Exception {
        ProjectBuilder builder = verify("with-unused-parent-dependencies");

        // also check if unused parent is not listed
        List<String> unusedBundles = new LinkedList<String>();
        boolean recording = false;
        for (String line: builder.getBuildOutput()) {
            if (line.contains("------")) {
                continue;
            }
            if (line.contains(" unused bundles")) {
                recording = true;
                continue;
            }
            if (recording) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    break;
                }
                unusedBundles.add(trimmed);
            }
        }
        String[] unused = unusedBundles.toArray(new String[unusedBundles.size()]);
        Arrays.sort(unused);

        // the project should have at 1 unused bundle but not the one from the parent
        assertTrue("unused bundles > 0", unused.length > 0);
        assertEquals("unused bundle", "[org.jsoup:jsoup:jar:1.10.3]", Arrays.toString(unused));
    }
}
