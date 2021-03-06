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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.maven.it.VerificationException;
import org.junit.Assert;
import org.junit.Test;

public class ValidatePackageIT {

    private static final String TEST_PROJECT_NAME = "/validator-projects/";

    private ProjectBuilder verify(String projectName, boolean expectToFail) throws VerificationException, IOException {
        return new ProjectBuilder()
                .setTestProjectDir(TEST_PROJECT_NAME + projectName)
                .setBuildExpectedToFail(expectToFail)
                .build();
    }

    @Test
    public void testInvalidProject() throws Exception {
        ProjectBuilder projectBuilder = verify("invalid-project", true);
        projectBuilder.verifyExpectedLogLines(Paths.get("META-INF", "vault", "filter.xml").toString());
        File csvReportFile = new File(projectBuilder.getTestProjectDir(), "report.csv");
        Assert.assertTrue(csvReportFile.exists());
        CSVParser csvParser = CSVParser.parse(csvReportFile, StandardCharsets.UTF_8, CSVFormat.EXCEL);
        List<CSVRecord> actualRecords = csvParser.getRecords();
        Assert.assertEquals(4, actualRecords.size()); // 3 issues + header
    }
 
    @Test
    public void testValidProjectWithZip() throws Exception {
        verify("project-with-zip", false);
    }
}
