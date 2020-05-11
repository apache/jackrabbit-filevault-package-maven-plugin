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
package org.apache.jackrabbit.filevault.maven.packaging.impl.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.codehaus.plexus.components.io.resources.AbstractPlexusIoResource;

public class PlexusIoNonExistingDirectoryResource extends AbstractPlexusIoResource {

    public PlexusIoNonExistingDirectoryResource(String name) {
        // although this resource is non-existing, make it have the existing flag as otherwise AbstractArchiver.asArchiveEntry(...) will fai
        super(name, -1, -1, false, true, true);
    }

    @Override
    public InputStream getContents() throws IOException {
        throw new UnsupportedOperationException("The underlying folder does not exist!");
    }

    @Override
    public URL getURL() throws IOException {
        throw new UnsupportedOperationException("The underlying folder does not exist!");
    }

}
