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

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SimpleEmbeddedTest {
    
    private SimpleEmbedded embedded;
    
    private List<Artifact> artifacts;
    
    @Before
    public void setUp() {
        embedded = new SimpleEmbedded();
        artifacts = new ArrayList<>();
        // the order is important here!
        artifacts.add(new SimpleArtifact("mygroupid", "artifact1", "compile"));
        artifacts.add(new SimpleArtifact("mygroupid", "artifact2", "provided")); 
        artifacts.add(new SimpleArtifact("myothergroupid", "artifact3", "test"));
        artifacts.add(new SimpleArtifact("myothergroupid", "artifact1", "test"));
        artifacts.add(new SimpleArtifact("mygroupid", "artifact1", "test", "bundle"));
        artifacts.add(new SimpleArtifact("mygroupid", "artifact2", "provided", "bundle"));
        artifacts.add(new SimpleArtifact("mygroupid", "artifact2", "compile", "bundle", "myclassifier"));
    }
    
    @Test
    public void testGroupIdOnlyFilter() throws ConfigurationException {
        embedded.setGroupId("mygroupid");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.containsInAnyOrder(artifacts.get(0), artifacts.get(1), artifacts.get(4), artifacts.get(5), artifacts.get(6)));
    }

    @Test
    public void testArtifactIdOnlyFilter() throws ConfigurationException {
        embedded.setArtifactId("artifact1");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.containsInAnyOrder(artifacts.get(0), artifacts.get(3), artifacts.get(4)));
    }

    @Test
    public void testTypeBundleOnlyFilter() throws ConfigurationException {
        embedded.setType("bundle");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.contains(artifacts.get(4), artifacts.get(5), artifacts.get(6)));
    }

    @Test
    public void testTypeBundleOrJarOnlyFilter() throws ConfigurationException {
        // in addition filter for jar
        embedded.setType("jar");
        embedded.setType("bundle");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.containsInAnyOrder(artifacts.toArray()));
    }

    @Test
    public void testClassifierOnlyFilter() throws ConfigurationException {
        // in addition filter for jar
        embedded.setClassifier("myclassifier");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.contains(artifacts.get(6)));
    }

    @Test
    public void testScopeOnlyFilter() {
        // should contain all artifacts with scope "compile", "runtime" or "system"
        embedded.setScope("compile");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.containsInAnyOrder(artifacts.get(0), artifacts.get(1), artifacts.get(5), artifacts.get(6)));
    }

    @Test
    public void testComplexFilter() throws ConfigurationException {
        embedded.setType("bundle");
        embedded.setArtifactId("artifact1");
        embedded.setGroupId("mygroupid");
        Assert.assertThat(embedded.getMatchingArtifacts(artifacts), Matchers.containsInAnyOrder(artifacts.get(4)));
    }

    public final static class SimpleArtifact extends DefaultArtifact {
        SimpleArtifact(String groupId, String artifactId) {
            this(groupId, artifactId, null);
        }

        SimpleArtifact(String groupId, String artifactId, String scope) {
            this(groupId, artifactId, scope, null);
        }

        SimpleArtifact(String groupId, String artifactId, String scope, String type) {
            this(groupId, artifactId, scope, type, null);
        }
        
        SimpleArtifact(String groupId, String artifactId, String scope, String type, String classifier) {
            super(groupId, artifactId, "default", scope, type == null ? "jar" : type, classifier == null ? "" : classifier, null);
        }
    }
}
