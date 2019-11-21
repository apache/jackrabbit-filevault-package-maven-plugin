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

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.filevault.maven.packaging.validator.impl.context.DependencyResolver;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.validation.ValidationExecutorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.impl.AdvancedFilterValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.impl.DependencyValidatorFactory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.repository.DefaultRepositoryRequest;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Common ancestor for all validation related mojos
 */
public abstract class AbstractValidateMojo extends AbstractMojo {

    /** All validator settings in a map. The keys are the validator ids and the values
     * are a complex object of type ValdidatorSettings.
     * An example configuration looks like
     * <pre>
     *  &lt;jackrabbit-filter&gt;
     *      &lt;options&gt;
     *          &lt;
     *      &lt;/options&gt;
     *  &lt;/jackrabbit-filter&gt;
     * </pre>
     */
    @Parameter
    Map<String, ValidatorSettings> validatorsSettings;

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

    /** The Maven project (might be {@code null}) */
    @Parameter(property = "project", readonly = true, required = false)
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
     * Each entry must have the format {@code <group>:<name>=<groupId>:<artifactId>}
     */
    @Parameter(property = "vault.package.dependency.to.maven.ga")
    protected Collection<String> mapPackageDependencyToMavenGa;

    @Component
    protected RepositorySystem repositorySystem;

    @Component
    protected ResolutionErrorHandler resolutionErrorHandler;

    @Component
    protected BuildContext buildContext;

    protected final ValidationExecutorFactory validationExecutorFactory;

    protected final ValidationHelper validationHelper;

    protected DependencyResolver resolver;

    public AbstractValidateMojo() {
        super();
        this.validationExecutorFactory = new ValidationExecutorFactory(this.getClass().getClassLoader());
        this.validationHelper = new ValidationHelper();
    }

    static Map<Dependency, Artifact> resolveMap(Collection<String> mapPackageDependencyToMavenGa) {
        // resolve mapping map
        return mapPackageDependencyToMavenGa.stream()
                .map(s -> s.split("=", 2))
                .peek((p) -> { if(p.length != 2) { throw new IllegalArgumentException("Could not parse value"); } })
                .collect(Collectors.toMap(a -> Dependency.fromString(a[0]), a -> { String[] mavenGA = a[1].split(":", 2); if(mavenGA.length != 2) { throw new IllegalArgumentException("Could not parse Maven group Id and artifact Id (must be separated by ':')"); } return new DefaultArtifact(mavenGA[0], mavenGA[1], "", "", "", "", null);} ));
    }
    @Override 
    public void execute() throws MojoExecutionException, MojoFailureException {
        translateLegacyParametersToValidatorParameters();
        final Collection<PackageInfo> resolvedDependencies = new LinkedList<>();
        if (project != null) {
            validationHelper.clearPreviousValidationMessages(buildContext, project.getBasedir());
        }
        // repository structure only defines valid roots
        // https://github.com/apache/jackrabbit-filevault-package-maven-plugin/blob/02a853e64d985f075fe88d19101d7c66d741767f/src/main/java/org/apache/jackrabbit/filevault/maven/packaging/impl/DependencyValidator.java#L51
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
                resolutionErrorHandler, resolveMap(mapPackageDependencyToMavenGa), resolvedDependencies);
        doExecute();
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
            ValidatorSettings dependencyValidatorSettings = new ValidatorSettings(ValidationMessageSeverity.DEBUG);
            validatorsSettings.put(DependencyValidatorFactory.ID, dependencyValidatorSettings);
        }
    }

    public abstract void doExecute() throws MojoExecutionException, MojoFailureException;
}
