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

/**
 * Sorted from most specific to least specific.
 *
 */
public class PackageRestriction implements Comparable<PackageRestriction> {

    public String group;
    public String name;
    public boolean subPackageOnly;

    // default constructor for parameter injection
    public PackageRestriction() {
    }

    public PackageRestriction(String group, String name) {
        this(group, name, false);
    }

    public PackageRestriction(String group, String name, boolean subPackageOnly) {
        this.group = group;
        this.name = name;
        this.subPackageOnly = subPackageOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, subPackageOnly);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PackageRestriction other = (PackageRestriction) obj;
        return Objects.equals(group, other.group) && Objects.equals(name, other.name) && Objects.equals(subPackageOnly, other.subPackageOnly);
    }

    @Override
    public String toString() {
        return "PackageRestrictions [groupName=" + group + ", packageName=" + name + ", subPackageOnly=" + subPackageOnly + "]";
    }

    @Override
    public int compareTo(PackageRestriction o) {
        if (this.name != null) {
            if (o.name == null) {
                return -1;
            }
        } else {
            if (o.name != null) {
                return 1;
            }
        }
        if (this.group != null) {
            if (o.group == null) {
                return -1;
            }
        } else {
            if (o.group != null) {
                return 1;
            }
        }
        return 0;
    }
}
