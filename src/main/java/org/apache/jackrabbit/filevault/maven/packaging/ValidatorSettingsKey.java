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

import java.util.Objects;

import org.apache.jackrabbit.vault.packaging.PackageId;

/** 
 * The parsed key as used on {@link AbstractValidateMojo#validatorsSettings}.
 * Implements a comparator which sorts the more generic settings first and the more specific ones later.
 * The exact order is
 * <ol>
 * <li>settings without restrictions</li>
 * <li>settings for subpackages</li>
 * <li>settings for any groupId (*) and a specific artifactId</li>
 * <li>settings for a specific groupId and artifactId</li>
 * </ol>
 * 
 */
public class ValidatorSettingsKey implements Comparable<ValidatorSettingsKey>{

    private static final int LESS_THAN = -1;
    private static final int GREATER_THEN = 1;
    private static final String WILDCARD_FILTER_VALUE = "*";
    final String key;
    final String validatorId;
    final boolean isForSubPackagesOnly;
    final String packageNameFilter;
    final String packageGroupFilter;

    public static ValidatorSettingsKey fromString(String key) {
        String[] parts = key.split(":", 3);
        final String validatorId = parts[0];
        boolean isForSubPackagesOnly = false;
        String packageGroupFilter = null;
        String packageNameFilter = null;
        if (parts.length == 2) {
            if (parts[1].equals("subpackage")) {
                isForSubPackagesOnly = true;
            } else {
                throw new IllegalArgumentException("Invalid validatorSettings key '" + key +"'" );
            }
        }
        // does it contain a package id filter?
        else if (parts.length == 3) {
            packageGroupFilter = parts[1];
            packageNameFilter = parts[2];
        }
        return new ValidatorSettingsKey(key, validatorId, isForSubPackagesOnly, packageGroupFilter, packageNameFilter);
    }
 
    ValidatorSettingsKey(String key, String validatorId, boolean isForSubPackagesOnly, String packageGroupFilter, String packageNameFilter) {
        super();
        this.key = key;
        this.validatorId = validatorId;
        this.isForSubPackagesOnly = isForSubPackagesOnly;
        this.packageGroupFilter = packageGroupFilter;
        this.packageNameFilter = packageNameFilter;
    }

    public String getKey() {
        return key;
    }

    public String getValidatorId() {
        return validatorId;
    }

    public boolean matchesForPackage(PackageId packageId, boolean isSubPackage) {
        if (!isSubPackage && isForSubPackagesOnly) {
            return false;
        }
        if (packageNameFilter != null) {
            if (!packageNameFilter.equals(packageId.getName())) {
                return false;
            }
        }
        if (packageGroupFilter != null && !WILDCARD_FILTER_VALUE.equals(packageGroupFilter)) {
            if (!packageGroupFilter.equals(packageId.getGroup())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(ValidatorSettingsKey o) {
        if (packageNameFilter != null && o.packageNameFilter != null) {
            if (WILDCARD_FILTER_VALUE.equals(packageGroupFilter)) {
                if (WILDCARD_FILTER_VALUE.equals(o.packageGroupFilter)) {
                    return packageNameFilter.compareTo(o.packageNameFilter);
                } else {
                    return LESS_THAN;
                }
            } else {
                if (WILDCARD_FILTER_VALUE.equals(o.packageGroupFilter)) {
                    return GREATER_THEN;
                } else {
                    return packageNameFilter.compareTo(o.packageNameFilter);
                }
            }
        } else if (packageNameFilter == null && o.packageNameFilter != null) {
            return LESS_THAN;
        } else if (packageNameFilter != null && o.packageNameFilter == null) {
            return GREATER_THEN;
        } else if (!isForSubPackagesOnly && o.isForSubPackagesOnly) {
            return LESS_THAN;
        } else if (isForSubPackagesOnly && !o.isForSubPackagesOnly) {
            return GREATER_THEN;
        }
        return validatorId.compareTo(o.validatorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isForSubPackagesOnly, key, packageGroupFilter, packageNameFilter, validatorId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ValidatorSettingsKey other = (ValidatorSettingsKey) obj;
        return isForSubPackagesOnly == other.isForSubPackagesOnly && Objects.equals(key, other.key)
                && Objects.equals(packageGroupFilter, other.packageGroupFilter)
                && Objects.equals(packageNameFilter, other.packageNameFilter) && Objects.equals(validatorId, other.validatorId);
    }

    @Override
    public String toString() {
        return "ValidatorSettingsKey [" + (key != null ? "key=" + key + ", " : "")
                + (validatorId != null ? "validatorId=" + validatorId + ", " : "") + "isForSubPackagesOnly=" + isForSubPackagesOnly + ", "
                + (packageNameFilter != null ? "packageNameFilter=" + packageNameFilter + ", " : "")
                + (packageGroupFilter != null ? "packageGroupFilter=" + packageGroupFilter : "") + "]";
    }
}
