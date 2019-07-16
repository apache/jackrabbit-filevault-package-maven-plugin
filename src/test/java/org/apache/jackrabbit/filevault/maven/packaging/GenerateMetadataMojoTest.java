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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Test;

public class GenerateMetadataMojoTest {

    private static final String MANIFEST_ATTRIBUTE_NAME = "test";

    @Test
    public void testEscapeManifestValue() throws IOException {
        assertEscapedValueWorksInManifest("Paragraph\r\rAnother paragraph");
        assertEscapedValueWorksInManifest("single line value");
        assertEscapedValueWorksInManifest("Paragraph\n\nAnother paragraph");
        assertEscapedValueWorksInManifest("Paragraph\r\n\r\nAnother paragraph");
        assertEscapedValueWorksInManifest("some very long line above 72 chars. some very long line above 72 chars. some very long line above 72 chars\n\nAnother paragraph");
        assertEscapedValueWorksInManifest("some very long line above 72 chars. some very long line above 72 chars. some very long line above 72 chars\r\rAnother paragraph");
    }

    private void assertEscapedValueWorksInManifest(String value) throws IOException {
        String escapedValue = GenerateMetadataMojo.escapeManifestValue(value);
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue(MANIFEST_ATTRIBUTE_NAME, escapedValue);
        attributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            manifest.write(outputStream);
            //System.out.println(new String(outputStream.toByteArray(), StandardCharsets.UTF_8));
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
                manifest = new Manifest(inputStream);
                // java.util.jar.Manifest removes the new lines unfortunately from values, but maybe this gets fixed by Oracle at some point in time...
                String actualValue = manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE_NAME);
                Assert.assertEquals(removeNewlines(value), unescapeContinuations(actualValue));
            }
        };
        
    }

    private static String removeNewlines(String value) {
        return value.replaceAll("\n|\r", "");
    }

    /**
     * Java's Manifest parser {@link java.util.jar.Manifest} does not correctly remove the continuation for values in the form
     * {@code SomeText CR SPACE CR}
     * @param value
     * @return the given value with all {@code CR}s followed by {@code SPACE} removed
     */
    private static String unescapeContinuations(String value) {
        return value.replaceAll("\r ", "");
    }
    
}
