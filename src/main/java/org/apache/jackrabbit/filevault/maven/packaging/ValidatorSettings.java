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
import java.util.Objects;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;

/**
 * Mutable implementation of org.apache.jackrabbit.vault.validation.spi.ValidatorSettings. Used as mojo parameter type.
 */
public class ValidatorSettings implements org.apache.jackrabbit.vault.validation.spi.ValidatorSettings {

    private Boolean isDisabled;
    
    private ValidationMessageSeverity defaultSeverity;

    private final Map<String, String> options;
    
    public ValidatorSettings() {
        options = new HashMap<>();
    }

    public ValidatorSettings merge(ValidatorSettings otherSettings) {
        // fields of current object take precedence (if not null)
        ValidatorSettings mergedSettings = new ValidatorSettings();
        mergedSettings.isDisabled = isDisabled != null ? isDisabled : otherSettings.isDisabled;
        mergedSettings.defaultSeverity = defaultSeverity != null ? defaultSeverity : otherSettings.defaultSeverity;
        mergedSettings.options.putAll(options);
        for (Map.Entry<String, String> entry : otherSettings.getOptions().entrySet()) {
            if (!options.containsKey(entry.getKey())) {
                mergedSettings.addOption(entry.getKey(), entry.getValue());
            }
        }
        return mergedSettings;
    }

    public ValidatorSettings setDefaultSeverity(String defaultSeverity) {
        if (defaultSeverity != null) {
            this.defaultSeverity = ValidationMessageSeverity.valueOf(defaultSeverity.toUpperCase());
        }
        return this;
    }

    public void setDefaultSeverity(ValidationMessageSeverity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
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
        return (isDisabled != null) && isDisabled.booleanValue();
    }

    public void setIsDisabled(boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    @Override
    public String toString() {
        return "ValidatorSettings [" + (isDisabled != null ? "isDisabled=" + isDisabled + ", " : "")
                + (defaultSeverity != null ? "defaultSeverity=" + defaultSeverity + ", " : "")
                + (options != null ? "options=" + options : "") + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultSeverity, isDisabled, options);
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
        return defaultSeverity == other.defaultSeverity && Objects.equals(isDisabled, other.isDisabled)
                && Objects.equals(options, other.options);
    }

    
}
