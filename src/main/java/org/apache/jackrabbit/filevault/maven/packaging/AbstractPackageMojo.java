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
import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public abstract class AbstractPackageMojo extends AbstractMojo {

    static final String JCR_ROOT = "jcr_root/";

    private static final String PROPERTIES_EMBEDDEDFILESMAP_KEY = "vault.embeddedfiles.map";

    private static final String VAULT_DIR = "META-INF/vault";

    public static final String PROPERTIES_FILE = VAULT_DIR + "/properties.xml";

    public static final String FILTER_FILE = VAULT_DIR + "/filter.xml";

    /**
     * The Maven project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Adds a path prefix to all resources useful for shallower source trees.
     */
    @Parameter(property = "vault.prefix")
    String prefix = "";
    
    public void setPrefix(String prefix) {
        if (prefix == null) {
            prefix = "";
        } else if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        this.prefix = prefix;
    }

    /**
     * The directory containing the content to be packaged up into the content
     * package.
     */
    @Parameter(
            defaultValue = "${project.build.directory}/vault-work",
            required = true)
    File workDirectory;

    /**
     * The directory that contains the META-INF/vault. Multiple directories can be specified as a comma separated list,
     * which will act as a search path and cause the plugin to look for the first existing directory.
     * <p>
     * This directory is added as fileset to the package archiver before the the {@link #workDirectory}. This means that
     * files specified in this directory have precedence over the one present in the {@link #workDirectory}. For example,
     * if this directory contains a {@code properties.xml} it will not be overwritten by the generated one. A special
     * case is the {@code filter.xml} which will be merged with inline filters if present.
     */
    @Parameter(
            property = "vault.metaInfVaultDirectory",
            required = true,
            defaultValue =
                    "${project.basedir}/META-INF/vault," +
                    "${project.basedir}/src/main/META-INF/vault," +
                    "${project.basedir}/src/main/content/META-INF/vault," +
                    "${project.basedir}/src/content/META-INF/vault"
    )
    File[] metaInfVaultDirectory;

    /**
     * Defines whether the package is allowed to contain index definitions. This will become the
     * {@code allowIndexDefinitions} property of the properties.xml file.
     */
    @Parameter(
            property = "vault.allowIndexDefinitions",
            defaultValue="false",
            required = true)
    boolean allowIndexDefinitions;

    /**
     * Defines the content package type. this is either 'application', 'content', 'container' or 'mixed'.
     * If omitted, it is calculated automatically based on filter definitions. certain package types imply restrictions,
     * for example, 'application' and 'content' packages are not allowed to contain sub packages or embedded bundles.<br/>
     * Possible values:
     * <ul>
     *   <li>{@code application}: An application package consists purely of application content. It serializes
     *       entire subtrees with no inclusion or exclusion filters. it does not contain any subpackages nor OSGi
     *       configuration or bundles.</li> 
     *   <li>{@code content}: A content package consists only of content and user defined configuration.
     *       It usually serializes entire subtrees but can contain inclusion or exclusion filters. it does not contain
     *       any subpackages nor OSGi configuration or bundles.</li> 
     *   <li>{@code container}: A container package only contains sub packages and OSGi configuration and bundles.
     *       The container package is only used as container for deployment.</li> 
     *   <li>{@code mixed}: Catch all type for a combination of the above.</li> 
     * </ul>
     */
    @Parameter(property = "vault.packageType")
    protected PackageType packageType;

    /**
     * Sets the package type.
     * @param type the string representation of the package type
     * @throws MojoFailureException if an error occurrs
     */
    public void setPackageType(String type) throws MojoFailureException {
        try {
            packageType = PackageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("Invalid package type specified: " + type +".\n" +
                    "Must be empty or one of 'application', 'content', 'container', 'mixed'");
        }
    }

    /**
     * Sets the map of embedded files as project properties as a helper to pass data between the goals
     * @param embeddedFiles map of embedded files.
     */
    void setEmbeddedFilesMap(Map<String, File> embeddedFiles) {
        project.getProperties().put(PROPERTIES_EMBEDDEDFILESMAP_KEY, embeddedFiles);
    }

    /**
     * Reads the map of embedded files from the project properties. This is a helper to pass data between the goals.
     * @return the map of embedded files.
     */
    Map<String, File> getEmbeddedFilesMap() {
        Object value = project.getProperties().get(PROPERTIES_EMBEDDEDFILESMAP_KEY);
        if (value == null) {
            return Collections.emptyMap();
        } else {
            if (value instanceof Map<?,?>) {
                return (Map<String, File>) value;
            } else {
                throw new IllegalStateException("The Maven property " + PROPERTIES_EMBEDDEDFILESMAP_KEY + " is not containing a Map but rather " + value.getClass());
            }
        }
    }

    File getVaultDir() {
        return new File(workDirectory, VAULT_DIR);
    }

    File getMetaInfDir() {
        // find the meta-inf source directory
        File metaInfDirectory = null;
        for (File dir: metaInfVaultDirectory) {
            if (dir.exists() && dir.isDirectory()) {
                metaInfDirectory = dir;
                break;
            }
        }
        if (metaInfDirectory != null) {
            getLog().info("using meta-inf/vault from " + metaInfDirectory.getPath());
        }
        return metaInfDirectory;
    }

    File getManifestFile() {
        return new File(workDirectory, "META-INF/MANIFEST.MF");
    }

    File getFilterFile() {
        return new File(getVaultDir(), "filter.xml");
    }

    Filters loadFilterFile() throws IOException, ConfigurationException {
        // load filters for further processing
        Filters filters = new Filters();
        filters.load(getFilterFile());
        return filters;
    }

 }
