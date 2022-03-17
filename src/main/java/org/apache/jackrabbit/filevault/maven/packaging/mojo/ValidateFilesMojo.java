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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.filevault.maven.packaging.impl.DirectoryValidationContext;
import org.apache.jackrabbit.filevault.maven.packaging.impl.ValidationMessagePrinter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.LifecycleNotFoundException;
import org.apache.maven.lifecycle.LifecyclePhaseNotFoundException;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.codehaus.plexus.util.AbstractScanner;
import org.codehaus.plexus.util.Scanner;

/** 
 * Validates individual files with all registered validators. This is only active for incremental builds (i.e. inside m2e)
 * or when mojo "validate-package" is not executed in the current Maven execution.
 * <p>
 * <i>This goal is executed/bound by default for Maven modules of type {@code content-package}.</i>
 * @see <a href="https://jackrabbit.apache.org/filevault-package-maven-plugin/validators.html">Validators</a>
 * @since 1.1.0
 */
@Mojo(name = "validate-files", 
    defaultPhase = LifecyclePhase.PROCESS_TEST_SOURCES, // to make sure it runs after "generate-metadata"
    requiresDependencyResolution = ResolutionScope.COMPILE, 
    threadSafe = true)
public class ValidateFilesMojo extends AbstractValidateMojo {

    //-----
    // Start: Copied from AbstractMetadataPackageMojo
    // -----
    /**
     * The directory that contains the META-INF/vault. Multiple directories can be specified as a comma separated list,
     * which will act as a search path and cause the plugin to look for the first existing directory.
     * <p>
     * This directory is added as fileset to the package archiver before the the {@link #workDirectory}. This means that
     * files specified in this directory have precedence over the one present in the {@link #workDirectory}. For example,
     * if this directory contains a {@code properties.xml} it will not be overwritten by the generated one. A special
     * case is the {@code filter.xml} which will be merged with inline filters if present.
     */
    @Parameter(property = "vault.metaInfVaultDirectory", required = true, defaultValue = "${project.basedir}/META-INF/vault,"
            + "${project.basedir}/src/main/META-INF/vault," + "${project.basedir}/src/main/content/META-INF/vault,"
            + "${project.basedir}/src/content/META-INF/vault")
    File[] metaInfVaultDirectory;

    /**
     * The directory containing the metadata to be packaged up into the content package.
     * Basically containing all files/folders being generated by goal "generate-metadata".
     */
    @Parameter(
            defaultValue = "${project.build.directory}/vault-work",
            required = true)
    File workDirectory;
    
    /**
     * If given validates files built for the given classifier. This modifies the {@link #workDirectory} and appends the suffix {@code -<classifier>} to it.
     */
    @Parameter(property = "vault.classifier")
    protected String classifier = "";

    //-----
    // End: Copied from AbstractMetadataPackageMojo
    // -----
    
    //-----
    // Start: Copied from AbstractSourceAndMetadataPackageMojo
    // -----
    

    /**
     * The directory containing the content to be packaged up into the content
     * package.
     *
     * This property is deprecated; use {@link #jcrRootSourceDirectory} instead.
     */
    @Deprecated
    @Parameter
    private File builtContentDirectory;

    /**
     * The directory that contains the jcr_root of the content. Multiple directories can be specified as a comma separated list,
     * which will act as a search path and cause the plugin to look for the first existing directory.
     */
    @Parameter(property = "vault.jcrRootSourceDirectory", required = true, defaultValue = "${project.basedir}/jcr_root,"
            + "${project.basedir}/src/main/jcr_root," + "${project.basedir}/src/main/content/jcr_root,"
            + "${project.basedir}/src/content/jcr_root," + "${project.build.outputDirectory}")
    private File[] jcrRootSourceDirectory;

    /**
     * The file name patterns to exclude in addition to the ones listed in
     * {@link AbstractScanner#DEFAULTEXCLUDES}. The format of each pattern is described in {@link DirectoryScanner}.
     * The comparison is against the path relative to the according filter root.
     * Since this is hardly predictable it is recommended to use only filename/directory name patterns here 
     * but not take into account file system hierarchies!
     * <p>
     * Each value is either a regex pattern if enclosed within {@code %regex[} and {@code ]}, otherwise an 
     * <a href="https://ant.apache.org/manual/dirtasks.html#patterns">Ant pattern</a>.
     */
    @Parameter(property = "vault.excludes", defaultValue = "**/.vlt,**/.vltignore", required = true)
    protected String[] excludes;
    //-----
    // End: Copied from AbstractSourceAndMetadataPackageMojo
    // -----

    @Component
    protected LifecycleExecutor lifecycleExecutor;

    private static final String PLUGIN_KEY = "org.apache.jackrabbit:filevault-package-maven-plugin";

    public ValidateFilesMojo() {
    }

    @Override
    protected boolean shouldSkip() {
        final List<String> allGoals;
        if (session != null) {
            allGoals = session.getGoals();
            getLog().debug("Following goals are detected: " + StringUtils.join(allGoals, ", "));
        } else {
            getLog().debug("MavenSession not available. Maybe executed by m2e.");
            allGoals = Collections.emptyList();
        }
        // is another mojo from this plugin called in this maven session later on?
        try {
            if (!buildContext.isIncremental() && isMojoGoalExecuted(lifecycleExecutor, "validate-package", allGoals.toArray(new String[0]))) { // how to detect that "install" contains "package"? how to resolve the given goals?
                getLog().info("Skip this goal as this is not an incremental build and 'validate-package' is executed later on!");
                return true;
            }
        } catch (PluginNotFoundException | PluginResolutionException | PluginDescriptorParsingException | MojoNotFoundException
                | NoPluginFoundForPrefixException | InvalidPluginDescriptorException | PluginVersionResolutionException
                | LifecyclePhaseNotFoundException | LifecycleNotFoundException | PluginManagerException e1) {
            getLog().warn("Could not determine plugin executions", e1);
        }
        return false;
    }

    @Override
    public void doExecute(ValidationMessagePrinter validationHelper) throws MojoExecutionException, MojoFailureException {
        try {
            File metaInfoVaultSourceDirectory = AbstractMetadataPackageMojo.getMetaInfVaultSourceDirectory(metaInfVaultDirectory, getLog());
            File metaInfRootDirectory = null;
            if (metaInfoVaultSourceDirectory != null) {
                metaInfRootDirectory = metaInfoVaultSourceDirectory.getParentFile();
            }
            File generatedMetaInfRootDirectory = new File(AbstractMetadataPackageMojo.getWorkDirectory(getLog(), false, workDirectory, classifier), Constants.META_INF);
            getLog().info("Validate files in generatedMetaInfRootDirectory " + getProjectRelativeFilePath(generatedMetaInfRootDirectory.toPath()) + " and metaInfRootDir " + getProjectRelativeFilePath(generatedMetaInfRootDirectory.toPath()));
            ValidationContext context = new DirectoryValidationContext(buildContext.isIncremental(), generatedMetaInfRootDirectory, metaInfRootDirectory, resolver, getLog());
            ValidationExecutor executor = validationExecutorFactory.createValidationExecutor(context, false, false, getEffectiveValidatorSettingsForPackage(context.getProperties().getId(), false));
            if (executor == null) {
                throw new MojoExecutionException("No registered validators found!");
            }
            validationHelper.printUsedValidators(getLog(), executor, context, true);
            if (metaInfRootDirectory != null) {
                validateDirectoryRecursively(validationHelper, executor, metaInfRootDirectory.toPath(), true);
            }
            validateDirectoryRecursively(validationHelper, executor, generatedMetaInfRootDirectory.toPath(), true);
            File jcrSourceDirectory = AbstractSourceAndMetadataPackageMojo.getJcrSourceDirectory(jcrRootSourceDirectory, builtContentDirectory, getLog());
            if (jcrSourceDirectory != null) {
                validateDirectoryRecursively(validationHelper, executor, jcrSourceDirectory.toPath(), false);
            }
            validationHelper.printMessages(executor.done(), buildContext, project.getBasedir().toPath());
        } catch (IOException | ConfigurationException e) {
            throw new MojoFailureException("Could not execute validation", e);
        }
        validationHelper.failBuildInCaseOfViolations(failOnValidationWarnings);
    }

    private void validateDirectoryRecursively(ValidationMessagePrinter validationHelper, ValidationExecutor executor, Path baseDir, boolean isMetaInf) {
        Scanner scanner = buildContext.newScanner(baseDir.toFile());
        // make sure filtering does work equally as within the package goal
        scanner.setExcludes(excludes);
        scanner.addDefaultExcludes();
        scanner.scan();
        getLog().info("Scanning baseDir " + getProjectRelativeFilePath(baseDir) + "...");
        SortedSet<Path> sortedFileAndFolderNames = sortAndEnrichFilesAndDirectories(baseDir, scanner.getIncludedFiles(), scanner.getIncludedDirectories());
        
        for (Path fileOrFolder : sortedFileAndFolderNames) {
            getLog().info("Scanning path " + getProjectRelativeFilePath(baseDir.resolve(fileOrFolder)) + "...");
            if (Files.isDirectory(baseDir.resolve(fileOrFolder))) {
                validateDirectory(validationHelper, executor, baseDir, isMetaInf, fileOrFolder);
            } else {
                validateFile(validationHelper, executor, baseDir, isMetaInf, fileOrFolder);
            }
        }
    }

    /**
     * Sorts the given files and directories with {@link ParentAndDotContentXmlFirstComparator}.
     * In addition adds all potentially relevant (parent) node definitions. 
     * That is
     * <ul>
     * <li>sibling {@code .content.xml} files</li>
     * <li>{@code .content.xml} below {@code .dir} suffixed directories</li>
     * <li>parent directories</li>
     * </ul>
     * @param baseDir
     * @param files
     * @param directories
     * @return the sorted set of files/directories
     */
    static SortedSet<Path> sortAndEnrichFilesAndDirectories(Path baseDir, String[] files, String[] directories) {
        // first sort by segments
        NavigableSet<Path> paths = new TreeSet<>(new ParentAndDotContentXmlFirstComparator());
        for (String file : files) {
            paths.add(Paths.get(file));
        }
        for (String directory : directories) {
            paths.add(Paths.get(directory));
        }
        // start with longest path first
        Iterator<Path> pathIterator = paths.descendingIterator();
        Set<Path> additionalPaths = new HashSet<>();
        while (pathIterator.hasNext()) {
            Path path = pathIterator.next();
            // add in addition all potentially relevant parent node definitions
            Path parent = path.getParent();
            if (parent != null) {
                if (!paths.contains(parent) && !additionalPaths.contains(parent) && Files.isDirectory(baseDir.resolve(parent))) {
                    additionalPaths.add(parent);
                }
                Path parentContentXml = parent.resolve(Constants.DOT_CONTENT_XML);
                if (!paths.contains(parentContentXml) && !additionalPaths.contains(parentContentXml) && Files.exists(baseDir.resolve(parentContentXml))) {
                    additionalPaths.add(parentContentXml);
                }
                // and the node definition for https://jackrabbit.apache.org/filevault/vaultfs.html#Extended_File_aggregates
                Path extendedFileAggregateContentXml = parent.resolve(path.getFileName().toString() + ".dir").resolve(Constants.DOT_CONTENT_XML);
                if (!paths.contains(extendedFileAggregateContentXml) && !additionalPaths.contains(extendedFileAggregateContentXml) && Files.exists(baseDir.resolve(extendedFileAggregateContentXml))) {
                    additionalPaths.add(parentContentXml);
                }
            }
        }
        paths.addAll(additionalPaths);
        return paths;
    }

    private void validateFile(ValidationMessagePrinter validationHelper, ValidationExecutor executor, Path baseDir, boolean isMetaInf, Path relativeFile) {
        Path absoluteFile = baseDir.resolve(relativeFile);
        validationHelper.clearPreviousValidationMessages(buildContext, absoluteFile.toFile());
        getLog().debug("Validating file " + getProjectRelativeFilePath(absoluteFile) + "...");
        try (InputStream input = Files.newInputStream(absoluteFile)) {
            validateInputStream(validationHelper, executor, input, baseDir, isMetaInf, relativeFile);
        } catch (FileNotFoundException e) {
            getLog().error("Could not find file " + getProjectRelativeFilePath(absoluteFile), e);
        } catch (IOException e) {
            getLog().error("Could not validate file " + getProjectRelativeFilePath(absoluteFile), e);
        }
    }

    private void validateDirectory(ValidationMessagePrinter validationHelper, ValidationExecutor executor, Path baseDir, boolean isMetaInf, Path relativeFolder) {
        Path absoluteFolder = baseDir.resolve(relativeFolder);
        validationHelper.clearPreviousValidationMessages(buildContext, absoluteFolder.toFile());
        getLog().debug("Validating directory " + getProjectRelativeFilePath(absoluteFolder) + "...");
        try {
            validateInputStream(validationHelper, executor, null, baseDir, isMetaInf, relativeFolder);
        } catch (IOException e) {
            getLog().error("Could not validate directory " + getProjectRelativeFilePath(absoluteFolder), e);
        }
    }

    private void validateInputStream(ValidationMessagePrinter validationHelper, ValidationExecutor executor, InputStream input, Path baseDir, boolean isMetaInf, Path relativeFile) throws IOException {
        final Collection<ValidationViolation> messages;
        if (isMetaInf) {
            messages = executor.validateMetaInf(input, relativeFile, baseDir);
        } else {
            messages = executor.validateJcrRoot(input, relativeFile, baseDir);
        }
        validationHelper.printMessages(messages, buildContext, project.getBasedir().toPath());
    }

    /**
     * Checks if a certain goal is executed at some point in time in the same Maven Session
     * @param lifecycleExecutor
     * @param mojoGoal
     * @param goals
     * @return
     * @throws PluginNotFoundException
     * @throws PluginResolutionException
     * @throws PluginDescriptorParsingException
     * @throws MojoNotFoundException
     * @throws NoPluginFoundForPrefixException
     * @throws InvalidPluginDescriptorException
     * @throws PluginVersionResolutionException
     * @throws LifecyclePhaseNotFoundException
     * @throws LifecycleNotFoundException
     * @throws PluginManagerException
     * @see <a href="https://github.com/apache/maven/blob/master/maven-core/src/main/java/org/apache/maven/lifecycle/DefaultLifecycleExecutor.java">DefaultLifecycleExecutor</a>
     */
    private boolean isMojoGoalExecuted(LifecycleExecutor lifecycleExecutor, String mojoGoal, String... goals) throws PluginNotFoundException, PluginResolutionException, PluginDescriptorParsingException, MojoNotFoundException, NoPluginFoundForPrefixException, InvalidPluginDescriptorException, PluginVersionResolutionException, LifecyclePhaseNotFoundException, LifecycleNotFoundException, PluginManagerException {
        if (goals.length == 0) {
            return false;
        }
        
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            MavenExecutionPlan executionPlan = lifecycleExecutor.calculateExecutionPlan(session, goals);
            for (MojoExecution mojoExecution : executionPlan.getMojoExecutions()) {
                if (isMojoGoalExecuted(mojoExecution, mojoGoal)) {
                    return true;
                }
                lifecycleExecutor.calculateForkedExecutions(mojoExecution, session);
                // also evaluate forked execution goals
                if (mojoExecution.getForkedExecutions().values().stream().flatMap(Collection::stream).anyMatch( t -> isMojoGoalExecuted(t, mojoGoal))) {
                    return true;
                }
            }
            return false;
        } finally {
            // restore old classloader as calculate execution plan modifies it
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }
    
    private static boolean isMojoGoalExecuted(MojoExecution mojoExecution, String mojoGoal) {
        if (PLUGIN_KEY.equals(mojoExecution.getPlugin().getKey()) && mojoGoal.equals(mojoExecution.getGoal())) {
            return true;
        }
        return false;
    }
    

    /** 
     * Comparator on paths which makes sure that the parent directories come first, then a file in the parent directory called {@code .content.xml} 
     * and then all other child directories and files ordered lexicographically. 
     */
    static final class ParentAndDotContentXmlFirstComparator implements Comparator<Path> {
        private final DotContentXmlFirstComparator dotXmlFirstComparator;
        
        
        public ParentAndDotContentXmlFirstComparator() {
            super();
            this.dotXmlFirstComparator = new DotContentXmlFirstComparator();
        }

        @Override
        public int compare(Path s1, Path s2) {
            if (s1.getNameCount() < s2.getNameCount()) {
                return -1;
            } else if (s1.getNameCount() > s2.getNameCount()) {
                return 1;
            } else {
                if (s1.getParent() != null && s1.getParent().equals(s2.getParent())) {
                    return dotXmlFirstComparator.compare(s1.getFileName().toString(), s2.getFileName().toString());
                } else {
                    return s1.compareTo(s2);
                }
            }
        }
    }
}
