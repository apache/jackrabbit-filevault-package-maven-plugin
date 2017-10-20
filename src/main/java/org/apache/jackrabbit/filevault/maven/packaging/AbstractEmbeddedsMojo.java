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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Base class for mojos which use embedded artifacts.
 */
abstract class AbstractEmbeddedsMojo extends AbstractMojo {
    
    /**
     * The Maven project.
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    /**
     * list of embedded bundles
     */
    @Parameter
    protected final List<Embedded> embeddeds = new ArrayList<Embedded>();

    /**
     * Defines whether to fail the build when an embedded artifact is not
     * found in the project's dependencies
     * @since 0.0.12
     */
    @Parameter(property = "vault.failOnMissingEmbed", defaultValue = "false", required = true)
    protected boolean failOnMissingEmbed;

    public void addEmbedded(final Embedded embedded) {
        embeddeds.add(embedded);
    }

}
