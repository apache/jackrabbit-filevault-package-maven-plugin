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
package org.apache.jackrabbit.filevault.maven.packaging.it.util;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedReader;
import org.apache.jackrabbit.filevault.maven.packaging.mojo.VaultMojo;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;

/**
 * Helper class to build and verify a Maven project.
 */
public class ProjectBuilder implements AutoCloseable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ProjectBuilder.class);

    private static final Set<String> IGNORED_MANIFEST_ENTRIES = new HashSet<>(Arrays.asList("Build-Jdk-Spec", "Created-By"));

    public static final String TEST_PROJECTS_ROOT = "target/test-classes/test-projects";

    public static final String TEST_PACKAGE_DEFAULT_NAME = "target/package-plugin-test-pkg-1.0.0-SNAPSHOT";

    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(\\d*)%");

    private File testProjectsRoot;

    private File testProjectDir;

    private File testPackageFile;

    private Properties testProperties;

    private String[] testGoals = {"clean", "verify"};

    private List<String> pkgZipEntries;

    private File expectedFilesFile;

    private File expectedOrderFile;

    private File expectedManifestFile;

    private File expectedFilterFile;
    
    private File expectedFilesWithChecksumsFile;

    private File expectedLogLinesFile;

    private File logTxtFile;

    private boolean buildExpectedToFail;

    private boolean verifyPackageContents = true;

    private JarFile contentPackageZip;

    /**
     * This should only be used from {@link ProjectBuilderStore}
     */
    ProjectBuilder() {
        testProjectsRoot = new File(TEST_PROJECTS_ROOT);
        testProperties = new Properties();

        testProperties.put("plugin.version", getPluginVersion());
        testProperties.put("testcontent.directory", new File("target/test-classes/test-content").getAbsolutePath());
        testProperties.put("maven.compiler.source", "1.8");
        testProperties.put("maven.compiler.target", "1.8");
    }

    /**
     * Retrieves the version of the {@code filevault-package-maven-plugin} of the current project. The version is used in the
     * test poms so that the cli build uses the current plugin. Usually the version is set via the system property
     * {@code plugin.version} via the failsafe plugin. If the property is missing the method tries to read it from the
     * {@code pom.xml} of the project. This is useful when running the tests in an IDE.
     *
     * @return the version of the current {@code filevault-package-maven-plugin}
     * @throws IllegalArgumentException if the version cannot be determined.
     */
    public static String getPluginVersion() {
        String pluginVersion  = System.getProperty("plugin.version");
        if (pluginVersion == null) {
            try (FileReader fileReader = new FileReader("pom.xml")) {
                // try to read from project
                MavenXpp3Reader reader = new MavenXpp3Reader();
                Model model = reader.read(fileReader);
                pluginVersion = model.getVersion();
            } catch (IOException | XmlPullParserException e) {
                log.error("Unable to read version from pom", e);
            }
        }
        if (pluginVersion == null) {
            throw new IllegalArgumentException("Unable to detect plugin.version");
        }
        return pluginVersion;
    }

    public ProjectBuilder setTestProjectsRoot(File testProjectsRoot) {
        if (testProjectDir != null) {
            throw new IllegalArgumentException("projects root can't be changed after projects dir is set.");
        }
        this.testProjectsRoot = testProjectsRoot;
        return this;
    }

    public ProjectBuilder setTestProjectDir(File testProjectDir, String classifier) {
        this.testProjectDir = testProjectDir;
        this.testPackageFile = new File(testProjectDir, TEST_PACKAGE_DEFAULT_NAME + (StringUtils.isNotBlank(classifier)?"-"+classifier:"") + VaultMojo.PACKAGE_EXT);

        // if we ever need different files, just create the setters.
        this.expectedFilesFile = new File(testProjectDir, "expected-files.txt");
        this.expectedOrderFile = new File(testProjectDir, "expected-file-order.txt");
        this.expectedManifestFile = new File(testProjectDir, "expected-manifest.txt");
        this.expectedFilterFile = new File(testProjectDir, "expected-filter.xml");
        this.expectedFilesWithChecksumsFile = new File(testProjectDir, "expected-files-with-checksums.txt");
        this.expectedLogLinesFile = new File(testProjectDir, "expected-log-lines.txt");
        this.logTxtFile = new File(testProjectDir, "log.txt");
        
        return this;
    }

    public ProjectBuilder setTestProjectDir(File testProjectDir) {
        return setTestProjectDir(testProjectDir, null);
    }

    public ProjectBuilder setTestProjectDir(String relPath, String classifier) {
        return setTestProjectDir(new File(testProjectsRoot, relPath), classifier);
    }

    public ProjectBuilder setTestProjectDir(String relPath) {
        return setTestProjectDir(relPath, null);
    }

    public File getTestProjectDir() {
        return testProjectDir;
    }

    public ProjectBuilder setTestPackageFile(File testPackageFile) {
        this.testPackageFile = testPackageFile;
        return this;
    }

    public ProjectBuilder setTestPackageFile(String testPackageFileName) {
        this.setTestPackageFile(new File(testProjectDir, testPackageFileName));
        return this;
    }

    public File getTestPackageFile() {
        return testPackageFile;
    }

    public ProjectBuilder setTestGoals(String ... testGoals) {
        if (testGoals != null && testGoals.length != 0) {
            this.testGoals = testGoals;
        }
        return this;
    }

    public ProjectBuilder setBuildExpectedToFail(boolean buildExpectedToFail) {
        this.buildExpectedToFail = buildExpectedToFail;
        return this;
    }

    public ProjectBuilder setVerifyPackageContents(boolean verifyPackageContents) {
        this.verifyPackageContents = verifyPackageContents;
        return this;
    }

    public ProjectBuilder setProperty(String name, String value) {
        testProperties.put(name, value);
        return this;
    }

    public ProjectBuilder setExpectedFilesWithChecksumsFile(String expectedFilesWithChecksumsFile) {
        this.expectedFilesWithChecksumsFile = new File(testProjectDir, expectedFilesWithChecksumsFile);
        return this;
    }

    public ProjectBuilder build() throws VerificationException, IOException {
        close(); // make sure to release the package from last build
        Verifier verifier = new Verifier(testProjectDir.getAbsolutePath());
        verifier.setSystemProperties(testProperties);
        verifier.setDebug(true);
        verifier.setAutoclean(false);

        StringBuilder mavenOpts = new StringBuilder();

        //verifier.setDebugJvm(true);
        // verifier.setMavenDebug(true);
        // propagate jacoco agent settings
        String jacocoAgentSettings = System.getProperty("jacoco.command");
        if (StringUtils.isNotBlank(jacocoAgentSettings)) {
            mavenOpts.append(jacocoAgentSettings + " ");
        }
        if (mavenOpts.length() > 0)  {
            verifier.setEnvironmentVariable("MAVEN_OPTS", mavenOpts.toString());
        }
        try {
            verifier.executeGoals(Arrays.asList(testGoals));
            assertFalse(buildExpectedToFail, "Build expected to fail in project " + testProjectDir.getAbsolutePath());
        } catch (VerificationException e) {
            if (buildExpectedToFail) {
                return this;
            }
            throw e;
        } finally {
            verifier.resetStreams();
        }
        verifier.verify(true);

        if (!verifyPackageContents) {
            return this;
        }

        // read zip
        openPackage();
        pkgZipEntries = verifyPackageZipEntries(contentPackageZip);
        return this;
    }

    private JarFile openPackage() throws IOException {
        if (contentPackageZip == null) {
            contentPackageZip = new JarFile(testPackageFile);
        }
        return contentPackageZip;
    }

    public static List<String> verifyPackageZipEntries(File packageFile) throws IOException {
        assertTrue(packageFile.exists(), "Project did not generate package file at " + packageFile);
        try (JarFile jarFile = new JarFile(packageFile)) {
            return verifyPackageZipEntries(jarFile);
        }
    }
 
    private static List<String> verifyPackageZipEntries(JarFile packageFile) throws IOException {
        List<String> pkgZipEntries = new ArrayList<>();
        Enumeration<JarEntry> e = packageFile.entries();
        while (e.hasMoreElements()) {
            pkgZipEntries.add(e.nextElement().getName());
        }
        // ensure that MANIFEST.MF is first entry
        String first = pkgZipEntries.get(0);
        if ("META-INF/".equals(first)) {
            first = pkgZipEntries.get(1);
        }
        assertEquals("META-INF/MANIFEST.MF", first, "MANIFEST.MF is not first entry in package " + packageFile);

        // ensure that there is a jcr_root directory
        assertTrue(pkgZipEntries.contains("jcr_root/"), "Package does not contain mandatory 'jcr_root' folder in package " + packageFile);
        return pkgZipEntries;
    }

    public ProjectBuilder verifyPackageProperty(String key, String value) throws IOException {
        if (buildExpectedToFail) {
            return this;
        }
        assertEquals(value, getPackageProperty(key), "Property '" + key + "' has incorrect value");
        return this;
    }
  
    public String getPackageProperty(String key) throws ZipException, IOException {
        Properties properties;
        openPackage();
        ZipEntry propertiesFile = contentPackageZip.getEntry("META-INF/vault/properties.xml");
        MatcherAssert.assertThat(propertiesFile, notNullValue());

        properties = new Properties();
        properties.loadFromXML(contentPackageZip.getInputStream(propertiesFile));
        return properties.getProperty(key);
    }

    public ProjectBuilder verifyExpectedManifest() throws IOException {
        final List<String> expectedEntries = Files.readAllLines(expectedManifestFile.toPath(), StandardCharsets.US_ASCII);
        List<String> entries;
        openPackage();
        entries = new ArrayList<>();
        for (Map.Entry<Object, Object> e : contentPackageZip.getManifest().getMainAttributes().entrySet()) {
            String key = e.getKey().toString();
            if (IGNORED_MANIFEST_ENTRIES.contains(key)) {
                continue;
            }
            if ("Import-Package".equals(key)) {
                // split export package so that we have a sorted set
                Parameters params = new Parameters(e.getValue().toString());
                for (Map.Entry<String, Attrs> entry : params.entrySet()) {
                    entries.add(key + ":" + entry.getKey() + ";" + entry.getValue());
                }
                continue;
            }
            entries.add(e.getKey() + ":" + e.getValue());
        }
        Collections.sort(entries);
        assertEquals(expectedEntries, entries, "Manifest");
        return this;
    }

    public ProjectBuilder verifyExpectedFiles() throws IOException {
        verifyExpectedFiles(expectedFilesFile, pkgZipEntries);
        return this;
    }

    public ProjectBuilder verifyExpectedFiles(File expectedFilesFile, List<String> pkgZipEntries) throws IOException {
        // first check that only the expected entries are there in the package (regardless of the order)
        List<String> expectedEntries = Files.readAllLines(expectedFilesFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(
                toTidyString(expectedEntries),
                toTidyString(pkgZipEntries),
                "Package does not contain the expected entry names");
        return this;
    }

    public ProjectBuilder verifyExpectedFilesChecksum() throws IOException {
        List<String> expectedEntriesWithChecksums = Files.readAllLines(expectedFilesWithChecksumsFile.toPath(), StandardCharsets.UTF_8);
        for (String expectedEntryWithChecksum : expectedEntriesWithChecksums) {
            // split name and checksum
            String[] parts = expectedEntryWithChecksum.split(" ", 2);
            verifyExpectedFileChecksum(parts[0], parts[1]);
        }
        return this;
    }

    public ProjectBuilder verifyExpectedFileChecksum(String name, String checksum) throws IOException {
        // the second part must be a hexadecimal CRC32 checksum
        final long expectedChecksum = Long.parseLong(checksum, 16);
        openPackage();
        JarEntry entry = contentPackageZip.getJarEntry(name);
        if (entry == null) {
            fail("Could not find entry with name " + name + " in package " + testPackageFile);
        }
        MatcherAssert.assertThat(entry, new JarEntryMatcher(name, contentPackageZip, expectedChecksum));
        return this;
    }

    public ProjectBuilder verifyExpectedFileContent(String name, Charset cs, String expectedContent) throws IOException {
        openPackage();
        JarEntry entry = contentPackageZip.getJarEntry(name);
        if (entry == null) {
            fail("Could not find entry with name " + name + " in package " + testPackageFile);
        }
        try (InputStream actualInput = contentPackageZip.getInputStream(entry)) {
            assertEquals(expectedContent, IOUtils.toString(actualInput, cs));
        }
        return this;
    }

    private final static class JarEntryMatcher extends TypeSafeMatcher<JarEntry> {

        private final String name;
        private final long expectedCrc; 
        private final JarFile jarFile;
        public JarEntryMatcher(String name, JarFile jarFile, long expectedCrc) {
            this.name = name;
            this.jarFile = jarFile;
            this.expectedCrc = expectedCrc;
        }

        @Override
        protected void describeMismatchSafely(JarEntry item, Description mismatchDescription) {
            mismatchDescription.appendText("was Jar entry with name ").appendValue(item.getName()).appendText(" having the CRC ").appendValue(Long.toHexString(item.getCrc()));
            try (Reader reader = new BoundedReader(new InputStreamReader(jarFile.getInputStream(item), StandardCharsets.UTF_8), 8000)) {
                String content = IOUtils.toString(reader);
                // make new line visible
                content = content.replaceAll("\r", Matcher.quoteReplacement("\r")).replaceAll("\n",  Matcher.quoteReplacement("\n"));
                mismatchDescription.appendText(" (").appendValue(content).appendText(")");
            } catch (IOException e) {
                mismatchDescription.appendText(" (Could not extract value due to exception ").appendValue(e).appendText(")");
            }
        }

        @Override
        protected boolean matchesSafely(JarEntry item) {
            return expectedCrc == item.getCrc();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Jar entry with name ").appendValue(name).appendText(" having the CRC ").appendValue(Long.toHexString(expectedCrc));
        }
    }

    public ProjectBuilder verifyExpectedFilesOrder() throws IOException {
        List<String> expectedEntriesInOrder= Files.readAllLines(expectedOrderFile.toPath(), StandardCharsets.UTF_8);
        MatcherAssert.assertThat("Order of entries within package", pkgZipEntries, Matchers.containsInRelativeOrder(expectedEntriesInOrder.toArray()));
        return this;
    }

    public ProjectBuilder verifyExpectedFilter() throws IOException {
        if (buildExpectedToFail) {
            return this;
        }
        openPackage();
        ZipEntry entry = contentPackageZip.getEntry("META-INF/vault/filter.xml");
        assertNotNull(entry, "package has a filter.xml");
        verifyExpectedFilter(IOUtils.toString(contentPackageZip.getInputStream(entry), StandardCharsets.UTF_8));
        return this;
    }

    public ProjectBuilder verifyExpectedFilterInWorkDirectory(final String workDirectory) throws IOException {
        if (buildExpectedToFail) {
            return this;
        }
        File workDirFile = new File(testProjectDir, workDirectory);
        assertTrue(workDirFile.isDirectory(), "workDirectory should exist: " + workDirFile.toString());
        File filterFile = new File(workDirFile, "META-INF/vault/filter.xml");
        assertTrue(filterFile.isFile(), "filterFile should exist: " + filterFile.toString());
        verifyExpectedFilter(new String(Files.readAllBytes(filterFile.toPath()), StandardCharsets.UTF_8));
        return this;
    }

    private void verifyExpectedFilter(String actualFilter) throws IOException {
        String expected = new String(Files.readAllBytes(expectedFilterFile.toPath()), StandardCharsets.UTF_8);
        assertEquals(normalizeWhitespace(expected), normalizeWhitespace(actualFilter), "filter.xml is incorrect");
    }

    public ProjectBuilder verifyExpectedLogLines(String... placeholderValues) throws IOException {
        List<String> expectedLogLines = Files.readAllLines(expectedLogLinesFile.toPath());
        List<String> actualLogLines = getBuildOutput();
        for (String expectedLogLine : expectedLogLines) {
            // do placeholder replacement
            
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(expectedLogLine);
            while (matcher.find()) {
                int placeholderIndex = Integer.parseInt(matcher.group(1));
                if (placeholderIndex >= placeholderValues.length) {
                    throw new IllegalArgumentException("At least " + placeholderIndex + " placeholder values need to be given, but only "+ placeholderValues.length + " received.");
                }
                // replace current item in iterator with the new value
                expectedLogLine = matcher.replaceAll(Matcher.quoteReplacement(placeholderValues[placeholderIndex]));
            }
            // update list
            MatcherAssert.assertThat("Could not find the expected log line in the output '" + logTxtFile +"'", actualLogLines, Matchers.hasItem(expectedLogLine));
        }
        // support not and exists
        return this;
    }

    public List<String> getBuildOutput() throws IOException {
        return Files.readAllLines(logTxtFile.toPath(), StandardCharsets.UTF_8);
    }

    private String toTidyString(List<String> lines) {
        String[] copy = lines.toArray(new String[lines.size()]);
        Arrays.sort(copy);
        StringBuilder buf = new StringBuilder();
        for (String line: copy) {
            buf.append(line).append("\n");
        }
        return buf.toString();
    }

    /**
     * Eliminates differences in line separators when executing tests on different platform (*nix / windows)
     */
    private String normalizeWhitespace(String s) {
        return s.replaceAll("[\r\n]+", "\n");
    }

    @Override
    public void close() throws IOException {
        if (contentPackageZip != null) {
            contentPackageZip.close();
            contentPackageZip = null;
        }
    }
}
