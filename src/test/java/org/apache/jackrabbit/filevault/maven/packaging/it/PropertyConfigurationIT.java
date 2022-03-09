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
package org.apache.jackrabbit.filevault.maven.packaging.it;

import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProjectBuilderExtension.class)
public class PropertyConfigurationIT {

    /**
     * Tests if the property.xml contains the description from the plugin config and not the project.
     */
    @Test
   void test_that_description_can_be_overridden_in_properties(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
                .setTestProjectDir("override-description")
                .build()
                .verifyPackageProperty("description", "Description from plugin");

    }

    /**
     * Tests that properties like acHandling can be set via the "properties" map.
     */
    @Test
   void test_that_properties_can_be_set_via_map(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
                .setTestProjectDir("properties-from-map")
                .build()
                .verifyPackageProperty("requiresRoot", "true")
                .verifyPackageProperty("allowIndexDefinitions", "true")
                .verifyPackageProperty("acHandling", "overwrite");

    }

    /**
     * Tests set properties set explicitly as plugin config param have higher precedence than those in the properties map.
     */
    @Test
   void test_that_properties_set_in_plugin_config_have_higher_precedence(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
                .setTestProjectDir("properties-from-plugin-config")
                .build()
                .verifyPackageProperty("requiresRoot", "true")
                .verifyPackageProperty("allowIndexDefinitions", "true")
                .verifyPackageProperty("acHandling", "merge");
    }

    /**
     * Tests set properties set explicitly as plugin config param have higher precedence than those in the properties map.
     */
    @Test
   void test_that_properties_set_in_plugin_config_have_higher_precedence_with_alias(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
                .setTestProjectDir("properties-from-plugin-config-aliased")
                .build()
                .verifyPackageProperty("requiresRoot", "true")
                .verifyPackageProperty("allowIndexDefinitions", "true")
                .verifyPackageProperty("acHandling", "merge");

    }

}
