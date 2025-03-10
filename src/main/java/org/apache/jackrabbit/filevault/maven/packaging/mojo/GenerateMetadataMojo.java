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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FilenameUtils;
import org.apache.jackrabbit.filevault.maven.packaging.ArtifactCoordinates;
import org.apache.jackrabbit.filevault.maven.packaging.Embedded;
import org.apache.jackrabbit.filevault.maven.packaging.Filters;
import org.apache.jackrabbit.filevault.maven.packaging.MavenBasedPackageDependency;
import org.apache.jackrabbit.filevault.maven.packaging.SubPackage;
import org.apache.jackrabbit.filevault.maven.packaging.SubPackageHandlingEntry;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.xml.serialize.FormattingXmlStreamWriter;
import org.apache.maven.archiver.ManifestConfiguration;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.sonatype.plexus.build.incremental.BuildContext;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Processor;

/**
 * Generates the metadata ending up in the content package like {@code META-INF/MANIFEST.MF} as well as the
 * files ending up in {@code META-INF/vault} like {@code filter.xml}, {@code properties.xml}, {@code config.xml} and
 * {@code settings.xml}. Those files will be written to the directory given via parameter {@link #workDirectory}.
 * In addition performs some validations.
 * Also configures artifacts (like OSGi bundles or subpackages) to be embedded in the content package as those may affect metadata as well.
 * The generated metadata is usually packaged in a content package in a subsequent goal {@code package}.
 * <p>
 * <i>This goal is executed/bound by default for Maven modules of type {@code content-package}.</i>
 * @since 1.0.3
 */
@Mojo(
        name = "generate-metadata",
        defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES, // to make sure it runs after "analyze-classes"
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class GenerateMetadataMojo extends AbstractMetadataPackageMojo {

    /**
     *  A date format which is compliant with {@code org.apache.jackrabbit.util.ISO8601.parse(...)}
     *  @see <a href="https://www.w3.org/TR/NOTE-datetime">Restricted profile for ISO8601</a>
     *  @see <a href="https://issues.apache.org/jira/browse/JCR-4267">JCR-4267</a>
     */
    private final DateFormat iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

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
     String group;

    /**
     * The name of the content package
     */
    @Parameter(
            property = "vault.name",
            defaultValue="${project.artifactId}",
            required = true)
    String name;

    /**
     * The version of the content package.
     */
    @Parameter(
            property = "vault.version",
            defaultValue = "${project.version}",
            required = true)
    String version;

    /**
     * Defines the content of the filter.xml file.
     * Each filter consists of the mandatory element {@code root} and the optional {@code mode} and {@code type} elements. All those elements are simple strings.
     * In addition optionally a number of {@code include} and {@code exclude} elements are supported below {@code includes}/{@code excludes} respectively.
     */
    @Parameter
    Filters filters = new Filters();

    /**
     * Optional file that specifies the source of the workspace filter. The filters specified in the configuration
     * and injected via emebedds or subpackages are merged into it.
     */
    @Parameter
    private File filterSource;

    /**
     * Controls if empty workspace filter fails the build.
     * @deprecated This is no longer evaluated as every package is supposed to come with a non-empty filter
     */
    @Deprecated
    @Parameter(
            property = "vault.failOnEmptyFilter",
            defaultValue="true",
            required = true)
    private boolean failOnEmptyFilter;

    /**
     * Specifies additional <a href="https://jackrabbit.apache.org/filevault/properties.html">package properties</a> to be set in the properties.xml file.
     * These properties cannot overwrite the following predefined properties:
     * <p>
     * <table>
     * <tr><td>{@code group}</td><td>Use <i>group</i> parameter to set</td></tr>
     * <tr><td>{@code name}</td><td>Use <i>name</i> parameter to set</td></tr>
     * <tr><td>{@code version}</td><td>Use <i>version</i> parameter to set</td></tr>
     * <tr><td>{@code groupId}</td><td><i>groupId</i> of the Maven project descriptor</td></tr>
     * <tr><td>{@code artifactId}</td><td><i>artifactId</i> of the Maven project descriptor</td></tr>
     * <tr><td>{@code dependencies}</td><td>Use <i>dependencies</i> parameter to set</td></tr>
     * <tr><td>{@code createdBy}</td><td>The value of the <i>user.name</i> system property</td></tr>
     * <tr><td>{@code created}</td><td>The current system time</td></tr>
     * <tr><td>{@code requiresRoot}</td><td>Use <i>requiresRoot</i> parameter to set</td></tr>
     * <tr><td>{@code allowIndexDefinitions}</td><td>Use <i>allowIndexDefinitions</i> parameter to set</td></tr>
     * <tr><td>{@code packagePath}</td><td>Automatically generated from the group and package name</td></tr>
     * <tr><td>{@code packageType}</td><td>Set via the package type parameter</td></tr>
     * <tr><td>{@code acHandling}</td><td>Use <i>accessControlHandling</i> parameter to set</td></tr>
     * <tr><td>{@code subPackageHandling}</td><td>Use <i>subPackageHandlingEntries</i> parameter to set</td></tr>
     * </table>
     */
    @Parameter
    private Properties properties = new Properties();

    /**
     * Specifies <a href="https://jackrabbit.apache.org/filevault/packagedefinition.html">JCR package definition properties</a> to be serialized into the {@code META-INF/vault/definition/.content.xml} file.
     * Those are implementation-specific and not standardized by FileVault. Only non-namespaced string properties are allowed here.
     * Properties canonically stored somewhere else (like package properties or filter rules) should not be set.
     */
    @Parameter
    private Map<String,String> packageDefinitionProperties = new HashMap<>();

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
    @Parameter(property = "vault.dependencies")
    Collection<MavenBasedPackageDependency> dependencies = new LinkedList<>();

    /**
     * Defines a list of sub package handling entries, which affect how sub packages are installed.
     * Each entry has the following elements:
     * <ul>
     *  <li>option, mandatory, one of the values from {@link SubPackageHandling.Option}</li>
     *  <li>groupName, optional, restricts the option to the given group name, if not set affects there is no package group restriction</li>
     *  <li>packageName, optional, restricts the option to the given package name, if not set affects all package names</li></li>
     * </ul>
     * @since 1.1.10
     */
    @Parameter()
    List<SubPackageHandlingEntry> subPackageHandlingEntries = new LinkedList<>();

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
            alias = "acHandling")
    private AccessControlHandling accessControlHandling;

    /**
     * Defines whether the package requires an admin/privileged session for installation. This will become the
     * {@code requiresRoot} property of the properties.xml file.
     */
    @Parameter(
            property = "vault.requiresRoot",
            defaultValue="false",
            required = true)
    private boolean requiresRoot;
    
    /**
     * Defines additional package dependencies via the `import-package` entry in the manifest.
     * Is merged with the input from {@link #generatedImportPackage}.
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
     * Defines the default parent directory path in the package ZIP archive which contains the embedded bundles ({@code /} needs to be used as file separator). 
     * Must be relative to root entry {@code jcr_root}. Defaults to {@code apps/bundles/install}.
     */
    @Parameter(property = "vault.embeddedTarget")
    private String embeddedTarget;

    /**
     * List of filters for artifacts to embed in the package.
     * The {@link Embedded} class represents one or multiple embedded artifact dependencies
     * from the project descriptor.
     * Each item may configure any of the following fields
     * <p>
     * <table>
     * <tr><td>{@code groupId}</td><td>{@link String}</td><td>Filter criterion against the group id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code artifactId}</td><td>{@link String}</td><td>Filter criterion against the artifact id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code scope}</td><td>{@link ScopeArtifactFilter}</td><td>Filter criterion against the <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">scope of a project dependency</a>. Possible values are <ul><li>{@code test}, which allows every scope</li><li>{@code compile+runtime} which allows every scope except {@code test}</li><li>{@code runtime+system} which allows every scope except {@code test} and {@code provided}</li><li>{@code compile} which allows only scope {@code compile}, {@code provided} and {@code system}</li><li>{@code runtime} which only allows scope {@code runtime} and {@code compile}.</td></tr>
     * <tr><td>{@code type}</td><td>{@link String}</td><td>Filter criterion against the type of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code classifier}</td><td>{@link String}</td><td>Filter criterion against the classifier of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code filter}</td><td>{@link Boolean}</td><td>If set to {@code true} adds the embedded artifact location to the package's filter. Default = {@code false}.</td></tr>
     * <tr><td>{@code isAllVersionsFilter}</td><td>{@link Boolean}</td><td>If {@code filter} is {@code true} and this is {@code true} as well, the filter entry will contain all versions of the same artifact (by creating an according filter pattern). Default = {@code false}.</td></tr>
     * <tr><td>{@code excludeTransitive}</td><td>{@link Boolean}</td><td>If {@code true} only filters on direct dependencies (not on transitive ones). Default = {@code false}.</td></tr>
     * <tr><td>{@code target}</td><td>{@link String}</td><td>The parent directory path in the package ZIP archive where to place the embedded artifact ({@code /} needs to be used as file separator). Must be relative to root entry {@code jcr_root}. Falls back to {@link #embeddedTarget} if not set.</td></tr>
     * </table>
     * All fields are optional. All filter criteria is concatenated with AND logic (i.e. every criterion must match for a specific dependency to be embedded).
     * <br>
     * All filter patterns follow the format <code>&lt;filter&gt;{,&lt;filter&gt;}</code>.
     * Each {@code filter} is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, 
     * otherwise as blacklist. The last matching filter determines the outcome. Only matching dependencies are being considered for being embedded.</td></tr>
     * <br>
     * <i>The difference between {@link #embeddeds} and {@link #subPackages} is that for the former an explicit target is given while for the latter the target is being computed from the artifact's vault property file.</i>
     */
    @Parameter
    Embedded[] embeddeds = new Embedded[0];

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
     * <p>
     * <table>
     * <tr><td>{@code groupId}</td><td>{@link String}</td><td>Filter criterion against the group id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code artifactId}</td><td>{@link String}</td><td>Filter criterion against the artifact id of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code scope}</td><td>{@link ScopeArtifactFilter}</td><td>Filter criterion against the <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">scope of a project dependency</a>. Possible values are <ul><li>{@code test}, which allows every scope</li><li>{@code compile+runtime} which allows every scope except {@code test}</li><li>{@code runtime+system} which allows every scope except {@code test} and {@code provided}</li><li>{@code compile} which allows only scope {@code compile}, {@code provided} and {@code system}</li><li>{@code runtime} which only allows scope {@code runtime} and {@code compile}.</td></tr>
     * <tr><td>{@code type}</td><td>{@link String}</td><td>Filter criterion against the type of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code classifier}</td><td>{@link String}</td><td>Filter criterion against the classifier of a project dependency. A pattern as described below.</td></tr>
     * <tr><td>{@code filter}</td><td>{@link Boolean}</td><td>If set to {@code true} adds the embedded artifact location to the package's filter. Default = {@code false}.</td></tr>
     * <tr><td>{@code isAllVersionsFilter}</td><td>{@link Boolean}</td><td>If {@code filter} is {@code true} and this is {@code true} as well, the filter entry will contain all versions of the same artifact (by creating an according filter pattern). Default = {@code false}.</td></tr>
     * <tr><td>{@code excludeTransitive}</td><td>{@link Boolean}</td><td>If {@code true} only filters on direct dependencies (not on transitive ones). Default = {@code false}.</td></tr>
     * </table>
     * All fields are optional. All filter criteria is concatenated with AND logic (i.e. every criterion must match for a specific dependency to be embedded as a sub package).
     * <br>
     * All filter patterns follow the format <code>&lt;filter&gt;{,&lt;filter&gt;}</code>.
     * Each {@code filter} within a filter pattern is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, 
     * otherwise as blacklist. The last matching filter determines the outcome. Only matching dependencies are being considered for being embedded.
     * <br>
     * <i>The difference between {@link #embeddeds} and {@link #subPackages} is that for the former an explicit target is given while for the latter the target is being computed from the artifact's vault property file.</i>
     */
    @Parameter
    SubPackage[] subPackages = new SubPackage[0];

    /**
     * File from which to read the generated manifest snippet generated by goal "analyze-classes".
     * The contents of the file end up in the `import-package` entry in the manifest.
     */
    @Parameter(property = "vault.generatedImportPackage", defaultValue = "${project.build.directory}/vault-generated-import.txt")
    private File generatedImportPackage;

    /**
     * The archive configuration to use. See <a
     * href="http://maven.apache.org/shared/maven-archiver/index.html">the
     * documentation for Maven Archiver</a>.
     * 
     * All settings related to manifest are not relevant as this gets overwritten by the manifest in {@link AbstractMetadataPackageMojo#workDirectory}
     */
    @Parameter
    private MavenArchiveConfiguration archive;

    /**
     * Optional reference to PNG image that should be used as thumbnail for the content package. Should have a width of 64 pixels.
     * @since 1.0.1
     */
    @Parameter
    private File thumbnailImage;


    /**
     * Defines the content package type. This is either 'application', 'content', 'container' or 'mixed'.
     * If omitted, it is calculated automatically based on filter definitions. Certain package types imply restrictions,
     * for example, 'application' and 'content' packages are not allowed to contain sub packages or embedded bundles.<br>
     * Possible values:
     * <ul>
     *   <li>{@code application}: An application package consists purely of application content. It serializes
     *       entire subtrees with no inclusion or exclusion filters. it does not contain any subpackages nor OSGi
     *       configuration or bundles.</li> 
     *   <li>{@code content}: A content package consists only of content and user defined configuration.
     *       It usually serializes entire subtrees but can contain inclusion or exclusion filters. it does not contain
     *       any subpackages nor OSGi configuration or bundles.</li> 
     *   <li>{@code container}: A container package only contains sub packages and OSGi configuration and bundles.
     *       The container package is only used as container for deployment.</li> 
     *   <li>{@code mixed}: Catch all type for a combination of the above.</li> 
     * </ul>
     */
    @Parameter(property = "vault.packageType")
    protected PackageType packageType;


    /**
     * Defines whether the package is allowed to contain index definitions. This will become the
     * {@code allowIndexDefinitions} property of the properties.xml file.
     */
    @Parameter(
            property = "vault.allowIndexDefinitions",
            defaultValue="false",
            required = true)
    boolean allowIndexDefinitions;

    @Parameter( defaultValue = "${repositorySystemSession}", readonly = true, required = true )
    private RepositorySystemSession repoSession;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true )
    private List<RemoteRepository> repositories;

    /**
     * A list of artifact coordinates in the format {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}.
     * The resolved artifacts are embedded as <a href="http://jackrabbit.apache.org/filevault/installhooks.html">internal install hooks</a> in the resulting content package.
     * @since 1.1.8
     */
    @Parameter(defaultValue="")
    List<ArtifactCoordinates> installHooks;


    /**
     * For m2e incremental build support
     */
    private final BuildContext buildContext;

    /**
     * For correct source of standard embedded path base name.
     */
    private final ArtifactRepositoryLayout embedArtifactLayout;

    private final RepositorySystem repoSystem;

    // take the first "-" followed by a digit as separator between version suffix and rest
    private static final Pattern FILENAME_PATTERN_WITHOUT_VERSION_IN_GROUP1 = Pattern.compile("((?!-\\d).*-)\\d.*");


    @Inject
    public GenerateMetadataMojo(BuildContext buildContext, @Named("default")ArtifactRepositoryLayout embedArtifactLayout, RepositorySystem repoSystem) {
        super();
        // always emit dates in UTC timezone
        iso8601DateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.buildContext = buildContext;
        this.embedArtifactLayout = embedArtifactLayout;
        this.repoSystem = repoSystem;
    }

    /**
     * Sets the package type.
     * @param type the string representation of the package type
     * @throws MojoFailureException if an error occurs
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
     * Sets the access control handling.
     * @param type the string representation of the ac handling
     * @throws MojoFailureException if an error occurs
     */
    public void setAccessControlHandling(String type) throws MojoFailureException {
        try {
            accessControlHandling = AccessControlHandling.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // TODO: emit in lower case
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
                    getLog().debug("Detecting a change on " + getProjectRelativeFilePath(filterSource) + " therefore not cancelling build");
                } else {
                    getLog().debug(getProjectRelativeFilePath(filterSource) + " unchanged therefore cancelling build");
                    return;
                }
            } else {
                getLog().debug("No file change would be relevant therefore cancelling build");
                return;
            }
        }

        if (!failOnEmptyFilter) {
            getLog().warn("The parameter 'failOnEmptyFilter' is no longer supported and ignored. Every package must have a non-empty filter!");
        }
        final File vaultDir = getGeneratedVaultDir(true);
        vaultDir.mkdirs();

        // JCRVLT-331 share work directory to expose vault metadata between process-classes and package phases for
        // multi-module builds.
        getArtifactWorkDirectoryLookup(getPluginContext())
                .put(getModuleArtifactKey(project.getArtifact()), getWorkDirectory(true));

        try {
            // find the meta-inf source directory
            File metaInfDirectory = getMetaInfVaultSourceDirectory();
            
            // generate the filter.xml
            String sourceFilters = computeFilters(metaInfDirectory);
            computeImportPackage();

            // this must happen before the filter rules are extended 
            // but after filters have been consolidated
            if (packageType == null) {
                packageType = computePackageType();
            }

            // calculate the embeddeds and subpackages
            Map<String, File> embeddedFiles = getEmbeddeds();
            embeddedFiles.putAll(getSubPackages());
            
            // embed install hooks
            if (installHooks != null && !installHooks.isEmpty()) {
                for (ArtifactCoordinates installHook : installHooks) {
                    File installHookFile = resolveArtifact(installHook.toArtifact());
                    embeddedFiles.put(Constants.META_DIR + "/" + Constants.HOOKS_DIR + "/" + installHookFile.getName(), installHookFile);
                    getLog().info("Embed install hook " + installHookFile);
                }
            }
            setEmbeddedFilesMap(embeddedFiles);

            String dependenciesString = computeDependencies();
            String dependenciesLocations = computeDependenciesLocations();
            
            // generate properties.xml
            final Properties vaultProperties = computeProperties(dependenciesString, dependenciesLocations);
            try (FileOutputStream fos = new FileOutputStream(new File(vaultDir, Constants.PROPERTIES_XML))) {
                vaultProperties.storeToXML(fos, project.getName());
            }
            writeFilters(sourceFilters);
            copyFile("/vault/config.xml", new File(vaultDir, Constants.CONFIG_XML));
            copyFile("/vault/settings.xml", new File(vaultDir, Constants.SETTINGS_XML));
            File packageDefinitionXml = new File(vaultDir, Constants.PACKAGE_DEFINITION_XML);
            if (!packageDefinitionXml.getParentFile().exists()) {
                packageDefinitionXml.getParentFile().mkdir();
            }
            try (OutputStream output = new FileOutputStream( packageDefinitionXml )) {
                writePackageDefinition(output);
            }
            if (thumbnailImage != null && thumbnailImage.exists()) {
                FileUtils.copyFile(thumbnailImage, new File(packageDefinitionXml.getParentFile(), "thumbnail.png"));
            }
            writeManifest(getGeneratedManifestFile(true), dependenciesString, dependenciesLocations, vaultProperties);
        } catch (IOException | ManifestException | DependencyResolutionRequiredException | ConfigurationException | NamespaceException | XMLStreamException e) {
            throw new MojoExecutionException(e.toString(), e);
        }
        buildContext.refresh(vaultDir);
    }

    void writePackageDefinition(OutputStream outputStream) throws XMLStreamException, FactoryConfigurationError, NamespaceException, IllegalArgumentException, IOException {
        NamespaceMapping namespaceResolver = new NamespaceMapping(); // no namespaces necessary
        namespaceResolver.setMapping(Name.NS_EMPTY_PREFIX, Name.NS_DEFAULT_URI);
        namespaceResolver.setMapping(Name.NS_JCR_PREFIX, Name.NS_JCR_URI);
        try (FormattingXmlStreamWriter xmlWriter = FormattingXmlStreamWriter.create(outputStream, new DocViewFormat().getXmlOutputFormat())) {
            Collection<DocViewProperty2> packDefProps = new ArrayList<>();
            packDefProps.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, JcrPackage.NT_VLT_PACKAGE_DEFINITION));
            packDefProps.add(new DocViewProperty2(NameFactoryImpl.getInstance().create(Name.NS_DEFAULT_URI, "builtWith"), getCreatedBy(), PropertyType.STRING));
            for (Map.Entry<String, String> propertyEntry : packageDefinitionProperties.entrySet()) {
                packDefProps.add(new DocViewProperty2(NameFactoryImpl.getInstance().create(Name.NS_DEFAULT_URI, propertyEntry.getKey()), propertyEntry.getValue(), PropertyType.STRING));
            }
            DocViewNode2 docViewNode = new DocViewNode2(NameConstants.JCR_ROOT, packDefProps);
            docViewNode.writeStart(xmlWriter, namespaceResolver, Collections.singleton(Name.NS_JCR_PREFIX));
            DocViewNode2.writeEnd(xmlWriter);
        }
    }

    void writeManifest(File file, String dependenciesString, String dependenciesLocations, final Properties vaultProperties)
            throws ManifestException, DependencyResolutionRequiredException, IOException, FileNotFoundException {
        // generate manifest file
        MavenArchiver mavenArchiver = new MavenArchiver();
        // it is not possible to manually set the version
        mavenArchiver.setCreatedBy(getMavenProjectProperties().getProperty("name"), "org.apache.jackrabbit", "filevault-package-maven-plugin");
        Manifest manifest = mavenArchiver.getManifest(session, project, getMavenArchiveConfiguration(vaultProperties, dependenciesString, dependenciesLocations));
        try (OutputStream out = new FileOutputStream(file)) {
            manifest.write(out);
        }
    }

    /**
     * Properties contain some resolved properties from this project's Maven model
     * @return some resolved Maven project properties
     * @throws IOException in case the underlying file could not be accessed
     */
    static Properties getMavenProjectProperties() throws IOException {
        final Properties properties = new Properties();
        try (InputStream input = GenerateMetadataMojo.class.getResourceAsStream("/maven-project.properties")) {
            properties.load(input);
        }
        return properties;
    }

    static String getCreatedBy() throws IOException {
        Properties properties = getMavenProjectProperties();
        // name and version of the current project's Maven model
        return properties.getProperty("name") + " " + properties.getProperty("version");
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
     *@return the source filter string (if there have been a filter given manually), otherwise {@code null}
     * @throws IOException if an I/O error occurs
     * @throws MojoExecutionException if the build fails
     */
    private String computeFilters(File vaultMetaDir) throws IOException, MojoExecutionException {
        // backward compatibility: if implicit filter exists, use it. but check for conflicts
        File filterFile = getGeneratedFilterFile(true);
        if (filterFile.exists() && filterFile.lastModified() != 0) {
            // if both, a inline filter and a implicit filter is present, the build fails.
            if (!filters.getFilterSets().isEmpty()) {
                getLog().error("Refuse to merge inline filters and non-sourced filter.xml. If this is intended, specify the filter.xml via the 'filterSource' property.");
                throw new MojoExecutionException("Conflicting filters, look at above log for details.");
            }
            // load filters for further processing
            try {
                filters.load(filterFile);
            } catch (ConfigurationException e) {
                throw new IOException("Error loading filter file " + getProjectRelativeFilePath(filterFile) + "", e);
            }

            getLog().warn("The project is using a filter.xml provided via the resource plugin.");
            getLog().warn("This is deprecated and might no longer be supported in future versions.");
            getLog().warn("Use the 'filterSource' property to specify the filter or use inline filters.");
            return null;
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
                    throw new MojoExecutionException("Conflicting filters, look at above log for details.");
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
            // sourceFilters.resetSource();
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
                throw new IOException("Error loading filter file " + getProjectRelativeFilePath(filterSource), e);
            }
        }

        // if the prefix property is set, it should be used if no filter is set
        if (filters.getFilterSets().isEmpty() && prefix.length() > 0) {
            filters.add(new PathFilterSet(prefix));
        }

        return sourceFilters.getSourceAsString();
    }

    private void mergeFilters(DefaultWorkspaceFilter dst, WorkspaceFilter src) {
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

    private void writeFilters(String sourceFilters) throws IOException, MojoExecutionException {
        // if no filter is defined at all, fail
        if (filters.getFilterSets().isEmpty()) {
            throw new MojoExecutionException("No workspace filter defined!");
        }

        File filterFile = getGeneratedFilterFile(true);
        // if the source filters and the generated filters are the same, copy the source file to retain the comments
        if (filterSource != null && filters.getSourceAsString().equals(sourceFilters)) {
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
     * @throws URISyntaxException 
     */
    String computeDependencies() throws IOException {
        String dependenciesString = null;
        if (!dependencies.isEmpty()) {
            MavenBasedPackageDependency.resolve(project, getLog(), dependencies);
            Dependency[] vaultDependencies = dependencies.stream().map(MavenBasedPackageDependency::getPackageDependency).toArray(Dependency[]::new);
            dependenciesString = Dependency.toString(vaultDependencies);
        }
        return dependenciesString;
    }

    private String computeDependenciesLocations() throws IOException {
        String dependenciesLocations = null;
        if (!dependencies.isEmpty()) {
            MavenBasedPackageDependency.resolve(project, getLog(), dependencies);
            // pid = uri
            dependenciesLocations = dependencies.stream().filter(a -> a.getInfo() != null).map(a -> a.getInfo().getId().toString() + "=" + a.getLocation()).collect(Collectors.joining(","));
        }
        return dependenciesLocations;
    }

    /**
     * Escapes multiline manifest values to work around bug <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8222547">JDK-8222547</a>
     * Java itself only adds leading SPACE in case a line is longer than 72 chars.
     *
     * If the value contains a newline, suffix it with an additional space (continuation character)!
     *
     * Unfortunately although the generated manifests for such escaped values are perfectly valid according to the spec,
     * when reading those via {@link java.util.jar.Manifest} the new lines are stripped.
     */
    static final String escapeManifestValue(String value) {
        return value.replaceAll("\n", "\n ")    // this covers CRLF and LF
               .replaceAll("\r(?!\n)", "\r ");  // only CR (not followed by LF)
    }

    private MavenArchiveConfiguration getMavenArchiveConfiguration(Properties vaultProperties, String dependenciesString, String dependenciesLocations) throws IOException {
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
            archive.addManifestEntry(PackageProperties.MF_KEY_PACKAGE_TYPE, escapeManifestValue(packageType.name().toLowerCase()));
            archive.addManifestEntry(PackageProperties.MF_KEY_PACKAGE_ID, escapeManifestValue(id.toString()));
            archive.addManifestEntry(PackageProperties.MF_KEY_PACKAGE_DESC, escapeManifestValue(vaultProperties.getProperty("description", "")));
            if (dependenciesString != null && dependenciesString.length() > 0) {
                archive.addManifestEntry(PackageProperties.MF_KEY_PACKAGE_DEPENDENCIES, escapeManifestValue(dependenciesString));
                if (dependenciesLocations != null && dependenciesLocations.length() > 0) {
                    archive.addManifestEntry(PackageProperties.MF_KEY_PACKAGE_DEPENDENCIES_LOCATIONS, escapeManifestValue(dependenciesLocations));
                }
            }
            
            // be sure to avoid duplicates
            Set<String> rts = new TreeSet<>();
            for (PathFilterSet p: filters.getFilterSets()) {
                rts.add(p.getRoot());
            }
            String[] roots = rts.toArray(new String[rts.size()]);
            Arrays.sort(roots);
            archive.addManifestEntry(PackageProperties.MF_KEY_PACKAGE_ROOTS, escapeManifestValue(StringUtils.join(roots, ",")));

            // import package is not yet there!
            if (StringUtils.isNotEmpty(importPackage)) {
                archive.addManifestEntry(PackageProperties.MF_KEY_IMPORT_PACKAGE, escapeManifestValue(StringUtils.deleteWhitespace(importPackage)));
            }
        }

        return archive;
    }

    Properties computeProperties(String dependenciesString, String dependenciesLocations) {
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
        props.put(PackageProperties.NAME_DESCRIPTION, description);

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
        props.put(PackageProperties.NAME_GROUP, group);
        props.put(PackageProperties.NAME_NAME, name);
        props.put(PackageProperties.NAME_VERSION, version);

        // maven artifact identification
        props.put("groupId", project.getGroupId());
        props.put("artifactId", project.getArtifactId());

        // dependencies
        if (dependenciesString != null && dependenciesString.length() > 0) {
            props.put(PackageProperties.NAME_DEPENDENCIES, dependenciesString);
            if (dependenciesLocations != null && dependenciesLocations.length() > 0) {
                props.put(PackageProperties.NAME_DEPENDENCIES_LOCATIONS, dependenciesLocations);
            }
        }

        MavenArchiver archiver = new MavenArchiver();
        Date createdDate = null;
        if (!ArtifactUtils.isSnapshot(version)) {
            createdDate = archiver.parseOutputTimestamp(outputTimestamp);
        } else {
            getLog().debug("Disabling reproducible builds as the version is a SNAPSHOT, therefore a dynamic created date is used");
        }
        if (createdDate == null) {
            createdDate = new Date();
        }
        props.put(PackageProperties.NAME_CREATED, iso8601DateFormat.format(createdDate));

        // configurable properties
        props.put(PackageProperties.NAME_REQUIRES_ROOT, String.valueOf(requiresRoot));
        props.put(PackageProperties.NAME_ALLOW_INDEX_DEFINITIONS, String.valueOf(allowIndexDefinitions));
        props.put(PackageProperties.NAME_PACKAGE_TYPE, packageType.name().toLowerCase());
        if (accessControlHandling != null) {
            props.put(PackageProperties.NAME_AC_HANDLING, accessControlHandling.name().toLowerCase());
        }
        if (!subPackageHandlingEntries.isEmpty()) {
            SubPackageHandling subPackageHandling = new org.apache.jackrabbit.vault.packaging.SubPackageHandling();
            subPackageHandlingEntries.stream().map(SubPackageHandlingEntry::toEntry).forEach(e -> subPackageHandling.getEntries().add(e));
            props.put(PackageProperties.NAME_SUB_PACKAGE_HANDLING, subPackageHandling.getString());
        }
        return props;
    }
    

    private Map<String, File> getEmbeddeds() throws MojoFailureException, ConfigurationException {
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
                throw new MojoFailureException("destFileName defined but several artifacts match for " + emb);
            }

            String targetPath = emb.getTarget();
            if (targetPath == null) {
                targetPath = embeddedTarget;
                if (targetPath == null) {
                    final String loc = (prefix.length() == 0)
                            ? "apps/"
                            : prefix;
                    targetPath = loc + "bundles/install/";
                    getLog().info("No target path set on " + emb + "; assuming default " + targetPath);
                }
            }
            targetPath = makeAbsolutePath(targetPath);

            targetPath = Constants.ROOT_DIR + targetPath;
            targetPath = FileUtils.normalize(targetPath);
            if (!targetPath.endsWith("/")) {
                targetPath += "/";
            }

            getLog().info("Embedding --- " + emb + " ---");
            for (final Artifact artifact : artifacts) {
                final File source = artifact.getFile();
                String destFileName = emb.getDestFileName();

                if (destFileName == null) {
                    // construct final name from artifact coordinates
                    destFileName = Text.getName(embedArtifactLayout.pathOf(artifact));
                }
                final String targetPathName = targetPath + destFileName;
                final String targetNodePathName = targetPathName.substring(Constants.ROOT_DIR.length());

                File oldSource = fileMap.put(targetPathName, source);
                if (oldSource != null) {
                    throw new MojoFailureException(String.format("Duplicate target path %s for %s and %s", targetPathName, source, oldSource));
                }
                getLog().info(String.format("Embedding %s (from %s) -> %s", artifact.getId(), source.getAbsolutePath(), targetPathName));

                if (emb.isFilter()) {
                    addEmbeddedFileToFilter(targetNodePathName, emb.isAllVersionsFilter());
                }
            }
        }
        return fileMap;
    }

    private Map<String, File> getSubPackages() throws MojoFailureException, ConfigurationException {
        final String propsRelPath = Constants.META_DIR + "/" + Constants.PROPERTIES_XML;
        Map<String, File> fileMap = new HashMap<>();
        for (SubPackage pack : subPackages) {
            final Collection<Artifact> artifacts = pack.getMatchingArtifacts(project);
            if (artifacts.isEmpty()) {
                getLog().warn("No matching artifacts for sub package " + pack);
                continue;
            }

            // get the package path
            getLog().info("Embedding subpackage --- " + pack + " ---");
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
                        otherWorkDirectory = source; // points to "target/classes"
                    }

                    File propsXml = new File(otherWorkDirectory, propsRelPath);
                    if (!propsXml.exists()) {
                        // fallback to work dir (assuming the same folder name)
                        propsXml = new File(otherWorkDirectory.getParent(), getWorkDirectory(false).getName() + "/" + propsRelPath);
                    }
                    try (InputStream input = new FileInputStream(propsXml)) {
                        props.loadFromXML(input);
                    } catch (IOException e) {
                        throw new MojoFailureException("Could not read META-INF/vault/properties.xml from directory '" +
                                otherWorkDirectory + "' to extract metadata: " + e.getMessage(), e);
                    }
                } else {
                    // load properties
                    try (ZipFile zip = new ZipFile(source)) {
                        ZipEntry e = zip.getEntry(propsRelPath);
                        if (e == null) {
                            throw new IOException("Package does not contain 'META-INF/vault/properties.xml'");
                        }
                        try (InputStream in = zip.getInputStream(e)) {
                            props.loadFromXML(in);
                        }
                    } catch (IOException e) {
                        throw new MojoFailureException("Could not open subpackage '" + source + "' to extract metadata: " + e.getMessage(), e);
                    }
                }
                PackageId pid = new PackageId(
                        props.getProperty(PackageProperties.NAME_GROUP),
                        props.getProperty(PackageProperties.NAME_NAME),
                        props.getProperty(PackageProperties.NAME_VERSION)
                );
                final String targetNodePathName = pid.getInstallationPath() + ".zip";
                final String targetPathName = "jcr_root" + targetNodePathName;

                getLog().info(String.format("Embedding %s (from %s) -> %s", artifact.getId(), source.getAbsolutePath(), targetPathName));
                fileMap.put(targetPathName, source);
                if (pack.isFilter()) {
                    addEmbeddedFileToFilter(targetNodePathName, pack.isAllVersionsFilter());
                }
            }
        }
        return fileMap;
    }

    /**
     * Establishes a session-shareable workDirectory lookup map for the given pluginContext.
     *
     * @param pluginContext a Map retrieved from {@link MavenSession#getPluginContext(PluginDescriptor, MavenProject)}.
     * @return a lookup Map. The key is {@link Artifact#getId()} and value is {@link AbstractMetadataPackageMojo#workDirectory}.
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
     * I.E. {@link Artifact#getId()} contains either ":content-package:" or ":zip:" depending on whether it comes from
     * this.session.getProjects() -> MavenProject#getArtifact() or from this.project.getArtifacts().
     *
     * @param artifact the module artifact ({@link MavenProject#getArtifact()}) to identify
     * @return a handler-independent artifact disambiguation key
     */
    String getModuleArtifactKey(final Artifact artifact) {
        return this.embedArtifactLayout.pathOf(artifact);
    }

    private void addEmbeddedFileToFilter(String embeddedFile, boolean includeAllVersions) throws ConfigurationException {
        filters.add(getPathFilterSetForEmbeddedFile(embeddedFile, includeAllVersions));
    }

    static PathFilterSet getPathFilterSetForEmbeddedFile(final String embeddedFile, boolean includeAllVersions)
            throws ConfigurationException {
        final PathFilterSet pathFilterSet;
        if (includeAllVersions) {
            String filename = FilenameUtils.getName(embeddedFile);
            // shorten the filter root by one level
            String rootName = StringUtils.chomp(embeddedFile, "/");
            String extension = FilenameUtils.getExtension(filename);

            // now find a mattern which should apply to embeddedFile and all similar artifacts with different versions from format:
            // ${artifactId}-${version})
            Matcher matcher = FILENAME_PATTERN_WITHOUT_VERSION_IN_GROUP1.matcher(filename);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Could not figure out version part in filename '" + filename
                        + "'. For this artifact you cannot use 'isAllVersionsFilter=true'");
            }

            // create new pattern which matches the same artifacts in all versions
            String pattern = Pattern.quote(rootName + "/" + matcher.group(1)) + ".*" + "\\." + extension + "(/.*)?";
            if (!embeddedFile.matches(pattern)) {
                throw new IllegalArgumentException("Detected pattern '" + pattern + "' does not even match given filename '" + embeddedFile
                        + "'. For this artifact you cannot use 'isAllVersionsFilter=true'");
            }
            pathFilterSet = new PathFilterSet(rootName);
            pathFilterSet.addInclude(new DefaultPathFilter(pattern));
        } else {
            pathFilterSet = new PathFilterSet(embeddedFile);
        }
        return pathFilterSet;
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

    private static final Pattern OSGI_CONFIG_OR_BUNDLE_FILTER_ROOT_PATTERN = Pattern.compile("/apps/(.*/)?(config|install)(\\.[^/]*)?");
    PackageType computePackageType() {
        final PackageType packageType;
        // auto detect...
        boolean hasApps = false;
        boolean hasOsgiConfigsOrBundles = false;
        boolean hasOther = false;
        for (PathFilterSet p : filters.getFilterSets()) {
            if (PathFilterSet.TYPE_CLEANUP.equals(p.getType())) {
                continue;
            }
            String root = p.getRoot();
            if ("/apps".equals(root) || root.startsWith("/apps/") || "/libs".equals(root) || root.startsWith("/libs/")) {
                // is it OSGi config or bundles (just some heuristic)
                if (OSGI_CONFIG_OR_BUNDLE_FILTER_ROOT_PATTERN.matcher(root).matches()) {
                    getLog().debug("Detected probably OSGi config/bundle filter entry: " + p);
                    hasOsgiConfigsOrBundles = true;
                } else {
                    hasApps = true;
                    getLog().debug("Detected /apps or /libs filter entry: " + p);
                }
            } else {
                hasOther = true;
                getLog().debug("Detected filter entry outside /apps and /libs: " + p);
            }
        }
        // no embeds and subpackages?
        getLog().debug("Detected " + embeddeds.length + " bundle(s) and " + subPackages.length + " sub package(s).");
        if (embeddeds.length == 0 && subPackages.length == 0) {
            if (hasApps && !hasOther && !hasOsgiConfigsOrBundles) {
                packageType = PackageType.APPLICATION;
            } else if (hasOther && !hasApps && !hasOsgiConfigsOrBundles) {
                packageType = PackageType.CONTENT;
            } else if (!hasOther && !hasApps && hasOsgiConfigsOrBundles) {
                packageType = PackageType.CONTAINER;
            } else {
                packageType = PackageType.MIXED;
            }
        } else {
            if (!hasApps && !hasOther) {
                packageType = PackageType.CONTAINER;
            } else {
                packageType = PackageType.MIXED;
            }
        }
        getLog().info("Auto-detected package type: " + packageType.toString().toLowerCase());
        return packageType;
    }

    /**
     * Copies from the class loader resource given by {@link source} into the file given in {@link target} in case
     * the target file does not exist yet.
     * @param source the name of the class loader resource
     * @param target the target file to copy to
     * @throws IOException
     */
    private void copyFile(String source, File target) throws IOException {
        // nothing to do if the file exists
        if (target.exists()) {
            return;
        }

        target.getParentFile().mkdirs();

        try (InputStream ins = getClass().getResourceAsStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (ins != null) {
                IOUtil.copy(ins, out);
            } else {
                throw new IllegalArgumentException("Could not find resource " + source);
            }
        }
    }
    
    private File resolveArtifact(org.eclipse.aether.artifact.Artifact artifact) throws MojoExecutionException {
        return resolveArtifact(artifact, repoSystem, repoSession, repositories);
    }
}
