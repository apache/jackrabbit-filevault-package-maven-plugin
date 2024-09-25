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
package org.apache.jackrabbit.filevault.maven.packaging.impl.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DocviewEscapingInterpolatorCustomizerFactoryTest {

    private static final String NO_ESCAPE_EXPRESSION = "some.expression";
    private static final String ESCAPE_EXPRESSION = "vltattributeescape.some.expression";

    @Test
    void testEscaping() {
        DocviewEscapingInterpolatorCustomizerFactory processor = new DocviewEscapingInterpolatorCustomizerFactory();
        assertEquals("&lt;&gt;&amp;\\\\", processor.execute(ESCAPE_EXPRESSION, "<>&\\"));
    }

    @Test
    void testNoEscapingWithoutPrefix() {
        DocviewEscapingInterpolatorCustomizerFactory processor = new DocviewEscapingInterpolatorCustomizerFactory();
        assertNull(processor.execute(NO_ESCAPE_EXPRESSION, "<>/"));
    }
}
