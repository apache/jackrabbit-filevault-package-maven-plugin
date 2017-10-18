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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;
import org.apache.maven.archiver.ManifestConfiguration;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Processor;
import org.apache.jackrabbit.filevault.maven.packaging.impl.DefaultWorkspaceFilter;
import org.apache.jackrabbit.filevault.maven.packaging.impl.PackageType;
import org.apache.jackrabbit.filevault.maven.packaging.impl.PathFilterSet;
import org.apache.jackrabbit.filevault.maven.packaging.impl.PlatformNameFormat;

import static org.codehaus.plexus.archiver.util.DefaultFileSet.fileSet;

/**
 * Build a content package.
 */
@Mojo(
        name = "package",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class VaultMojo extends AbstractEmbeddedsMojo {

    private static final String JCR_ROOT = "jcr_root/";

    private static final String VAULT_DIR = "META-INF/vault";

    public static final String PROPERTIES_FILE = VAULT_DIR + "/properties.xml";

    public static final String FILTER_FILE = VAULT_DIR + "/filter.xml";

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static final String PACKAGE_TYPE = "zip";

    private static final String PACKAGE_EXT = "." + PACKAGE_TYPE;

    public static final String MF_KEY_PACKAGE_TYPE = "Content-Package-Type";

    public static final String MF_KEY_PACKAGE_ID = "Content-Package-Id";

    private static final String MF_KEY_PACKAGE_DEPENDENCIES = "Content-Package-Dependencies";

    public static final String MF_KEY_PACKAGE_ROOTS = "Content-Package-Roots";

    private static final String MF_KEY_PACKAGE_DESC = "Content-Package-Description";

    private static final String MF_KEY_IMPORT_PACKAGE = "Import-Package";

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * The directory containing the content to be packaged up into the content
     * package.
     */
    @Parameter(
            defaultValue = "${project.build.outputDirectory}",
            required = true)
    private File builtContentDirectory;

    /**
     * The directory containing the compiled classes to use to import analysis.
     */
    @Parameter(
            defaultValue = "${project.build.outputDirectory}",
            required = true)
    private File classesDirectory;

    /**
     * The name of the generated package ZIP file without the ".zip" file
     * extension.
     */
    @Parameter(
            property = "vault.finalName",
            defaultValue = "${project.build.finalName}",
            required = true)
    private String finalName;

    /**
     * Directory in which the built content package will be output.
     */
    @Parameter(
            defaultValue="${project.build.directory}",
            required = true)
    private File outputDirectory;

    /**
     * Optional file that specifies the source of the workspace filter. The filters specified in the configuration
     * and injected via emebedds or subpackages are merged into it.
     */
    @Parameter
    private File filterSource;

    /**
     * The directory containing the content to be packaged up into the content
     * package.
     */
    @Parameter(
            defaultValue = "${project.build.directory}/vault-work",
            required = true)
    private File workDirectory;

    /**
     * The archive configuration to use. See <a
     * href="http://maven.apache.org/shared/maven-archiver/index.html">the
     * documentation for Maven Archiver</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive;

    /**
     * Adds a path prefix to all resources useful for shallower source trees.
     */
    @Parameter(property = "vault.prefix")
    private String prefix;

    /**
     * The groupId used for the generated content package. This will be part of
     * the target installation path of the content package.
     */
    @Parameter(
            property = "vault.group",
            defaultValue="${project.groupId}",
            required = true)
    private String group;

    /**
     * The name of the content package
     */
    @Parameter(
            property = "vault.name",
            defaultValue="${project.artifactId}",
            required = true)
    private String name;

    /**
     * The version of the content package.
     */
    @Parameter(
            property = "vault.version",
            defaultValue = "${project.version}",
            required = true)
    private String version;

    /**
     * Defines whether the package requires root. This will become the
     * <code>requiresRoot</code> property of the properties.xml file.
     */
    @Parameter(
            property = "vault.requiresRoot",
            defaultValue="false",
            required = true)
    private boolean requiresRoot;

    /**
     * Defines whether the package is allowed to contain index definitions. This will become the
     * <code>allowIndexDefinitions</code> property of the properties.xml file.
     */
    @Parameter(
            property = "vault.allowIndexDefinitions",
            defaultValue="false",
            required = true)
    private boolean allowIndexDefinitions;

    /**
     * Controls if errors during dependency validation should fail the build.
     */
    @Parameter(
            property = "vault.failOnDependencyErrors",
            defaultValue="true",
            required = true)
    private boolean failOnDependencyErrors;

    /**
     * Controls if empty workspace filter fails the build.
     */
    @Parameter(
            property = "vault.failOnEmptyFilter",
            defaultValue="true",
            required = true)
    private boolean failOnEmptyFilter;


    /**
     * Defines the path under which the embedded bundles are placed. defaults to '/apps/bundles/install'
     * @since 0.0.6
     */
    @Parameter(property = "vault.embeddedTarget")
    private String embeddedTarget;

    /**
     * Defines the content package type. this is either 'application', 'content', 'container' or 'mixed'.
     * If omitted, it is calculated automatically based on filter definitions. certain package types imply restrictions,
     * for example, 'application' and 'content' packages are not allowed to contain sub packages or embedded bundles.
     *
     * @since 0.5.12
     */
    @Parameter(property = "vault.packageType")
    private PackageType packageType;

    /**
     * Sets the package type.
     * @param type the string representation of the package type
     * @throws MojoFailureException if an error occurrs
     */
    public void setPackageType(String type) throws MojoFailureException {
        try {
            packageType = PackageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("Invalid package type specified: " + type +".\n" +
                    "Must be empty or one of 'application', 'content', 'container', 'mixed'");
        }

    }

    /**
     * Defines additional bundle dependency via the osgi import-package entry in the manifest.
     * @since 0.5.12
     */
    @Parameter(property = "vault.importPackage")
    private String importPackage;

    /**
     * Defines the content of the filter.xml file
     */
    @Parameter
    private final DefaultWorkspaceFilter filters = new DefaultWorkspaceFilter();

    /**
     * Defines the list of dependencies
     */
    @Parameter
    private Dependency[] dependencies = new Dependency[0];

    /**
     * Defines the packages that define the repository structure
     */
    @Parameter
    private Dependency[] repositoryStructurePackages = new Dependency[0];

    /**
     * computed dependency string
     * @see #computeDependencies()
     */
    private String dependenciesString;

    /**
     * Defines the list of sub packages.
     */
    @Parameter
    private SubPackage[] subPackages = new SubPackage[0];

    /**
     * File to store the generated manifest snippet.
     */
    @Parameter(property = "vault.generatedImportPackage", defaultValue = "${project.build.directory}/vault-generated-import.txt")
    private File generatedImportPackage;

    /**
     * Specifies additional properties to be set in the properties.xml file.
     * These properties cannot overwrite the following predefined properties:
     * <p>
     * <table>
     * <tr><td>group</td><td>Use <i>group</i> parameter to set</td></tr>
     * <tr><td>name</td><td>Use <i>name</i> parameter to set</td></tr>
     * <tr><td>version</td><td>Use <i>version</i> parameter to set</td></tr>
     * <tr><td>groupId</td><td><i>groupId</i> of the Maven project descriptor</td></tr>
     * <tr><td>artifactId</td><td><i>artifactId</i> of the Maven project descriptor</td></tr>
     * <tr><td>dependencies</td><td>Use <i>dependencies</i> parameter to set</td></tr>
     * <tr><td>createdBy</td><td>The value of the <i>user.name</i> system property</td></tr>
     * <tr><td>created</td><td>The current system time</td></tr>
     * <tr><td>requiresRoot</td><td>Use <i>requiresRoot</i> parameter to set</td></tr>
     * <tr><td>allowIndexDefinitions</td><td>Use <i>allowIndexDefinitions</i> parameter to set</td></tr>
     * <tr><td>packagePath</td><td>Automatically generated from the group and package name</td></tr>
     * <tr><td>packageType</td><td>Set via the package type parameter</td></tr>
     * </table>
     */
    @Parameter
    private final Properties properties = new Properties();

    /**
     * Creates a fileset for the archiver
     * @param directory the directory
     * @param prefix the prefix
     * @return the fileset
     */
    @Nonnull
    private FileSet createFileSet(@Nonnull File directory, @Nonnull String prefix) {
        return fileSet(directory).prefixed(prefix).includeExclude(null, null).includeEmptyDirs(true);
    }

    /**
     * Executes this mojo
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (prefix == null) {
            prefix = "";
        } else if (!prefix.endsWith("/")) {
            prefix += "/";
        }

        final File vaultDir = new File(workDirectory, VAULT_DIR);
        final File finalFile = new File(outputDirectory, finalName + PACKAGE_EXT);

        try {
            vaultDir.mkdirs();

            Map<String, File> embeddedFiles = copyEmbeddeds();
            embeddedFiles.putAll(copySubPackages());

            /*
                - if last modified of vault-work/META-INF/vault/filter.xml == 0 -> delete it
                - if filterSource exists, read the filters into sourceFilters
                - if no filterSource exists, but filter.xml read the filters into sourceFilters
                - if pom filters exist, merge into sourceFilters
                - apply potential prefixes to sourceFilters
                - generate xml and write to filter.xml
                - (extra step: if sourceFilters and filters from original are the same, just copy back the original. this preserves potential comments in the xml)
                - update the last modified time of filter.xml to 0
             */

            File filterFile = new File(vaultDir, "filter.xml");

            if (filterSource == null || !filterSource.exists()) {
                // ignore filtersource
                DefaultWorkspaceFilter oldFilter;
                if (filterFile.exists()) {
                    oldFilter = new DefaultWorkspaceFilter();
                    oldFilter.load(filterFile);

                    // don't write filter if same
                    if (filters.getSourceAsString().equals(oldFilter.getSourceAsString())) {
                        filterFile = null;
                    } else {
                        if (filters.getFilterSets().isEmpty()) {
                            filters.load(filterFile);
                            filterFile = null;
                        } else {
                            filterFile = new File(vaultDir, "filter-plugin-generated.xml");
                        }
                    }
                } else {
                    if (filters.getFilterSets().isEmpty() && prefix.length() > 0) {
                        addWorkspaceFilter(prefix);
                    }
                }
            } else {
                getLog().info("Merging " + filterSource.getPath() + " with inlined filter specifications.");
                DefaultWorkspaceFilter oldFilter = new DefaultWorkspaceFilter();
                oldFilter.load(filterSource);
                oldFilter.merge(filters);
                filters.getFilterSets().clear();
                filters.getFilterSets().addAll(oldFilter.getFilterSets());
                if (filters.getFilterSets().isEmpty() && prefix.length() > 0) {
                    addWorkspaceFilter(prefix);
                }
            }

            if (filterFile != null) {
                FileUtils.fileWrite(filterFile.getAbsolutePath(), filters.getSourceAsString());
            }
            if (filters.getFilterSets().isEmpty() && failOnEmptyFilter) {
                final String msg = "No workspace filter defined (failOnEmptyFilter=true)";
                getLog().error(msg);
                throw new MojoExecutionException(msg);
            }

            validatePackageType();
            computeImportPackage();
            computeDependencies();
            if (packageType == PackageType.APPLICATION) {
                validateDependencies();
            } else {
                getLog().info("Ignoring dependency validation due to non-application package type: " + packageType);
            }

            final Properties vaultProperties = computeProperties();
            final FileOutputStream fos = new FileOutputStream(new File(vaultDir, "properties.xml"));
            vaultProperties.storeToXML(fos, project.getName());

            copyFile("/vault/config.xml", new File(vaultDir, "config.xml"));
            copyFile("/vault/settings.xml", new File(vaultDir, "settings.xml"));

            ContentPackageArchiver contentPackageArchiver = new ContentPackageArchiver();
            contentPackageArchiver.setIncludeEmptyDirs(true);
            contentPackageArchiver.addDirectory(workDirectory);

            // include content from build only if it exists
            if (builtContentDirectory.exists()) {
                // See GRANITE-16348
                // we want to build a list of all the root directories in the order they were specified in the filter
                // but ignore the roots that don't point to a directory
                List<PathFilterSet> filterSets = filters.getFilterSets();
                if (filterSets.isEmpty()) {
                    contentPackageArchiver.addFileSet(createFileSet(builtContentDirectory, FileUtils.normalize(JCR_ROOT + prefix)));
                } else {
                    for (PathFilterSet filterSet : filterSets) {
                        String relPath = PlatformNameFormat.getPlatformPath(filterSet.getRoot());
                        String rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath);

                        // CQ-4204625 skip embedded files, will be added later in the proper way
                        if (embeddedFiles.containsKey(rootPath)) {
                            continue;
                        }

                        // check for full coverage aggregate
                        File fullCoverage = new File(builtContentDirectory, relPath + ".xml");
                        if (fullCoverage.isFile()) {
                            rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath + ".xml");
                            contentPackageArchiver.addFile(fullCoverage, rootPath);
                            continue;
                        }

                        File rootDirectory = new File(builtContentDirectory, relPath);

                        // traverse the ancestors until we find a existing directory (see CQ-4204625)
                        while ((!rootDirectory.exists() || !rootDirectory.isDirectory())
                                && !builtContentDirectory.equals(rootDirectory)) {
                            rootDirectory = rootDirectory.getParentFile();
                            relPath = StringUtils.chomp(relPath, "/");
                        }

                        if (!builtContentDirectory.equals(rootDirectory)) {
                            rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath);
                            contentPackageArchiver.addFileSet(createFileSet(rootDirectory, rootPath + "/"));
                        }
                    }
                }
            }

            for (Map.Entry<String, File> entry : embeddedFiles.entrySet()) {
                contentPackageArchiver.addFile(entry.getValue(), entry.getKey());
            }

            //NPR-14102 - Automated check for index definition
            if (!allowIndexDefinitions) {
                FileValidator fileValidator = new FileValidator();
                getLog().info("Scanning files for oak index definitions.");
                for (ArchiveEntry entry: contentPackageArchiver.getFiles().values()) {
                    if (entry.getType() == ArchiveEntry.FILE) {
                        InputStream in = null;
                        try {
                            in = entry.getInputStream();
                            fileValidator.lookupIndexDefinitionInArtifact(in, entry.getName());
                        } finally {
                            IOUtils.closeQuietly(in);
                        }
                    }
                }
                if (fileValidator.isContainingIndexDef) {
                    getLog().error(fileValidator.getMessageWithPathsOfIndexDef());
                    throw new MojoExecutionException("Package should not contain index definitions, because 'allowIndexDefinitions=false'.");
                }
            }

            MavenArchiver mavenArchiver = new MavenArchiver();
            mavenArchiver.setArchiver(contentPackageArchiver);
            mavenArchiver.setOutputFile(finalFile);
            mavenArchiver.createArchive(null, project, getMavenArchiveConfiguration(vaultProperties));

            // set the file for the project's artifact and ensure the
            // artifact is correctly handled with the "zip" handler
            // (workaround for MNG-1682)
            final Artifact projectArtifact = project.getArtifact();
            projectArtifact.setFile(finalFile);
            projectArtifact.setArtifactHandler(artifactHandlerManager.getArtifactHandler(PACKAGE_TYPE));

        } catch (Exception e) {
            throw new MojoExecutionException(e.toString(), e);
        }
    }

    /**
     * Computes the dependency string.
     */
    private void computeDependencies() throws IOException {
        if (dependencies.length > 0) {
            dependenciesString = PackageDependency.toString(Dependency.resolve(project, getLog(), dependencies));
        }
        Dependency.resolve(project, getLog(), repositoryStructurePackages);
    }

    /**
     * Checks if the filter roots of this package are covered by the dependencies and also checks for colliding roots
     * in the dependencies.
     */
    private void validateDependencies() throws MojoExecutionException {
        List<String> errors = new DependencyValidator()
                .addDependencies(dependencies)
                .addDependencies(repositoryStructurePackages)
                .setFilters(filters)
                .validate()
                .getErrors();

        if (errors.size() > 0) {
            String msg = String.format("%d error(s) detected during dependency analysis.", errors.size());
            if (failOnDependencyErrors) {
                getLog().error(msg);
                for (String error: errors) {
                    getLog().error(error);
                }
                throw new MojoExecutionException(msg);
            }
            getLog().warn(msg);
            for (String error: errors) {
                getLog().warn(error);
            }
        } else {
            getLog().info("All dependencies satisfied.");
        }
    }

    /**
     * Computes the import-package definition from the given bundles if not provided by the project.
     */
    private void computeImportPackage() throws IOException {
        TreeMap<String, Attrs> importParams = new TreeMap<String, Attrs>();
        if (generatedImportPackage.exists()) {
            String importPackageStr = FileUtils.fileRead(generatedImportPackage);
            if (importPackageStr.length() > 0) {
                importParams.putAll(new Parameters(importPackageStr));
            }
        }

        // override computed patterns
        if (importPackage != null) {
            for (Map.Entry<String, Attrs> entry : new Parameters(importPackage).entrySet()) {
                boolean delete = false;
                String pkg = entry.getKey();
                if ("-*".equals(pkg)) {
                    importParams.clear();
                    continue;
                }
                if (pkg.charAt(0) == '-') {
                    pkg = pkg.substring(1);
                    delete = true;
                }
                if (pkg.endsWith("*")) {
                    String pkgDot = pkg.substring(0, pkg.length() - 1);
                    if (!pkgDot.endsWith(".")) {
                        // matches both, the packages and sub packages
                        pkg = pkgDot;
                        pkgDot = pkg + ".";
                    }
                    Iterator<Map.Entry<String, Attrs>> iter = importParams.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<String, Attrs> e = iter.next();
                        String pkgName = e.getKey();
                        if (pkgName.equals(pkg) || pkgName.startsWith(pkgDot)) {
                            if (delete) {
                                iter.remove();
                            } else {
                                e.setValue(entry.getValue());
                            }
                        }
                    }
                } else {
                    if (delete) {
                        importParams.remove(pkg);
                    } else {
                        importParams.put(pkg, entry.getValue());
                    }
                }
            }
        }

        importPackage = Processor.printClauses(importParams);
    }

    private MavenArchiveConfiguration getMavenArchiveConfiguration(Properties vaultProperties) throws IOException {
        if (archive == null) {
            archive = new MavenArchiveConfiguration();
            archive.setManifest(new ManifestConfiguration());

            archive.setAddMavenDescriptor(true);
            archive.setCompress(true);
            archive.setIndex(false);
            archive.getManifest().setAddDefaultSpecificationEntries(false);
            archive.getManifest().setAddDefaultImplementationEntries(true);

            PackageId id = new PackageId(group, name, version);
            archive.addManifestEntry(MF_KEY_PACKAGE_TYPE, packageType.name().toLowerCase());
            archive.addManifestEntry(MF_KEY_PACKAGE_ID, id.toString());
            archive.addManifestEntry(MF_KEY_PACKAGE_DESC, vaultProperties.getProperty("description", ""));
            if (dependenciesString != null && dependenciesString.length() > 0) {
                archive.addManifestEntry(MF_KEY_PACKAGE_DEPENDENCIES, dependenciesString);
            }
            // be sure to avoid duplicates
            Set<String> rts = new TreeSet<String>();
            for (PathFilterSet p: filters.getFilterSets()) {
                rts.add(p.getRoot());
            }
            String[] roots = rts.toArray(new String[rts.size()]);
            Arrays.sort(roots);
            archive.addManifestEntry(MF_KEY_PACKAGE_ROOTS, StringUtils.join(roots, ","));

            if (StringUtils.isNotEmpty(importPackage)) {
                archive.addManifestEntry(MF_KEY_IMPORT_PACKAGE, StringUtils.deleteWhitespace(importPackage));
            }
        }

        return archive;
    }

    private Properties computeProperties() {
        final Properties props = new Properties();

        // find the description of the content package (bug #30546)
        // this is allowed to be overwritten by the properties map (GRANITE-1527)
        String description = project.getDescription();
        if (description == null) {
            description = project.getName();
            if (description == null) {
                description = project.getArtifactId();
            }
        }
        props.put("description", description);

        // add all user defined properties
        // before the rest of the properties to prevent user
        // overwriting of predefined properties
        // (see JavaDoc of properties field for list)

        // but make sure, that we don't have null values in there
        for (Object o : properties.keySet()) {
            if (properties.get(o) == null) {
                properties.put(o, "");
            }
        }

        props.putAll(properties);

        // package descriptor properties
        props.put("group", group);
        props.put("name", name);
        props.put("version", version);

        // maven artifact identification
        props.put("groupId", project.getGroupId());
        props.put("artifactId", project.getArtifactId());

        // dependencies
        if (dependenciesString != null && dependenciesString.length() > 0) {
            props.put("dependencies", dependenciesString);
        }

        // creation stamp
        if (!props.containsKey("createdBy")) {
            props.put("createdBy", System.getProperty("user.name"));
        }
        props.put("created", DATE_FORMAT.format(new Date()));

        // configurable properties
        props.put("requiresRoot", String.valueOf(requiresRoot));
        props.put("allowIndexDefinitions", String.valueOf(allowIndexDefinitions));
        props.put("packageType", packageType.name().toLowerCase());
        return props;
    }

    private void copyFile(String source, File target) throws IOException {

        // nothing to do if the file exists
        if (target.exists()) {
            return;
        }

        target.getParentFile().mkdirs();

        InputStream ins = getClass().getResourceAsStream(source);
        if (ins != null) {
            OutputStream out = null;
            try {
                out = new FileOutputStream(target);
                IOUtil.copy(ins, out);
            } finally {
                IOUtil.close(ins);
                IOUtil.close(out);
            }
        }
    }

    private Map<String, File> copyEmbeddeds() throws IOException, MojoFailureException {
        Map<String, File> fileMap = new HashMap<String, File>();
        for (Embedded emb : embeddeds) {
            final List<Artifact> artifacts = emb.getMatchingArtifacts(project);
            if (artifacts.isEmpty()) {
                if (failOnMissingEmbed) {
                    throw new MojoFailureException("Embedded artifact specified " + emb + ", but no matching dependency artifact found. Add the missing dependency or fix the embed definition.");
                } else {
                    getLog().warn("No matching artifacts for " + emb);
                    continue;
                }
            }
            if (emb.getDestFileName() != null && artifacts.size() > 1) {
                getLog().warn("destFileName defined but several artifacts match for " + emb);
            }

            String targetPath = emb.getTarget();
            if (targetPath == null) {
                targetPath = embeddedTarget;
                if (targetPath == null) {
                    final String loc = (prefix.length() == 0)
                            ? "/apps/"
                            : prefix;
                    targetPath = loc + "bundles/install/";
                    getLog().info("No target path set on " + emb + "; assuming default " + targetPath);
                }
            }
            targetPath = makeAbsolutePath(targetPath);

            targetPath = JCR_ROOT + targetPath;
            targetPath = FileUtils.normalize(targetPath);

            getLog().info("Embedding --- " + emb + " ---");
            for (final Artifact artifact : artifacts) {
                final File source = artifact.getFile();
                String destFileName = emb.getDestFileName();

                // todo: add support for patterns
                if (destFileName == null) {
                    destFileName = source.getName();
                }
                final String targetPathName = targetPath + destFileName;
                final String targetNodePathName = targetPathName.substring(JCR_ROOT.length() - 1);

                fileMap.put(targetPathName, source);
                getLog().info(String.format("Embedding %s (from %s) -> %s", artifact.getId(), source.getAbsolutePath(), targetPathName));

                if (emb.isFilter()) {
                    addWorkspaceFilter(targetNodePathName);
                }
            }
        }
        return fileMap;
    }

    private Map<String, File> copySubPackages() throws IOException {
        Map<String, File> fileMap = new HashMap<String, File>();
        for (SubPackage pack : subPackages) {
            final List<Artifact> artifacts = pack.getMatchingArtifacts(project);
            if (artifacts.isEmpty()) {
                getLog().warn("No matching artifacts for " + pack);
                continue;
            }

            // get the package path
            getLog().info("Embedding --- " + pack + " ---");
            for (Artifact artifact : artifacts) {
                final File source = artifact.getFile();

                // load properties
                ZipFile zip = null;
                InputStream in = null;
                Properties props = new Properties();
                try {
                    zip = new ZipFile(source, ZipFile.OPEN_READ);
                    ZipEntry e = zip.getEntry("META-INF/vault/properties.xml");
                    if (e == null) {
                        getLog().error("Package does not contain properties.xml");
                        throw new IOException("properties.xml missing");
                    }
                    in = zip.getInputStream(e);
                    props.loadFromXML(in);
                } finally {
                    IOUtil.close(in);
                    if (zip != null) {
                        zip.close();
                    }
                }
                PackageId pid = new PackageId(
                        props.getProperty("group"),
                        props.getProperty("name"),
                        props.getProperty("version")
                );
                final String targetNodePathName = pid.getInstallationPath() + ".zip";
                final String targetPathName = "jcr_root" + targetNodePathName;

                fileMap.put(targetPathName, source);
                getLog().info("Embedding " + artifact.getId() + " -> " + targetPathName);
                if (pack.isFilter()) {
                    addWorkspaceFilter(targetNodePathName);
                }
            }
        }
        return fileMap;
    }

    private void addWorkspaceFilter(final String filterRoot) {
        filters.add(new PathFilterSet(filterRoot));
    }

    private String makeAbsolutePath(final String relPath) {
        final String absPath;
        if (!relPath.startsWith("/")) {
            absPath = ((prefix.length() == 0) ? "/" : prefix) + relPath;
            getLog().info("Relative path resolved to " + absPath);
        } else {
            absPath = relPath;
        }

        return absPath;
    }

    private void validatePackageType() throws MojoFailureException {
        if (packageType == null) {
            // auto detect...
            boolean hasApps = false;
            boolean hasOther = false;
            for (PathFilterSet p: filters.getFilterSets()) {
                if (p.isCleanUp()) {
                    continue;
                }
                String root = p.getRoot();
                if ("/apps".equals(root) || root.startsWith("/apps/") || "/libs".equals(root) || root.startsWith("/libs/")) {
                    hasApps = true;
                } else {
                    hasOther = true;
                }
            }
            if (hasApps && !hasOther) {
                packageType = PackageType.APPLICATION;
            } else if (hasOther && !hasApps) {
                packageType = PackageType.CONTENT;
            } else {
                packageType = PackageType.MIXED;
            }
        }
    }

}
