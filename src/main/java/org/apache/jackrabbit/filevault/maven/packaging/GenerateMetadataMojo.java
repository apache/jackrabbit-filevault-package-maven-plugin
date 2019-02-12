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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Processor;
import org.apache.jackrabbit.filevault.maven.packaging.impl.DependencyValidator;
import org.apache.jackrabbit.filevault.maven.packaging.impl.PackageDependency;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.maven.archiver.ManifestConfiguration;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Maven goal which generates the metadata ending up in the package like {@code META-INF/MANIFEST.MF} as well as the
 * files ending up in {@code META-INF/vault} like {@code filter.xml}, {@code properties.xml}, {@code config.xml} and
 * {@code settings.xml}. Those files will be written to the directory given via parameter {@link #workDirectory}.
 * In addition performs some validations.
 */
@Mojo(
        name = "generate-metadata",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class GenerateMetadataMojo extends AbstractPackageMojo {

    /**
     *  A date format which is compliant with {@code org.apache.jackrabbit.util.ISO8601.parse(...)}
     *  @see <a href="https://www.w3.org/TR/NOTE-datetime">Restricted profile for ISO8601</a>
     *  @see <a href="https://issues.apache.org/jira/browse/JCR-4267">JCR-4267</a>
     */
    private final DateFormat iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public static final String MF_KEY_PACKAGE_TYPE = "Content-Package-Type";

    public static final String MF_KEY_PACKAGE_ID = "Content-Package-Id";

    public static final String MF_KEY_PACKAGE_DEPENDENCIES = "Content-Package-Dependencies";

    public static final String MF_KEY_PACKAGE_ROOTS = "Content-Package-Roots";

    public static final String MF_KEY_PACKAGE_DESC = "Content-Package-Description";

    public static final String MF_KEY_IMPORT_PACKAGE = "Import-Package";

    /**
     * For m2e incremental build support
     */
    @Component
    private BuildContext buildContext;

    /**
     * For correct source of standard embedded path base name.
     */
    @Component(hint = "default")
    private ArtifactRepositoryLayout embedArtifactLayout;

    /**
     * The Maven session.
     */
    @Parameter(property = "session", readonly = true, required = true)
    private MavenSession session;

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
     * Defines the content of the filter.xml file
     */
    @Parameter
    private final Filters filters = new Filters();

    /**
     * Optional file that specifies the source of the workspace filter. The filters specified in the configuration
     * and injected via emebedds or subpackages are merged into it.
     */
    @Parameter
    private File filterSource;

    /**
     * Controls if empty workspace filter fails the build.
     */
    @Parameter(
            property = "vault.failOnEmptyFilter",
            defaultValue="true",
            required = true)
    private boolean failOnEmptyFilter;

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
     * <tr><td>acHandling</td><td>Use <i>accessControlHandling</i> parameter to set</td></tr>
     * </table>
     */
    @Parameter
    private final Properties properties = new Properties();

    /**
     * Defines the list of dependencies
     * A dependency is declared as a {@code <dependency>} element of a list
     * style {@code <dependencies>} element:
     * <pre>
     * &lt;dependency&gt;
     *     &lt;group&gt;theGroup&lt;/group&gt;
     *     &lt;name&gt;theName&lt;/name&gt;
     *     &lt;version&gt;1.5&lt;/version&gt;
     * &lt;/dependency&gt;
     * </pre>
     * <p>
     * The dependency can also reference a maven project dependency, this is preferred
     * as it yields to more robust builds.
     * <pre>
     * &lt;dependency&gt;
     *     &lt;groupId&gt;theGroup&lt;/groupId&gt;
     *     &lt;artifactId&gt;theName&lt;/artifactId&gt;
     * &lt;/dependency&gt;
     * </pre>
     * <p>
     * The {@code versionRange} may be indicated as a single version, in which
     * case the version range has no upper bound and defines the minimal version
     * accepted. Otherwise, the version range defines a lower and upper bound of
     * accepted versions, where the bounds are either included using parentheses
     * {@code ()} or excluded using brackets {@code []}
     */
    @Parameter
    private Dependency[] dependencies = new Dependency[0];

    /**
     * Controls if errors during dependency validation should fail the build.
     */
    @Parameter(
            property = "vault.failOnDependencyErrors",
            defaultValue="true",
            required = true)
    private boolean failOnDependencyErrors;

    /**
     * Defines the Access control handling. This will become the
     * {@code acHandling} property of the properties.xml file.<br/>
     * Possible values:
     * <ul>
     * <li>{@code ignore}: Ignores the packaged access control and leaves the target unchanged.</li>
     * <li>{@code overwrite}: Applies the access control provided with the package to the target. this also removes
     * existing access control.</li>
     * <li>{@code merge}: Merge access control provided with the package with the one in the content by replacing the
     * access control entries of corresponding principals (i.e. package first). It never alters access control entries of
     * principals not present in the package.</li>
     * <li>{@code merge_preserve}: Merge access control in the content with the one provided with the package by
     * adding the access control entries of principals not present in the content (i.e. content first). It never alters
     * access control entries already existing in the content.</li>
     * <li>{@code clear}: Clears all access control on the target system.</li>
     * </ul>
     */
    @Parameter(
            property = "vault.acHandling",
            alias = "acHandling",
            required = false)
    private AccessControlHandling accessControlHandling;

    /**
     * Defines whether the package requires root. This will become the
     * {@code requiresRoot} property of the properties.xml file.
     */
    @Parameter(
            property = "vault.requiresRoot",
            defaultValue="false",
            required = true)
    private boolean requiresRoot;
    
    /**
     * Defines additional bundle dependency via the osgi import-package entry in the manifest.
     */
    @Parameter(
            property = "vault.importPackage",
            defaultValue =
                    // exclude HTL compiler packages as they are never real dependencies of the content
                    "-org.apache.sling.scripting.sightly.compiler.expression.nodes," +
                    "-org.apache.sling.scripting.sightly.java.compiler," +
                    "-org.apache.sling.scripting.sightly.render"
    )
    private String importPackage;

    /**
     * Defines the path under which the embedded bundles are placed. defaults to '/apps/bundles/install'
     */
    @Parameter(property = "vault.embeddedTarget")
    private String embeddedTarget;

    /**
     * List of filters for artifacts to embed in the package.
     * The {@code Embedded} class represents one or multiple embedded artifact dependencies
     * from the project descriptor.
     * Each {@code <embedded>} element may configure any of the following fields
     *  <p>
     * <table>
     * <tr><td>groupId</td><td>String</td><td>Filter criterion against the group id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>artifactId</td><td>String</td><td>Filter criterion against the artifact id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>scope</td><td>ScopeArtifactFilter</td><td>Filter criterion against the <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">scope of a project dependency</a>. Possible values are <ul><li>{@code test}, which allows every scope</li><li>{@code compile+runtime} which allows every scope except {@code test}</li><li>{@code runtime+system} which allows every scope except {@code test} and {@code provided}</li><li>{@code compile} which allows only scope {@code compile}, {@code provided} and {@code system}</li><li>{@code runtime} which only allows scope {@code runtime} and {@code compile}.</td></tr>
     * <tr><td>type</td><td>String</td><td>Filter criterion against the type of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>classifier</td><td>String</td><td>Filter criterion against the classifier of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>filter</td><td>Boolean</td><td>If set to {@code true} adds the embedded artifact location to the package's filter.</td></tr>
     * <tr><td>target</td><td>String</td><td>The parent folder location in the package where to place the embedded artifact. Falls back to {@link #embeddedTarget} if not set.</td></tr>
     * </table>
     * </pre>
     * All fields are optional. All filter criteria is concatenated with AND logic (i.e. every criterion must match for a specific dependency to be embedded).
     * <br>
     * All filter patterns follow the format {@code &lt;filter&gt;{,&lt;filter&gt;}}.
     * Each {@code filter} is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, 
     * otherwise as blacklist. The last matching filter determines the outcome. Only matching dependencies are being considered for being embedded.</td></tr>
     * <br>
     * <i>The difference between {@link #embeddeds} and {@link #subPackages} is that for the former an explicit target is given while for the latter the target is being computed from the artifact's vault property file.</i>
     */
    @Parameter
    private Embedded[] embeddeds = new Embedded[0];

    /**
     * Defines whether to fail the build when an embedded artifact is not
     * found in the project's dependencies
     */
    @Parameter(property = "vault.failOnMissingEmbed", defaultValue = "false", required = true)
    private boolean failOnMissingEmbed;

    /**
     * Defines the list of sub packages to be embedded in this package.
     * The {@code SubPackage} class represents one or multiple subpackage artifact dependencies
     * from the project descriptor. Each {@code <subPackage>} element may configure any of the following fields
     *  <p>
     * <table>
     * <tr><td>groupId</td><td>String</td><td>Filter criterion against the group id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>artifactId</td><td>String</td><td>Filter criterion against the artifact ids of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>scope</td><td>ScopeArtifactFilter</td><td>Filter criterion against the <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">scope of a project dependency</a>. Possible values are <ul><li>{@code test}, which allows every scope</li><li>{@code compile+runtime} which allows every scope except {@code test}</li><li>{@code runtime+system} which allows every scope except {@code test} and {@code provided}</li><li>{@code compile} which allows only scope {@code compile}, {@code provided} and {@code system}</li><li>{@code runtime} which only allows scope {@code runtime} and {@code compile}.</td></tr>
     * <tr><td>type</td><td>String</td><td>Filter criterion against the type of a project dependency.A pattern as described below.</td></tr>
     * <tr><td>classifier</td><td>String</td><td>Filter criterion against the classifier of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>filter</td><td>Boolean</td><td>If set to {@code true} adds the embedded artifact location to the package's filter</td></tr>
     * </table>
     * </pre>
     * All fields are optional. All filter criteria is concatenated with AND logic (i.e. every criterion must match for a specific dependency to be embedded as a sub package).
     * <br>
     * All filter patterns follow the format {@code &lt;filter&gt;{,&lt;filter&gt;}}.
     * Each {@code filter} within a filter pattern is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, 
     * otherwise as blacklist. The last matching filter determines the outcome. Only matching dependencies are being considered for being embedded.
     * <br>
     * <i>The difference between {@link #embeddeds} and {@link #subPackages} is that for the former an explicit target is given while for the latter the target is being computed from the artifact's vault property file.</i>
     */
    @Parameter
    private SubPackage[] subPackages = new SubPackage[0];

    /**
     * Defines the packages that define the repository structure.
     * For the format description look at {@link #dependencies}.
     * <p>
     * The repository-init feature of sling-start can define initial content that will be available in the
     * repository before the first package is installed. Packages that depend on those nodes have no way to reference
     * any dependency package that provides these nodes. A "real" package that would creates those nodes cannot be
     * installed in the repository, because it would void the repository init structure. On the other hand would filevault
     * complain, if the package was listed as dependency but not installed in the repository. So therefor this
     * repository-structure packages serve as indicator packages that helps satisfy the structural dependencies, but are
     * not added as real dependencies to the package.
     */
    @Parameter
    private Dependency[] repositoryStructurePackages = new Dependency[0];

    /**
     * File to store the generated manifest snippet.
     */
    @Parameter(property = "vault.generatedImportPackage", defaultValue = "${project.build.directory}/vault-generated-import.txt")
    private File generatedImportPackage;

    /**
     * The archive configuration to use. See <a
     * href="http://maven.apache.org/shared/maven-archiver/index.html">the
     * documentation for Maven Archiver</a>.
     * 
     * All settings related to manifest are not relevant as this gets overwritten by the manifest in {@link AbstractPackageMojo#workDirectory}
     */
    @Parameter
    private MavenArchiveConfiguration archive;

    /**
     * Optional reference to PNG image that should be used as thumbnail for the content package.
     */
    @Parameter
    private File thumbnailImage;

    /**
     * Sets the access control handling.
     * @param type the string representation of the ac handling
     * @throws MojoFailureException if an error occurs
     */
    public void setAccessControlHandling(String type) throws MojoFailureException {
        try {
            accessControlHandling = AccessControlHandling.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("Invalid accessControlHandling specified: " + type +".\n" +
                    "Must be empty or one of '" + StringUtils.join(AccessControlHandling.values(), "','") + "'.");
        }

    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (buildContext.isIncremental()) {
            getLog().debug("Incremental build");
            // only execute in case of changes towards the filter.xml as the generated one contains a merge
            if (filterSource != null) {
                if (buildContext.hasDelta(filterSource)) {
                    getLog().debug("Detecting a change on '" + filterSource + "' therefore not cancelling build");
                } else {
                    getLog().debug("'" + filterSource + "' unchanged therefore cancelling build");
                    return;
                }
            } else {
                getLog().debug("No file change would be relevant therefore cancelling build");
                return;
            }
        }

        final File vaultDir = getVaultDir();
        vaultDir.mkdirs();

        // JCRVLT-331 share work directory to expose vault metadata between process-classes and package phases for
        // multi-module builds.
        getArtifactWorkDirectoryLookup(getPluginContext())
                .put(getModuleArtifactKey(project.getArtifact()), workDirectory);

        try {
            // calculate the embeddeds and subpackages
            Map<String, File> embeddedFiles = getEmbeddeds();
            embeddedFiles.putAll(getSubPackages());
            setEmbeddedFilesMap(embeddedFiles);

            // find the meta-inf source directory
            File metaInfDirectory = getMetaInfDir();
            
            // generate the filter.xml
            computePackageFilters(metaInfDirectory);
            computeImportPackage();
            String dependenciesString = computeDependencies();
            
            // some validations
            validatePackageType();
            if (packageType == PackageType.APPLICATION) {
                validateDependencies();
            } else {
                getLog().info("Ignoring dependency validation due to non-application package type: " + packageType);
            }

            // generate properties.xml
            final Properties vaultProperties = computeProperties(dependenciesString);
            final FileOutputStream fos = new FileOutputStream(new File(vaultDir, "properties.xml"));
            vaultProperties.storeToXML(fos, project.getName());

            copyFile("/vault/config.xml", new File(vaultDir, "config.xml"));
            copyFile("/vault/settings.xml", new File(vaultDir, "settings.xml"));
            
            // add package thumbnail
            if (thumbnailImage != null && thumbnailImage.exists()) {
                File vaultDefinitionFolder = new File(vaultDir, "definition");
                if (!vaultDefinitionFolder.exists()) {
                    vaultDefinitionFolder.mkdir();
                }
                copyFile("/vault/definition/.content.xml", new File(vaultDefinitionFolder, ".content.xml"));
                FileUtils.copyFile(thumbnailImage, new File(vaultDefinitionFolder, "thumbnail.png"));
            }

            // generate manifest file
            MavenArchiver mavenArchiver = new MavenArchiver();
            Manifest manifest = mavenArchiver.getManifest(session, project, getMavenArchiveConfiguration(vaultProperties, dependenciesString));
            try (OutputStream out = new FileOutputStream(getManifestFile())) {
                manifest.write(out);
            }
        } catch (IOException | ManifestException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(e.toString(), e);
        }
    }
    
    /**
     * Computes the package filters.
     *
     * Requirements:
     * - backward compatibility: if a filter.xml is copied to vault-work with the resource plugin, then it should still "work" correctly.
     * - if there are any comments in the original filter source, they should not get lost, if possible
     * - if there are filters specified in the pom and in a filter source, they should get merged.
     * - if the prefix property is set, it should be used if no filter is set.
     * - if both, a inline filter and a implicit filter is present, the build fails.
     * - re-run the package goal w/o cleaning the target first must work
     *
     * @throws IOException if an I/O error occurs
     * @throws MojoExecutionException if the build fails
     */
    private void computePackageFilters(File vaultMetaDir) throws IOException, MojoExecutionException {
        // backward compatibility: if implicit filter exists, use it. but check for conflicts
        File filterFile = getFilterFile();
        if (filterFile.exists() && filterFile.lastModified() != 0) {
            // if both, a inline filter and a implicit filter is present, the build fails.
            if (!filters.getFilterSets().isEmpty()) {
                getLog().error("Refuse to merge inline filters and non-sourced filter.xml. If this is intended, specify the filter.xml via the 'filterSource' property.");
                throw new MojoExecutionException("conflicting filters.");
            }
            // load filters for further processing
            try {
                filters.load(filterFile);
            } catch (ConfigurationException e) {
                throw new IOException(e);
            }

            getLog().warn("The project is using a filter.xml provided via the resource plugin.");
            getLog().warn("This is deprecated and might no longer be supported in future versions.");
            getLog().warn("Use the 'filterSource' property to specify the filter or use inline filters.");
            return;
        }

        // if last modified of vault-work/META-INF/vault/filter.xml == 0 -> delete it
        if (filterFile.exists() && filterFile.lastModified() == 0) {
            try {
                Files.delete(filterFile.toPath());
            } catch (IOException e) {
                getLog().error("Unable to delete previously generated filter.xml. re-run the goals with a clean setup.");
                throw new MojoExecutionException("Unable to delete file.", e);
            }
        }

        // check for filters file in vaultDir
        if (vaultMetaDir != null) {
            File metaFilterFile = new File(vaultMetaDir, "filter.xml");
            if (metaFilterFile.exists()) {
                if (filterSource != null && !filterSource.equals(metaFilterFile)) {
                    getLog().error("Project contains filter.xml in META-INF/vault but also specifies a filter source.");
                    throw new MojoExecutionException("conflicting filters.");
                }
                filterSource = metaFilterFile;
            }
        }

        // if filterSource exists, read the filters into sourceFilters
        DefaultWorkspaceFilter sourceFilters = new DefaultWorkspaceFilter();
        if (filterSource != null && filterSource.exists()) {
            getLog().info("Loading filter from " + filterSource.getPath());
            try {
                sourceFilters.load(filterSource);
            } catch (ConfigurationException e) {
                throw new IOException(e);
            }
            if (!filters.getFilterSets().isEmpty()) {
                getLog().info("Merging inline filters.");
                mergeFilters(sourceFilters, filters);
            }

            // now copy everything from sourceFilter to filters (as the latter is supposed to contain the final filter rules)!
            sourceFilters.resetSource();
            // there is no suitable clone nor constructor, therefore use a serialization/deserialization approach
            try (InputStream serializedFilters = sourceFilters.getSource()) {
                filters.load(serializedFilters);
            } catch (ConfigurationException e) {
                throw new IllegalStateException("cloning filters failed.", e);
            }

            // reset source filters for later. this looks a bit complicated but is needed to keep the same
            // filter order as in previous versions
            sourceFilters = new DefaultWorkspaceFilter();
            try {
                sourceFilters.load(filterSource);
            } catch (ConfigurationException e) {
                throw new IOException(e);
            }
            sourceFilters.resetSource();
        }

        // if the prefix property is set, it should be used if no filter is set
        if (filters.getFilterSets().isEmpty() && prefix.length() > 0) {
            addWorkspaceFilter(prefix);
        }

        // if no filter is defined at all, fail
        if (filters.getFilterSets().isEmpty()) {
            if (failOnEmptyFilter) {
                final String msg = "No workspace filter defined (failOnEmptyFilter=true)";
                getLog().error(msg);
                throw new MojoExecutionException(msg);
            } else {
                getLog().warn("No workspace filter defined. Package import might have unexpected results.");
            }
        }

        // if the source filters and the generated filters are the same, copy the source file to retain the comments
        if (filterSource != null && sourceFilters.getSourceAsString().equals(filters.getSourceAsString())) {
            FileUtils.copyFile(filterSource, filterFile);
        } else {
            // generate xml and write to filter.xml
            getLog().info("Generating filter.xml from plugin configuration");
            FileUtils.fileWrite(filterFile.getAbsolutePath(), filters.getSourceAsString());
        }

        // update the last modified time of filter.xml to for generated filters
        if (!filterFile.setLastModified(0)) {
            getLog().warn("Unable to set last modified of filters file. make sure to clean the project before next run.");
        }
    }

    private void mergeFilters(DefaultWorkspaceFilter dst, DefaultWorkspaceFilter src) {
        for (PathFilterSet fs: src.getFilterSets()) {
            // check for collision
            for (PathFilterSet mfs: dst.getFilterSets()) {
                if (mfs.getRoot().equals(fs.getRoot())) {
                    throw new IllegalArgumentException("Merging of equal filter roots not allowed for: " + fs.getRoot());
                }
            }
            dst.add(fs);
        }
    }


    /**
     * Checks if the filter roots of this package are covered by the dependencies and also checks for colliding roots
     * in the dependencies.
     */
    private void validateDependencies() throws MojoExecutionException {
        List<String> errors = new DependencyValidator()
                .addDependencies(dependencies)
                .addRepositoryStructure(repositoryStructurePackages)
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
        TreeMap<String, Attrs> importParams = new TreeMap<>();
        if (generatedImportPackage.exists()) {
            String importPackageStr = FileUtils.fileRead(generatedImportPackage);
            if (importPackageStr.length() > 0) {
                importParams.putAll(new Parameters(importPackageStr));
            }
        }

        // override computed patterns
        if (importPackage != null) {
            getLog().debug("merging analyzer-packages with:\n" + importPackage + "\n");
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

        if (!importPackage.isEmpty()) {
            getLog().info("Merged detected packages from analyzer with 'importPackage':");
            for (Map.Entry<String, Attrs> e: importParams.entrySet()) {
                StringBuilder report = new StringBuilder();
                report.append("  ").append(e.getKey());
                try {
                    Processor.printClause(e.getValue(), report);
                } catch (IOException e1) {
                    throw new IllegalStateException("Internal error while generating report", e1);
                }
                getLog().info(report);
            }
            getLog().info("");
        }
    }

    /**
     * Computes the dependency string.
     * @return the dependency string
     */
    private String computeDependencies() throws IOException {
        String dependenciesString = null;
        if (dependencies.length > 0) {
            dependenciesString = PackageDependency.toString(Dependency.resolve(project, getLog(), dependencies));
        }
        // this is mainly checking that the dependencies given by the repositoryStructurePackages are valid
        Dependency.resolve(project, getLog(), repositoryStructurePackages);
        return dependenciesString;
    }

    private MavenArchiveConfiguration getMavenArchiveConfiguration(Properties vaultProperties, String dependenciesString) throws IOException {
        if (archive == null) {
            archive = new MavenArchiveConfiguration();
            archive.setManifest(new ManifestConfiguration());

            archive.setAddMavenDescriptor(true);
            archive.setCompress(true);
            archive.setIndex(false);
            
            archive.getManifest().setAddDefaultSpecificationEntries(false);
            archive.getManifest().setAddDefaultImplementationEntries(true);

            // TODO: split up manifest generation
            PackageId id = new PackageId(group, name, version);
            archive.addManifestEntry(MF_KEY_PACKAGE_TYPE, packageType.name().toLowerCase());
            archive.addManifestEntry(MF_KEY_PACKAGE_ID, id.toString());
            archive.addManifestEntry(MF_KEY_PACKAGE_DESC, vaultProperties.getProperty("description", ""));
            if (dependenciesString != null && dependenciesString.length() > 0) {
                archive.addManifestEntry(MF_KEY_PACKAGE_DEPENDENCIES, dependenciesString);
            }
            // be sure to avoid duplicates
            Set<String> rts = new TreeSet<>();
            for (PathFilterSet p: filters.getFilterSets()) {
                rts.add(p.getRoot());
            }
            String[] roots = rts.toArray(new String[rts.size()]);
            Arrays.sort(roots);
            archive.addManifestEntry(MF_KEY_PACKAGE_ROOTS, StringUtils.join(roots, ","));

            // import package is not yet there!
            if (StringUtils.isNotEmpty(importPackage)) {
                archive.addManifestEntry(MF_KEY_IMPORT_PACKAGE, StringUtils.deleteWhitespace(importPackage));
            }
        }

        return archive;
    }

    private Properties computeProperties(String dependenciesString) {
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
        props.put("created", iso8601DateFormat.format(new Date()));

        // configurable properties
        props.put("requiresRoot", String.valueOf(requiresRoot));
        props.put("allowIndexDefinitions", String.valueOf(allowIndexDefinitions));
        props.put("packageType", packageType.name().toLowerCase());
        if (accessControlHandling != null) {
            props.put("acHandling", accessControlHandling.name().toLowerCase());
        }
        return props;
    }
    

    private Map<String, File> getEmbeddeds() throws MojoFailureException {
        Map<String, File> fileMap = new HashMap<>();
        for (Embedded emb : embeddeds) {
            final Collection<Artifact> artifacts = emb.getMatchingArtifacts(project);
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
            if (!targetPath.endsWith("/")) {
                targetPath += "/";
            }

            getLog().info("Embedding --- " + emb + " ---");
            for (final Artifact artifact : artifacts) {
                final File source = artifact.getFile();
                String destFileName = emb.getDestFileName();

                // todo: add support for patterns
                if (destFileName == null) {
                    destFileName = Text.getName(embedArtifactLayout.pathOf(artifact));
                }
                final String targetPathName = targetPath + destFileName;
                final String targetNodePathName = targetPathName.substring(JCR_ROOT.length() - 1);

                getLog().info(String.format("Embedding %s (from %s) -> %s", artifact.getId(), source.getAbsolutePath(), targetPathName));
                fileMap.put(targetPathName, source);

                if (emb.isFilter()) {
                    addWorkspaceFilter(targetNodePathName);
                }
            }
        }
        return fileMap;
    }

    /**
     * Establishes a session-shareable workDirectory lookup map for the given pluginContext.
     *
     * @param pluginContext a Map retrieved from {@link MavenSession#getPluginContext(PluginDescriptor, MavenProject)}.
     * @return a lookup Map. The key is {@link Artifact#getId()} and value is {@link AbstractPackageMojo#workDirectory}.
     */
    @SuppressWarnings("unchecked")
    static Map<String, File> getArtifactWorkDirectoryLookup(final Map pluginContext) {
        final String workDirectoryLookupKey = "workDirectoryLookup";
        if (!pluginContext.containsKey(workDirectoryLookupKey)) {
            pluginContext.put(workDirectoryLookupKey, new ConcurrentHashMap<String, File>());
        }
        return (Map<String, File>) pluginContext.get(workDirectoryLookupKey);
    }

    /**
     * Find the other project which produces the provided artifact.
     *
     * @param artifact the dependency artifact needle
     * @return another project that is a dependency of thisProject
     */
    MavenProject findModuleForArtifact(final Artifact artifact) {
        for (MavenProject otherProject : session.getProjects()) {
            if (otherProject != this.project) {
                final Artifact otherArtifact = otherProject.getArtifact();
                if (getModuleArtifactKey(artifact).equals(getModuleArtifactKey(otherArtifact))) {
                    return otherProject;
                }
            }
        }
        return null;
    }

    /**
     * Construct a handler-independent artifact disambiguation key. This helps with the issue
     * of matching dependency artifacts, which cannot reliably reference their original artifact handler to match the
     * correct packaging type, to multimodule artifacts, which include the packaging type in their getId() result.
     *
     * @param artifact the module artifact ({@link MavenProject#getArtifact()}) to identify
     * @return a handler-independent artifact disambiguation key
     */
    String getModuleArtifactKey(final Artifact artifact) {
        return this.embedArtifactLayout.pathOf(artifact);
    }

    private Map<String, File> getSubPackages() throws MojoFailureException {
        final String propsRelPath = "META-INF/vault/properties.xml";
        Map<String, File> fileMap = new HashMap<>();
        for (SubPackage pack : subPackages) {
            final Collection<Artifact> artifacts = pack.getMatchingArtifacts(project);
            if (artifacts.isEmpty()) {
                getLog().warn("No matching artifacts for sub package " + pack);
                continue;
            }

            // get the package path
            getLog().info("Embedding --- " + pack + " ---");
            for (Artifact artifact : artifacts) {
                final Properties props = new Properties();

                final File source = artifact.getFile();
                if (source.isDirectory()) {
                    File otherWorkDirectory = null;
                    final MavenProject otherProject = findModuleForArtifact(artifact);
                    if (otherProject != null) {
                        final PluginDescriptor pluginDescriptor = (PluginDescriptor) this.getPluginContext().get("pluginDescriptor");
                        if (pluginDescriptor != null) {
                            Map<String, Object> otherContext = this.session.getPluginContext(pluginDescriptor, otherProject);
                            otherWorkDirectory = getArtifactWorkDirectoryLookup(otherContext).get(getModuleArtifactKey(artifact));
                        }
                    }

                    // if not identifiable as a filevault content-package dependency, assume a generic archive layout.
                    if (otherWorkDirectory == null) {
                        otherWorkDirectory = source;
                    }

                    final File propsXml = new File(otherWorkDirectory, propsRelPath);
                    try (InputStream input = new FileInputStream(propsXml)) {
                        props.loadFromXML(input);
                    } catch (IOException e) {
                        throw new MojoFailureException("Could not read META-INF/vault/properties.xml from directory '" +
                                otherWorkDirectory + "' to extract metadata: " + e.getMessage(), e);
                    }
                } else {
                    // load properties
                    InputStream in = null;
                    try (ZipFile zip = new ZipFile(source, ZipFile.OPEN_READ)) {
                        ZipEntry e = zip.getEntry(propsRelPath);
                        if (e == null) {
                            throw new IOException("Package does not contain 'META-INF/vault/properties.xml'");
                        }
                        in = zip.getInputStream(e);
                        props.loadFromXML(in);
                    } catch (IOException e) {
                        throw new MojoFailureException("Could not open subpackage '" + source + "' to extract metadata: " + e.getMessage(), e);
                    } finally {
                        IOUtil.close(in);
                    }
                }
                PackageId pid = new PackageId(
                        props.getProperty("group"),
                        props.getProperty("name"),
                        props.getProperty("version")
                );
                final String targetNodePathName = pid.getInstallationPath() + ".zip";
                final String targetPathName = "jcr_root" + targetNodePathName;

                getLog().info(String.format("Embedding %s (from %s) -> %s", artifact.getId(), source.getAbsolutePath(), targetPathName));
                fileMap.put(targetPathName, source);
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
                if ("cleanup".equals(p.getType())) {
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
}
