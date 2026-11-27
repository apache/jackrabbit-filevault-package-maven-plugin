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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.felix.cm.file.ConfigurationHandler;
import org.apache.felix.cm.json.Configurations;
import org.apache.jackrabbit.vault.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads OSGi configuration files as they are stored in content packages.
 * <p>
 * Two serialization formats are supported (see
 * <a href="https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#configuration-serialization-formats">Configuration Serialization Formats</a>):
 * <ul>
 * <li>{@code <pid>.cfg.json}: JSON, optionally with comments and type hints such as {@code "name:String[]"}</li>
 * <li>{@code <pid>.config}: the Apache Felix ConfigAdmin format with typed values</li>
 * </ul>
 * Other formats ({@code .cfg} property files and {@code sling:OsgiConfig} nodes) are not supported.
 */
public final class OsgiConfigurationFiles {

    /** File extension of the JSON format. */
    public static final String CFG_JSON_EXTENSION = ".cfg.json";

    /** File extension of the Apache Felix ConfigAdmin format. */
    public static final String CONFIG_EXTENSION = ".config";

    private OsgiConfigurationFiles() {
        // only static methods
    }

    /**
     * Returns the path of a package entry if it is an OSGi configuration below {@code jcr_root}, with {@code /} as
     * separator on every operating system.
     * <p>
     * Archive entry names use the platform's file separator, e.g. {@code jcr_root\apps\example\config\my.Service.cfg.json}
     * on Windows, so the name is normalized before it is checked.
     *
     * @param entryName the name of the entry in the archiver
     * @return the normalized path, e.g. {@code jcr_root/apps/example/config/my.Service.cfg.json}, or {@code null} if the
     *         entry is not below {@code jcr_root} or does not have a supported OSGi configuration extension
     */
    public static @Nullable String getConfigurationPath(@NotNull String entryName) {
        String path = entryName.replace('\\', '/');
        if (!path.startsWith(Constants.ROOT_DIR + "/") || getPid(path) == null) {
            return null;
        }
        return path;
    }

    /**
     * Returns the PID encoded in a configuration file name, e.g. {@code my.Service} for
     * {@code my.Service.cfg.json}.
     *
     * @param fileName the file name (a path is allowed, only the last segment is used)
     * @return the PID or {@code null} if the file name does not have a supported extension
     */
    public static @Nullable String getPid(@NotNull String fileName) {
        String name = getLastPathSegment(fileName);
        if (name.endsWith(CFG_JSON_EXTENSION)) {
            return name.substring(0, name.length() - CFG_JSON_EXTENSION.length());
        }
        if (name.endsWith(CONFIG_EXTENSION)) {
            return name.substring(0, name.length() - CONFIG_EXTENSION.length());
        }
        return null;
    }

    /**
     * Parses a configuration file.
     *
     * @param fileName the file name, used to pick the format
     * @param content the raw file content
     * @return the configuration properties, with Java types (e.g. {@code String[]} for arrays)
     * @throws IOException if the content cannot be parsed
     * @throws IllegalArgumentException if the file name does not have a supported extension
     */
    public static @NotNull Dictionary<String, Object> read(@NotNull String fileName, @NotNull byte[] content) throws IOException {
        if (isJson(fileName)) {
            // the Felix reader also accepts comments and type hints such as "name:String[]"
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
                Hashtable<String, Object> properties = Configurations.buildReader().build(reader).readConfiguration();
                if (properties == null) {
                    throw new IOException("Could not parse JSON configuration " + fileName);
                }
                return properties;
            }
        }
        if (isConfig(fileName)) {
            return toStringKeys(ConfigurationHandler.read(new ByteArrayInputStream(content)));
        }
        throw new IllegalArgumentException("Unsupported OSGi configuration file name " + fileName);
    }

    private static boolean isJson(String fileName) {
        return getLastPathSegment(fileName).endsWith(CFG_JSON_EXTENSION);
    }

    private static boolean isConfig(String fileName) {
        return getLastPathSegment(fileName).endsWith(CONFIG_EXTENSION);
    }

    /** Works with both '/' and '\' as separators, because archive resource names may use either. */
    private static String getLastPathSegment(String path) {
        int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(lastSeparator + 1);
    }

    /** The Felix ConfigAdmin API returns a raw {@link Dictionary}; all its keys are strings. */
    private static Dictionary<String, Object> toStringKeys(Dictionary<?, ?> rawProperties) {
        Hashtable<String, Object> properties = new Hashtable<>();
        Enumeration<?> keys = rawProperties.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            properties.put(key.toString(), rawProperties.get(key));
        }
        return properties;
    }
}
