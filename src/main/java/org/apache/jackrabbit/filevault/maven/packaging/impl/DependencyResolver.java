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
package org.apache.jackrabbit.filevault.maven.packaging.impl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.filevault.maven.packaging.mojo.AbstractValidateMojo;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.VersionRange;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageInfo;
import org.apache.jackrabbit.vault.validation.context.AbstractDependencyResolver.MavenCoordinates;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Allows to resolve a {@link Dependency} from the underlying Maven repository (first local, then remote). */
public class DependencyResolver implements org.apache.jackrabbit.vault.validation.context.DependencyResolver {

    private final RepositorySystemSession repositorySession;
    private final RepositorySystem repositorySystem;
    private final Log log;

    private final Collection<PackageInfo> packageInfoCache;
    private final Map<Dependency, Artifact> mapPackageDependencyToMavenArtifact;
    private final List<RemoteRepository> repositories;

    public DependencyResolver(RepositorySystemSession repositorySession, RepositorySystem repositorySystem,
            List<RemoteRepository> repositories,
            Map<Dependency, Artifact> mapPackageDependencyToMavenArtifact,
            Collection<PackageInfo> knownPackageInfos, Log log) {
        this.repositorySession = repositorySession;
        this.repositorySystem = repositorySystem;
        this.repositories = repositories;
        this.mapPackageDependencyToMavenArtifact = mapPackageDependencyToMavenArtifact;
        this.log = log;
        this.packageInfoCache = new LinkedList<>(knownPackageInfos);
    }

    // copy from org.apache.jackrabbit.vault.validation.context.AbstractDependencyResolver due to https://issues.apache.org/jira/browse/JCRVLT-769
    // FIXME: remove once https://issues.apache.org/jira/browse/JCRVLT-774 is resolved
    @Override
    public @NotNull Collection<PackageInfo> resolvePackageInfo(@NotNull Dependency[] dependencies, @NotNull Map<PackageId, URI> dependencyLocations) throws IOException {
        List<PackageInfo> packageInfos = new LinkedList<>();
        // resolve dependencies
        for (Dependency dependency : dependencies) {
            PackageInfo packageInfo = null;
            // 1. try to get from cache
            for (PackageInfo packageInfoFromCache : packageInfoCache) {
                if (dependency.matches(packageInfoFromCache.getId())) {
                    log.debug("Dependency is already resolved from project dependencies: " + dependency);
                    packageInfo = packageInfoFromCache;
                }
            }
            // 2. try to resolve via provided dependency location URIs
            if (packageInfo == null) {
                for (Map.Entry<PackageId, URI> dependencyLocation : dependencyLocations.entrySet()) {
                    if (dependency.matches(dependencyLocation.getKey())) {
                        MavenCoordinates coordinates = MavenCoordinates.parse(dependencyLocation.getValue());
                        if (coordinates == null) {
                            log.warn("Unsupported URL scheme for dependency location '" + dependencyLocation.getValue()
                                    + "'");
                        } else {
                            packageInfo = resolvePackageInfo(coordinates);
                        }
                        
                    }
                }
            }
            // 3. try to apply some heuristics
            if (packageInfo == null) {
                packageInfo = resolvePackageInfo(dependency);
            }
            if (packageInfo != null) {
                packageInfos.add(packageInfo);
                if (packageInfoCache.contains(packageInfo)) {
                    packageInfoCache.add(packageInfo);
                }
            }
        }
        return packageInfos;
    }

    private PackageInfo resolvePackageInfo(@NotNull MavenCoordinates mavenCoordinates) throws IOException {
        Artifact artifact = new DefaultArtifact(mavenCoordinates.getGroupId(), mavenCoordinates.getArtifactId(), mavenCoordinates.getClassifier(), mavenCoordinates.getPackaging(), mavenCoordinates.getVersion());
        return resolvePackageInfo(artifact);
    }

    private @Nullable File resolve(Artifact artifact, Log log) {
        ArtifactRequest request = new ArtifactRequest(artifact, repositories, null);
        try {
            ArtifactResult result = repositorySystem.resolveArtifact(repositorySession, request);
            log.debug("Successfully resolved artifact " + artifact.getArtifactId());
            return result.getArtifact().getFile();
        } catch(ArtifactResolutionException e) {
            log.warn("Could not resolve artifact '" + artifact +"': " + e.getMessage());
            return null;
        }
    }

    /**
     * Use some heuristics to map the package dependency to Maven coordinates and try to resolve them then via {@link #resolvePackageInfo(MavenCoordinates)}.
     * @param dependency
     * @return the resolved package info or {@code null}
     * @throws IOException
     */
    protected @Nullable PackageInfo resolvePackageInfo(@NotNull Dependency dependency) throws IOException {
        Artifact artifact = mapPackageDependencyToMavenArtifact.get(new Dependency(dependency.getGroup(), dependency.getName(), null));
        // is it special artifact which is supposed to be ignored?
        if (artifact == AbstractValidateMojo.IGNORE_ARTIFACT) {
            log.info("Ignoring package dependency '" + dependency + "' as it is marked to be ignored.");
            return null;
        }
        // is it not part of the mapping table?
        if (artifact == null) {
            artifact = new DefaultArtifact(dependency.getGroup(), dependency.getName(), "zip", null);
        }
        log.info("Trying to resolve package dependency '" + dependency + "' from Maven artifact '" + artifact + "'");
        // first get available versions
        String version = resolveVersion(artifact, dependency.getRange());
        if (version == null) {
            return null;
        }
        artifact = artifact.setVersion(version);
        PackageInfo info = resolvePackageInfo(artifact);
        if (info == null) {
            log.warn("Could not resolve Maven artifact '" + artifact + "' in any repository");
            return null;
        }
        return info;
    }

    /** 
     * Resolve a version which is available for the given artifacts coordinates (except for its version)
     * and a package dependency's version range
     * 
     * @param artifact
     * @param dependencyVersionRange
     * @return the resolved version or {@code null} 
     */
    private String resolveVersion(final Artifact artifact, final VersionRange dependencyVersionRange) {
        if (VersionRange.INFINITE.equals(dependencyVersionRange)) {
            VersionRequest request = new VersionRequest(artifact.setVersion(org.apache.maven.artifact.Artifact.LATEST_VERSION), repositories, null);
            try {
                return repositorySystem.resolveVersion(repositorySession, request).getVersion();
            } catch (Exception e) {
                log.warn("Could not resolve version for artifact '" + artifact + "': " + e.getMessage());
            }
        } else {
            VersionRangeRequest request = new VersionRangeRequest(artifact.setVersion(convertToMavenVersionRange(dependencyVersionRange)), repositories, null);
            try {
                Version highestVersion = repositorySystem.resolveVersionRange(repositorySession, request).getHighestVersion();
                if (highestVersion != null) {
                    return highestVersion.toString();
                }
            } catch (Exception e) {
                log.warn("Could not resolve version range for artifact '" + artifact + "': " + e.getMessage());
            }
        }
        return null;
    }

    static String convertToMavenVersionRange(VersionRange packageVersionRange) {
        if (packageVersionRange.getLow() == null && packageVersionRange.getHigh() == null) {
            throw new IllegalArgumentException("Cannot convert infinite version range to Maven version range");
        }
        // always create a range
        StringBuilder versionRange = new StringBuilder();
        versionRange.append(packageVersionRange.isLowInclusive() ? "[" : "(");
        if (packageVersionRange.getLow() != null) {
            versionRange.append(packageVersionRange.getLow());
        }
        versionRange.append(",");
        if (packageVersionRange.getHigh() != null) {
            versionRange.append(packageVersionRange.getHigh());
        }
        versionRange.append(packageVersionRange.isHighInclusive() ? "]" : ")");
        return versionRange.toString();
    }

    protected @Nullable PackageInfo resolvePackageInfo(Artifact artifact) throws IOException {
        File file = resolve(artifact, log);
        if (file != null) {
            return DefaultPackageInfo.read(file);
        } else {
            return null;
        }
    }
}
