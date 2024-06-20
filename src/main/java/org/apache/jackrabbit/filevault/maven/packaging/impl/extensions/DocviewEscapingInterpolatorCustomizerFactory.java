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

import java.util.function.Consumer;

import javax.inject.Named;

import org.apache.jackrabbit.filevault.maven.packaging.InterpolatorCustomizerFactory;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;

/** Enhances the regular filtering with handling {@code vltdocviewattributeescape.} which will automatically escape the interpolated value of
 * the suffix according to <a href="https://jackrabbit.apache.org/filevault/docview.html#Escaping">FileVault DocView Escaping Rules</a> for using it inside
 * XML attribute values. */
@Named()
public class DocviewEscapingInterpolatorCustomizerFactory extends AbstractValueSource implements InterpolatorCustomizerFactory, InterpolationPostProcessor {

    public DocviewEscapingInterpolatorCustomizerFactory() {
        super(false);
    }

    public static final String EXPRESSION_PREFIX = "vltdocviewattributeescape.";

    private static final Name DEFAULT_NAME = NameConstants.JCR_TITLE;

    @Override
    public Object getValue(String expression) {
        if (expression.startsWith(EXPRESSION_PREFIX)) {
            // FIXME: currently the delimiter is hardcoded (https://github.com/codehaus-plexus/plexus-interpolation/issues/76)
            return StringSearchInterpolator.DEFAULT_START_EXPR + expression.substring(EXPRESSION_PREFIX.length()) +  StringSearchInterpolator.DEFAULT_END_EXPR;
        } else {
            return null;
        }
    }

    @Override
    public Object execute(String expression, Object value) {
        if (expression.startsWith(EXPRESSION_PREFIX)) {
            String escapedValue;
            // use hardcoded name as it doesn't matter here
            escapedValue = new DocViewProperty2(DEFAULT_NAME, value.toString()).formatValue();
            // now escape for usage inside XML attributes
            return Text.encodeIllegalXMLCharacters(escapedValue);
        }
        return null;
    }

    @Override
    public Consumer<Interpolator> create(MavenSession mavenSession, MavenProject mavenProject) {
        return i -> {
            i.addPostProcessor(this);
            i.addValueSource(this);
        };
    }

}
