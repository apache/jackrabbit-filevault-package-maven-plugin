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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.jackrabbit.filevault.maven.packaging.impl.StringFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base class for all kinds of embeds (both OSGi bundles as well as subpackages)
 */
public class SimpleEmbedded {

    /**
     * A group filter string, consists of one or several comma separated patterns.
     */
    private final StringFilterSet groupId = new StringFilterSet();

    /**
     * A artifact filter string, consists of one or several comma separated patterns.
     */
    private final StringFilterSet artifactId = new StringFilterSet();

    private ScopeArtifactFilter scope;

    /**
     * A type filter string, consists of one or several comma separated patterns.
     */
    private StringFilterSet type = new StringFilterSet();

    /**
     * A classifier filter string, consists of one or several comma separated patterns.
     */
    private StringFilterSet classifier = new StringFilterSet();

    /**
     * If {@code true} a filter entry will be generated for all embedded artifacts.
     */
    private boolean filter;

    /**
     * If {@link #filter} is {@code true} and this is {@code true} as well, the filter entry will contain
     * all versions of the same artifact.
     */
    private boolean isAllVersionsFilter;

    private boolean excludeTransitive;
    
    public void setGroupId(String groupId) throws ConfigurationException {
        this.groupId.addEntries(groupId);
    }

    public void setArtifactId(String artifactId) throws ConfigurationException {
        this.artifactId.addEntries(artifactId);
    }

    public void setScope(String scope) {
        this.scope = new ScopeArtifactFilter(scope);
    }

    public void setType(String type) throws ConfigurationException {
        this.type.addEntries(type);
    }

    public void setClassifier(String classifier) throws ConfigurationException {
        this.classifier.addEntries(classifier);
    }

    public void setAddFilter(boolean filter) {
        this.filter = filter;
    }

    public boolean isFilter() {
        return filter;
    }

    public boolean isAllVersionsFilter() {
        return isAllVersionsFilter;
        
    }
    
    public void setExcludeTransitive(boolean excludeTransitive) {
        this.excludeTransitive = excludeTransitive;
    }

    public boolean isExcludeTransitive() {
        return excludeTransitive;
    }
    
    @SuppressWarnings("deprecation")
    public Collection<Artifact> getMatchingArtifacts(final MavenProject project) {

        // get artifacts depending on whether we exclude transitives or not
        final Set<Artifact> deps;
        if (excludeTransitive) {
            // only direct dependencies, transitives excluded
            deps = project.getDependencyArtifacts();
        } else {
            // all dependencies, transitives included
            deps = project.getArtifacts();
        }
        return getMatchingArtifacts(deps);
    }

    public Collection<Artifact> getMatchingArtifacts(final Collection<Artifact> deps) {
        final List<Artifact> matches = new ArrayList<Artifact>();
        for (Artifact artifact : deps) {
            if (groupId.contains(artifact.getGroupId())
                    && artifactId.contains(artifact.getArtifactId())
                    && (scope == null || scope.include(artifact))
                    && (type == null || type.contains(artifact.getType()))
                    && (classifier == null || classifier.contains(artifact.getClassifier()))) {
                matches.add(artifact);
            }
        }
        return matches;
    }

    @NotNull
    public StringBuilder toString(@Nullable StringBuilder builder) {
        if (builder == null) {
            builder = new StringBuilder();
        }
        builder.append("groupId=").append(groupId).append(",");
        builder.append("artifactId=").append(artifactId).append(",");

        if (scope != null) {
            builder.append("scope=").append(scope).append(",");
        }
        if (type != null) {
            builder.append("type=").append(type).append(",");
        }
        if (classifier != null) {
            builder.append("classifier=").append(classifier).append(",");
        }
        builder.append("filter=").append(filter);
        builder.append(",excludeTransitive=").append(excludeTransitive);
        return builder;
    }

    public String toString() {
        return toString(null).toString();
    }
}
