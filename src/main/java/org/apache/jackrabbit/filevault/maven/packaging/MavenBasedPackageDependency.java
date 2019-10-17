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
import java.util.Collection;
import java.util.LinkedList;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.VersionRange;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageInfo;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

/**
 * The {@code Dependency} class represents a dependency to another content package.
 * A dependency consists of a group name, a package name and a version range.
 * <p>
 * A dependency is declared as a {@code <dependency>} element of a list
 * style {@code <dependencies>} element:
 * <pre>
 * &lt;dependency&gt;
 * 	   &lt;group&gt;theGroup&lt;/group&gt;
 * 	   &lt;name&gt;theName&lt;/name&gt;
 * 	   &lt;version&gt;1.5&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 * <p>
 * The dependency can also reference a maven project dependency, this is preferred
 * as it yields to more robust builds.
 * <pre>
 * &lt;dependency&gt;
 * 	   &lt;groupId&gt;theGroup&lt;/groupId&gt;
 * 	   &lt;artifactId&gt;theName&lt;/artifactId&gt;
 * &lt;/dependency&gt;
 * </pre>
 * <p>
 * The {@code versionRange} may be indicated as a single version, in which
 * case the version range has no upper bound and defines the minimal version
 * accepted. Otherwise, the version range defines a lower and upper bound of
 * accepted versions, where the bounds are either included using parentheses
 * {@code ()} or excluded using brackets {@code []}
 */
public class MavenBasedPackageDependency {

    /**
     * The group name, required for package-id references
     */
    private String group;

    /**
     * The group id, required for maven coordinate references
     */
    private String groupId;

    /**
     * The package name, required for package-id references.
     */
    private String name;

    /**
     * The artifact id, required for maven coordinate references
     */
    private String artifactId;

    /**
     * The version range (optional)
     */
    private String version;

    /**
     * Package information of this dependency. only available for maven referenced dependencies after {@link #resolve(MavenProject, Log)}
     */
    private PackageInfo info;

    /**
     * Resolved package dependency. only available after {@link #resolve(MavenProject, Log)}
     */
    private org.apache.jackrabbit.vault.packaging.Dependency dependency;
    
    // default constructor for passing Maven Mojo parameters of that type
    public MavenBasedPackageDependency() {
        
    }

    /**
     * Converts a list of {@link MavenBasedPackageDependency} instances to vault dependencies.
     *
     * @param project the Maven project
     * @param log the Logger
     * @param dependencyList The list of {@link MavenBasedPackageDependency} instances to convert.
     * @return The Vault Packaging Dependency representing the resolved dependencies. Not all given items from the {@code dependencyList} can necessarily be resolved, therefore the the size of the collection might be less than the size of the collection given in {@code dependencyList} 
     * @throws IOException in case meta information could not be read from the project dependency or the 
     * dependency is not a content package.
     */
    public static @Nonnull Collection<Dependency> resolve(final MavenProject project, final Log log, final MavenBasedPackageDependency... dependencyList) throws IOException {
        Collection<Dependency> dependencies = new LinkedList<>();
        for (int i = 0; i < dependencyList.length; i++) {
            Dependency dependency = dependencyList[i].resolve(project, log);
            if (dependency != null) {
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }

    /**
     * Helper method for {@link #toString)} to convert an instance of this
     * class to a Vault Packaging Dependency for easy string conversion.
     * @throws IOException in case meta information could not be read from the project dependency or the 
     * dependency is not a content package.
     */
    @SuppressWarnings("deprecation")
    private @CheckForNull org.apache.jackrabbit.vault.packaging.Dependency resolve(final MavenProject project, final Log log) throws IOException {
        if (!StringUtils.isEmpty(group) || !StringUtils.isEmpty(name)) {
            log.warn("Using package id in dependencies is deprecated. Use Maven coordinates (given via 'groupId' and 'artifactId') instead of '" + group + ":" + name +"'!");
        }
        if (!StringUtils.isEmpty(groupId) && !StringUtils.isEmpty(artifactId)) {
            boolean foundMavenDependency = false;
            if (project != null) {
                for (Artifact a : project.getDependencyArtifacts()) {
                    if (a.getArtifactId().equals(artifactId) && a.getGroupId().equals(groupId)) {
                        // check if file exists and if it points to a real file (might also point to a classes dir)
                        try {
                            readMetaData(a.getFile(), log);
                            foundMavenDependency = true;
                        } catch (IOException e) {
                            // can not resolve name and group
                            log.warn("Could not resolve dependency '" + this + "'", e);
                            return null;
                        }
                        break;
                    }
                }
                if (!foundMavenDependency) {
                    throw new IOException("Specified dependency '" + this + "' was not found among the Maven dependencies of this project!");
                }
            } else {
                log.warn("Dependency '" + this + "' was given via Maven coordinates but there is no Maven project connected which allows to resolve those.");
                return null;
            }
        }
        if (StringUtils.isEmpty(group) || StringUtils.isEmpty(name)) {
            throw new IOException("Specified dependency " + this + " is not qualified (group and name or groupId and artifactId is missing)!");
        }
        VersionRange range = StringUtils.isEmpty(version) ? VersionRange.INFINITE : VersionRange.fromString(version);
        return dependency = new org.apache.jackrabbit.vault.packaging.Dependency(group, name, range);
    }

    public void readMetaData(File file, Log log) throws IOException {
        PackageInfo info;
        if (file.isDirectory()) {
            log.info("Trying to extract package info from folder '" + file + "' as no package is availabe for the given dependency " + this);
            info = DefaultPackageInfo.read(file);
            if (info == null) {
                // fallback to work dir
                File fallbackDirectory = new File(file.getParent(), "vault-work");
                log.info("Trying to extract package info from fallback folder '" + fallbackDirectory + "' as no package is availabe for the given dependency " + this);
                info = DefaultPackageInfo.read(fallbackDirectory);
            }
        } else {
            info = DefaultPackageInfo.read(file);
        }
        if (info != null) {
            PackageId id = info.getId();
            group = id.getGroup();
            name = id.getName();
            if (StringUtils.isEmpty(version)) {
                log.info("No explicit version range given for dependency '" + this+ "'. Using default version range derived from the Maven dependency");
                version = new VersionRange(id.getVersion(), true, null, false).toString();
            }
            this.info = info;
        } else {
            throw new IOException("Dependency at '" + file + "' does not contain mandatory metadata for a content-package");
        }
    }

    /**
     * Returns the package info or {@code null}.
     *
     * @return the info.
     */
    @Nullable
    public PackageInfo getInfo() {
        return info;
    }

    /**
     * Returns the package dependency or {@code null} if not resolved.
     *
     * @return the package dependency.
     */
    @Nullable
    public org.apache.jackrabbit.vault.packaging.Dependency getPackageDependency() {
        return dependency;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Dependency{");
        sb.append("group='").append(group).append('\'');
        sb.append(", groupId='").append(groupId).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", artifactId='").append(artifactId).append('\'');
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
