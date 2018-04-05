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

/**
 * The {@code SubPackage} class represents an subpackage artifact dependency
 * from the project descriptor. Such a package is declared in
 * {@code &lt;subPackages&gt;} elements inside the list style
 * {@code &lt;subPackage&gt;} element as follows:
 * <pre>
 * &lt;subPackage&gt;
 *     &lt;groupId&gt;artifact.groupId.pattern&lt;/groupId&gt;
 *     &lt;artifactId&gt;artifact.artifactId.pattern&lt;/artifactId&gt;
 *     &lt;scope&gt;compile&lt;/scope&gt;
 *     &lt;type&gt;artifact.type.pattern&lt;/type&gt;
 *     &lt;classifier&gt;artifact.classifier.pattern&lt;/classifier&gt;
 *     &lt;filter&gt;true&lt;/filter&gt;
 * &lt;/subPackage&gt;
 * </pre>
 */
public class SubPackage extends SimpleEmbedded {

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Sub Packages: ");
        super.toString(builder);
        return builder.toString();
    }
}
