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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import org.apache.jackrabbit.filevault.maven.packaging.PackageId;
import org.apache.jackrabbit.filevault.maven.packaging.VaultMojo;

/**
 * Very simple class that reads basic package info from a file.
 */
public class PackageInfo {

    private final PackageId id;

    private final DefaultWorkspaceFilter filter;

    private final PackageType packageType;

    private PackageInfo(PackageId id, DefaultWorkspaceFilter filter, PackageType packageType) {
        this.id = id;
        this.filter = filter;
        this.packageType = packageType;
    }

    /**
     * Reads the package file.
     * @param file the file.
     * @return {@code true} if the package is valid.
     * @throws IOException if an error occurrs.
     */
    public static PackageInfo read(@Nonnull File file) throws IOException {
        PackageId id = null;
        DefaultWorkspaceFilter filter = null;
        PackageType packageType = PackageType.MIXED;

        ZipFile zip = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements() && (id == null || filter == null)) {
            ZipEntry e = entries.nextElement();
            if (JarFile.MANIFEST_NAME.equalsIgnoreCase(e.getName())) {
                Manifest mf = new Manifest(zip.getInputStream(e));
                String idStr = mf.getMainAttributes().getValue(VaultMojo.MF_KEY_PACKAGE_ID);
                if (idStr != null) {
                    id = PackageId.fromString(idStr);
                }
                String roots = mf.getMainAttributes().getValue(VaultMojo.MF_KEY_PACKAGE_ROOTS);
                filter = new DefaultWorkspaceFilter();
                if (roots != null) {
                    for (String root: StringUtils.split(roots, ',')) {
                        filter.add(new PathFilterSet(root));
                    }
                }
                String type = mf.getMainAttributes().getValue(VaultMojo.MF_KEY_PACKAGE_TYPE);
                if (type != null) {
                    packageType = PackageType.valueOf(type.toUpperCase());
                }
            } else if (VaultMojo.PROPERTIES_FILE.equalsIgnoreCase(e.getName())) {
                Properties props = new Properties();
                props.loadFromXML(zip.getInputStream(e));
                String version = props.getProperty("version");
                if (version == null) {
                    version = "";
                }
                String group = props.getProperty("group");
                String name = props.getProperty("name");
                if (group != null && name != null) {
                    id = new PackageId(group, name, version);
                } else {
                    // check for legacy packages that only contains a 'path' property
                    String path = props.getProperty("path");
                    if (path == null || path.length() == 0) {
                        path = "/etc/packages/unknown";
                    }
                    id = new PackageId(path, version);
                }
            } else if (VaultMojo.FILTER_FILE.equalsIgnoreCase(e.getName())) {
                filter = new DefaultWorkspaceFilter();
                filter.load(zip.getInputStream(e));
            }
        }
        zip.close();
        if (id == null || filter == null) {
            return null;
        } else {
            return new PackageInfo(id, filter, packageType);
        }
    }

    /**
     * Returns the package id.
     * @return the package id.
     */
    public PackageId getId() {
        return id;
    }

    /**
     * Returns the workspace filter
     * @return the filter
     */
    public DefaultWorkspaceFilter getFilter() {
        return filter;
    }

    /**
     * Returns the package type.
     * @return the package type
     */
    public PackageType getPackageType() {
        return packageType;
    }
}