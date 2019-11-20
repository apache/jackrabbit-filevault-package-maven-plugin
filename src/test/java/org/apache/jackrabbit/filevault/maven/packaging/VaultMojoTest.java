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
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.jackrabbit.filevault.maven.packaging.it.ProjectBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class VaultMojoTest {

    @Test
    public void testUncoveredFiles() {
        String prefix = "/prefix/";
        Set<String> excludes = Collections.emptySet();
        File sourceDirectory = new File(ProjectBuilder.TEST_PROJECTS_ROOT, "filter-tests/filter-not-covering-all-files/jcr_root");
        Collection<File> uncoveredFiles = VaultMojo.getUncoveredFiles(sourceDirectory, excludes, prefix, Collections.emptySet());
        Assert.assertThat(uncoveredFiles, Matchers.contains(new File(sourceDirectory, "apps/.content.xml")));
    }

    @Test
    public void testUncoveredFilesWithNothingUncovered() {
        Set<String> excludes = Collections.emptySet();
        File sourceDirectory = new File(ProjectBuilder.TEST_PROJECTS_ROOT, "filter-tests/filter-not-covering-all-files/jcr_root");
        Set<String> entryNames = Collections.singleton(new File("jcr_root/apps/.content.xml").getPath());
        Collection<File> uncoveredFiles = VaultMojo.getUncoveredFiles(sourceDirectory, excludes, "", entryNames);
        Assert.assertThat(uncoveredFiles, Matchers.empty());
    }
}
