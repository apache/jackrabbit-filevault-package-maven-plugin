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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.vault.util.StandaloneManagerProvider;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateCndMojoTest {

    @Test
    void testGenerateCndMojo(@TempDir Path tmpFolder) throws IOException, RepositoryException, ParseException {
        GenerateCndMojo mojo = new GenerateCndMojo();
        mojo.setLog(new SystemStreamLog());
        mojo.project = new MavenProject();
        mojo.project.setFile(new File(getFile("/test-packages/generate-cnd", tmpFolder).toFile(), "pom.xml"));
        StandaloneManagerProvider managerProvider = new StandaloneManagerProvider();
        Path cndFile = tmpFolder.resolve("nodetypes.cnd");

        Exception e = assertThrows(IllegalStateException.class, () -> mojo.generateCnd(managerProvider, cndFile, getFile("/test-packages/generate-cnd/jcr_root", tmpFolder)));
        assertEquals("Cannot get expanded name for type cq:ClientLibraryFolder", e.getMessage());

        // now register additional CND file and try again
        try (InputStream input = getStream("cq-nodetypes.cnd");
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
             managerProvider.registerNodeTypes(reader);
        }
        assertEquals(11, mojo.generateCnd(managerProvider, cndFile, getFile("/test-packages/generate-cnd/jcr_root", tmpFolder)));
    }

    public Path getFile(String name, Path tmpFolder) throws IOException {
        URI uri;
        try {
            uri = Objects.requireNonNull(getClass().getResource(name),  "Could not find class resource with name '" + name + "'").toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Could not convert class resource URL to URI", e);
        }
        if (uri.isOpaque()) { // non hierarchical URIs (for resources in a JAR)  can not use classical file operations
            Path tmpFile = Files.createTempFile(tmpFolder, null, null);
            try (InputStream in = getStream(name)) {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tmpFile;
        } else {
            return Paths.get(uri);
        }
    }

    public InputStream getStream(String name) {
        return Objects.requireNonNull(getClass().getResourceAsStream(name), "Could not find class resource with name '" + name + "'");
    }
}
