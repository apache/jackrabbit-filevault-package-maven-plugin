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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.animal_sniffer.ClassListBuilder;
import org.codehaus.mojo.animal_sniffer.SignatureChecker;
import org.codehaus.mojo.animal_sniffer.maven.MavenLogger;
import org.codehaus.mojo.animal_sniffer.maven.Signature;

/**
 * Maven goal which checks the embedded libraries against a defined
 * signature. Based on the Animal Sniffer project.
 */
@Mojo(
        name = "check-signature",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class CheckSignatureMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    /**
     * list of embedded bundles
     */
    @Parameter
    private Embedded[] embeddeds = new Embedded[0];

    /**
     * Defines whether to fail the build when an embedded artifact is not
     * found in the project's dependencies
     */
    @Parameter(property = "vault.failOnMissingEmbed", defaultValue = "false", required = true)
    private boolean failOnMissingEmbed;


    /**
     */
    @Component
    private ArtifactFactory artifactFactory;

    /**
     * Project classpath.
     */
    @Parameter(property = "project.compileClasspathElements", required = true, readonly = true)
    private List classpathElements;

    /**
     * Class names to ignore signatures for (wildcards accepted).
     */
    @Parameter
    private String[] ignores;

    /**
     */
    @Parameter(property = "localRepository", readonly = true)
    private ArtifactRepository localRepository;

    /**
     */
    @Component
    private ArtifactResolver resolver;

    /**
     * Signature module to use.
     */
    @Parameter
    private Signature signature;

    /**
     * If true, skip the signature check.
     */
    @Parameter(property = "vault.checksignature.skip", defaultValue="false")
    private boolean skipCheckSignature;

    /**
     * List of packages defined in the application.
     */
    private Set<String> buildPackageList() throws IOException {
        ClassListBuilder plb = new ClassListBuilder(new MavenLogger(getLog()));
        for (Object classpathElement : classpathElements) {
            String path = (String) classpathElement;
            plb.process(new File(path));
        }
        return plb.getPackages();
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipCheckSignature) {
            getLog().info("Skipping signature check.");
            return;
        }
        if (signature == null) {
            getLog().info("No signature defined. Skipping signature check.");
            return;
        }
        
        try {
            getLog().info("Checking unresolved references to " + signature);

            org.apache.maven.artifact.Artifact a = signature.createArtifact(artifactFactory);

            resolver.resolve(a, project.getRemoteArtifactRepositories(), localRepository);
            // just check code from this module
            final Set<String> ignoredPackages = buildPackageList();

            if (ignores != null) {
                for (String ignore : ignores) {
                    if (ignore == null) {
                        continue;
                    }
                    ignoredPackages.add(ignore.replace('.', '/'));
                }
            }

            if (getLog().isDebugEnabled()) {
                getLog().debug(ignoredPackages.toString());
            }

            final SignatureChecker signatureChecker = new SignatureChecker(new FileInputStream(a.getFile()),
                    ignoredPackages, new MavenLogger(getLog()));
            signatureChecker.setCheckJars(true);
            signatureChecker.setSourcePath(Collections.singletonList(new File(project.getBuild().getSourceDirectory())));
            signatureChecker.process(getEmbeddeds().toArray(new File[0]));

            if (signatureChecker.isSignatureBroken()) {
                throw new MojoFailureException(
                        "Signature errors found. Verify them and put @IgnoreJRERequirement on them.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to check signatures", e);
        } catch (AbstractArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to obtain signature: " + signature, e);
        }
    }

    private Collection<File> getEmbeddeds() throws MojoFailureException {
        Set<File> files = new HashSet<File>();
        for (Embedded emb : embeddeds) {
            final Collection<Artifact> artifacts = emb.getMatchingArtifacts(project);
            if (artifacts.isEmpty()) {
                if (failOnMissingEmbed) {
                    throw new MojoFailureException(
                            "Embedded artifact specified "
                                    + emb
                                    + ", but no matching dependency artifact found. Add the missing dependency or fix the embed definition.");
                } else {
                    getLog().warn("No matching artifacts for " + emb);
                    continue;
                }
            }

            for (Artifact artifact : artifacts) {
                final File source = artifact.getFile();
                files.add(source);
            }
        }
        return files;
    }
}
