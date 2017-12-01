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

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;

public class Filter {

    /**
     * General format:
     *
     * <pre>
     *
     * &lt;filter&gt;
     *      &lt;root&gt;/var&lt;/root&gt;
     *      &lt;type&gt;cleanup&lt;/type&gt;
     *      &lt;mode&gt;merge&lt;/mode&gt;
     *      &lt;includes&gt;
     *          &lt;include&gt;dam/sharepoint(/.*)?&lt;/include&gt;
     *          &lt;include&gt;/var/dam&lt;/include&gt;
     *      &lt;/includes&gt;
     * &lt;/filter&gt;
     * </pre>
     */

    static final String INDENT = "    ";

    private String root;

    /** ImportMode defaulting to REPLACE */
    private ImportMode mode = ImportMode.REPLACE;

    private boolean cleanupType;

    private final IncludeExcludeList includes = new IncludeExcludeList();

    private final IncludeExcludeList excludes = new IncludeExcludeList();

    public Filter() {
    }

    public Filter(String root) {
        setRoot(root);
    }

    public void setRoot(final String root) {
        this.root = root;
    }

    public String getRoot() {
        return root;
    }

    public void setMode(String mode) {
        if (mode != null) {
            this.mode = ImportMode.valueOf(mode.toUpperCase());
        }
    }

    public ImportMode getMode() {
        return mode;
    }

    public boolean isCleanupType() {
        return cleanupType;
    }

    public void setType(String type) {
        cleanupType = "cleanup".equals(type);
    }

    public PathFilterSet toPathFilterSet() {
        PathFilterSet set = new PathFilterSet();
        set.setRoot(root);
        set.setImportMode(mode);
        if (cleanupType) {
            set.setType("cleanup");
        }
        for (String pattern: includes) {
            set.addInclude(new DefaultPathFilter(pattern));
        }
        for (String pattern: excludes) {
            set.addExclude(new DefaultPathFilter(pattern));
        }
        return set;
    }

}
