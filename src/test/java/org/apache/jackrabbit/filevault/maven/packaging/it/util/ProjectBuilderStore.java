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
package org.apache.jackrabbit.filevault.maven.packaging.it.util;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * Helper class managing storage of {@link ProjectBuilder} in extension context
 * store.
 */
final class ProjectBuilderStore {

    private static final Namespace PROJECT_BUILDER_NAMESPACE = Namespace.create(ProjectBuilderExtension.class);

    private ProjectBuilderStore() {
        // static methods only
    }

    /**
     * Get {@link ProjectBuilder} from extension context store.
     * 
     * @param extensionContext Extension context
     * @return ProjectBuilder or null
     */
    public static @Nullable ProjectBuilder getProjectBuilder(@NotNull ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getTestClass().orElse(null);
        if (testClass == null) {
            return null;
        }
        // try to get existing context from current extension context, or any parent
        // extension context (in case of nested tests)
        return Optional.ofNullable(ProjectBuilderStore.getStore(extensionContext).get(testClass, ProjectBuilder.class))
                .orElseGet(() -> extensionContext.getParent().map(ProjectBuilderStore::getProjectBuilder).orElse(null));
    }

    /**
     * Get {@link ProjectBuilder} from extension context store - if it does not exist
     * create a new one and store it.
     * 
     * @param extensionContext Extension context
     * @return ProjectBuilder (never null)
     */
    public static @NotNull ProjectBuilder getOrCreateProjectBuilder(@NotNull ExtensionContext extensionContext) {
        ProjectBuilder projectBuilder = getProjectBuilder(extensionContext);
        if (projectBuilder == null) {
            projectBuilder = new ProjectBuilder();
            storeProjectBuilder(extensionContext, projectBuilder);
        }
        return projectBuilder;
    }

    /**
     * Removes {@link ProjectBuilder} from extension context store (if it exists).
     * 
     * @param extensionContext Extension context
     */
    public static void removeProjectBuildert(@NotNull ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        getStore(extensionContext).remove(testClass);
    }

    /**
     * Store {@link ProjectBuilder} in extension context store.
     * 
     * @param extensionContext Extension context
     * @param aemContext       AEM context
     */
    public static void storeProjectBuilder(@NotNull ExtensionContext extensionContext, @NotNull ProjectBuilder projectBuilder) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        getStore(extensionContext).put(testClass, projectBuilder);
    }

    private static Store getStore(ExtensionContext context) {
        return context.getStore(PROJECT_BUILDER_NAMESPACE);
    }

}
