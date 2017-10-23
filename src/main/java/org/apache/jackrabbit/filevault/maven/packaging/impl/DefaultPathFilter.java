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

import java.util.regex.Pattern;

/**
 * The default path filter provides hierarchical filtering.
 *
 */
public class DefaultPathFilter implements PathFilter {

    /**
     * the internal regex pattern
     */
    private Pattern regex;

    /**
     * Default constructor
     */
    public DefaultPathFilter() {
    }

    /**
     * Creates a new default path filter
     * @param pattern the pattern
     * @see #setPattern
     */
    public DefaultPathFilter(String pattern) {
        setPattern(pattern);
    }

    /**
     * Sets the regexp pattern for this filter.
     * <p>
     * Examples:
     * <pre>
     * | Pattern        | Matches
     * | /foo           | exactly "/foo"
     * | /foo.*         | all paths starting with "/foo"
     * | ^.* /foo[^/]*$ | all files starting with "foo"
     * | /foo/[^/]*$    | all direct children of /foo
     * | /foo/.*        | all children of /foo
     * | /foo(/.*)?     | all children of /foo and foo itself
     * </pre>
     *
     * @param pattern the pattern.
     */
    public void setPattern(String pattern) {
        regex = Pattern.compile(pattern);
    }

    /**
     * Returns the pattern
     * @return the pattern
     */
    public String getPattern() {
        return regex.pattern();
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(String path) {
        return regex.matcher(path).matches();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAbsolute() {
        return regex.pattern().startsWith("/");
    }

}