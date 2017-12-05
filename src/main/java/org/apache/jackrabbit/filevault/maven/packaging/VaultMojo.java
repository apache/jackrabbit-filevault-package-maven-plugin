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
import java.nio.file.Files;
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
import org.apache.jackrabbit.filevault.maven.packaging.impl.DependencyValidator;
import org.apache.jackrabbit.filevault.maven.packaging.impl.FileValidator;
import org.apache.jackrabbit.filevault.maven.packaging.impl.PackageDependency;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.maven.archiver.ManifestConfiguration;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.util.AbstractScanner;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Processor;

import static org.codehaus.plexus.archiver.util.DefaultFileSet.fileSet;

/**
 * Build a content package.
 */
@Mojo(
        name = "package",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class VaultMojo extends AbstractMojo {

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
     * The Maven project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    /**
     * The directory containing the content to be packaged up into the content
     * package.
     *
     * This property is deprecated; use jcrRootSourceDirectory instead.
     */
    @Deprecated
    @Parameter
    private File builtContentDirectory;

    /**
     * The directory that contains the jcr_root of the content. Multiple directories can be specified as a comma separated list,
     * which will act as a search path and cause the plugin to look for the first existing directory.
     */
    @Parameter(
            property = "vault.jcrRootSourceDirectory",
            required = true,
            defaultValue =
                    "${project.basedir}/jcr_root," +
                    "${project.basedir}/src/main/jcr_root," +
                    "${project.basedir}/src/main/content/jcr_root," +
                    "${project.basedir}/src/content/jcr_root," +
                    "${project.build.outputDirectory}"
    )
    private File[] jcrRootSourceDirectory;

    /**
     * The directory that contains the META-INF/vault. Multiple directories can be specified as a comma separated list,
     * which will act as a search path and cause the plugin to look for the first existing directory.
     */
    @Parameter(
            property = "vault.metaInfVaultDirectory",
            required = true,
            defaultValue =
                    "${project.basedir}/META-INF/vault," +
                    "${project.basedir}/src/main/META-INF/vault," +
                    "${project.basedir}/src/main/content/META-INF/vault," +
                    "${project.basedir}/src/content/META-INF/vault"
    )
    private File[] metaInfVaultDirectory;

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
     * Optional reference to PNG image that should be used as thumbnail for the content package.
     */
    @Parameter
    private File thumbnailImage;

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
     * {@code requiresRoot} property of the properties.xml file.
     */
    @Parameter(
            property = "vault.requiresRoot",
            defaultValue="false",
            required = true)
    private boolean requiresRoot;

    /**
     * Defines whether the package is allowed to contain index definitions. This will become the
     * {@code allowIndexDefinitions} property of the properties.xml file.
     */
    @Parameter(
            property = "vault.allowIndexDefinitions",
            defaultValue="false",
            required = true)
    private boolean allowIndexDefinitions;

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
     * The file name patterns to exclude in addition to the ones listed in 
     * {@link AbstractScanner#DEFAULTEXCLUDES}. The format of each pattern is described in {@link DirectoryScanner}.
     */
    @Parameter(property = "vault.excludes",
               defaultValue="**/.vlt,**/.vltignore,**/.DS_Store",
               required = true)
    private String[] excludes;

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
     * <tr><td>groupId</td><td>String</td><td>Filter criterion against the group id of a project dependency. A pattern of format {@code &lt;filter&gt;{,&lt;filter&gt;}}. Each {@code filter} is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, otherwise as blacklist. The last matching filter determines the outcome. Only matching group ids are being considered for being embedded.</td></tr>
     * <tr><td>artifactId</td><td>String</td><td>Filter criterion against the artifact ids of a project dependency. A pattern of format {@code &lt;filter&gt;{,&lt;filter&gt;}}. Each {@code filter} is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, otherwise as blacklist. The last matching filter determines the outcome. Only matching artifacts ids are being considered for being embedded.</td></tr>
     * <tr><td>scope</td><td>ScopeArtifactFilter</td><td>Filter criterion against the <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">scope of a project dependency</a>. Possible values are <ul><li>{@code test}, which allows every scope</li><li>{@code compile+runtime} which allows every scope except {@code test}</li><li>{@code runtime+system} which allows every scope except {@code test} and {@code provided}</li><li>{@code compile} which allows only scope {@code compile}, {@code provided} and {@code system}</li><li>{@code runtime} which only allows scope {@code runtime} and {@code compile}.</td></tr>
     * <tr><td>type</td><td>String</td><td>Filter criterion against the type of a project dependency. The value given here must be equal to the project dependency's type.</td></tr>
     * <tr><td>classifier</td><td>String</td><td>Filter criterion against the classifier of a project dependency. The value given here must be equal to the project dependency's classifier.</td></tr>
     * <tr><td>filter</td><td>Boolean</td><td>If set to {@code true} adds the embedded artifact location to the package's filter.</td></tr>
     * <tr><td>target</td><td>String</td><td>The parent folder location in the package where to place the embedded artifact. Falls back to {@link #embeddedTarget} if not set.</td></tr>
     * </table>
     * </pre>
     * All fields are optional. All filter criteria is concatenated with AND logic (i.e. every criterion must match for a specific dependency to be embedded).
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
     * Defines the content package type. this is either 'application', 'content', 'container' or 'mixed'.
     * If omitted, it is calculated automatically based on filter definitions. certain package types imply restrictions,
     * for example, 'application' and 'content' packages are not allowed to contain sub packages or embedded bundles.<br/>
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
     * Defines the content of the filter.xml file
     */
    @Parameter
    private final Filters filters = new Filters();

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
     * computed dependency string
     * @see #computeDependencies()
     */
    private String dependenciesString;

    /**
     * Defines the list of sub packages to be embedded in this package.
     * The {@code SubPackage} class represents one or multiple subpackage artifact dependencies
     * from the project descriptor. Each {@code <subPackage>} element may configure any of the following fields
     *  <p>
     * <table>
     * <tr><td>groupId</td><td>String</td><td>Filter criterion against the group id of a project dependency. A pattern of format {@code &lt;filter&gt;{,&lt;filter&gt;}}. Each {@code filter} is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, otherwise as blacklist. The last matching filter determines the outcome. Only matching group ids are being considered for being embedded.</td></tr>
     * <tr><td>artifactId</td><td>String</td><td>Filter criterion against the artifact ids of a project dependency. A pattern of format {@code &lt;filter&gt;{,&lt;filter&gt;}}. Each {@code filter} is a string which is either an exclude (if it starts with a {@code ~}) or an include otherwise. If the first {@code filter} is an include the pattern acts as whitelist, otherwise as blacklist. The last matching filter determines the outcome. Only matching artifacts ids are being considered for being embedded.</td></tr>
     * <tr><td>scope</td><td>ScopeArtifactFilter</td><td>Filter criterion against the <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">scope of a project dependency</a>. Possible values are <ul><li>{@code test}, which allows every scope</li><li>{@code compile+runtime} which allows every scope except {@code test}</li><li>{@code runtime+system} which allows every scope except {@code test} and {@code provided}</li><li>{@code compile} which allows only scope {@code compile}, {@code provided} and {@code system}</li><li>{@code runtime} which only allows scope {@code runtime} and {@code compile}.</td></tr>
     * <tr><td>type</td><td>String</td><td>Filter criterion against the type of a project dependency. The value given here must be equal to the project dependency's type. In most cases should be "content-package" or "zip".</td></tr>
     * <tr><td>classifier</td><td>String</td><td>Filter criterion against the classifier of a project dependency. The value given here must be equal to the project dependency's classifier.</td></tr>
     * <tr><td>filter</td><td>Boolean</td><td>If set to {@code true} adds the embedded artifact location to the package's filter</td></tr>
     * </table>
     * </pre>
     * All fields are optional. All filter criteria is concatenated with AND logic (i.e. every criterion must match for a specific dependency to be embedded).
     * <i>The difference between {@link #embeddeds} and {@link #subPackages} is that for the former an explicit target is given while for the latter the target is being computed from the artifact's vault property file.</i>
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
     * <tr><td>acHandling</td><td>Use <i>accessControlHandling</i> parameter to set</td></tr>
     * </table>
     */
    @Parameter
    private final Properties properties = new Properties();

    /**
     * Creates a {@link FileSet} for the archiver
     * @param directory the directory
     * @param prefix the prefix
     * @return the fileset
     */
    @Nonnull
    private FileSet createFileSet(@Nonnull File directory, @Nonnull String prefix) {
        return fileSet(directory)
                .prefixed(prefix)
                .includeExclude(null, excludes)
                .includeEmptyDirs(true);
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
            // find the meta-inf source directory
            File metaInfDirectory = null;
            for (File dir: metaInfVaultDirectory) {
                if (dir.exists() && dir.isDirectory()) {
                    metaInfDirectory = dir;
                    break;
                }
            }
            if (metaInfDirectory != null) {
                getLog().info("using meta-inf/vault from " + metaInfDirectory.getPath());
            }
            // find the source directory
            File jcrSourceDirectory = null;
            if (builtContentDirectory != null) {
                getLog().warn("The 'builtContentDirectory' is deprecated. Please use the new 'jcrRootSourceDirectory' instead.");
                jcrSourceDirectory = builtContentDirectory;
            } else {
                for (File dir: jcrRootSourceDirectory) {
                    if (dir.exists() && dir.isDirectory()) {
                        jcrSourceDirectory = dir;
                        break;
                    }
                }
            }
            if (jcrSourceDirectory != null) {
                getLog().info("packaging content from " + jcrSourceDirectory.getPath());
            }

            vaultDir.mkdirs();

            Map<String, File> embeddedFiles = copyEmbeddeds();
            embeddedFiles.putAll(copySubPackages());

            computePackageFilters(vaultDir, metaInfDirectory);
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
            if (metaInfDirectory != null) {
                contentPackageArchiver.addFileSet(createFileSet(metaInfDirectory, "META-INF/vault/"));
            }
            contentPackageArchiver.addFileSet(createFileSet(workDirectory, ""));

            // include content from build only if it exists
            if (jcrSourceDirectory != null && jcrSourceDirectory.exists()) {
                // See GRANITE-16348
                // we want to build a list of all the root directories in the order they were specified in the filter
                // but ignore the roots that don't point to a directory
                List<PathFilterSet> filterSets = filters.getFilterSets();
                if (filterSets.isEmpty()) {
                    contentPackageArchiver.addFileSet(createFileSet(jcrSourceDirectory, FileUtils.normalize(JCR_ROOT + prefix)));
                } else {
                    for (PathFilterSet filterSet : filterSets) {
                        String relPath = PlatformNameFormat.getPlatformPath(filterSet.getRoot());
                        String rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath);

                        // CQ-4204625 skip embedded files, will be added later in the proper way
                        if (embeddedFiles.containsKey(rootPath)) {
                            continue;
                        }

                        // check for full coverage aggregate
                        File fullCoverage = new File(jcrSourceDirectory, relPath + ".xml");
                        if (fullCoverage.isFile()) {
                            rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath + ".xml");
                            contentPackageArchiver.addFile(fullCoverage, rootPath);
                            continue;
                        }

                        File rootDirectory = new File(jcrSourceDirectory, relPath);

                        // traverse the ancestors until we find a existing directory (see CQ-4204625)
                        while ((!rootDirectory.exists() || !rootDirectory.isDirectory())
                                && !jcrSourceDirectory.equals(rootDirectory)) {
                            rootDirectory = rootDirectory.getParentFile();
                            relPath = StringUtils.chomp(relPath, "/");
                        }

                        if (!jcrSourceDirectory.equals(rootDirectory)) {
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
            
            // add package thumbnail
            if (thumbnailImage != null && thumbnailImage.exists()) {
                File vaultDefinitionFolder = new File(vaultDir, "definition");
                if (!vaultDefinitionFolder.exists()) {
                    vaultDefinitionFolder.mkdir();
                }
                copyFile("/vault/definition/.content.xml", new File(vaultDefinitionFolder, ".content.xml"));
                FileUtils.copyFile(thumbnailImage, new File(vaultDefinitionFolder, "thumbnail.png"));
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
    private void computePackageFilters(File vaultWorkDir, File vaultMetaDir) throws IOException, MojoExecutionException {
        // backward compatibility: if implicit filter exists, use it. but check for conflicts
        File filterFile = new File(vaultWorkDir, "filter.xml");
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
            filters.getFilterSets().clear();
            filters.getFilterSets().addAll(sourceFilters.getFilterSets());
            filters.getPropertyFilterSets().clear();
            filters.getPropertyFilterSets().addAll(sourceFilters.getPropertyFilterSets());

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
        TreeMap<String, Attrs> importParams = new TreeMap<String, Attrs>();
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
        if (accessControlHandling != null) {
            props.put("acHandling", accessControlHandling.name().toLowerCase());
        }
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
            if (!targetPath.endsWith("/")) {
                targetPath += "/";
            }

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

}
