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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.filevault.maven.packaging.impl.FileValidator;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
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
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.util.AbstractScanner;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import static org.codehaus.plexus.archiver.util.DefaultFileSet.fileSet;

/**
 * Build a content package.
 */
@Mojo(
        name = "package",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class VaultMojo extends AbstractPackageMojo {

    private static final String PACKAGE_TYPE = "zip";

    private static final String PACKAGE_EXT = "." + PACKAGE_TYPE;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

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
     * Set to {@code true} to fail the build in case of files are being contained in the {@code jcrRootSourceDirectory} 
     * which are not covered by the filter rules and therefore would not end up in the package.
     */
    @Parameter(
            property = "vault.failOnUncoveredSourceFiles",
            required = true,
            defaultValue = "false"
    )
    private boolean failOnUncoveredSourceFiles;

    /**
     * Set to {@code false} to not fail the build in case of files/folders being added to the resulting 
     * package more than once. Usually this indicates overlapping with embedded files or overlapping filter rules.
     */
    @Parameter(
            property = "vault.failOnDuplicateEntries",
            required = true,
            defaultValue = "true"
    )
    private boolean failOnDuplicateEntries;

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
     * The archive configuration to use. See <a
     * href="http://maven.apache.org/shared/maven-archiver/index.html">the
     * documentation for Maven Archiver</a>.
     * 
     * All settings related to manifest are not relevant as this gets overwritten by the manifest in {@link AbstractPackageMojo#workDirectory}
     */
    @Parameter
    private MavenArchiveConfiguration archive;

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
    @Parameter(property = "vault.excludes",
               defaultValue="**/.vlt,**/.vltignore",
               required = true)
    private String[] excludes;


    /**
     * Creates a {@link FileSet} for the archiver
     * @param directory the directory
     * @param prefix the prefix
     * @return the fileset
     */
    @Nonnull
    private FileSet createFileSet(@Nonnull File directory, @Nonnull String prefix) {
        return createFileSet(directory, prefix, null);
    }

    /**
     * Creates a {@link FileSet} for the archiver
     * @param directory the directory
     * @param prefix the prefix
     * @param additionalExcludes excludes
     * @return the fileset
     */
    @Nonnull
    private FileSet createFileSet(@Nonnull File directory, @Nonnull String prefix, List<String> additionalExcludes) {
        List<String> excludes = new LinkedList<>(Arrays.asList(this.excludes));
        if (additionalExcludes != null) {
            excludes.addAll(additionalExcludes);
        }
        return fileSet(directory)
                .prefixed(prefix)
                .includeExclude(null, excludes.toArray(new String[0]))
                .includeEmptyDirs(true);
    }

    /**
     * Executes this mojo
     */
    @Override
    public void execute() throws MojoExecutionException {
        final File finalFile = new File(outputDirectory, finalName + PACKAGE_EXT);

        try {
            // find the meta-inf source directory
            File metaInfDirectory = getMetaInfDir();
            // find the source directory
            final File jcrSourceDirectory;
            if (builtContentDirectory != null) {
                getLog().warn("The 'builtContentDirectory' is deprecated. Please use the new 'jcrRootSourceDirectory' instead.");
                jcrSourceDirectory = builtContentDirectory;
            } else {
                jcrSourceDirectory = getFirstExistingDirectory(jcrRootSourceDirectory);
            }
            if (jcrSourceDirectory != null) {
                getLog().info("packaging content from " + jcrSourceDirectory.getPath());
            }

            // retrieve filters
            Filters filters = loadFilterFile();
            Map<String, File> embeddedFiles = getEmbeddedFilesMap();

            ContentPackageArchiver contentPackageArchiver = new ContentPackageArchiver();
            if (failOnDuplicateEntries) {
                contentPackageArchiver.setDuplicateBehavior(Archiver.DUPLICATES_FAIL);
            }
            contentPackageArchiver.setIncludeEmptyDirs(true);
            if (metaInfDirectory != null) {
                // first add the metadata from the metaInfDirectory (they should take precedence over the generated ones from workDirectory, 
                // except for the filter.xml, which should always come from the work directory)
                contentPackageArchiver.addFileSet(createFileSet(metaInfDirectory, "META-INF/vault/", Collections.singletonList("filter.xml")));
            }
            // then add all files from the workDirectory (they might overlap with the ones from metaInfDirectory, therefore only add the non-conflicting ones)
            Collection<File> workDirectoryFilesNotYetExistingInArchive = getUncoveredFiles(workDirectory, "", contentPackageArchiver.getFiles().keySet(), Collections.singletonList("META-INF/MANIFEST.MF"));
            for (File workDirectoryFile : workDirectoryFilesNotYetExistingInArchive) {
                contentPackageArchiver.addFile(workDirectoryFile, workDirectory.toPath().relativize(workDirectoryFile.toPath()).toString());
            }
            
            // add embedded files
            for (Map.Entry<String, File> entry : embeddedFiles.entrySet()) {
                contentPackageArchiver.addFile(entry.getValue(), entry.getKey());
            }
            
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

                        boolean isFilterRootDirectory = true;
                        File rootDirectory = new File(jcrSourceDirectory, relPath);

                        // traverse the ancestors until we find a existing directory (see CQ-4204625)
                        while ((!rootDirectory.exists() || !rootDirectory.isDirectory())
                                && !jcrSourceDirectory.equals(rootDirectory) && !fullCoverage.isFile()) {
                            rootDirectory = rootDirectory.getParentFile();
                            relPath = StringUtils.chomp(relPath, "/");
                            fullCoverage = new File(rootDirectory, relPath + ".xml");
                            isFilterRootDirectory = false;
                        }

                        // either parent node was covered by a full coverage aggregate
                        if (fullCoverage.isFile()) {
                            rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath + ".xml");
                            contentPackageArchiver.addFile(fullCoverage, rootPath);
                        } else {
                            // or a simple folder containing a ".content.xml"
                            rootPath = FileUtils.normalize(JCR_ROOT + prefix + relPath);
                            // is the folder the filter root?
                            if (isFilterRootDirectory) {
                                // then just include the full folder
                                contentPackageArchiver.addFileSet(createFileSet(rootDirectory, rootPath + "/"));
                            } else {
                                // otherwise, make sure to not add child directories which are not direct ancestors of the filter roots
                                contentPackageArchiver.addFileSet(createFileSet(rootDirectory, rootPath + "/",
                                        Collections.singletonList("%regex[^(?!\\.content\\.xml).*]")));
                            }
                        }
                    }
                }

                // check for for duplicates in the content package
                if (failOnDuplicateEntries) {
                    try {
                        for (ResourceIterator iter = contentPackageArchiver.getResources(); iter.hasNext();) {
                            iter.next();
                        }
                    } catch (ArchiverException e) {
                        // this is most probably a duplicate exception
                        // since there is no dedicated exception check if the message starts with "Duplicate file"
                        if (e.getMessage() != null && e.getMessage().startsWith("Duplicate file")) {
                            throw new MojoFailureException("Found duplicate files in content package, most probably you have overlapping filter roots " +
                                    "or you embed a file which is already there in 'jcrRootSourceDirectory'. For details check the nested exception!", e);
                        } else {
                            throw e;
                        }
                    }
                }

                // check for uncovered files
                Collection<File> uncoveredFiles = getUncoveredFiles(jcrSourceDirectory, JCR_ROOT + prefix, contentPackageArchiver.getFiles().keySet(), null);
                if (!uncoveredFiles.isEmpty()) {
                    for (File uncoveredFile : uncoveredFiles) {
                        getLog().warn("File " + uncoveredFile + " not covered by a filter rule and therefore not contained in the resulting package");
                    }
                    if (failOnUncoveredSourceFiles) {
                        throw new MojoFailureException("The following files are not covered by a filter rule: \n" + StringUtils.join(uncoveredFiles.iterator(), ",\n"));
                    }
                }
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
                            // ArchiveEntry.name always contains platform-dependent separators, convert to forwards slashes as separator
                            String sanitizedFileName = FilenameUtils.separatorsToUnix(entry.getName());
                            fileValidator.lookupIndexDefinitionInArtifact(in, sanitizedFileName);
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
            mavenArchiver.createArchive(null, project, getMavenArchiveConfiguration(getManifestFile()));

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
     * 
     * @param sourceDirectory
     * @param prefix
     * @param entryNames
     * @param additionalExcludes
     * @return the absolute file names in the source directory which are not already listed in {@code entryNames}.
     */
    private Collection<File> getUncoveredFiles(final File sourceDirectory, final String prefix, final Collection<String> entryNames,
                                               List<String> additionalExcludes) {
        /*
         *  similar method as in {@link org.codehaus.plexus.components.io.resources.PlexusIoFileResourceCollection#getResources();}
         */
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDirectory);
        List<String> excludes = new LinkedList<>(Arrays.asList(this.excludes));
        if (additionalExcludes != null) {
            excludes.addAll(additionalExcludes);
        }
        scanner.setExcludes(excludes.toArray(new String[0]));
        scanner.addDefaultExcludes();
        scanner.scan();
        return getUncoveredFiles(sourceDirectory, scanner.getIncludedFiles(), prefix, entryNames);
    }

    private Collection<File> getUncoveredFiles(final File sourceDirectory, final String[] relativeSourceFileNames, final String prefix, final Collection<String> entryNames) {
        Collection<File> uncoveredFiles = new ArrayList<>();
        for (String relativeSourceFileName : relativeSourceFileNames) {
            if (!entryNames.contains(prefix + relativeSourceFileName)) {
                uncoveredFiles.add(new File(sourceDirectory, relativeSourceFileName));
            }
        }
        return uncoveredFiles;
    }
    
    private MavenArchiveConfiguration getMavenArchiveConfiguration(File manifestFile) {
        if (archive == null) {
            archive = new MavenArchiveConfiguration();

            archive.setAddMavenDescriptor(true);
            archive.setCompress(true);
            archive.setIndex(false);
        }
        // use the manifest being generated beforehand
        archive.setManifestFile(manifestFile);
        
        return archive;
    }
}
