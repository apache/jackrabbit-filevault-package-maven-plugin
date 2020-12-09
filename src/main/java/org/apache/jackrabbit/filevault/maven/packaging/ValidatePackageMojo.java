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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.filevault.maven.packaging.validator.impl.context.ArchiveValidationContextImpl;
import org.apache.jackrabbit.filevault.maven.packaging.validator.impl.context.SubPackageValidationContext;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

/**
 * Validates a package (and optionally in addition all attached packages with the given classifiers) with all registered validators.
 * @see <a href="https://jackrabbit.apache.org/filevault-package-maven-plugin/validators.html">Validators</a>
 */
@Mojo(
        name = "validate-package", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE, requiresProject = false, threadSafe = true)
public class ValidatePackageMojo extends AbstractValidateMojo {

    /** The mainn package file to validate. By default will be the project's main artifact (in case a project is given) */
    @Parameter(property = "vault.packageToValidate", defaultValue = "${project.artifact.file}", required=true)
    private File packageFile;

    /** If set to {@code true} always executes all validators also for all sub packages (recursively). */
    @Parameter(required = true, defaultValue = "false")
    private boolean enforceRecursiveSubpackageValidation;

    /** If set to {@code true} will not validate any sub packages. This settings overwrites the parameter {@code enforceRecursiveSubpackageValidation}. */
    @Parameter(required = true, defaultValue = "false")
    private boolean skipSubPackageValidation;

    @Parameter(readonly = true, defaultValue = "${project.attachedArtifacts}")
    private List<Artifact> attachedArtifacts;

    /**
     * If given validates all attached artifacts with one of the given classifiers in addition
     */
    @Parameter()
    private List<String> classifiers;

    public ValidatePackageMojo() {
    }

    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException {
        try {
            boolean foundPackage = false;
            if (packageFile != null && !packageFile.isDirectory()) {
                validatePackage(packageFile);
                foundPackage = true;
            } 
            if (!attachedArtifacts.isEmpty()) {
                for (Artifact attached : attachedArtifacts) {
                    // validate attached artifacts with given classifiers
                    if (classifiers.contains(attached.getClassifier())) {
                        validatePackage(attached.getFile());
                        foundPackage = true;
                    }
                }
            } 
            if (!foundPackage) {
                getLog().warn("No packages found to validate.");
            }
            validationHelper.failBuildInCaseOfViolations(failOnValidationWarnings);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new MojoExecutionException("Could not validate package '" + packageFile + "': " + e.getMessage(), e);
        }
    }

    private void validatePackage(File file) throws IOException, ParserConfigurationException, SAXException, MojoExecutionException {
        getLog().info("Start validating package " + getProjectRelativeFilePath(file) + "...");

        // open file to extract the meta data for the validation context
        ArchiveValidationContextImpl context;
        ValidationExecutor executor;
        try (Archive archive = new ZipArchive(file)) {
            archive.open(true);
            context = new ArchiveValidationContextImpl(archive, file.toPath(), resolver, getLog());
            executor = validationExecutorFactory.createValidationExecutor(context, false, enforceRecursiveSubpackageValidation, getValidatorSettingsForPackage(context.getProperties().getId(), false));
            if (executor != null) {
                validationHelper.printUsedValidators(getLog(), executor, context, true);
                validateArchive(archive, file.toPath(), context, executor);
            } else {
                throw new MojoExecutionException("No registered validators found!");
            }
            getLog().debug("End validating package " + getProjectRelativeFilePath(file) + ".");
        }
    }

    private void validateArchive(Archive archive, Path path, ArchiveValidationContextImpl context,
            ValidationExecutor executor) throws IOException, SAXException, ParserConfigurationException {
        validateEntry(archive, archive.getRoot(), Paths.get(""), path, context, executor);
        validationHelper.printMessages(executor.done(), getLog(), buildContext, packageFile.toPath());
    }

    private void validateEntry(Archive archive, Archive.Entry entry, Path entryPath, Path packagePath, ArchiveValidationContextImpl context,
            ValidationExecutor executor) throws IOException, SAXException, ParserConfigurationException {
        // sort children to make sure that .content.xml comes first!
        List<Archive.Entry> sortedEntryList = new ArrayList<Archive.Entry>(entry.getChildren());
        sortedEntryList.sort(Comparator.comparing(Archive.Entry::getName, new DotContentXmlFirstComparator()));
        
        for (Archive.Entry childEntry : sortedEntryList) {
            if (childEntry.isDirectory()) {
                validateInputStream(null, entryPath.resolve(childEntry.getName()), packagePath, context, executor);
                validateEntry(archive, childEntry, entryPath.resolve(childEntry.getName()), packagePath, context, executor);
            } else {
                try (InputStream input = archive.openInputStream(childEntry)) {
                    validateInputStream(input, entryPath.resolve(childEntry.getName()), packagePath, context, executor);
                }
            }
        }
    }

    private void validateInputStream(@Nullable InputStream inputStream, Path entryPath, Path packagePath, ArchiveValidationContextImpl context,
            ValidationExecutor executor) throws IOException, SAXException, ParserConfigurationException {
        Collection<ValidationViolation> messages = new LinkedList<>();
        if (entryPath.startsWith(Constants.META_INF)) {
            messages.addAll(executor.validateMetaInf(inputStream, Paths.get(Constants.META_INF).relativize(entryPath), packagePath.resolve(Constants.META_INF)));
        } else if (entryPath.startsWith(Constants.ROOT_DIR)) {
            // strip off jcr_root
            Path relativeJcrPath = Paths.get(Constants.ROOT_DIR).relativize(entryPath);
            messages.addAll(executor.validateJcrRoot(inputStream, relativeJcrPath, packagePath.resolve(Constants.ROOT_DIR)));

            // in case this is a subpackage
            if (inputStream != null && entryPath.getFileName().toString().endsWith(VaultMojo.PACKAGE_EXT) && !skipSubPackageValidation) {
                Path subPackagePath = context.getPackageRootPath().resolve(entryPath);
                getLog().info("Start validating sub package '" + subPackagePath + "'...");
                // can't use archive.getSubPackage because that holds the wrong metadata
                Archive subArchive = new ZipStreamArchive(inputStream);
                subArchive.open(true);
                SubPackageValidationContext subPackageValidationContext = new SubPackageValidationContext(context, subArchive, subPackagePath, resolver, getLog());
                ValidationExecutor subPackageValidationExecutor = validationExecutorFactory
                        .createValidationExecutor(subPackageValidationContext, true, enforceRecursiveSubpackageValidation, getValidatorSettingsForPackage(subPackageValidationContext.getProperties().getId(), true));
                if (subPackageValidationExecutor != null) {
                    validationHelper.printUsedValidators(getLog(), executor, subPackageValidationContext, false);
                    validateArchive(subArchive, subPackagePath, subPackageValidationContext, subPackageValidationExecutor);
                } else {
                    getLog().debug("Skip validating sub package as no validator is interested in it.");
                }
                getLog().info("End validating sub package.");
            }
        } else {
            messages.add(new ValidationViolation(ValidationMessageSeverity.WARN, "Found unexpected file outside of " + Constants.ROOT_DIR + " and " + Constants.META_INF, entryPath, packagePath, null, 0,0, null));
        }
        validationHelper.printMessages(messages, getLog(), buildContext, packageFile.toPath());
    }

}
