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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.impl.ContentPackageArchiver;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.components.io.resources.ResourceFactory;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /** Creates an archiver containing one Oak authorization configuration under the given entry name. */
    private static ContentPackageArchiver archiverWithAuthorizationConfiguration(Path folder, String entryName) throws IOException {
        Path configuration = folder.resolve("configuration.cfg.json");
        Files.write(configuration, "{ \"administrativePrincipals\": [\"anonymous\"] }".getBytes(StandardCharsets.UTF_8));
        ContentPackageArchiver archiver = new ContentPackageArchiver();
        // addResource keeps the name as it is (addFile would replace '\' with '/'), like the file sets of VaultMojo on Windows
        archiver.addResource(ResourceFactory.createResource(configuration.toFile()), entryName, -1);
        return archiver;
    }

    @Test
    void testCheckAdministrativePrincipalsFailsForUnixAndWindowsEntryNames(@TempDir Path folder) throws IOException {
        VaultMojo mojo = new VaultMojo(null, null, null, Collections.emptySet());
        mojo.failOnUnsafeAdministrativePrincipals = true;
        // archive entry names use the platform separator, so on Windows they contain backslashes
        String[] entryNames = {
            "jcr_root/apps/example/config/org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl.cfg.json",
            "jcr_root\\apps\\example\\config\\org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl.cfg.json"
        };
        for (String entryName : entryNames) {
            ContentPackageArchiver archiver = archiverWithAuthorizationConfiguration(folder, entryName);
            assertThrows(MojoFailureException.class, () -> mojo.checkAdministrativePrincipals(archiver), entryName);
        }
    }

    @Test
    void testCheckAdministrativePrincipalsIsDisabledByDefault(@TempDir Path folder) throws IOException {
        VaultMojo mojo = new VaultMojo(null, null, null, Collections.emptySet());
        ContentPackageArchiver archiver = archiverWithAuthorizationConfiguration(folder,
                "jcr_root/apps/example/config/org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl.cfg.json");
        assertDoesNotThrow(() -> mojo.checkAdministrativePrincipals(archiver));
    }
}
