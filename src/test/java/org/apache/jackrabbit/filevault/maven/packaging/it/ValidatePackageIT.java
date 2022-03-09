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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilderExtension;
import org.apache.jackrabbit.filevault.maven.packaging.it.util.ProjectBuilder;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProjectBuilderExtension.class)
class ValidatePackageIT {

    private static final String TEST_PROJECT_NAME = "/validator-projects/";

    @Test
    void testInvalidProject(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir(TEST_PROJECT_NAME + "invalid-project");
        projectBuilder.setBuildExpectedToFail(true);
        projectBuilder.build();
        projectBuilder.verifyExpectedLogLines(Paths.get("META-INF", "vault", "filter.xml").toString());
        File csvReportFile = new File(projectBuilder.getTestProjectDir(), "report.csv");
        assertTrue(csvReportFile.exists());
        CSVParser csvParser = CSVParser.parse(csvReportFile, StandardCharsets.UTF_8, CSVFormat.EXCEL);
        List<CSVRecord> actualRecords = csvParser.getRecords();
        try (InputStream input = getClass().getResourceAsStream("report.csv")) {
            csvParser = CSVParser.parse(input, StandardCharsets.UTF_8, CSVFormat.EXCEL);
            List<CSVRecord> expectedRecords = csvParser.getRecords();
            // ignore file name in records.csv (4th column)
            MatcherAssert.assertThat(actualRecords, Matchers.contains(expectedRecords.stream().map(r -> new CSVRecordMatcher(r, 3)).toArray(CSVRecordMatcher[]::new)));
        }
    }

    @Test
    void testValidProjectWithZip(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir(TEST_PROJECT_NAME + "project-with-zip");
        projectBuilder.build();
    }

    @Test
    void testValidProjectWithClassifier(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder.setTestProjectDir(TEST_PROJECT_NAME + "classifier-project", "test");
        projectBuilder.build();
    }

    @Test
    void testValidPackageWithoutProject(ProjectBuilder projectBuilder) throws Exception {
        projectBuilder
        // some new dir
        .setTestProjectDir("../test-content")
        .setTestGoals(String.format("org.apache.jackrabbit:filevault-package-maven-plugin:%s:validate-package", ProjectBuilder.getPluginVersion()))
        .setProperty("vault.packageToValidate", "test-package.zip")
        .setVerifyPackageContents(false)
        .build();
    }

    private static final class CSVRecordMatcher extends TypeSafeMatcher<CSVRecord> {
        private final CSVRecord expectedCsvRecord;
        private final Collection<Integer> ignoredValueIndices;

        CSVRecordMatcher(CSVRecord csvRecord, Integer... ignoredValueIndices) {
            this.expectedCsvRecord = csvRecord;
            this.ignoredValueIndices =  Arrays.asList(ignoredValueIndices);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(expectedCsvRecord.toString());
        }

        @Override
        protected boolean matchesSafely(CSVRecord item) {
            int size = expectedCsvRecord.size();
            if (item.size() != size) {
                return false;
            }
            for (int i=0; i<size; i++) {
                if(ignoredValueIndices.contains(i)) {
                    continue;
                }
                if (!item.get(i).equals(expectedCsvRecord.get(i))) {
                    return false;
                }
            }
            return true;
        }
        
    }
}
