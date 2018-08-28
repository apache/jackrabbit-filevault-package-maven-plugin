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
package org.apache.jackrabbit.filevault.maven.packaging.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nonnull;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.StringUtils;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Clazz;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Descriptors;
import aQute.bnd.osgi.FileResource;
import aQute.bnd.osgi.Processor;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FilenameUtils.normalize;

/**
 * The import package builder is used to analyze the classes and dependencies of the project and calculate the
 * import-package statement for this package.
 */
public class ImportPackageBuilder {

    /**
     * class file directory
     */
    private File classFileDirectory;

    /**
     * list of class files. initialized during {@link #analyze()}
     */
    private List<File> classFiles;

    /**
     * list of artifacts relevant for analysis
     */
    private List<Artifact> artifacts;

    /**
     * BND analyzer
     */
    private Analyzer analyzer;

    /**
     * The scan result from the fast-classpath analyzer
     */
    private ScanResult scanResult;

    /**
     * artifact-id -> bundle info mapping
     */
    private Map<String, BundleInfo> bundles = new HashMap<String, BundleInfo>();

    /**
     * map of all exported packaged by the bundles.
     */
    private Map<String, PackageInfo> exported = new HashMap<String, PackageInfo>();

    /**
     * map of all classes
     */
    private Map<String, ClassInfo> classes = new HashMap<String, ClassInfo>();

    /**
     * the calculated import parameters.
     */
    private Map<String, Attrs> importParameters = Collections.emptyMap();

    /**
     * specifies if unused packages should be included if there are not classes in the project
     */
    private boolean includeUnused;

    /**
     * filter for project artifacts
     */
    private ArtifactFilter filter = new ArtifactFilter() {
        @Override
        public boolean include(Artifact artifact) {
            return true;
        }
    };

    /**
     * Sets the class files directory
     * @param classes the directory
     * @return this.
     */
    @Nonnull
    public ImportPackageBuilder withClassFileDirectory(File classes) {
        classFileDirectory = classes;
        return this;
    }

    /**
     * defines the project from which the artifacts should be loaded.
     * The current implementation requires the filter to be set before calling this method.
     * @param project the maven project
     * @return this
     */
    @Nonnull
    public ImportPackageBuilder withDependenciesFromProject(@Nonnull MavenProject project) {
        artifacts = new ArrayList<Artifact>();
        for (Artifact a : project.getDependencyArtifacts()) {
            if (!filter.include(a)) {
                continue;
            }
            // skip all test dependencies (all other scopes are potentially relevant)
            if (Artifact.SCOPE_TEST.equals(a.getScope())) {
                continue;
            }
            // type of the considered dependencies must be either "jar" or "bundle"
            if (!"jar".equals(a.getType()) && (!"bundle".equals(a.getType()))) {
                continue;
            }
            artifacts.add(a);
        }
        return this;
    }

    /**
     * defines if unused packages should be included if no classes exist in the project.
     * @param includeUnused {@code true} to include unused.
     * @return this
     */
    @Nonnull
    public ImportPackageBuilder withIncludeUnused(boolean includeUnused) {
        this.includeUnused = includeUnused;
        return this;
    }

    /**
     * defines the filter for the project artifact
     * @param filter the filter
     * @return this
     */
    @Nonnull
    public ImportPackageBuilder withFilter(@Nonnull ArtifactFilter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * analyzes the imports
     * @return this
     * @throws IOException if an error occurrs.
     */
    @Nonnull
    public ImportPackageBuilder analyze() throws IOException {
        initClassFiles();
        initAnalyzer();
        scanClassPath();
        scanBundles();
        scanClasses();
        calculateImportParameters();
        return this;
    }

    /**
     * returns the import parameter header. only available after {@link #analyze()}
     * @return the parameters
     */
    @Nonnull
    public Map<String, Attrs> getImportParameters() {
        return importParameters;
    }

    /**
     * generates a package report
     * @return the report
     */
    @Nonnull
    public String createExportPackageReport() {
        TreeSet<String> unusedBundles = new TreeSet<String>(bundles.keySet());
        StringBuilder report = new StringBuilder("Export package report:\n\n");
        List<String> packages = new ArrayList<String>(exported.keySet());
        Collections.sort(packages);
        int pad = 18;
        for (String packageName : packages) {
            pad = Math.max(pad, packageName.length());
        }
        pad += 2;

        report
                .append(StringUtils.rightPad("Exported packages", pad))
                .append(StringUtils.rightPad("Uses", 5))
                .append(StringUtils.rightPad("Version", 10))
                .append("Dependency\n");
        report.append(StringUtils.repeat("-", pad + 30)).append("\n");
        for (String packageName : packages) {
            PackageInfo info = exported.get(packageName);

            report.append(StringUtils.rightPad(packageName, pad));
            report.append(StringUtils.rightPad(String.valueOf(info.usedBy.size()), 5));
            boolean first = true;
            for (BundleInfo bInfo : info.bundles.values()) {
                if (first) {
                    String version = bInfo.packageVersions.get(packageName);
                    if (StringUtils.isEmpty(version)) {
                        version = "0.0.0";
                    }
                    report.append(StringUtils.rightPad(version, 10));
                    report.append(bInfo.getId());
                    first = false;
                }
                if (!info.usedBy.isEmpty()) {
                    unusedBundles.remove(bInfo.getId());
                }
            }
            if (first) {
                report.append(StringUtils.rightPad("n/a", 10));
            }
            report.append("\n");
        }

        report.append("\n").append(unusedBundles.size()).append(" unused bundles\n");
        report.append("------------------------------\n");
        for (String bundleId : unusedBundles) {
            report.append(bundleId).append("\n");
        }

        report.append("\nPackages used in the analyzed classes: \n");
        report.append("------------------------------\n");
        for (Map.Entry<String, Attrs> e: importParameters.entrySet()) {
            report.append(e.getKey());
            try {
                Processor.printClause(e.getValue(), report);
            } catch (IOException e1) {
                throw new IllegalStateException("Internal error while generating report", e1);
            }
            report.append("\n");
        }

        return report.toString();
    }

    /**
     * internally scans all the class files.
     */
    private void initClassFiles() {
        if (!classFileDirectory.exists()) {
            classFiles = Collections.emptyList();
            return;
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(classFileDirectory);
        scanner.setIncludes(new String[]{"**/*.class"});
        scanner.scan();
        String[] paths = scanner.getIncludedFiles();
        classFiles = new ArrayList<File>(paths.length);
        for (String path : paths) {
            File file = new File(path);
            if (!file.isAbsolute()) {
                file = new File(classFileDirectory, path);
            }
            classFiles.add(file);
        }
    }

    /**
     * Returns the classloader for the analysis. this should exclude the classes of the maven runtime.
     *
     * @throws IOException If an error occurs
     */
    private ClassLoader getClassLoader() throws IOException,
            DependencyResolutionRequiredException {
        List<URL> classPath = new ArrayList<URL>();

        // add output directory to classpath
        classPath.add(classFileDirectory.toURI().toURL());

        // add artifacts from project
        for (Artifact a: artifacts) {
            classPath.add(a.getFile().toURI().toURL());
        }

        // use our parent as parent, in order to exclude the maven runtime.
        return new URLClassLoader(classPath.toArray(new URL[classPath.size()]), this.getClass().getClassLoader().getParent());
    }

    /**
     * scans the classpath
     * @throws IOException if an error occurrs.
     */
    private void scanClassPath() throws IOException {
        try {
            scanResult = new FastClasspathScanner()
                    .overrideClassLoaders(getClassLoader())
                    .scan();

        } catch (Exception e) {
            throw new IOException("Failed to scan the classpath", e);
        }
    }

    /**
     * initializes the bnd analyzer
     */
    private void initAnalyzer() {
        analyzer = new Analyzer();
    }

    /**
     * scans all the bundles and initializes their export packages.
     * @throws IOException if an error occurrs
     */
    private void scanBundles() throws IOException {
        for (Artifact a : artifacts) {
            BundleInfo info = new BundleInfo(a);
            bundles.put(info.getId(), info);

            // update the reverse map
            for (String pkgName : info.packageVersions.keySet()) {
                PackageInfo pkg = exported.get(pkgName);
                if (pkg == null) {
                    pkg = new PackageInfo(pkgName);
                    exported.put(pkgName, pkg);
                }
                pkg.bundles.put(info.getId(), info);
            }
        }
    }

    /**
     * Registers the package reference from the given class
     * @param info the class info that references the package
     * @param pkgName the package that is referenced
     */
    private void registerPackageReference(ClassInfo info, String pkgName) {
        PackageInfo pkgInfo = exported.get(pkgName);
        if (pkgInfo == null) {
            pkgInfo = new PackageInfo(pkgName);
            exported.put(pkgName, pkgInfo);
        }
        info.resolved.put(pkgName, pkgInfo);
        pkgInfo.usedBy.add(info.getName());
    }

    /**
     * scans the classes and resolves them against the bundles.
     * @throws IOException if an error occurrs.
     */
    private void scanClasses() throws IOException {
        for (File file : classFiles) {
            try {
                Clazz clazz = new Clazz(analyzer, file.getPath(), new FileResource(file));
                clazz.parseClassFile();
                ClassInfo info = new ClassInfo(clazz);
                classes.put(info.getName(), info);

                String myPackage = getPackageName(info.getName());
                for (Descriptors.PackageRef ref : clazz.getReferred()) {
                    String importPkgName = ref.getFQN();
                    if (!importPkgName.equals(myPackage)) {
                        registerPackageReference(info, importPkgName);
                    }
                }

                // checking for super classes
                io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo clzInfo = scanResult.getClassNameToClassInfo().get(clazz.getFQN());
                if (clzInfo != null) {
                    for (String name: clzInfo.getNamesOfImplementedInterfaces()) {
                        registerPackageReference(info, getPackageName(name));
                    }
                    for (String name: clzInfo.getNamesOfSuperclasses()) {
                        registerPackageReference(info, getPackageName(name));
                    }
                }
            } catch (Exception e) {
                throw new IOException("Error while parsing class: " + file.getPath(), e);
            }
        }
    }

    /**
     * Returns the package name for the given class name
     * @param className the class name
     * @return the package name
     */
    private static String getPackageName(String className) {
        return StringUtils.chomp(className, ".");
    }

    /**
     * Calculates returns the import parameter header.
     */
    private void calculateImportParameters() {
        importParameters = new TreeMap<String, Attrs>();
        for (PackageInfo info : exported.values()) {
            if (!classFiles.isEmpty() && info.usedBy.isEmpty()) {
                // skip if not used.
                continue;
            }
            if (classFiles.isEmpty() && !includeUnused) {
                continue;
            }
            if (info.bundles.isEmpty()) {
                // skip if no bundle
                continue;
            }
            // get first version
            BundleInfo bInfo = info.bundles.values().iterator().next();
            String version = bInfo.packageVersions.get(info.getName());
            Attrs options = new Attrs();
            if (!StringUtils.isEmpty(version)) {
                options.put(Constants.VERSION_ATTRIBUTE, new aQute.bnd.version.VersionRange("@" + version).toString());
            }
            importParameters.put(info.getName(), options);
        }
    }


    private static class PackageInfo {

        private final String name;

        private final Map<String, BundleInfo> bundles = new HashMap<String, BundleInfo>();

        private final Set<String> usedBy = new HashSet<String>();

        private PackageInfo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static class BundleInfo {

        private final String id;

        private final Map<String, String> packageVersions = new HashMap<String, String>();

        private BundleInfo(Artifact artifact) throws IOException {
            id = artifact.getId();
            File file = artifact.getFile();

            // In case of an internal dependency in a multi-module project, the dependency may be represented by a directory rather than a JAR file
            // if the maven lifecyle phase does not include binding the JAR file to the dependency.
            Dependency dependency = file.isDirectory() ? new DirectoryDependency(file) : new JarBasedDependency(file);

            Manifest manifest = dependency.getManifest();
            if (manifest != null && manifest.getMainAttributes().getValue(Constants.EXPORT_PACKAGE) != null) {
                String exportPackages = manifest.getMainAttributes().getValue(Constants.EXPORT_PACKAGE);
                for (Map.Entry<String, Attrs> entry : new Parameters(exportPackages).entrySet()) {
                    Attrs options = entry.getValue();
                    String version = options.getVersion();
                    packageVersions.put(entry.getKey(), version == null ? "" : version);
                }
            } else {
                // scan the class files and associate the version
                for (String path : dependency.getClassFiles()) {
                    // skip internal / impl
                    if (path.contains("/impl/") || path.contains("/internal/")) {
                        continue;
                    }
                    path = StringUtils.chomp(path, "/");
                    if (path.charAt(0) == '/') {
                        path = path.substring(1);
                    }
                    String packageName = path.replaceAll("/", ".");
                    packageVersions.put(packageName, "");
                }
            }
        }

        public String getId() {
            return id;
        }
    }

    private interface Dependency {
        /**
         * @return may be <code>null</code>.
         */
        Manifest getManifest() throws IOException;

        /**
         * @return paths representing .class files in their java package directory with *nix-style
         *         path separators, e.g. <code>/some/package/name/ClassFile.class</code>.
         */
        Collection<String> getClassFiles() throws IOException;
    }

    /**
     * Represents the JAR file of an {@link Artifact} dependency.
     */
    private static class JarBasedDependency implements Dependency {
        private final File file;

        private JarBasedDependency(File file) {
            this.file = file;
        }

        @Override
        public Manifest getManifest() throws IOException {
            try (JarFile jarFile = new JarFile(this.file)) {
                return jarFile.getManifest();
            }
        }

        @Override
        public Collection<String> getClassFiles() throws IOException {
            List<String> fileNames = new LinkedList<>();
            try (JarFile jar = new JarFile(this.file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (e.isDirectory()) {
                        continue;
                    }
                    String path = e.getName();
                    if (path.endsWith(".class")) {
                        fileNames.add(path);
                    }
                }
            }

            return fileNames;
        }
    }

    /**
     * Represents an {@link Artifact} dependency to another module of a multi-module setup
     * which may point to the target/classes directory of the module rather than a jar file.
     */
    private static class DirectoryDependency implements Dependency {
        private final File directory;

        private DirectoryDependency(File directory) {
            this.directory = directory;
        }

        public Manifest getManifest() throws IOException {
            File manifest = new File(this.directory, "META-INF/MANIFEST.MF");
            if (!manifest.exists()) {
                return null;
            }
            try (InputStream in = new FileInputStream(manifest)) {
                return new Manifest(in);
            }
        }

        @Override
        public Collection<String> getClassFiles() throws IOException {
            Collection<File> files = listFiles(this.directory, new String[]{"class"}, true);
            String basePath = this.directory.getCanonicalPath();
            Collection<String> fileNames = new ArrayList<>(files.size());
            for (File file : files) {
                // Use the relative file path as the the path segments will be used
                // to calculate the package name.
                String relativePath = file.getCanonicalPath().substring(basePath.length());
                // The path may contain platform-specific paths that must be normalized to unix paths.
                fileNames.add(normalize(relativePath, true));
            }
            return fileNames;
        }
    }

    private static class ClassInfo {

        private final Clazz clazz;

        private final Map<String, PackageInfo> resolved = new HashMap<String, PackageInfo>();

        private ClassInfo(Clazz clazz) {
            this.clazz = clazz;
        }

        public String getName() {
            return clazz.getFQN();
        }
    }
}