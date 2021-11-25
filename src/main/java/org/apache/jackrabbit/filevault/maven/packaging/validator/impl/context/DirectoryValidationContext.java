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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.jackrabbit.filevault.maven.packaging.GenerateMetadataMojo;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageProperties;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

/**
 * Validation context built from files in two directories:
 * <ul>
 *  <li>workDir, the directory in which the {@link GenerateMetadataMojo} has written all automatically generated files (must have name META-INF)
 *  <li>metaInfDir, the directory in which meta inf files have been manually written (must have name META-INF)
 * </ul>
 */
public class DirectoryValidationContext implements ValidationContext {

    private final PackageProperties properties;
    private final DefaultWorkspaceFilter filter;
    private Collection<PackageInfo> resolvedDependencies;
    private final boolean isIncremental;
    
    private static final Path RELATIVE_PROPERTIES_XML_PATH = Paths.get(Constants.VAULT_DIR, Constants.PROPERTIES_XML);

    public DirectoryValidationContext(boolean isIncremental, @NotNull final File generatedMetaInfRootDirectory, final File metaInfRootDirectory, DependencyResolver resolver, @NotNull final Log log) throws IOException, ConfigurationException {
        Path propertiesPath = null;
        if (!Constants.META_INF.equals(generatedMetaInfRootDirectory.getName())) {
            throw new IllegalArgumentException("The workDir must end with 'META-INF' but is '" + generatedMetaInfRootDirectory+"'");
        }
        if (metaInfRootDirectory != null) {
            if (!Constants.META_INF.equals(metaInfRootDirectory.getName())) {
                throw new IllegalArgumentException("The metaInfRootDirectory must end with 'META-INF' but is '" + metaInfRootDirectory+"'");
            }
            propertiesPath = metaInfRootDirectory.toPath().resolve(Constants.VAULT_DIR).resolve(Constants.PROPERTIES_XML);
        }
        if (propertiesPath == null || !Files.exists(propertiesPath)) {
            propertiesPath = generatedMetaInfRootDirectory.toPath().resolve(RELATIVE_PROPERTIES_XML_PATH);
            if (!Files.exists(propertiesPath)) {
                throw new IllegalStateException("No '" + RELATIVE_PROPERTIES_XML_PATH + "' found in either '" +metaInfRootDirectory + "' or '" + generatedMetaInfRootDirectory + "'");
            }
            log.debug("Using '" + RELATIVE_PROPERTIES_XML_PATH + "' from directory " + generatedMetaInfRootDirectory);
        } else {
            log.debug("Using '" + RELATIVE_PROPERTIES_XML_PATH + "' from directory " + metaInfRootDirectory);
        }
        properties = DefaultPackageProperties.fromFile(propertiesPath);
        
        // filter always comes from the workDir
        filter = new DefaultWorkspaceFilter();
        File filterFile = new File(generatedMetaInfRootDirectory, Constants.VAULT_DIR +"/"+Constants.FILTER_XML);
        if (!filterFile.exists()) {
            throw new IllegalStateException("No mandatory '" + Constants.VAULT_DIR +"/"+Constants.FILTER_XML + "' found in " + generatedMetaInfRootDirectory + "'");
        }
        filter.load(filterFile);
        
        this.resolvedDependencies = resolver.resolvePackageInfo(getProperties().getDependencies(), getProperties().getDependenciesLocations());
        this.isIncremental = isIncremental;
    }

    @Override
    public PackageProperties getProperties() {
        return properties;
    }

    @Override
    public WorkspaceFilter getFilter() {
        return filter;
    }

    @Override
    public ValidationContext getContainerValidationContext() {
        return null;
    }

    @Override
    public Path getPackageRootPath() {
        return null;
    }

    @Override
    public Collection<PackageInfo> getDependenciesPackageInfo() {
        return resolvedDependencies;
    }

    @Override
    public boolean isIncremental() {
        return isIncremental;
    }
}
