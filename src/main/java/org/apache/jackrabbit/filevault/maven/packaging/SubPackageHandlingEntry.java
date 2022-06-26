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

import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling.Entry;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling.Option;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;

/** 
 * Defines a sub package handling entry encapsulating option, groupName and packageName.
 * This is a helper bean for creating {@link Entry} objects.
 */
public class SubPackageHandlingEntry {

    private String groupName;

    private String packageName;

    private Option option;

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Sets the access control handling.
     * Explicit setter methods to allow specifying the option in both lower and upper case.
     * 
     * @param option the string representation of the access control handling
     * @throws MojoFailureException if an error occurs
     */
    public void setOption(String option) throws MojoFailureException {
        try {
            this.option = Option.valueOf(option.toUpperCase());
        } catch (IllegalArgumentException e) {
            // TODO: emit in lower case
            throw new MojoFailureException("Invalid option specified: " + option +".\n" +
                    "Must be one of '" + StringUtils.join(AccessControlHandling.values(), "','") + "'.");
        }
    }

    public Entry toEntry() {
        if (option == null) {
            throw new IllegalArgumentException("Field 'option' must be set for each SubPackageHandlingEntry!");
        }
        return new Entry(groupName, packageName, option);
    }
}
