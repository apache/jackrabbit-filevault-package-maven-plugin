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
package org.apache.jackrabbit.filevault.maven.packaging.mojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.impl.AnonymousUserConfigurationTransformer;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.components.io.functions.InputStreamTransformer;
import org.codehaus.plexus.util.IOUtil;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class VaultMojoTest {

    @Test
    void testUncoveredFiles() {
        String prefix = "/prefix/";
        Set<String> excludes = Collections.emptySet();
        File sourceDirectory = new File(ProjectBuilder.TEST_PROJECTS_ROOT, "filter-tests/filter-not-covering-all-files/jcr_root");
        Collection<File> uncoveredFiles = VaultMojo.getUncoveredFiles(sourceDirectory, excludes, prefix, Collections.emptySet());
        MatcherAssert.assertThat(uncoveredFiles, Matchers.contains(new File(sourceDirectory, "apps/.content.xml")));
    }

    @Test
    void testUncoveredFilesWithEverythingCovered() {
        Set<String> excludes = Collections.emptySet();
        File sourceDirectory = new File(ProjectBuilder.TEST_PROJECTS_ROOT, "filter-tests/filter-not-covering-all-files/jcr_root");
        Set<String> entryNames = Collections.singleton(new File("jcr_root/apps/.content.xml").getPath());
        Collection<File> uncoveredFiles = VaultMojo.getUncoveredFiles(sourceDirectory, excludes, "", entryNames);
        MatcherAssert.assertThat(uncoveredFiles, Matchers.empty());
    }

    @Test
    void testCloneFileSet() {
        DefaultFileSet fileSet = new DefaultFileSet();
        fileSet.setIncludes(new String[] { "ab", "c" });
        fileSet.setExcludes(new String[] { "de", "f" });
        fileSet.setIncludingEmptyDirectories(true);
        DefaultFileSet clonedFileSet = VaultMojo.cloneFileSet(fileSet);
        assertTrue( EqualsBuilder.reflectionEquals(fileSet, clonedFileSet), "Expected " + ToStringBuilder.reflectionToString(fileSet) + " but got " + ToStringBuilder.reflectionToString(clonedFileSet));
    }

    @Test
    void testChainStreamTransformersRunsFirstThenSecond() throws IOException {
        InputStreamTransformer appendA = (resource, in) -> append(in, "A");
        InputStreamTransformer appendB = (resource, in) -> append(in, "B");

        assertSame(appendB, VaultMojo.chainStreamTransformers(null, appendB));
        InputStreamTransformer chained = VaultMojo.chainStreamTransformers(appendA, appendB);
        try (InputStream result = chained.transform(null, new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)))) {
            assertEquals("xAB", new String(IOUtil.toByteArray(result), StandardCharsets.UTF_8));
        }
    }

    private static InputStream append(InputStream in, String suffix) throws IOException {
        String text = new String(IOUtil.toByteArray(in), StandardCharsets.UTF_8) + suffix;
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testNoOsgiConfigurationTransformerByDefault() {
        VaultMojo mojo = new VaultMojo(null, null, null, Collections.emptySet());
        DefaultFileSet fileSet = new DefaultFileSet();

        mojo.addOsgiConfigurationTransformers(fileSet);

        assertNull(fileSet.getStreamTransformer());
    }

    @Test
    void testAnonymousUserTransformerAddedWhenEnabled() {
        VaultMojo mojo = new VaultMojo(null, null, null, Collections.emptySet());
        mojo.removeAdminAsAnonymousUser = true;
        DefaultFileSet fileSet = new DefaultFileSet();

        mojo.addOsgiConfigurationTransformers(fileSet);

        assertTrue(fileSet.getStreamTransformer() instanceof AnonymousUserConfigurationTransformer);
    }
}
