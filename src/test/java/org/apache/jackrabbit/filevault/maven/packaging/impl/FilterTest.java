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
package org.apache.jackrabbit.filevault.maven.packaging.impl;

import junit.framework.TestCase;

import java.util.LinkedList;
import java.util.List;

/**
 * Test the string filter
 */
public class FilterTest extends TestCase {

    private static final String[] TEST_STRINGS = {
            "com.day.cq",
            "foo-bar",
            "com.day.cq.impl",
            "artifact1-test",
            "artifact2-test"
    };

    private static final TestSet[] TESTS = new TestSet[]{
        new TestSet(
                "Exact",
                "com.day.cq",
                new String[]{
                        "com.day.cq"
                }),
            new TestSet(
                "Exact Multiple",
                "com.day.cq, com.day.cq.impl",
                new String[]{
                        "com.day.cq",
                        "com.day.cq.impl"
                }),
            new TestSet(
                "Exclude Single",
                "~com.day.cq",
                new String[]{
                        "foo-bar",
                        "com.day.cq.impl",
                        "artifact1-test",
                        "artifact2-test"
                }),
            new TestSet(
                "Exclude Multiple",
                "~com.day.cq,~foo-bar",
                new String[]{
                        "com.day.cq.impl",
                        "artifact1-test",
                        "artifact2-test"
                }),
            new TestSet(
                "Exclude Pattern",
                "~/.*-test/",
                new String[]{
                        "com.day.cq",
                        "foo-bar",
                        "com.day.cq.impl"
                }),
            new TestSet(
                "Exclude Subpackage",
                "~/com\\.day\\.cq(.*)?/",
                new String[]{
                        "foo-bar",
                        "artifact1-test",
                        "artifact2-test"
                })
    };

    public void testPatterns() {
        for (TestSet test: TESTS) {
            StringFilterSet set = new StringFilterSet();
            set.addEntries(test.pattern);
            assertEquals(test.name, test.result, getMatching(set, TEST_STRINGS));
        }
    }

    private String[] getMatching(StringFilterSet set, String[] testStrings) {
        List<String> ret = new LinkedList<String>();
        for (String name: testStrings) {
            if (set.contains(name)) {
                ret.add(name);
            }
        }
        return ret.toArray(new String[ret.size()]);
    }

    private void assertEquals(String name, String[] expected, String[] test) {
        assertEquals(name, expected.length, test.length);
        for (int i=0; i< test.length; i++) {
            assertEquals(name, expected[i], test[i]);
        }
    }

    private static class TestSet {
        private final String name;

        private final String pattern;

        private final String[] result;

        private TestSet(String name, String pattern, String[] result) {
            this.name = name;
            this.pattern = pattern;
            this.result = result;
        }
    }
}