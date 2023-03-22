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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.apache.jackrabbit.filevault.maven.packaging.Embedded;
import org.apache.jackrabbit.filevault.maven.packaging.Filters;
import org.apache.jackrabbit.filevault.maven.packaging.MavenBasedPackageDependency;
import org.apache.jackrabbit.filevault.maven.packaging.SubPackage;
import org.apache.jackrabbit.util.ISO8601;
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
import org.mockito.Mockito;

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
        // this includes potentially the version in case the pom.properties is already created (automatically done through Eclipse Incremental Build with m2e)
        expectedAttributes.put("Created-By", Pattern.compile("Apache Jackrabbit FileVault - Package Maven Plugin(.*)?"));
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

    @Test
    void testComputePackageType() {
        GenerateMetadataMojo mojo = new GenerateMetadataMojo();
        // no filter, embeds (should not happen in reality)
        assertEquals(PackageType.MIXED, mojo.computePackageType());
        mojo.embeddeds = new Embedded[]{ new Embedded() };
        assertEquals(PackageType.CONTAINER, mojo.computePackageType());
        Filters filters = new Filters();
        filters.add(new PathFilterSet("/apps/mycontext"));
        mojo.filters = filters;
        // embedded + /apps content
        assertEquals(PackageType.MIXED, mojo.computePackageType());
        // just "/apps" content
        mojo.embeddeds = new Embedded[]{ };
        assertEquals(PackageType.APPLICATION, mojo.computePackageType());
        // mixed /apps and non-apps content
        filters.add(new PathFilterSet("/content/mycontext"));
        assertEquals(PackageType.MIXED, mojo.computePackageType());
        // non-apps content
        filters = new Filters();
        mojo.filters = filters;
        filters.add(new PathFilterSet("/content/mycontext"));
        assertEquals(PackageType.CONTENT, mojo.computePackageType());
        // subpackage + OSGi config/bundle file
        mojo.subPackages = new SubPackage[] { new SubPackage() };
        assertEquals(PackageType.MIXED, mojo.computePackageType());
        filters = new Filters();
        mojo.filters = filters;
        filters.add(new PathFilterSet("/apps/mycontext/config"));
        assertEquals(PackageType.CONTAINER, mojo.computePackageType());
        filters.add(new PathFilterSet("/apps/mycontext/config.somerunmode.author"));
        assertEquals(PackageType.CONTAINER, mojo.computePackageType());
        filters.add(new PathFilterSet("/apps/mycontext/configsuffix"));
        assertEquals(PackageType.MIXED, mojo.computePackageType());
        // just OSGi config/bundle file
        mojo.subPackages = new SubPackage[] {};
        filters = new Filters();
        mojo.filters = filters;
        filters.add(new PathFilterSet("/apps/mycontext/config"));
        assertEquals(PackageType.CONTAINER, mojo.computePackageType());
        filters.add(new PathFilterSet("/apps/mycontext/config.somerunmode.author"));
        assertEquals(PackageType.CONTAINER, mojo.computePackageType());
        filters.add(new PathFilterSet("/apps/mycontext/configsuffix"));
        assertEquals(PackageType.MIXED, mojo.computePackageType());
        // some content with config filter
        filters = new Filters();
        mojo.filters = filters;
        filters.add(new PathFilterSet("/content/mycontext/config"));
        assertEquals(PackageType.CONTENT, mojo.computePackageType());
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

    @Test
    void testComputeProperties() {
        // first test with SNAPSHOT version
        MavenProject project = Mockito.mock(MavenProject.class);
        Mockito.when(project.getArtifactId()).thenReturn("myartifactid");
        Mockito.when(project.getGroupId()).thenReturn("mygroupid");
        Mockito.when(project.getVersion()).thenReturn("1.0.0-SNAPSHOT");
        Mockito.when(project.getDescription()).thenReturn("my description");
        GenerateMetadataMojo mojo = new GenerateMetadataMojo();
        mojo.project = project;
        mojo.group = "mygroup";
        mojo.name = "myname";
        mojo.version = "1.0.0-SNAPSHOT";
        mojo.allowIndexDefinitions = true;
        mojo.packageType = PackageType.MIXED;
        mojo.outputTimestamp = "1";
        Properties properties = mojo.computeProperties(null, null);
        Properties expectedProperties = new Properties();
        expectedProperties.put("allowIndexDefinitions", "true");
        expectedProperties.put("name", "myname");
        expectedProperties.put("group", "mygroup");
        expectedProperties.put("description", "my description");
        expectedProperties.put("groupId", "mygroupid");
        expectedProperties.put("artifactId", "myartifactid");
        expectedProperties.put("version", "1.0.0-SNAPSHOT");
        expectedProperties.put("packageType", "mixed");
        expectedProperties.put("requiresRoot", "false");
        String expectedDate = ISO8601.format(new Date());
        expectedDate = expectedDate.substring(0, expectedDate.lastIndexOf("."));
        expectedProperties.put("created", expectedDate);
        for (Object key : properties.keySet()) {
            if (key.equals("created")) {
                // only compare on seconds level
                assertTrue(properties.get(key).toString().startsWith(expectedProperties.get(key).toString()), "Key " + key + "=" +  properties.get(key) + " does not start with " +  expectedProperties.get(key));
            } else {
                assertEquals(expectedProperties.get(key), properties.get(key), "Key " + key + " is not as expected");
            }
        }
        // test with release version
        mojo.version = "1.0.0";
        expectedDate = ISO8601.format(new Date(1l));
        for (Object key : properties.keySet()) {
            if (key.equals("created")) {
                // only compare on seconds level
                assertTrue(properties.get(key).toString().startsWith(expectedProperties.get(key).toString()), "Key " + key + "=" +  properties.get(key) + " does not start with " +  expectedProperties.get(key));
            } else {
                assertEquals(expectedProperties.get(key), properties.get(key), "Key " + key + " is not as expected");
            }
        }
    }
}
