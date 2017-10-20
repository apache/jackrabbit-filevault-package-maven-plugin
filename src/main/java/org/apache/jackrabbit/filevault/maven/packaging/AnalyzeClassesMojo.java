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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import aQute.bnd.osgi.Processor;

/**
 * Maven goal which analyzes the generated class files and generates a usage report
 */
@Mojo(
        name = "analyze-classes",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class AnalyzeClassesMojo extends AbstractEmbeddedsMojo {

    /**
     * Location of class files
     */
    @Parameter(property = "vault.classesDirectory", defaultValue = "${project.build.outputDirectory}")
    private File sourceDirectory;

    /**
     * Controls if the output should contain the package report.
     */
    @Parameter(property = "vault.showPackageReport", defaultValue = "true")
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

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            getLog().info("Analyzing java package dependencies.");
            ImportPackageBuilder builder = new ImportPackageBuilder()
                    .withDependenciesFromProject(project)
                    .withClassFileDirectory(sourceDirectory)
                    .setIncludeUnused(importUnusedPackages)
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
