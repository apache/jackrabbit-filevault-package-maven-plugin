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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.filevault.maven.packaging.impl.ImportPackageBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.PatternExcludesArtifactFilter;

import aQute.bnd.osgi.Processor;

/**
 * Analyzes the generated class files and generates a usage report. This report can be used by the {@code generate-metadata} goal to generate a manifest header out of it.
 */
@Mojo(
        name = "analyze-classes",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class AnalyzeClassesMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    /**
     * Location of class files
     */
    @Parameter(property = "vault.classesDirectory", defaultValue = "${project.build.outputDirectory}")
    private File sourceDirectory;

    /**
     * Controls if the output should contain the package report.
     */
    @Parameter(property = "vault.showPackageReport", defaultValue = "false")
    private boolean showImportPackageReport;

    /**
     * File to store the generated manifest snippet.
     */
    @Parameter(property = "vault.generatedImportPackage", defaultValue = "${project.build.directory}/vault-generated-import.txt")
    private File generatedImportPackage;

    /**
     * Defines if unused packages should be included in the import-package entry if no classes exist in the project
     */
    @Parameter(property = "vault.importUnusedPackages")
    private boolean importUnusedPackages;

    /**
     * Defines a list of libraries in partial maven coordinates that are not used for analysis.
     */
    @Parameter(property = "vault.excludedLibraries")
    private String[] excludedLibraries;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            getLog().info("Analyzing Java package dependencies.");
            List<String> excluded = new ArrayList<>(excludedLibraries.length);
            for (String lib: excludedLibraries) {
                excluded.add(lib.trim());
            }

            ImportPackageBuilder builder = new ImportPackageBuilder()
                    .withFilter(new PatternExcludesArtifactFilter(excluded))
                    .withDependenciesFromProject(project)
                    .withClassFileDirectory(sourceDirectory)
                    .withIncludeUnused(importUnusedPackages)
                    .analyze();

            String report = builder.createExportPackageReport();
            if (showImportPackageReport) {
                getLog().info(report);
            }

            String importParams = Processor.printClauses(builder.getImportParameters());
            FileUtils.write(generatedImportPackage, importParams, "utf-8");

        } catch (IOException e) {
            throw new MojoExecutionException("Error while analysing imports", e);
        }
    }
}
