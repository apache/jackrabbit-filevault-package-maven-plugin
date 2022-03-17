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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

class ValidatorSettingsTest {

    @Test
    void testMerge() {
        ValidatorSettings settings1 = new ValidatorSettings();
        settings1.setIsDisabled(true);
        settings1.addOption("option1", "from1");
        settings1.addOption("option3", "from1");
        ValidatorSettings settings2 = new ValidatorSettings();
        settings2.setIsDisabled(false);
        settings2.addOption("option1", "from2");
        settings2.addOption("option2", "from2");
        settings2.addOption("option3", "from2");
        settings2.setDefaultSeverity("WARN");
        ValidatorSettings mergedSettings = new ValidatorSettings();
        mergedSettings.setIsDisabled(true);
        mergedSettings.addOption("option1", "from1");
        mergedSettings.addOption("option2", "from2");
        mergedSettings.addOption("option3", "from1");
        mergedSettings.setDefaultSeverity("WARN");
        assertEquals(mergedSettings, settings1.merge(settings2));
    }

    @Test 
    void testOrder() {
        ValidatorSettings settings1 = new ValidatorSettings();
        settings1.setPackageRestriction(new PackageRestriction("group", "package"));
        ValidatorSettings settings2 = new ValidatorSettings();
        settings2.setPackageRestriction(new PackageRestriction(null, "package"));
        ValidatorSettings settings3 = new ValidatorSettings();
        settings3.setPackageRestriction(new PackageRestriction("group", null));
        ValidatorSettings settings4 = new ValidatorSettings();
        settings4.setPackageRestriction(new PackageRestriction(null, null));
        ValidatorSettings settings5 = new ValidatorSettings();
        SortedSet<ValidatorSettings> sortedSettings = new TreeSet<>();
        sortedSettings.add(settings5);
        sortedSettings.add(settings4);
        sortedSettings.add(settings2);
        sortedSettings.add(settings1);
        sortedSettings.add(settings3);
        MatcherAssert.assertThat(sortedSettings, org.hamcrest.Matchers.contains(settings1, settings2, settings3, settings4, settings5));
    }

    @Test
    void testIsApplicable() {
        ValidatorSettings settings = new ValidatorSettings();
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), true));
        assertTrue(settings.isApplicable(PackageId.fromString("group1:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name1:1.0.0"), true));

        settings.setPackageRestriction(new PackageRestriction("group", "name"));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), true));
        assertFalse(settings.isApplicable(PackageId.fromString("group1:name:1.0.0"), false));
        assertFalse(settings.isApplicable(PackageId.fromString("group:name1:1.0.0"), true));

        settings.setPackageRestriction(new PackageRestriction("group", null));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), true));
        assertFalse(settings.isApplicable(PackageId.fromString("group1:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name1:1.0.0"), true));

        settings.setPackageRestriction(new PackageRestriction(null, "name"));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), true));
        assertTrue(settings.isApplicable(PackageId.fromString("group1:name:1.0.0"), false));
        assertFalse(settings.isApplicable(PackageId.fromString("group:name1:1.0.0"), true));

        settings.setPackageRestriction(new PackageRestriction(null, null, true));
        assertFalse(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), true));
        assertFalse(settings.isApplicable(PackageId.fromString("group1:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name1:1.0.0"), true));

        settings.setPackageRestriction(new PackageRestriction(null, null, false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name:1.0.0"), true));
        assertTrue(settings.isApplicable(PackageId.fromString("group1:name:1.0.0"), false));
        assertTrue(settings.isApplicable(PackageId.fromString("group:name1:1.0.0"), true));
    }
}
