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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Maven goal which either checks only the formatting of the Docview XML files to comply with the 
 * formatting rules from FileVault or also reformats those files.
 */
@Mojo(
        name = "format-xml",
        defaultPhase = LifecyclePhase.PROCESS_SOURCES
)
public class FormatDocviewXmlMojo extends AbstractMojo {

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
     * Include patterns for files to include. If not set defaults to [**\/*.xml].
     * <p>
     * Note: includes have precedences over excludes.
     * Each pattern can either be an <a href="https://ant.apache.org/manual/dirtasks.html#patterns">Ant-like pattern</a> or
     * a regular expression if it is starting with {@code %regex[} and ending with {@code ]}.
     */
    @Parameter
    protected Set<String> includes = new HashSet<>();

    /**
     * Exclude patterns for files to exclude. If not set defaults to [].
     * <p>
     * Note: includes have precedences over excludes.
     * Each pattern can either be an <a href="https://ant.apache.org/manual/dirtasks.html#patterns">Ant-like pattern</a> or
     * a regular expression if it is starting with {@code %regex[} and ending with {@code ]}.
     */
    @Parameter
    protected Set<String> excludes = new HashSet<>();
    
    /**
     * If set to {@code true} then fails the build if it encounters XML files which don't follow the
     * formatting rules for XML Docview files.
     * If set to {@code false} will reformat all XML Docview files.
     */
    @Parameter(defaultValue = "false")
    private boolean validateOnly;

    @Component
    protected BuildContext buildContext;

    private static final String MSG_MALFORMED_FILE = "Malformed according to DocView format";
    private static final String MSG_COUNTERMEASURE = "Use either your IDE with JCR-VLT integration to sync files with the repository or use vlt-cli to apply the docview format " + 
           "(http://jackrabbit.apache.org/filevault/usage.html).";
 
    public void execute() throws MojoExecutionException, MojoFailureException {
        File jcrSourceDirectory = AbstractSourceAndMetadataPackageMojo.getFirstExistingDirectory(jcrRootSourceDirectory);
        executeInternal(jcrSourceDirectory);
    }
    
    protected void executeInternal(File sourceDirectory)
            throws MojoExecutionException, MojoFailureException {

        if (includes.isEmpty()) {
            includes.add("**/*.xml");
        }

        Log log = getLog();
        List<File> malformedFiles = new LinkedList<>();
        DocViewFormat docViewFormat = new DocViewFormat();
        Scanner directoryScanner = buildContext.newScanner(sourceDirectory);
        directoryScanner.setIncludes(includes.toArray(new String[includes.size()]));
        directoryScanner.setExcludes(excludes.toArray(new String[excludes.size()]));
        directoryScanner.scan();

        for (String subpath : directoryScanner.getIncludedFiles()) {
            File toCheck = new File(sourceDirectory, subpath);
            try {
                if (docViewFormat.format(toCheck, validateOnly)) {
                    if (validateOnly) {
                        // collect all malformed files to emit one failure at the end
                        malformedFiles.add(toCheck);
                        // emit violations for m2e (https://www.eclipse.org/m2e/documentation/m2e-making-maven-plugins-compat.html)
                        buildContext.addMessage(toCheck, 0, 0, MSG_MALFORMED_FILE + ". " +  MSG_COUNTERMEASURE, BuildContext.SEVERITY_ERROR, null);
                        log.error(MSG_MALFORMED_FILE + ":" + toCheck);
                    } else {
                        log.info("Reformatted file '" + toCheck + "'.");
                    }
                } else {
                    buildContext.removeMessages(toCheck);
                }
            } catch (IOException ex) {
                throw new MojoExecutionException("Failed to read " + toCheck, ex);
            }
        }

        if (!malformedFiles.isEmpty()) {
            String message = "Found " + malformedFiles.size() + " files being malformed according to DocView format: Please check error message emitted above." +
                    "\n\n" + MSG_COUNTERMEASURE;
            throw new MojoFailureException(message);
        }
    }
}
