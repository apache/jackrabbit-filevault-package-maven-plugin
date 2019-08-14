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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.VersionRange;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageInfo;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.RepositoryRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.repository.RepositorySystem;

/**
 * Allows to resolve a {@link Dependency} from the underlying Maven repository (first local, then remote).
 */
public class DependencyResolver {

    private final RepositoryRequest repositoryRequest;
    private final RepositorySystem repositorySystem;
    private final ResolutionErrorHandler resolutionErrorHandler;
    private final Map<Dependency, Artifact> mapPackageDependencyToMavenArtifact;

    public DependencyResolver(RepositoryRequest repositoryRequest, RepositorySystem repositorySystem, ResolutionErrorHandler resolutionErrorHandler, Map<Dependency, Artifact> mapPackageDependencyToMavenArtifact) {
        super();
        this.repositoryRequest = repositoryRequest;
        this.repositorySystem = repositorySystem;
        this.resolutionErrorHandler = resolutionErrorHandler;
        this.mapPackageDependencyToMavenArtifact = mapPackageDependencyToMavenArtifact;
    }

    public List<PackageInfo> resolve(Dependency[] dependencies, Collection<PackageInfo> potentiallyResolvedDependencies, Log log) throws IOException {
        List<PackageInfo> resolvedDependencies = new LinkedList<>(potentiallyResolvedDependencies);
        
        // resolve dependencies
        for (Dependency dependency : dependencies) {
            boolean dependencyAlreadyResolved = false;
            // is it already resolved?
            for (PackageInfo resolvedDependency : resolvedDependencies) {
                if (dependency.matches(resolvedDependency.getId())) {
                    log.debug("Dependency is already resolved from project dependencies: " + dependency);
                    dependencyAlreadyResolved = true;
                    break;
                }
            }
            if (!dependencyAlreadyResolved) {
                log.info("Trying to resolve dependency '" + dependency + "' from Maven repository");
                PackageInfo resolvedDependency = resolve(dependency, log);
                if (resolvedDependency != null) {
                    resolvedDependencies.add(resolvedDependency);
                }
            }
        }
        return resolvedDependencies;
    }

    public @CheckForNull PackageInfo resolve(Dependency dependency, Log log) throws IOException {
        // resolving a version range is not supported with Maven API, but only with lower level Aether API (requires Maven 3.5 or newer)
        // https://github.com/eclipse/aether-demo/blob/master/aether-demo-snippets/src/main/java/org/eclipse/aether/examples/FindAvailableVersions.java
        // therefore do an best effort resolve instead
        
        File file = null;
        final String groupId;
        final String artifactId;
        Artifact artifact = mapPackageDependencyToMavenArtifact.get(dependency);
        // is it part of the mapping table?
        if (artifact != null) {
            groupId = artifact.getGroupId();
            artifactId = artifact.getArtifactId();
        } else {
            groupId = dependency.getGroup();
            artifactId = dependency.getName();
        }
        if (dependency.getRange().isLowInclusive()) {
            file = resolve(groupId, artifactId, dependency.getRange().getLow().toString(), log);
        }
        if (file == null && dependency.getRange().isHighInclusive()) {
            file = resolve(groupId, artifactId, dependency.getRange().getHigh().toString(), log);
        }
        if (file == null && VersionRange.INFINITE.equals(dependency.getRange())) {
            file = resolve(groupId, artifactId, Artifact.LATEST_VERSION, log);
        }
        if (file == null) {
            log.warn("Could not resolve dependency from any Maven Repository for dependency " + dependency);
            return null;
        }
        return DefaultPackageInfo.read(file);
    }

    private @CheckForNull File resolve(String groupId, String artifactId, String version, Log log) {
        Artifact artifact = repositorySystem.createArtifact(groupId, artifactId, version, "zip");
        return resolve(artifact, log);
    }

    private @CheckForNull File resolve(Artifact artifact, Log log) {
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
                log.warn("Could not resolve artifact '" + artifact +"': " + e.getMessage());
                log.debug(e);
            }
            return null;
        }
        
    }
}
