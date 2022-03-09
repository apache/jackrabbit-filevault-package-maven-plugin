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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.apache.jackrabbit.filevault.maven.packaging.MavenBasedPackageDependency;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class GenerateMetadataMojoTest {

    private static final String MANIFEST_ATTRIBUTE_NAME = "test";

    @Test
    void testEscapeManifestValue() throws IOException {
        assertEscapedValueWorksInManifest("Paragraph\r\rAnother paragraph");
        assertEscapedValueWorksInManifest("single line value");
        assertEscapedValueWorksInManifest("Paragraph\n\nAnother paragraph");
        assertEscapedValueWorksInManifest("Paragraph\r\n\r\nAnother paragraph");
        assertEscapedValueWorksInManifest("some very long line above 72 chars. some very long line above 72 chars. some very long line above 72 chars\n\nAnother paragraph");
        assertEscapedValueWorksInManifest("some very long line above 72 chars. some very long line above 72 chars. some very long line above 72 chars\r\rAnother paragraph");
    }

    @Test
    void testGetPathFilterSetForEmbeddedFile() throws ConfigurationException {
        // TODO: check filter
        // use OSGi bundle filename patterns first
        PathFilterSet expectedPathFilter = new PathFilterSet("/apps/install/jcr-2.0.jar");
        assertEquals(expectedPathFilter, GenerateMetadataMojo.getPathFilterSetForEmbeddedFile("/apps/install/jcr-2.0.jar", false));
        expectedPathFilter = new PathFilterSet("/apps/install");
        expectedPathFilter.addInclude(new DefaultPathFilter(Pattern.quote("/apps/install/jcr-") + ".*\\.jar(/.*)?"));
        assertEquals(expectedPathFilter, GenerateMetadataMojo.getPathFilterSetForEmbeddedFile("/apps/install/jcr-2.0.jar", true));
        assertEquals(expectedPathFilter, GenerateMetadataMojo.getPathFilterSetForEmbeddedFile("/apps/install/jcr-3.0.jar", true));

        expectedPathFilter = new PathFilterSet("/apps/some/other/install");
        expectedPathFilter.addInclude(new DefaultPathFilter(Pattern.quote("/apps/some/other/install/jcr-") + ".*\\.jar(/.*)?"));
        assertEquals(expectedPathFilter, GenerateMetadataMojo.getPathFilterSetForEmbeddedFile("/apps/some/other/install/jcr-2.0-alpha1.jar", true));

        // then test against some sub package names
        // look at PackageId.getInstallationPath for patterns ("/etc/packages/<group>/<name>-<version>.zip")
        expectedPathFilter = new PathFilterSet("/etc/packages/some/weird/group/name-1.0.zip");
        assertEquals(expectedPathFilter, GenerateMetadataMojo.getPathFilterSetForEmbeddedFile("/etc/packages/some/weird/group/name-1.0.zip", false));
        
        expectedPathFilter = new PathFilterSet("/etc/packages/some/weird/group");
        expectedPathFilter.addInclude(new DefaultPathFilter(Pattern.quote("/etc/packages/some/weird/group/name-") + ".*\\.zip(/.*)?"));
        assertEquals(expectedPathFilter, GenerateMetadataMojo.getPathFilterSetForEmbeddedFile("/etc/packages/some/weird/group/name-1.0.zip", true));
    }

    @Test
    void testWriteManifest() throws FileNotFoundException, ManifestException, DependencyResolutionRequiredException, IOException {
        GenerateMetadataMojo mojo = new GenerateMetadataMojo();
        mojo.name = "mypackage";
        mojo.group = "mygroup";
        mojo.version = "1.4";
        mojo.packageType = PackageType.APPLICATION;
        mojo.project = new MavenProject();
        Properties vaultProperties = new Properties();
        File outputFile = File.createTempFile("filevault-test-", null);
        
        Map<String, Pattern> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Manifest-Version", Pattern.compile("1\\.0"));
        expectedAttributes.put("Implementation-Title", Pattern.compile("empty-project"));
        expectedAttributes.put("Implementation-Version", Pattern.compile("0")); // project.version
        expectedAttributes.put("Content-Package-Roots", Pattern.compile(""));
        expectedAttributes.put("Content-Package-Dependencies", Pattern.compile("somegroup:dependency:1.0"));
        expectedAttributes.put("Build-Jdk-Spec", Pattern.compile(".*"));
        expectedAttributes.put("Content-Package-Type", Pattern.compile("application"));
        // this includes the version in case the pom.properties is already created which shouldn't be the case here
        expectedAttributes.put("Created-By", Pattern.compile("Apache Jackrabbit FileVault - Package Maven Plugin"));
        expectedAttributes.put("Content-Package-Id", Pattern.compile("mygroup:mypackage:1\\.4"));
        expectedAttributes.put("Content-Package-Description", Pattern.compile("")); 
        try {
            mojo.writeManifest(outputFile, "somegroup:dependency:1.0", null, vaultProperties);
            try (InputStream input = new FileInputStream(outputFile)) {
                Manifest manifest = new Manifest(input);
                Attributes attributes = manifest.getMainAttributes();
                for (Map.Entry<Object, Object> attribute : attributes.entrySet()) {
                    Pattern expectedAttributeValuePattern = expectedAttributes.get(attribute.getKey().toString());
                    if (expectedAttributeValuePattern == null) {
                        fail("Found unexpected attribute " + attribute.getKey() + " in Manifest");
                    }
                    MatcherAssert.assertThat("Found unexpected attribute value for " + attribute.getKey(), (String)attribute.getValue(), Matchers.matchesPattern(expectedAttributeValuePattern));
                    expectedAttributes.remove(attribute.getKey().toString());
                }
            }
            MatcherAssert.assertThat("Not found expected attributes in manifest", expectedAttributes, Matchers.anEmptyMap());
        } finally {
            outputFile.delete();
        }
    }

    @Test
    void testComputeDependencies() throws IOException {
        GenerateMetadataMojo mojo = new GenerateMetadataMojo();
        mojo.dependencies = new ArrayList<>();
        MavenBasedPackageDependency dependency = MavenBasedPackageDependency.fromGroupNameAndVersion("day/cq60/product", "cq-content", "[6.3.64,)");
        mojo.dependencies.add(dependency);
        dependency = MavenBasedPackageDependency.fromGroupNameAndVersion("mygroup", "mypackage", "[1,2)");
        mojo.dependencies.add(dependency);
        // upper bound of the first dependency is left out in case it is unlimited
        assertEquals("day/cq60/product:cq-content:6.3.64,mygroup:mypackage:[1,2)", mojo.computeDependencies());
    }

    private void assertEscapedValueWorksInManifest(String value) throws IOException {
        String escapedValue = GenerateMetadataMojo.escapeManifestValue(value);
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue(MANIFEST_ATTRIBUTE_NAME, escapedValue);
        attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            manifest.write(outputStream);
            //System.out.println(new String(outputStream.toByteArray(), StandardCharsets.UTF_8));
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
                manifest = new Manifest(inputStream);
                // java.util.jar.Manifest removes the new lines unfortunately from values, but maybe this gets fixed by Oracle at some point in time...
                String actualValue = manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE_NAME);
                assertEquals(removeNewlines(value), unescapeContinuations(actualValue));
            }
        };
    }

    private static String removeNewlines(String value) {
        return value.replaceAll("\n|\r", "");
    }

    /**
     * Java's Manifest parser {@link java.util.jar.Manifest} does not correctly remove the continuation for values in the form
     * {@code SomeText CR SPACE CR}
     * @param value
     * @return the given value with all {@code CR}s followed by {@code SPACE} removed
     */
    private static String unescapeContinuations(String value) {
        return value.replaceAll("\r ", "");
    }
}
