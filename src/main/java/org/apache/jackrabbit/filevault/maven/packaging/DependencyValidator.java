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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import org.apache.jackrabbit.filevault.maven.packaging.impl.PackageInfo;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;

/**
 * {@code DependencyValidator}...
 */
public class DependencyValidator {

    private List<Dependency> dependencies = new LinkedList<>();

    private DefaultWorkspaceFilter filters;

    private List<String> errors = new LinkedList<String>();

    private Set<String> validRoots = new HashSet<>(Arrays.asList("", "/", "/libs", "/apps", "/etc", "/var", "/tmp", "/content"));

    public DependencyValidator addDependencies(Dependency... dependencies) {
        this.dependencies.addAll(Arrays.asList(dependencies));
        return this;
    }

    public DependencyValidator addRepositoryStructure(Dependency ... structures) {
        for (Dependency dep: structures) {
            PackageInfo info = dep.getInfo();
            if (info == null) {
                String msg = String.format("Dependency '%s' is not using maven coordinates and cannot be used for analysis.", dep.getPackageDependency());
                errors.add(msg);
                continue;
            }
            for (PathFilterSet set : info.getFilter().getFilterSets()) {
                String root = set.getRoot();
                validRoots.add(root);
            }
        }
        return this;
    }

    public DependencyValidator setFilters(DefaultWorkspaceFilter filters) {
        this.filters = filters;
        return this;
    }

    public List<String> getErrors() {
        return errors;
    }

    /**
     * Checks if the filter roots of this package are covered by the dependencies and also checks for colliding roots
     * in the dependencies.
     *
     * @return this validator
     */
    public DependencyValidator validate() {
        // check for overlapping dependency roots
        Map<String, Dependency> roots = new HashMap<String, Dependency>();
        for (Dependency dep: dependencies) {
            PackageInfo info = dep.getInfo();
            if (info == null) {
                String msg = String.format("Dependency '%s' is not using maven coordinates and cannot be used for analysis.", dep.getPackageDependency());
                errors.add(msg);
                continue;
            }
            for (PathFilterSet set : info.getFilter().getFilterSets()) {
                String root = set.getRoot();
                Dependency existing = roots.get(root);
                if (existing != null) {
                    String msg = String.format("Dependency '%s' defines same filter root '%s' as dependency '%s'",
                            dep.getPackageDependency(), root, existing.getPackageDependency());
                    errors.add(msg);
                }
                roots.put(root, dep);
            }
        }
        // check that this filter is covered.
        Set<String> ancestors = new HashSet<String>();
        for (PathFilterSet set: filters.getFilterSets()) {
            if ("cleanup".equals(set.getType())) {
                continue;
            }
            String root = StringUtils.substringBeforeLast(set.getRoot(), "/");
            // ignore well known roots
            if (validRoots.contains(root)) {
                continue;
            }

            // check if this package already contains the ancestor
            if (filters.contains(root)) {
                continue;
            }
            ancestors.add(root);
        }
        for (String root: ancestors) {
            String isCovered = null;
            boolean isContained = false;
            for (Dependency dep: roots.values()) {
                DefaultWorkspaceFilter filter = dep.getInfo().getFilter();
                if (filter.contains(root)) {
                    isContained = true;
                }
                if (filter.covers(root)) {
                    isCovered = dep.getPackageDependency().toString();
                }
            }
            if (!isContained) {
                String msg;
                if (isCovered == null) {
                    msg = String.format("Filter root's ancestor '%s' is not covered by any of the specified dependencies.", root);
                } else {
                    msg = String.format("Filter root's ancestor '%s' is covered by '%s' but excluded by its patterns.", root, isCovered);
                }
                errors.add(msg);
            }
        }
        return this;
    }
}