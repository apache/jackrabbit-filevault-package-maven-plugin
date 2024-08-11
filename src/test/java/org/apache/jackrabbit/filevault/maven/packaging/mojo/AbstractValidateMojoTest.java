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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jackrabbit.filevault.maven.packaging.PackageRestriction;
import org.apache.jackrabbit.filevault.maven.packaging.ValidatorSettings;
import org.apache.jackrabbit.filevault.maven.packaging.impl.ValidationMessagePrinter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.maven.artifact.InvalidArtifactRTException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class AbstractValidateMojoTest {

    @Test
    void testValidMapWithIgnoredArtifacts() {
        MatcherAssert.assertThat(AbstractValidateMojo.resolveMap(Arrays.asList("group1:name1=ignore", "group2:name2=groupId2:artifactId2")), 
                Matchers.allOf(
                    Matchers.hasEntry(Dependency.fromString("group1:name1"), AbstractValidateMojo.IGNORE_ARTIFACT),
                    Matchers.hasEntry(Dependency.fromString("group2:name2"), new DefaultArtifact("groupId2", "artifactId2", "zip", null)),
                    Matchers.aMapWithSize(2)));
    }

    @Test
    void testValidMap() {
        MatcherAssert.assertThat(AbstractValidateMojo.resolveMap(Arrays.asList("group1:name1=groupId1:artifactId1", "group2:name2=groupId2:artifactId2")), 
                Matchers.allOf(
                    Matchers.hasEntry(Dependency.fromString("group1:name1"), new DefaultArtifact("groupId1", "artifactId1", "zip", null)),
                    Matchers.hasEntry(Dependency.fromString("group2:name2"), new DefaultArtifact("groupId2", "artifactId2", "zip", null)),
                    Matchers.aMapWithSize(2)));
    }

    @Test
    void testInvalidMap1() {
        assertThrows(IllegalArgumentException.class, () -> AbstractValidateMojo.resolveMap(Arrays.asList("group1:=artifactId1"))); 
    }

    @Test
    void testInvalidMap2() {
        assertThrows(IllegalArgumentException.class, () -> AbstractValidateMojo.resolveMap(Arrays.asList("group1:=:groupId"))); 
    }

    @Test
    void testGetEffectiveValidatorSettingsForPackage() {
        Map<String, ValidatorSettings> validatorsSettings = new HashMap<>();
        validatorsSettings.put("id1", new ValidatorSettings().addOption("id1", "foo"));
        validatorsSettings.put("id2", new ValidatorSettings().addOption("id2", "foo").setPackageRestriction(new PackageRestriction("mygroup", "myname")));
        validatorsSettings.put("id3", new ValidatorSettings().addOption("id3", "foo").setPackageRestriction(new PackageRestriction(null, null, false)));
        validatorsSettings.put("id5", new ValidatorSettings().addOption("id5", "foo").setPackageRestriction(new PackageRestriction("othergroup", "othername")));
        validatorsSettings.put("id6", new ValidatorSettings().addOption("id3", "foo").setPackageRestriction(new PackageRestriction("mygroiup", "myothername")));
        validatorsSettings.put("id1__1", new ValidatorSettings().addOption("id2", "bar").setPackageRestriction(new PackageRestriction(null, "myname")));
        validatorsSettings.put("id3__2", new ValidatorSettings().addOption("id3", "bar"));
        Map<String, ValidatorSettings> actualValidatorSettings = AbstractValidateMojo.getEffectiveValidatorSettingsForPackage(validatorsSettings, PackageId.fromString("mygroup:myname:1.0.0"), false);
        Map<String, ValidatorSettings> expectedValidatorSettings = new HashMap<>();
        expectedValidatorSettings.put("id1", new ValidatorSettings().addOption("id1", "foo").addOption("id2", "bar"));
        expectedValidatorSettings.put("id2", new ValidatorSettings().addOption("id2", "foo"));
        expectedValidatorSettings.put("id3", new ValidatorSettings().addOption("id3", "foo"));
        MatcherAssert.assertThat(actualValidatorSettings, Matchers.equalTo(expectedValidatorSettings));
        
        actualValidatorSettings = AbstractValidateMojo.getEffectiveValidatorSettingsForPackage(validatorsSettings, PackageId.fromString("mygroup:myname:1.0.0"), true);
        expectedValidatorSettings.put("id3", new ValidatorSettings().addOption("id3", "foo"));
        MatcherAssert.assertThat(actualValidatorSettings, Matchers.equalTo(expectedValidatorSettings));
        
        actualValidatorSettings = AbstractValidateMojo.getEffectiveValidatorSettingsForPackage(null, PackageId.fromString("mygroup:myname:1.0.0"), true);
        expectedValidatorSettings.clear();
        MatcherAssert.assertThat(actualValidatorSettings, Matchers.equalTo(expectedValidatorSettings));
    }

    @Test
    void testDotContentXmlFirstComparator() {
        List<String> list = Arrays.asList("someEntryA", ".content.xml", "someEntryB", ".content.xml");
        list.sort(new AbstractValidateMojo.DotContentXmlFirstComparator());
        MatcherAssert.assertThat(list, Matchers.contains(".content.xml", ".content.xml", "someEntryA", "someEntryB"));
    }

    @Test
    void testGetProjectRelativeFilePathWithoutRealProject() {
        AbstractValidateMojo mojo = new AbstractValidateMojo() {
            @Override
            public void doExecute(ValidationMessagePrinter validationHelper) throws MojoExecutionException, MojoFailureException {
                throw new UnsupportedOperationException();
            }
        };
        mojo.project = new MavenProject();
        Path path = Paths.get("").toAbsolutePath();
        assertEquals("'" + path + "'", mojo.getProjectRelativeFilePath(path));
    }
}
