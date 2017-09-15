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

import java.util.List;

/**
 * <code>StringFilterSet</code>...
 */
public class StringFilterSet extends FilterSet<StringFilter> {

    public void addEntry(String pattern) {
        if (pattern.startsWith("~")) {
            addExclude(new StringFilter(pattern.substring(1)));
        } else {
            addInclude(new StringFilter(pattern));
        }
    }

    public void addEntries(String patterns) {
        for (String name: patterns.split(",")) {
            addEntry(name.trim());
        }
    }

    public boolean contains(String path) {
        List<Entry<StringFilter>> entries = getEntries();
        if (entries.isEmpty()) {
            return true;
        } else {
            boolean result = !entries.get(0).isInclude();
            for (Entry<StringFilter> entry: entries) {
                if (entry.getFilter().matches(path)) {
                    result = entry.isInclude();
                }
            }
            return result;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        String delim = "";
        for (Entry<StringFilter> entry: getEntries()) {
            builder.append(delim);
            if (!entry.isInclude()) {
                builder.append("~");
            }
            builder.append(entry.getFilter());
            delim=",";
        }
        return builder.toString();
    }
}