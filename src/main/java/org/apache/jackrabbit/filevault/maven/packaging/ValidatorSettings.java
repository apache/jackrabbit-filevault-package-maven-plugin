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

import javax.inject.Inject;

import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;

public class ValidatorSettings implements org.apache.jackrabbit.vault.validation.spi.ValidatorSettings {

    @Inject
    private final boolean isDisabled;
    
    private ValidationMessageSeverity defaultSeverity;

    @Inject
    private final Map<String, String> options;
    
    public ValidatorSettings() {
        isDisabled = false;
        options = new HashMap<>();
    }
    
    public ValidatorSettings(ValidationMessageSeverity defaultSeverity) {
        this();
        this.defaultSeverity = defaultSeverity;
    }

    @Inject
    public void setDefaultSeverity(String defaultSeverity) {
        if (defaultSeverity != null) {
            this.defaultSeverity = ValidationMessageSeverity.valueOf(defaultSeverity.toUpperCase());
        }
    }

    protected String addOption(String key, String value) {
        return options.put(key, value);
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

}
