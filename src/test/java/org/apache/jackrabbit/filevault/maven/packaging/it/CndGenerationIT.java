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
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.maven.it.VerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProjectBuilderExtension.class)
class CndGenerationIT {

    @Test
    void testCndGeneration(ProjectBuilder projectBuilder) throws IOException, VerificationException {
        String expectedCnd = "<'nt'='http://www.jcp.org/jcr/nt/1.0'>\n"
                + "<'jcr'='http://www.jcp.org/jcr/1.0'>\n"
                + "<'mix'='http://www.jcp.org/jcr/mix/1.0'>\n"
                + "\n"
                + "[nt:file] > nt:hierarchyNode\n"
                + "  primaryitem jcr:content\n"
                + "  + jcr:content (nt:base) mandatory\n"
                + "\n"
                + "[nt:hierarchyNode] > mix:created\n"
                + "  abstract\n"
                + "\n"
                + "[mix:created]\n"
                + "  mixin\n"
                + "  - jcr:createdBy (string) autocreated protected\n"
                + "  - jcr:created (date) autocreated protected\n"
                + "\n"
                + "[nt:base]\n"
                + "  abstract\n"
                + "  - jcr:mixinTypes (name) protected multiple compute\n"
                + "  - jcr:primaryType (name) mandatory autocreated protected compute\n"
                + "\n"
                + "[nt:folder] > nt:hierarchyNode\n"
                + "  + * (nt:hierarchyNode) version\n"
                + "\n";
        
        projectBuilder
                .setTestProjectDir("cnd-generation/simple")
                .build()
                .verifyExpectedFileContent("META-INF/vault/nodetypes.cnd", StandardCharsets.US_ASCII, expectedCnd);
    }
}
