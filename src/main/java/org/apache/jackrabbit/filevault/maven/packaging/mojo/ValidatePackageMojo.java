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
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.filevault.maven.packaging.impl.ValidationMessagePrinter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.context.ArchiveValidationContext;
import org.apache.jackrabbit.vault.validation.context.SubPackageInArchiveValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

/**
 * Validates a package (and optionally in addition all attached packages with the given classifiers) with all registered validators.
 * <p>
 * <i>This goal is executed/bound by default for Maven modules of type {@code content-package}.</i>
 * @see <a href="https://jackrabbit.apache.org/filevault-package-maven-plugin/validators.html">Validators</a>
 * @since 1.1.0
 */
@Mojo(
        name = "validate-package", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE, requiresProject = false, threadSafe = true)
public class ValidatePackageMojo extends AbstractValidateMojo {

    /** The main package file to validate. */
    @Parameter(readonly = true, defaultValue = "${project.artifact.file}")
    private File primaryArtifact;

    /**
     * If set to {@code true} skips validating the project's primary artifact but only the ones being attached and having a classifier
     * equal to the one(s) in either {@link #classifiers} and/or {@link #classifier}.
     * @since 1.3.2
     */
    @Parameter()
    private boolean skipPrimaryArtifact;

    /** If set to {@code true} always executes all validators also for all sub packages (recursively). */
    @Parameter(required = true, defaultValue = "false")
    private boolean enforceRecursiveSubpackageValidation;

    /**
     * If set to {@code true} will not validate any sub packages. This settings overwrites the parameter {@code enforceRecursiveSubpackageValidation}. 
     * @since 1.1.2
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean skipSubPackageValidation;

    @Parameter(readonly = true, defaultValue = "${project.attachedArtifacts}")
    private List<Artifact> attachedArtifacts;

    /**
     * The given classifiers are merged with the one from parameter {@link #classifier}.
     * All matching attached artifacts are validated (potentially in addition to the primary artifact depending on parameter {@link #skipPrimaryArtifact}).
     * @since 1.1.4
     */
    @Parameter(property = "vault.classifiers")
    private List<String> classifiers;

    /**
     * The given classifier is merged with the ones from parameter {@link #classifiers}.
     * All matching attached artifacts are validated (potentially in addition to the primary artifact depending on parameter {@link #skipPrimaryArtifact}).
     * @since 1.2.2
     */
    @Parameter(property = "vault.classifier")
    protected String classifier = "";

    public ValidatePackageMojo() {
    }

    @Override
    public void doExecute(ValidationMessagePrinter validationHelper) throws MojoExecutionException, MojoFailureException {
        boolean foundPackage = false;
        if (!skipPrimaryArtifact && primaryArtifact != null && primaryArtifact.isFile()) {
            validatePackage(validationHelper, primaryArtifact.toPath());
            foundPackage = true;
        }
        if (!attachedArtifacts.isEmpty()) {
            List<String> classifiersToCompare = new ArrayList<>();
            if (classifiers != null) {
                classifiersToCompare.addAll(classifiers);
            }
            if (StringUtils.isNotBlank(classifier)) {
                classifiersToCompare.add(classifier);
            }
            for (Artifact attached : attachedArtifacts) {
                // validate attached artifacts with given classifiers
                if (classifiersToCompare.contains(attached.getClassifier())) {
                    validatePackage(validationHelper, attached.getFile().toPath());
                    foundPackage = true;
                }
            }
        }
        if (!foundPackage) {
            getLog().warn("No packages found to validate.");
        }
        validationHelper.failBuildInCaseOfViolations(failOnValidationWarnings);
    }

    private void validatePackage(ValidationMessagePrinter validationHelper, Path file) throws MojoExecutionException, MojoFailureException {
        getLog().info("Start validating package " + getProjectRelativeFilePath(file) + "...");

        // open file to extract the meta data for the validation context
        ArchiveValidationContext context;
        ValidationExecutor executor;
        try (Archive archive = new ZipArchive(file.toFile())) {
            archive.open(true);
            context = new ArchiveValidationContext(archive, file, resolver);
            executor = validationExecutorFactory.createValidationExecutor(context, false, enforceRecursiveSubpackageValidation, getEffectiveValidatorSettingsForPackage(context.getProperties().getId(), false));
            if (executor != null) {
                validationHelper.printUsedValidators(getLog(), executor, context, true);
                validateArchive(validationHelper, archive, file, context, executor);
            } else {
                throw new MojoExecutionException("No registered validators found!");
            }
            getLog().debug("End validating package " + getProjectRelativeFilePath(file) + ".");
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new MojoExecutionException("Could not validate package '" + file + "': " + e.getMessage(), e);
        }
    }

    private void validateArchive(ValidationMessagePrinter validationHelper, Archive archive, Path path, ArchiveValidationContext context,
            ValidationExecutor executor) throws IOException, SAXException, ParserConfigurationException, MojoFailureException {
        validateEntry(validationHelper, archive, archive.getRoot(), Paths.get(""), path, context, executor);
        validationHelper.printMessages(executor.done(), buildContext, path);
    }

    private void validateEntry(ValidationMessagePrinter validationHelper, Archive archive, Archive.Entry entry, Path entryPath, Path packagePath, ArchiveValidationContext context,
            ValidationExecutor executor) throws IOException, SAXException, ParserConfigurationException, MojoFailureException {
        // sort children to make sure that .content.xml comes first!
        List<Archive.Entry> sortedEntryList = new ArrayList<>(entry.getChildren());
        sortedEntryList.sort(Comparator.comparing(Archive.Entry::getName, new DotContentXmlFirstComparator()));
        
        for (Archive.Entry childEntry : sortedEntryList) {
            if (childEntry.isDirectory()) {
                validateInputStream(validationHelper, null, entryPath.resolve(childEntry.getName()), packagePath, context, executor);
                validateEntry(validationHelper, archive, childEntry, entryPath.resolve(childEntry.getName()), packagePath, context, executor);
            } else {
                try (InputStream input = archive.openInputStream(childEntry)) {
                    validateInputStream(validationHelper, input, entryPath.resolve(childEntry.getName()), packagePath, context, executor);
                }
            }
        }
    }

    private void validateInputStream(ValidationMessagePrinter validationHelper, @Nullable InputStream inputStream, Path entryPath, Path packagePath, ArchiveValidationContext context,
            ValidationExecutor executor) throws IOException, SAXException, ParserConfigurationException, MojoFailureException {
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
                // can't use archive.getSubArchive because that holds the wrong metadata
                try (Archive subArchive = new ZipStreamArchive(inputStream)) {
                    subArchive.open(true);
                    // assure this is a real content package
                    if (subArchive.getJcrRoot() == null) {
                        getLog().debug("ZIP entry " + subPackagePath + " is no subpackage as it is lacking the mandatory jcr_root entry");
                    } else {
                        getLog().info("Start validating sub package '" + subPackagePath + "'...");
                        SubPackageInArchiveValidationContext subPackageValidationContext = new SubPackageInArchiveValidationContext(context, subArchive, subPackagePath, resolver);
                        ValidationExecutor subPackageValidationExecutor = validationExecutorFactory
                                .createValidationExecutor(subPackageValidationContext, true, enforceRecursiveSubpackageValidation, getEffectiveValidatorSettingsForPackage(subPackageValidationContext.getProperties().getId(), true));
                        if (subPackageValidationExecutor != null) {
                            validationHelper.printUsedValidators(getLog(), executor, subPackageValidationContext, false);
                            validateArchive(validationHelper, subArchive, subPackagePath, subPackageValidationContext, subPackageValidationExecutor);
                        } else {
                            getLog().debug("Skip validating sub package as no validator is interested in it.");
                        }
                        getLog().info("End validating sub package.");
                    }
                }
            }
        } else {
            messages.add(new ValidationViolation(ValidationMessageSeverity.WARN, "Found unexpected file outside of " + Constants.ROOT_DIR + " and " + Constants.META_INF, entryPath, packagePath, null, 0,0, null));
        }
        validationHelper.printMessages(messages, buildContext, packagePath);
    }

}
