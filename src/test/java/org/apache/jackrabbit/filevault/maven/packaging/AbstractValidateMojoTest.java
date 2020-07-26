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

import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidArtifactRTException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class AbstractValidateMojoTest {

    @Test
    public void testValidMapWithIgnoredArtifacts() {
        Assert.assertThat(AbstractValidateMojo.resolveMap(Arrays.asList("group1:name1=ignore", "group2:name2=groupId2:artifactId2")), 
                Matchers.allOf(
                    Matchers.hasEntry(Dependency.fromString("group1:name1"), AbstractValidateMojo.IGNORE_ARTIFACT),
                    Matchers.hasEntry(Dependency.fromString("group2:name2"), new DefaultArtifact("groupId2", "artifactId2", "", "", "", "", null)),
                    Matchers.aMapWithSize(2)));
    }

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

    @Test
    public void testGetValidatorSettingsForPackage() {
        Map<String, ValidatorSettings> validatorsSettings = new HashMap<>();
        validatorsSettings.put("id1", new ValidatorSettings().addOption("id1", "foo"));
        validatorsSettings.put("id2:mygroup:myname", new ValidatorSettings().addOption("id2", "foo"));
        validatorsSettings.put("id3:subpackage", new ValidatorSettings().addOption("id3", "foo"));
        validatorsSettings.put("id4:invalid", new ValidatorSettings().addOption("id4", "foo"));
        validatorsSettings.put("id5:othergroup:myname", new ValidatorSettings().addOption("id5", "foo"));
        validatorsSettings.put("id6:mygroup:myothername", new ValidatorSettings().addOption("id3", "foo"));
        validatorsSettings.put("id7:", new ValidatorSettings().addOption("id7", "foo"));
        
        Map<String, ValidatorSettings> actualValidatorSettings = AbstractValidateMojo.getValidatorSettingsForPackage(new SystemStreamLog(), validatorsSettings, PackageId.fromString("mygroup:myname:1.0.0"), false);
        Map<String, ValidatorSettings> expectedValidatorSettings = new HashMap<>();
        expectedValidatorSettings.put("id1", new ValidatorSettings().addOption("id1", "foo"));
        expectedValidatorSettings.put("id2", new ValidatorSettings().addOption("id2", "foo"));
        Assert.assertThat(actualValidatorSettings, Matchers.equalTo(expectedValidatorSettings));
        
        actualValidatorSettings = AbstractValidateMojo.getValidatorSettingsForPackage(new SystemStreamLog(), validatorsSettings, PackageId.fromString("mygroup:myname:1.0.0"), true);
        expectedValidatorSettings.put("id3", new ValidatorSettings().addOption("id3", "foo"));
        Assert.assertThat(actualValidatorSettings, Matchers.equalTo(expectedValidatorSettings));
        
        actualValidatorSettings = AbstractValidateMojo.getValidatorSettingsForPackage(new SystemStreamLog(), null, PackageId.fromString("mygroup:myname:1.0.0"), true);
        expectedValidatorSettings.clear();
        Assert.assertThat(actualValidatorSettings, Matchers.equalTo(expectedValidatorSettings));
    }

    @Test
    public void testDotContentXmlFirstComparator() {
        List<String> list = Arrays.asList("someEntryA", ".content.xml", "someEntryB", ".content.xml");
        list.sort(new AbstractValidateMojo.DotContentXmlFirstComparator());
        assertThat(list, Matchers.contains(".content.xml", ".content.xml", "someEntryA", "someEntryB"));
    }
}
