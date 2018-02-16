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

import static org.codehaus.plexus.archiver.util.DefaultFileSet.fileSet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

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
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.util.AbstractScanner;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

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
     */
    @Parameter(property = "vault.excludes",
               defaultValue="**/.vlt,**/.vltignore,**/.DS_Store",
               required = true)
    private String[] excludes;


    /**
     * Creates a {@link FileSet} for the archiver
     * @param directory the directory
     * @param prefix the prefix
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
                .includeExclude(null, excludes.toArray(new String[] {}))
                .includeEmptyDirs(true);
    }

    /**
     * Executes this mojo
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final File vaultDir = getVaultDir();
        final File finalFile = new File(outputDirectory, finalName + PACKAGE_EXT);

        try {
            // find the meta-inf source directory
            File metaInfDirectory = getMetaInfDir();
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

            // retrieve filters
            Filters filters = loadFilterFile();
            Map<String, File> embeddedFiles = getEmbeddedFilesMap();

            ContentPackageArchiver contentPackageArchiver = new ContentPackageArchiver();
            contentPackageArchiver.setIncludeEmptyDirs(true);
            if (metaInfDirectory != null) {
                // ensure that generated filter.xml comes first
                File filterXML = getFilterFile();
                if (filterXML.exists()) {
                    contentPackageArchiver.addFile(filterXML, "META-INF/vault/filter.xml");
                }
                contentPackageArchiver.addFileSet(createFileSet(metaInfDirectory, "META-INF/vault/", null));
            }
            contentPackageArchiver.addFileSet(createFileSet(workDirectory, "", Collections.singletonList("META-INF/MANIFEST.MF")));

            // include content from build only if it exists
            if (jcrSourceDirectory != null && jcrSourceDirectory.exists()) {
                // See GRANITE-16348
                // we want to build a list of all the root directories in the order they were specified in the filter
                // but ignore the roots that don't point to a directory
                List<PathFilterSet> filterSets = filters.getFilterSets();
                if (filterSets.isEmpty()) {
                    contentPackageArchiver.addFileSet(createFileSet(jcrSourceDirectory, FileUtils.normalize(JCR_ROOT + prefix), null));
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
                            contentPackageArchiver.addFileSet(createFileSet(rootDirectory, rootPath + "/", null));
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

    private MavenArchiveConfiguration getMavenArchiveConfiguration(File manifestFile) throws IOException {
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
