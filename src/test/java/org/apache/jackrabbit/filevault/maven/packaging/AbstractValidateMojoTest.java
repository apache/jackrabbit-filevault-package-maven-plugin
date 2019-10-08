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

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidArtifactRTException;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class AbstractValidateMojoTest {

    @Test
    public void testValidMap() {
        Assert.assertThat(AbstractValidateMojo.resolveMap(Arrays.asList("group1:name1=groupId1:artifactId1", "group2:name2=groupId2:artifactId2")), 
                Matchers.allOf(
                    Matchers.hasEntry(Dependency.fromString("group1:name1"), new DefaultArtifact("groupId1", "artifactId1", "", "", "", "", null)),
                    Matchers.hasEntry(Dependency.fromString("group2:name2"), new DefaultArtifact("groupId2", "artifactId2", "", "", "", "", null)),
                    Matchers.aMapWithSize(2)));
       
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMap1() {
        AbstractValidateMojo.resolveMap(Arrays.asList("group1:=artifactId1")); 
       
    }

    @Test(expected = InvalidArtifactRTException.class)
    public void testInvalidMap2() {
        AbstractValidateMojo.resolveMap(Arrays.asList("group1:=:groupId")); 
       
    }
}
