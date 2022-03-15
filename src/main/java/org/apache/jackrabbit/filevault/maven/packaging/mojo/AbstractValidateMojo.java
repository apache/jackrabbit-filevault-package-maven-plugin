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
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.filevault.maven.packaging.MavenBasedPackageDependency;
import org.apache.jackrabbit.filevault.maven.packaging.ValidatorSettings;
import org.apache.jackrabbit.filevault.maven.packaging.ValidatorSettingsKey;
import org.apache.jackrabbit.filevault.maven.packaging.impl.DependencyResolver;
import org.apache.jackrabbit.filevault.maven.packaging.impl.ValidationMessagePrinter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.ValidationExecutorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.impl.AdvancedFilterValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.impl.DependencyValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.impl.PackageTypeValidatorFactory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.repository.DefaultRepositoryRequest;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Version;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Common ancestor for all validation related mojos
 */
public abstract class AbstractValidateMojo extends AbstractMojo {
    public static final String IGNORE_GAV = "ignore";

    @Parameter(property = "vault.skipValidation", defaultValue = "false", required = true)
    boolean skipValidation;

    /** All validator settings in a map. The keys are the validator ids (optionally suffixed by {@code :<package group>:<package name>} to be restricted to certain packages).
     * You can use {@code *} as wildcard value for {@code package group}.
     * Alternatively you can use the suffix {@code :subpackages} to influence the settings for all sub packages only!
     * The values are a complex object of type {@link ValidatorSettings}.
     * An example configuration looks like
     * <pre>
     *  &lt;jackrabbit-filter&gt;
     *      &lt;options&gt;
     *          &lt;severityForUncoveredAncestorNodes&gt;error&lt;/severityForUncoveredAncestorNodes&gt;
     *      &lt;/options&gt;
     *  &lt;/jackrabbit-filter&gt;
     * </pre>
     * 
     * Each validator settings consists of the fields {@code isDisabled}, {@code defaultSeverity} and {@code options}.
     * 
     * As potentially multiple map entries may affect the same validator id (due to different suffixes) the settings for a single validator id are merged in the order from more specific to more generic keys:
     * <ol>
     * <li>settings for a specific groupId and artifactId</li>
     * <li>settings for any groupId (*) and a specific artifactId</li>
     * <li>settings for subpackages</li>
     * <li>settings without restrictions</li>
     * </ol>
     * Merging will only overwrite non-existing fields, i.e. same-named options from more specific keys will overwrite those from more generic keys (for the same validator id).
     */
    @Parameter
    private Map<String, ValidatorSettings> validatorsSettings;

    /** Controls if errors during dependency validation should fail the build. 
     *  
     * @deprecated Use {@link validatorsSettings} with the following values
     * instead
     * <pre>
     *   &lt;jackrabbit-dependencies&gt;
     *       &lt;defaultSeverity&gt;debug&lt;/defaultSeverity&gt;
     *   &lt;/jackrabbit-dependencies&gt;
     * </pre>
     * 
     */
    @Parameter(property = "vault.failOnDependencyErrors", defaultValue = "true", required = true)
    @Deprecated
    private boolean failOnDependencyErrors;

    /** The Maven project (never {@code null}, but might be dummy project returning {@code null} for all methods if running outside a {@code pom.xml} context) */
    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    protected MavenProject project;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    protected MojoExecution mojoExecution;

    @Parameter(defaultValue = "${session}", readonly = true, required = false)
    protected MavenSession session;

    /** If set to {@code true} will lead to all validation errors or warnings failing the build, otherwise only validation errors lead to a
     * build failure */
    @Parameter(property = "vault.failOnValidationWarning", defaultValue = "false")
    protected boolean failOnValidationWarnings;

    /** Defines the list of dependencies A dependency is declared as a {@code <dependency>} element of a list style {@code <dependencies>}
     * element:
     * 
     * <pre>
     * &lt;dependency&gt;
     *     &lt;group&gt;theGroup&lt;/group&gt;
     *     &lt;name&gt;theName&lt;/name&gt;
     *     &lt;version&gt;1.5&lt;/version&gt;
     * &lt;/dependency&gt;
     * </pre>
     * <p>
     * The dependency can also reference a maven project dependency, this is preferred as it yields to more robust builds.
     * 
     * <pre>
     * &lt;dependency&gt;
     *     &lt;groupId&gt;theGroup&lt;/groupId&gt;
     *     &lt;artifactId&gt;theName&lt;/artifactId&gt;
     * &lt;/dependency&gt;
     * </pre>
     * <p>
     * The {@code versionRange} may be indicated as a single version, in which case the version range has no upper bound and defines the
     * minimal version accepted. Otherwise, the version range defines a lower and upper bound of accepted versions, where the bounds are
     * either included using parentheses {@code ()} or excluded using brackets {@code []} */
    @Parameter(property = "vault.dependencies")
    protected Collection<MavenBasedPackageDependency> dependencies = new LinkedList<>();

    /** Defines the packages that define the repository structure. For the format description look at {@link #dependencies}.
     * <p>
     * The repository-init feature of sling-start can define initial content that will be available in the repository before the first
     * package is installed. Packages that depend on those nodes have no way to reference any dependency package that provides these nodes.
     * A "real" package that would creates those nodes cannot be installed in the repository, because it would void the repository init
     * structure. On the other hand would filevault complain, if the package was listed as dependency but not installed in the repository.
     * So therefore this repository-structure packages serve as indicator packages that helps satisfy the structural dependencies, but are
     * not added as real dependencies to the package. */
    @Parameter(property = "vault.repository.structure.packages")
    protected Collection<MavenBasedPackageDependency> repositoryStructurePackages = new LinkedList<>();

    /**
     * Mapping of package dependencies given via group and name to Maven identifiers for enhanced validation.
     * Each entry must have the format {@code <group>:<name>=<groupId>:<artifactId>}.
     * To disable lookup (e.g. because referenced artifact is not available in a Maven repository) use {@code <group>:<name>=ignore}.
     * This will also prevent the WARNING which would be otherwise be emitted.
     */
    @Parameter(property = "vault.package.dependency.to.maven.ga")
    protected Collection<String> mapPackageDependencyToMavenGa;

    /** 
     * The file where to write a report of all found validation violations (warnings and errors) in CSV format as defined in <a href="https://tools.ietf.org/html/rfc4180">RFC 4180</a>.
     * The generated file is using UTF-8 character encoding.
     * No CSV report is written if this parameter is not set (default).
     */
    @Parameter(property = "vault.validation.csvReportFile")
    protected File csvReportFile;

    @Component
    protected RepositorySystem repositorySystem;

    @Component
    protected ResolutionErrorHandler resolutionErrorHandler;

    @Component
    protected BuildContext buildContext;

    protected final ValidationExecutorFactory validationExecutorFactory;

    protected DependencyResolver resolver;
    
    /**
     * Artificial Maven artifact which indicates that it should not be considered for further lookup!
     */
    public static final Artifact IGNORE_ARTIFACT = new DefaultArtifact("ignore", "ignore", "1.0", "", "", "", null);
    
    private static Version fileVaultValidationBundleVersion = null;
    private static final Version VERSION_3_5_4 = Version.parseVersion("3.5.4");

    public AbstractValidateMojo() {
        super();
        this.validationExecutorFactory = new ValidationExecutorFactory(this.getClass().getClassLoader());
    }

    protected String getProjectRelativeFilePath(Path path) {
        final Path baseDir;
        if (project != null && project.getBasedir() != null) {
            baseDir = project.getBasedir().toPath();
        } else {
            baseDir = null;
        }
        return getRelativeFilePath(path, baseDir);
    }

    public static String getRelativeFilePath(@NotNull Path path, @Nullable Path baseDir) {
        final Path shortenedPath;
        if (baseDir != null) {
            shortenedPath = baseDir.relativize(path);
        } else {
            shortenedPath = path;
        }
        return "'" + shortenedPath.toString() + "'";
    }


    static Map<Dependency, Artifact> resolveMap(Collection<String> mapPackageDependencyToMavenGa) {
        // resolve mapping map
        return mapPackageDependencyToMavenGa.stream()
                .map(s -> s.split("=", 2))
                .peek((p) -> { if(p.length != 2) { throw new IllegalArgumentException("Could not parse value"); } })
                // cannot use null values due to https://bugs.openjdk.java.net/browse/JDK-8148463 therefore rely on artificial IGNORE_ARTIFACT
                .collect(Collectors.toMap(a -> Dependency.fromString(a[0]), a -> { if (a[1].equalsIgnoreCase(IGNORE_GAV)) { return IGNORE_ARTIFACT; } String[] mavenGA = a[1].split(":", 2); if(mavenGA.length != 2) { throw new IllegalArgumentException("Could not parse Maven group Id and artifact Id (must be separated by ':')"); } return new DefaultArtifact(mavenGA[0], mavenGA[1], "", "", "", "", null);} ));
    }
    
    /**
     * 
     * @return {@code true} to skip execution of the mojo. Default is {@code false}.
     */
    protected boolean shouldSkip() {
        return false;
    }
    
    @Override 
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (shouldSkip()) {
            return;
        }
        if (skipValidation) {
            getLog().info("Skipping validation");
            return;
        }
        translateLegacyParametersToValidatorParameters();
        try {
            fixWrongDefaultForValidatorParameters();
        } catch (IOException e) {
            getLog().error("Could not fix the default values of validators because retrieving the FileVault validation bundle version failed: " + e.getMessage(), e);
        }
        final Collection<PackageInfo> resolvedDependencies = new LinkedList<>();
        
        // repository structure only defines valid roots
        // https://github.com/apache/jackrabbit-filevault-package-maven-plugin/blob/02a853e64d985f075fe88d19101d7c66d741767f/src/main/java/org/apache/jackrabbit/filevault/maven/packaging/impl/DependencyValidator.java#L51
        try (ValidationMessagePrinter validationHelper = new ValidationMessagePrinter(getLog(), csvReportFile != null ? csvReportFile.toPath() : null)) {
            if (project != null) {
                getLog().debug("Clear markers in " + project.getBasedir());
                validationHelper.clearPreviousValidationMessages(buildContext, project.getBasedir());
            }
            try {
                Collection<String> validRoots = new LinkedList<>();
                for (PackageInfo packageInfo : getPackageInfoFromMavenBasedDependencies(repositoryStructurePackages)) {
                    for (PathFilterSet set : packageInfo.getFilter().getFilterSets()) {
                        validRoots.add(set.getRoot());
                    }
                }
                if (!validRoots.isEmpty()) {
                    ValidatorSettings settings = null;
                    if (validatorsSettings != null) {
                        settings = validatorsSettings.get(AdvancedFilterValidatorFactory.ID);
                    } else {
                        validatorsSettings = new HashMap<>();
                    }
                    if (settings == null) {
                        settings = new ValidatorSettings();
                        settings.addOption(AdvancedFilterValidatorFactory.OPTION_VALID_ROOTS, StringUtils.join(validRoots, ","));
                        validatorsSettings.put(AdvancedFilterValidatorFactory.ID, settings);
                    } else {
                        String oldValidRoots = settings.getOptions().get(AdvancedFilterValidatorFactory.OPTION_VALID_ROOTS);
                        settings.addOption(AdvancedFilterValidatorFactory.OPTION_VALID_ROOTS, oldValidRoots + "," + StringUtils.join(validRoots, ","));
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Could not get meta information for repositoryStructurePackages '"
                        + StringUtils.join(repositoryStructurePackages, ",") + "': " + e.getMessage(), e);
            }
            try {
                resolvedDependencies.addAll(getPackageInfoFromMavenBasedDependencies(dependencies));
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Could not get meta information for dependencies '" + StringUtils.join(dependencies, ",") + "': " + e.getMessage(),
                        e);
            }
            // resolve mapping map
            resolver = new DependencyResolver(DefaultRepositoryRequest.getRepositoryRequest(session, project), repositorySystem,
                    resolutionErrorHandler, resolveMap(mapPackageDependencyToMavenGa), resolvedDependencies, getLog());
            doExecute(validationHelper);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not create/write to CSV File", e);
        }
    }

    private Collection<PackageInfo> getPackageInfoFromMavenBasedDependencies(Collection<MavenBasedPackageDependency> dependencies) throws IOException {
        Collection<PackageInfo> packageInfos = new LinkedList<>();
        // try to resolve from project artifacts (in case a project is given)
        MavenBasedPackageDependency.resolve(project, getLog(), dependencies);
        for (MavenBasedPackageDependency dependency : dependencies) {
            // check if resolution was successful
            if (dependency.getInfo() != null) {
                packageInfos.add(dependency.getInfo());
            }
        }
        return packageInfos;
    }

    private void translateLegacyParametersToValidatorParameters() throws MojoExecutionException {
        if (!failOnDependencyErrors) {
            getLog().warn("Deprecated parameter 'failOnDependencyErrors' used.");

            if (validatorsSettings != null) {
                if (validatorsSettings.containsKey(DependencyValidatorFactory.ID)) {
                    throw new MojoExecutionException("Can not set parameters 'failOnDependencyErrors' and 'validationSettings' for '"
                            + DependencyValidatorFactory.ID + "' at the same time");
                }
            } else {
                validatorsSettings = new HashMap<>();
            }

            // do no fully disable but emit violations with level DEBUG
            ValidatorSettings dependencyValidatorSettings = new ValidatorSettings();
            dependencyValidatorSettings.setDefaultSeverity(ValidationMessageSeverity.DEBUG.name());
            validatorsSettings.put(DependencyValidatorFactory.ID, dependencyValidatorSettings);

            ValidatorSettings filterValidatorSettings = validatorsSettings.containsKey(AdvancedFilterValidatorFactory.ID) ? validatorsSettings.get(AdvancedFilterValidatorFactory.ID) : new ValidatorSettings();
            if (filterValidatorSettings.getOptions().containsKey(AdvancedFilterValidatorFactory.OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES)) {
                throw new MojoExecutionException("Can not set parameters 'failOnDependencyErrors' and 'validationSettings' for '"
                        + DependencyValidatorFactory.ID + "' with option'" +AdvancedFilterValidatorFactory.OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES + "' at the same time");
            }
            filterValidatorSettings.addOption(AdvancedFilterValidatorFactory.OPTION_SEVERITY_FOR_UNCOVERED_ANCESTOR_NODES, ValidationMessageSeverity.DEBUG.toString().toLowerCase(Locale.ROOT));
        }
    }

    // https://issues.apache.org/jira/browse/JCRVLT-564
    // TODO: remove once depending on FileVault 3.5.5 or newer
    private void fixWrongDefaultForValidatorParameters() throws IOException {
        if (getFileVaultValidationBundleVersion().equals(VERSION_3_5_4)) {
            if (validatorsSettings == null) {
                validatorsSettings = new HashMap<>();
            }
            ValidatorSettings packageTypeValidatorSettings = validatorsSettings.get("jackrabbit-packagetype");
            if (packageTypeValidatorSettings == null) {
                packageTypeValidatorSettings = new ValidatorSettings();
                validatorsSettings.put("jackrabbit-packagetype", packageTypeValidatorSettings);
            }
            if (!packageTypeValidatorSettings.getOptions().containsKey(PackageTypeValidatorFactory.OPTION_JCR_INSTALLER_ADDITIONAL_FILE_NODE_PATH_REGEX)) {
                packageTypeValidatorSettings.addOption(PackageTypeValidatorFactory.OPTION_JCR_INSTALLER_ADDITIONAL_FILE_NODE_PATH_REGEX, ".*\\.(config|cfg|cfg\\.json|jar)");
                getLog().info("Overriding wrong default value for validator option '" + PackageTypeValidatorFactory.OPTION_JCR_INSTALLER_ADDITIONAL_FILE_NODE_PATH_REGEX + "' (see https://issues.apache.org/jira/browse/JCRVLT-564)");
            }
        }
    }

    static synchronized Version getFileVaultValidationBundleVersion() throws IOException {
        if (fileVaultValidationBundleVersion == null) {
            URL url = AbstractValidateMojo.class.getClassLoader().getResource("org/apache/jackrabbit/vault/validation/ValidationExecutor.class");
            if (url == null) {
                throw new IllegalStateException("This classloader does not see the ValidationExecutor class from FileVault Validation");
            }
            URLConnection connection =  url.openConnection();
            if (connection instanceof JarURLConnection) {
                fileVaultValidationBundleVersion = Version.parseVersion(((JarURLConnection)connection).getMainAttributes().getValue("Bundle-Version"));
            } else {
                fileVaultValidationBundleVersion = Version.emptyVersion;
            }
        }
        return fileVaultValidationBundleVersion;
    }

    public abstract void doExecute(ValidationMessagePrinter validationHelper) throws MojoExecutionException, MojoFailureException;

    protected Map<String, ValidatorSettings> getValidatorSettingsForPackage(PackageId packageId, boolean isSubPackage) throws MojoFailureException {
        try {
            return getValidatorSettingsForPackage(getLog(), validatorsSettings, packageId, isSubPackage);
        }
        catch (IllegalArgumentException e) {
            throw new MojoFailureException("Invalid value for 'validatorsSettings': " + e.getMessage(), e);
        }
    }

    protected static Map<String, ValidatorSettings> getValidatorSettingsForPackage(Log log, Map<String, ValidatorSettings> validatorsSettings, PackageId packageId, boolean isSubPackage) {
        Map<String, ValidatorSettings> validatorSettingsById = new HashMap<>();
        if (validatorsSettings == null) {
            return validatorSettingsById;
        }
        // from most specific to least specific
        List<ValidatorSettingsKey> sortedKeys = validatorsSettings.keySet().stream().map(ValidatorSettingsKey::fromString).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        
        for (ValidatorSettingsKey key : sortedKeys) {
            if (key.matchesForPackage(packageId, isSubPackage)) {
                ValidatorSettings settings = validatorSettingsById.get(key.getValidatorId());
                if (settings != null) {
                    settings = settings.merge(validatorsSettings.get(key.getKey()));
                } else {
                    settings = validatorsSettings.get(key.getKey());
                }
                validatorSettingsById.put(key.getValidatorId(), settings);
            }
        }
        return validatorSettingsById;
    }

    /** 
     * Comparator on file names (excluding paths) which makes sure that the files named {@code .content.xml} come first. Other file names are ordered lexicographically. 
     */
    static final class DotContentXmlFirstComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            if (Constants.DOT_CONTENT_XML.equals(s1) && Constants.DOT_CONTENT_XML.equals(s2)) {
                return 0;
            } else if (Constants.DOT_CONTENT_XML.equals(s1)) {
                return -1;
            } else if (Constants.DOT_CONTENT_XML.equals(s2)) {
                return 1;
            }
            return s1.compareTo(s2);
        }
    }
}
