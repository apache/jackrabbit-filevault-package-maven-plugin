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

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;

/**
 * {@code StringFilter}...
 */
public class StringFilter extends DefaultPathFilter {

    private String string;

    public StringFilter(String pattern) throws ConfigurationException {
        super(pattern);
    }

    @Override
    public String getPattern() {
        if (string == null) {
            return super.getPattern();
        } else {
            return string;
        }
    }
    @Override
    public void setPattern(String pattern) throws ConfigurationException {
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1);
            if (pattern.endsWith("/")) {
                pattern = pattern.substring(0, pattern.length()-1);
            }
            super.setPattern(pattern);
        } else {
            string = pattern;
        }
    }

    @Override
    public boolean matches(String path) {
        if (string == null) {
            return super.matches(path);
        } else {
            return string.equals(path);
        }
    }

    @Override
    public String toString() {
        if (string == null) {
            return "/" + getPattern() + "/";
        } else {
            return getPattern();
        }
    }
}