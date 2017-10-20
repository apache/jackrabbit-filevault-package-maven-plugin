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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.it.Verifier;
import org.junit.Test;

public class PropertyConfigurationIT {

    @Test
    public void test_that_description_can_be_overridden_in_properties() throws Exception {
        final File projectDir = new File("target/test-classes/test-projects/override-description");

        final Properties props = new Properties();
        props.put("plugin.version", System.getProperty("plugin.version"));

        Verifier verifier = new Verifier(projectDir.getAbsolutePath());
        verifier.setSystemProperties(props);
        verifier.executeGoals(Arrays.asList("clean", "package"));

        final File packageFile = new File(projectDir, "target/package-plugin-test-pkg-1.0.0-SNAPSHOT.zip");
        assertThat(packageFile.exists(), is(true));

        ZipFile zip = new ZipFile(packageFile);
        ZipEntry propertiesFile = zip.getEntry("META-INF/vault/properties.xml");
        assertThat(propertiesFile, notNullValue());

        Properties properties = new Properties();
        properties.loadFromXML(zip.getInputStream(propertiesFile));

        assertThat(properties.getProperty("description"), equalTo("Description from plugin"));
    }
}
