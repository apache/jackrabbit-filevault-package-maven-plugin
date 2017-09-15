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
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.project.MavenProject;

import org.apache.jackrabbit.filevault.maven.packaging.impl.StringFilterSet;

/**
 * The <code>Embedded</code> class represents an embedded artifact dependency
 * from the project descriptor. Such an embedding is declared in
 * <code>&lt;embedded></code> elements inside the list style
 * <code>&lt;embeddeds></code> element as follows:
 *
 * <pre>
 * &lt;embedded&gt;
 * 	   &lt;groupId&gt;artifact.groupId.pattern&lt;/groupId&gt;
 * 	   &lt;artifactId&gt;artifact.artifactId.pattern&lt;/artifactId&gt;
 * 	   &lt;scope&gt;compile&lt;/scope&gt;
 *     &lt;type&gt;jar&lt;/type&gt;
 *     &lt;classifier&gt;sources&lt;/classifier&gt;
 * 	   &lt;filter&gt;true&lt;/filter&gt;
 * 	   &lt;target&gt;/libs/sling/install&lt;/target&gt;
 * &lt;/embedded&gt;
 * </pre>
 */
public class Embedded {

    /**
     * A group filter string, consisted of one or several comma separated patterns.
     * @parameter type="String"
     */
    private final StringFilterSet groupId = new StringFilterSet();

    /**
     * A artifact filter string, consisted of one or several comma separated patterns.
     * @parameter type="String"
     */
    private final StringFilterSet artifactId = new StringFilterSet();

    /**
     * @parameter
     */
    private ScopeArtifactFilter scope;

    /**
     * @parameter
     * @since 0.0.4
     */
    private String type;

    /**
     * @parameter
     * @since 0.0.4
     */
    private String classifier;

    /**
     * If <code>true</code> a filter entry will be generated for all embedded artifacts.
     * @parameter
     */
    private boolean filter;

    /**
     * Target location.
     * @parameter
     */
    private String target;

    /**
     * Name to use for the artifact in the destination
     * @since 0.0.5
     * @parameter
     */
    private String destFileName;

    private boolean excludeTransitive;

    public void setGroupId(String groupId) {
        this.groupId.addEntries(groupId);
    }

    public void setArtifactId(String artifactId) {
        this.artifactId.addEntries(artifactId);
    }

    public void setScope(String scope) {
        this.scope = new ScopeArtifactFilter(scope);
    }

    public void setAddFilter(boolean filter) {
        this.filter = filter;
    }

    public boolean isFilter() {
        return filter;
    }

    public String getDestFileName() {
        return destFileName;
    }

    public void setDestFileName(String destFileName) {
        this.destFileName = destFileName;
    }

    public void setTarget(String target) {
        // need trailing slash
        if (!target.endsWith("/")) {
            target += "/";
        }

        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public void setExcludeTransitive(boolean excludeTransitive) {
        this.excludeTransitive = excludeTransitive;
    }

    public boolean isExcludeTransitive() {
        return excludeTransitive;
    }

    public List<Artifact> getMatchingArtifacts(final MavenProject project) {

        // get artifacts depending on whether we exclude transitives or not
        final Set<Artifact> deps;
        if (excludeTransitive) {
            // only direct dependencies, transitives excluded
            deps = project.getDependencyArtifacts();
        } else {
            // all dependencies, transitives included
            deps = project.getArtifacts();
        }

        final List<Artifact> matches = new ArrayList<Artifact>();
        for (Artifact artifact : deps) {
            if (groupId.contains(artifact.getGroupId())
                    && artifactId.contains(artifact.getArtifactId())
                    && (scope == null || scope.include(artifact))
                    && (type == null || type.equals(artifact.getType()))
                    && (classifier == null || classifier.equals(artifact.getClassifier()))) {
                matches.add(artifact);
            }
        }
        return matches;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Embedded: ");
        if (groupId != null) {
            builder.append("groupId=").append(groupId).append(",");
        }
        if (artifactId != null) {
            builder.append("artifactId=").append(artifactId).append(",");
        }
        if (scope != null) {
            builder.append("scope=").append(scope).append(",");
        }
        builder.append("filter=").append(filter);
        builder.append(",excludeTransitive=").append(excludeTransitive);
        if (target != null) {
            builder.append(",target=").append(target);
        }
        if (destFileName != null) {
            builder.append(",destFileName=").append(destFileName);
        }
        return builder.toString();
    }
}
