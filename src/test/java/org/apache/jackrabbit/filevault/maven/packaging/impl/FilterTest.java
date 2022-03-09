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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test the string filter
 */
class FilterTest {

    private static final String[] TEST_STRINGS = { "com.day.cq", "foo-bar", "com.day.cq.impl", "artifact1-test", "artifact2-test" };

    private static Stream<Arguments> testPatterns() {
        return Stream.of(
                Arguments.of("Exact", "com.day.cq", new String[] { "com.day.cq" }),
                Arguments.of("Exact Multiple", "com.day.cq, com.day.cq.impl", new String[] { "com.day.cq", "com.day.cq.impl" }),
                Arguments.of("Exclude Single", "~com.day.cq", new String[] { "foo-bar", "com.day.cq.impl", "artifact1-test", "artifact2-test" }),
                Arguments.of("Exclude Multiple", "~com.day.cq,~foo-bar", new String[] { "com.day.cq.impl", "artifact1-test", "artifact2-test" }),
                Arguments.of("Exclude Pattern", "~/.*-test/", new String[] { "com.day.cq", "foo-bar", "com.day.cq.impl" }),
                Arguments.of("Exclude Subpackage", "~/com\\.day\\.cq(.*)?/", new String[] { "foo-bar", "artifact1-test", "artifact2-test" }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource()
    void testPatterns(String name, String pattern, String[] result) throws ConfigurationException {
        StringFilterSet set = new StringFilterSet();
        set.addEntries(pattern);
        assertArrayEquals(result, getMatching(set, TEST_STRINGS), name);
    }

    private String[] getMatching(StringFilterSet set, String[] testStrings) {
        List<String> ret = new LinkedList<String>();
        for (String name : testStrings) {
            if (set.contains(name)) {
                ret.add(name);
            }
        }
        return ret.toArray(new String[ret.size()]);
    }

}