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

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
import java.util.zip.ZipFile;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.maven.shared.utils.io.IOUtil;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;

/**
 * Helper class to build and verify a maven project.
 */
public class ProjectBuilder {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ProjectBuilder.class);

    private static final Set<String> IGNORED_MANIFEST_ENTRIES = new HashSet<>(Arrays.asList("Build-Jdk-Spec", "Created-By"));

    static final String TEST_PROJECTS_ROOT = "target/test-classes/test-projects";

    static final String TEST_PACKAGE_DEFAULT_NAME = "target/package-plugin-test-pkg-1.0.0-SNAPSHOT.zip";

    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(\\d*)%");

    private File testProjectsRoot;

    private File testProjectDir;

    private File testPackageFile;

    private Properties testProperties;

    private String[] testGoals = {"clean", "package"};

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

    public ProjectBuilder() {
        testProjectsRoot = new File(TEST_PROJECTS_ROOT);
        testProperties = new Properties();

        testProperties.put("plugin.version", getPluginVersion());
        testProperties.put("testcontent.directory", new File("target/test-classes/test-content").getAbsolutePath());
    }

    /**
     * Retrieves the version of the {@code filevault-package-maven-plugin} of the current project. The version is used in the
     * test poms so that the cli build uses the current plugin. Usually the version is set via the system property
     * {@code plugin.version} via the failsafe plugin. If the property is missing the method tries to read it from the
     * {@code pom.xml} of the project. this is useful when running the tests in an IDE.
     *
     * @return the version of the current {@code filevault-package-maven-plugin}
     * @throws IllegalArgumentException if the version cannot be determined.
     */
    private String getPluginVersion() {
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

    public ProjectBuilder setTestProjectDir(File testProjectDir) {
        this.testProjectDir = testProjectDir;
        this.testPackageFile = new File(testProjectDir, TEST_PACKAGE_DEFAULT_NAME);

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

    public ProjectBuilder setTestProjectDir(String relPath) {
        return setTestProjectDir(new File(testProjectsRoot, relPath));
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

    public ProjectBuilder build() throws VerificationException, IOException {
        Verifier verifier = new Verifier(testProjectDir.getAbsolutePath());
        verifier.setSystemProperties(testProperties);
        verifier.setDebug(true);
        verifier.setAutoclean(false);
        //verifier.setDebugJvm(true);

        try {
            verifier.executeGoals(Arrays.asList(testGoals));
            assertFalse("Build expected to fail in project " + testProjectDir.getAbsolutePath(), buildExpectedToFail);
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

        assertTrue("Project generates package file", testPackageFile.exists());

        // read zip
        pkgZipEntries = new ArrayList<>();
        try (JarFile jar = new JarFile(testPackageFile)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                pkgZipEntries.add(e.nextElement().getName());
            }
        }
        // ensure that MANIFEST.MF is first entry
        String first = pkgZipEntries.get(0);
        if ("META-INF/".equals(first)) {
            first = pkgZipEntries.get(1);
        }
        assertEquals("MANIFEST.MF must be first entry", "META-INF/MANIFEST.MF", first);

        return this;
    }

    public ProjectBuilder verifyPackageProperty(String key, String value) throws IOException {
        if (buildExpectedToFail) {
            return this;
        }
        assertEquals("Property '" + key + "' has correct value", value, getPackageProperty(key));
        return this;
    }

    public String getPackageProperty(String key) throws ZipException, IOException {
        Properties properties;
        try (ZipFile zip = new ZipFile(testPackageFile)) {
            ZipEntry propertiesFile = zip.getEntry("META-INF/vault/properties.xml");
            assertThat(propertiesFile, notNullValue());

            properties = new Properties();
            properties.loadFromXML(zip.getInputStream(propertiesFile));
        }
        return properties.getProperty(key);
    }

    public ProjectBuilder verifyExpectedManifest() throws IOException {
        final String expected = FileUtils.fileRead(expectedManifestFile);
        List<String> entries;
        String result;
        try (JarFile jar = new JarFile(testPackageFile)) {
            entries = new ArrayList<>();
            for (Map.Entry<Object, Object> e : jar.getManifest().getMainAttributes().entrySet()) {
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
        }
        Collections.sort(entries);
        result = StringUtils.join(entries.iterator(), "\n");
        assertEquals("Manifest", normalizeWhitespace(expected), normalizeWhitespace(result));
        return this;
    }

    public ProjectBuilder verifyExpectedFiles() throws IOException {
        // first check that only the expected entries are there in the package (regardless of the order)
        List<String> expectedEntries = Files.readAllLines(expectedFilesFile.toPath(), StandardCharsets.UTF_8);
        assertEquals("Package contains the expected entry names",
                toTidyString(expectedEntries),
                toTidyString(pkgZipEntries));
        return this;
    }

    public ProjectBuilder verifyExpectedFilesChecksum() throws IOException {
        List<String> expectedEntriesWithChecksums = Files.readAllLines(expectedFilesWithChecksumsFile.toPath(), StandardCharsets.UTF_8);
        for (String expectedEntryWithChecksum : expectedEntriesWithChecksums) {
            // split name and checksum
            String[] parts = expectedEntryWithChecksum.split(" ", 2);
            final String name = parts[0];
            // the second part must be a hexadecimal CRC32 checksum
            final long expectedChecksum = Long.parseLong(parts[1], 16);
            try (JarFile jar = new JarFile(testPackageFile)) {
                JarEntry entry = jar.getJarEntry(name);
                if (entry == null) {
                    fail("Could not find entry with name " + name + " in package " + testPackageFile);
                }
                long actualChecksum = entry.getCrc();
                assertEquals("Checksum of entry with name " + name + " is not equal to the expected value", expectedChecksum, actualChecksum);
            }
        }
        return this;
    }
    
    public ProjectBuilder verifyExpectedFilesOrder() throws IOException {
        List<String> expectedEntriesInOrder= Files.readAllLines(expectedOrderFile.toPath(), StandardCharsets.UTF_8);
        assertThat("Order of entries within package", pkgZipEntries, Matchers.containsInRelativeOrder(expectedEntriesInOrder.toArray()));
        return this;
    }

    public ProjectBuilder verifyExpectedFilter() throws IOException {
        if (buildExpectedToFail) {
            return this;
        }
        try (ZipFile zip = new ZipFile(testPackageFile)) {
            ZipEntry entry = zip.getEntry("META-INF/vault/filter.xml");
            assertNotNull("package has a filter.xml", entry);
            String result = IOUtil.toString(zip.getInputStream(entry), "utf-8");
            String expected = FileUtils.fileRead(expectedFilterFile);
            assertEquals("filter.xml is correct", normalizeWhitespace(expected), normalizeWhitespace(result));
        }
        return this;
    }

    public ProjectBuilder verifyExpectedFilterInWorkDirectory(final String workDirectory) throws IOException {
        if (buildExpectedToFail) {
            return this;
        }
        File workDirFile = new File(testProjectDir, workDirectory);
        assertTrue("workDirectory should exist: " + workDirFile.toString(), workDirFile.isDirectory());
        File filterFile = new File(workDirFile, "META-INF/vault/filter.xml");
        assertTrue("filterFile should exist: " + filterFile.toString(), filterFile.isFile());
        String result = FileUtils.fileRead(filterFile);
        String expected = FileUtils.fileRead(expectedFilterFile);
        assertEquals("filter.xml is incorrect", normalizeWhitespace(expected), normalizeWhitespace(result));
        return this;
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
            assertThat("Could not find the expected log line in the output '" + logTxtFile +"'", actualLogLines, Matchers.hasItem(expectedLogLine));
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
}
