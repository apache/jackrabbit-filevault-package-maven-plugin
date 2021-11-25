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
package org.apache.jackrabbit.filevault.maven.packaging.validator.impl.context;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.filevault.maven.packaging.AbstractValidateMojo;
import org.apache.jackrabbit.filevault.maven.packaging.MavenBasedPackageDependency;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.VersionRange;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageInfo;
import org.apache.jackrabbit.vault.validation.context.AbstractDependencyResolver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.RepositoryRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.repository.RepositorySystem;
import org.jetbrains.annotations.Nullable;

/** Allows to resolve a {@link Dependency} from the underlying Maven repository (first local, then remote). */
public class DependencyResolver extends AbstractDependencyResolver {

    private final RepositoryRequest repositoryRequest;
    private final RepositorySystem repositorySystem;
    private final ResolutionErrorHandler resolutionErrorHandler;
    private final Log log;

    public DependencyResolver(RepositoryRequest repositoryRequest, RepositorySystem repositorySystem,
            ResolutionErrorHandler resolutionErrorHandler, Map<Dependency, Artifact> mapPackageDependencyToMavenArtifact,
            Collection<PackageInfo> knownPackageInfos, Log log) {
        super(knownPackageInfos);
        this.repositoryRequest = repositoryRequest;
        this.repositorySystem = repositorySystem;
        this.resolutionErrorHandler = resolutionErrorHandler;
        this.log = log;
    }

    private @Nullable File resolve(Artifact artifact, Log log) {
        ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest(repositoryRequest);
        resolutionRequest.setArtifact(artifact);
        ArtifactResolutionResult result = repositorySystem.resolve(resolutionRequest);
        if (result.isSuccess()) {
            log.debug("Successfully resolved artifact " + artifact.getArtifactId());
            Artifact resolvedArtifact = result.getArtifacts().iterator().next();
            return resolvedArtifact.getFile();
        } else {
            try {
                resolutionErrorHandler.throwErrors(resolutionRequest, result);
            } catch (ArtifactResolutionException e) {
                // log.warn("Could not resolve artifact '" + artifact +"': " + e.getMessage());
                log.debug(e);
            }
            return null;
        }
    }

    @Override
    public @Nullable PackageInfo resolvePackageInfo(MavenCoordinates mavenCoordinates) throws IOException {
        final Artifact artifact;
        if (mavenCoordinates.getClassifier() != null) {
            artifact = repositorySystem.createArtifactWithClassifier(mavenCoordinates.getGroupId(), mavenCoordinates.getArtifactId(), mavenCoordinates.getVersion(), mavenCoordinates.getPackaging(), mavenCoordinates.getClassifier());
        } else {
            artifact = repositorySystem.createArtifact(mavenCoordinates.getGroupId(), mavenCoordinates.getArtifactId(), mavenCoordinates.getVersion(), mavenCoordinates.getPackaging());
        }
        File file = resolve(artifact, log);
        if (file != null) {
            return DefaultPackageInfo.read(file);
        } else {
            return null;
        }
    }
}
