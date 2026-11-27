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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;

/** A Maven log that remembers its warnings, so tests can check them. */
class CapturingLog extends SystemStreamLog {

    final List<String> warnings = new ArrayList<>();

    @Override
    public void warn(CharSequence content) {
        warnings.add(content.toString());
        super.warn(content);
    }

    /**
     * Creates an archive resource that only knows its name, which is all the transformers use.
     * A dynamic proxy avoids implementing the many other methods of the interface.
     */
    static PlexusIoResource resourceNamed(String name) {
        return (PlexusIoResource) Proxy.newProxyInstance(PlexusIoResource.class.getClassLoader(),
                new Class<?>[] {PlexusIoResource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
