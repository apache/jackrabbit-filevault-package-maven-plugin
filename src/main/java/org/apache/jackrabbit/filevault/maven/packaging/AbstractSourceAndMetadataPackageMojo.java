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

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.AbstractScanner;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Commons ancestor for all mojos dealing with package source files and meta data files
 */
public abstract class AbstractSourceAndMetadataPackageMojo extends AbstractMetadataPackageMojo {

    /**
     * The directory containing the content to be packaged up into the content
     * package.
     *
     * This property is deprecated; use {@link #jcrRootSourceDirectory} instead.
     */
    @Deprecated
    @Parameter
    private File builtContentDirectory;

    /**
     * The directory that contains the jcr_root of the content. Multiple directories can be specified as a comma separated list,
     * which will act as a search path and cause the plugin to look for the first existing directory.
     */
    @Parameter(property = "vault.jcrRootSourceDirectory", required = true, defaultValue = "${project.basedir}/jcr_root,"
            + "${project.basedir}/src/main/jcr_root," + "${project.basedir}/src/main/content/jcr_root,"
            + "${project.basedir}/src/content/jcr_root," + "${project.build.outputDirectory}")
    private File[] jcrRootSourceDirectory;

    /**
     * The file name patterns to exclude in addition to the ones listed in
     * {@link AbstractScanner#DEFAULTEXCLUDES}. The format of each pattern is described in {@link DirectoryScanner}.
     * The comparison is against the path relative to the according filter root.
     * Since this is hardly predictable it is recommended to use only filename/directory name patterns here 
     * but not take into account file system hierarchies!
     * <p>
     * Each value is either a regex pattern if enclosed within {@code %regex[} and {@code ]}, otherwise an 
     * <a href="https://ant.apache.org/manual/dirtasks.html#patterns">Ant pattern</a>.
     */
    @Parameter(property = "vault.excludes", defaultValue = "**/.vlt,**/.vltignore", required = true)
    protected String[] excludes;

    protected File getJcrSourceDirectory() {
        return getJcrSourceDirectory(jcrRootSourceDirectory, builtContentDirectory, getLog());
    }
    
    protected static File getJcrSourceDirectory(File[] jcrRootSourceDirectory, File builtContentDirectory, Log log) {
        final File jcrSourceDirectory;
        if (builtContentDirectory != null) {
            log.warn("The 'builtContentDirectory' is deprecated. Please use the new 'jcrRootSourceDirectory' instead.");
            jcrSourceDirectory = builtContentDirectory;
        } else {
            jcrSourceDirectory = getFirstExistingDirectory(jcrRootSourceDirectory);
        }
        return jcrSourceDirectory;
    }
}
