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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Coordinates addressing a Maven artifact. 
 * Can generate an Aether artifact which can be used to retrieve it from a local or remote repository.
 */
public class ArtifactCoordinates {

    private String coordinates;
    
    public ArtifactCoordinates() {
        
    }
    
    /**
     * 
     * @param coordinates The artifact coordinates in the format {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     */
    public ArtifactCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    // default set method being called by https://github.com/eclipse/sisu.plexus/blob/4a43cdd39acf8a9f8a128d0f08204e98639e0c4b/org.eclipse.sisu.plexus/src/org/eclipse/sisu/plexus/CompositeBeanHelper.java#L74
    public void set(String coordinates) {
        this.coordinates = coordinates;
    }

    public Artifact toArtifact() {
        return new DefaultArtifact(coordinates);
    }
}
