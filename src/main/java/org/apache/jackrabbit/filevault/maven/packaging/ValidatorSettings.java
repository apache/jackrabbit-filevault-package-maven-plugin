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

import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;

public class ValidatorSettings implements org.apache.jackrabbit.vault.validation.spi.ValidatorSettings {

    private final boolean isDisabled;
    
    private ValidationMessageSeverity defaultSeverity;

    private final Map<String, String> options;
    
    public ValidatorSettings() {
        isDisabled = false;
        options = new HashMap<>();
    }
    
    public ValidatorSettings(ValidationMessageSeverity defaultSeverity) {
        this();
        this.defaultSeverity = defaultSeverity;
    }

    public ValidatorSettings setDefaultSeverity(String defaultSeverity) {
        if (defaultSeverity != null) {
            this.defaultSeverity = ValidationMessageSeverity.valueOf(defaultSeverity.toUpperCase());
        }
        return this;
    }

    protected ValidatorSettings addOption(String key, String value) {
        options.put(key, value);
        return this;
    }

    @Override
    public ValidationMessageSeverity getDefaultSeverity() {
        return defaultSeverity != null ? defaultSeverity : ValidationMessageSeverity.ERROR;
    }

    @Override
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean isDisabled() {
        return isDisabled;
    }

    @Override
    public String toString() {
        return "ValidatorSettings [isDisabled=" + isDisabled + ", "
                + (defaultSeverity != null ? "defaultSeverity=" + defaultSeverity + ", " : "")
                + (options != null ? "options=" + options : "") + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((defaultSeverity == null) ? 0 : defaultSeverity.hashCode());
        result = prime * result + (isDisabled ? 1231 : 1237);
        result = prime * result + ((options == null) ? 0 : options.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ValidatorSettings other = (ValidatorSettings) obj;
        if (defaultSeverity != other.defaultSeverity)
            return false;
        if (isDisabled != other.isDisabled)
            return false;
        if (options == null) {
            if (other.options != null)
                return false;
        } else if (!options.equals(other.options))
            return false;
        return true;
    }

}
