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
 * The {@code Embedded} class represents an embedded artifact dependency
 * from the project descriptor. Such an embedding is declared in
 * {@code <embedded>} elements inside the list style
 * {@code <embeddeds>} element as follows:
 * <pre>
 * &lt;embedded&gt;
 *     &lt;groupId&gt;artifact.groupId.pattern&lt;/groupId&gt;
 *     &lt;artifactId&gt;artifact.artifactId.pattern&lt;/artifactId&gt;
 *     &lt;scope&gt;compile&lt;/scope&gt;
 *     &lt;type&gt;artifact.type.pattern&lt;/type&gt;
 *     &lt;classifier&gt;artifact.classifier.pattern&lt;/classifier&gt;
 *     &lt;filter&gt;true&lt;/filter&gt;
 *     &lt;target&gt;/libs/sling/install&lt;/target&gt;
 * &lt;/embedded&gt;
 * </pre>
 */
public class Embedded extends SimpleEmbedded{

    /**
     * Target location.
     */
    private String target;

    /**
     * Name to use for the artifact in the destination
     */
    private String destFileName;

    public String getDestFileName() {
        return destFileName;
    }

    public void setDestFileName(String destFileName) {
        this.destFileName = destFileName;
    }

    public void setTarget(String target) {
        // need trailing slash
        if (!target.endsWith("/")) {
            target += "/";
        }

        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Embedded: ");
        super.toString(builder);
        if (target != null) {
            builder.append(",target=").append(target);
        }
        if (destFileName != null) {
            builder.append(",destFileName=").append(destFileName);
        }
        return builder.toString();
    }

}
