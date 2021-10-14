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

import org.junit.Assert;
import org.junit.Test;

public class ValidatorSettingsTest {

    @Test
    public void testMerge() {
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
        Assert.assertEquals(mergedSettings, settings1.merge(settings2));
    }
}
