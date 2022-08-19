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
package org.apache.jackrabbit.filevault.maven.packaging.mojo;

import java.io.File;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.Nullable;

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
     * The file name patterns to exclude (in addition to the default ones mentioned at {@link AbstractSourceAndMetadataPackageMojo#addDefaultExcludes}. The format of each pattern is described in {@link org.codehaus.plexus.util.DirectoryScanner}.
     * The comparison is against the path relative to the according filter root.
     * Since this is hardly predictable it is recommended to use only filename/directory name patterns here 
     * but not take into account file system hierarchies!
     * <p>
     * Each value is either a regex pattern if enclosed within {@code %regex[} and {@code ]}, otherwise an 
     * <a href="https://ant.apache.org/manual/dirtasks.html#patterns">Ant pattern</a>.
     */
    @Parameter(property = "vault.excludes", defaultValue = "**/.vlt,**/.vltignore,**/.gitignore", required = true)
    protected Set<String> excludes;

    /**
     * By default certain metadata files are excluded which means they will not be copied into the package. 
     * If you need them for a particular reason you can do that by setting this parameter to {@code false}. This means
     * all files matching the following <a href="https://ant.apache.org/manual/dirtasks.html#patterns">Ant patterns</a> won't be copied by default.
     * <ul>
     * <li>Misc: &#42;&#42;/&#42;~, &#42;&#42;/#&#42;#, &#42;&#42;/.#&#42;, &#42;&#42;/%&#42;%, &#42;&#42;/._&#42;</li>
     * <li>CVS: &#42;&#42;/CVS, &#42;&#42;/CVS/&#42;&#42;, &#42;&#42;/.cvsignore</li>
     * <li>SVN: &#42;&#42;/.svn, &#42;&#42;/.svn/&#42;&#42;</li>
     * <li>GNU: &#42;&#42;/.arch-ids, &#42;&#42;/.arch-ids/&#42;&#42;</li>
     * <li>Bazaar: &#42;&#42;/.bzr, &#42;&#42;/.bzr/&#42;&#42;</li>
     * <li>SurroundSCM: &#42;&#42;/.MySCMServerInfo</li>
     * <li>Mac: &#42;&#42;/.DS_Store</li>
     * <li>Serena Dimension: &#42;&#42;/.metadata, &#42;&#42;/.metadata/&#42;&#42;</li>
     * <li>Mercurial: &#42;&#42;/.hg, &#42;&#42;/.hg/&#42;&#42;</li>
     * <li>GIT: &#42;&#42;/.git, &#42;&#42;/.git/&#42;&#42;</li>
     * <li>Bitkeeper: &#42;&#42;/BitKeeper, &#42;&#42;/BitKeeper/&#42;&#42;, &#42;&#42;/ChangeSet,
     * &#42;&#42;/ChangeSet/&#42;&#42;</li>
     * <li>Darcs: &#42;&#42;/_darcs, &#42;&#42;/_darcs/&#42;&#42;, &#42;&#42;/.darcsrepo,
     * &#42;&#42;/.darcsrepo/&#42;&#42;&#42;&#42;/-darcs-backup&#42;, &#42;&#42;/.darcs-temp-mail
     * </ul>
     *
     * @see org.codehaus.plexus.util.DirectoryScanner#DEFAULTEXCLUDES
     * @since 1.1.0
     */
    @Parameter(defaultValue = "true")
    protected boolean addDefaultExcludes;

    protected @Nullable File getJcrSourceDirectory() {
        return getJcrSourceDirectory(jcrRootSourceDirectory, builtContentDirectory, getLog());
    }
    
    protected static @Nullable File getJcrSourceDirectory(File[] jcrRootSourceDirectory, File builtContentDirectory, Log log) {
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
