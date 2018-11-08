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


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * This helper class checks for index definitions within packages
 */
public class FileValidator {
    public boolean isContainingIndexDef;

    //store entries of index found in META-INF/vault/filter.xml
    List<String> indexPaths = new ArrayList<String>();

    //store entries of index found in _oak_index/.content.xml
    Map<String, String> foundIndexes = new HashMap<String, String>();

    /**
     * Checks if the given input stream and file name refers to a index definition or filter file covering an oak index.
     * @param artifactFileInputStream the input stream of the artifact to check
     * @param artifactName the file name within the content package (using forward slashes as separators) of the artifact to check
     * @throws IOException if an I/O error occurs
     * @throws MojoExecutionException if an internal execution error occurs
     */
    public void lookupIndexDefinitionInArtifact(InputStream artifactFileInputStream, String artifactName) throws IOException, MojoExecutionException {
        // in case this is a subpackage
        if (artifactName.endsWith("zip")) {
            ZipInputStream zipArtifactStream = new ZipInputStream(artifactFileInputStream);
            String entryName = "";
            ZipEntry entry = zipArtifactStream.getNextEntry();
            while (entry != null) {
                // entryName must only contain forward slashes as separators
                entryName = entry.getName();
                if (entryName.endsWith("zip")) {
                    //recur if the entry is a zip file
                    lookupIndexDefinitionInArtifact(zipArtifactStream, artifactName + "/" + entryName);
                } else if ("META-INF/vault/filter.xml".equals(entryName) || entryName.contains("/_oak_index/")) {
                    //if entry is an vault filter file or an oak index definition file, parse it to check for indexes
                    this.parseXMLForIndexDefinition(zipArtifactStream, entryName, artifactName + "/" + entryName);
                }
                entry = zipArtifactStream.getNextEntry();
            }
        } else if ("META-INF/vault/filter.xml".equals(artifactName) || artifactName.contains("/_oak_index/")) {
            //if entry is an vault filter file or an oak index definition file, parse it to check for indexes
            this.parseXMLForIndexDefinition(artifactFileInputStream, artifactName, artifactName);
        }
    }

    private void parseXMLForIndexDefinition(InputStream xmlInputStream, String entryName, String absoluteEntryName) throws MojoExecutionException {
        try {
            Document xml = Jsoup.parse(xmlInputStream, "UTF-8", "", Parser.xmlParser());
            if ("META-INF/vault/filter.xml".equals(entryName)) {
                for (Element filter : xml.select("filter[root]")) {
                    String root = filter.attr("root");
                    int oakIndexPosition = root.indexOf("/oak:index");
                    if (oakIndexPosition >= 0) {
                        isContainingIndexDef = true;
                        int nextSlash = root.indexOf("/", oakIndexPosition + "oak:index".length() + 2);
                        String baseIndexPath;

                        if (nextSlash > -1) {
                            baseIndexPath = root.substring(0, nextSlash);
                        } else {
                            baseIndexPath = root;
                        }
                        indexPaths.add(baseIndexPath);
                    }

                }
            } else if (entryName.contains("/_oak_index/")) {
                String basePath = entryName.substring(8).replace("_oak_index", "oak:index").replace("/.content.xml", "");

                // search for "jcr:primaryType" properties that have a value ending in oak:QueryIndexDefinition
                // as it can also be "{Name}oak:QueryIndexDefinition"
                for (Element element : xml.select("*[jcr:primaryType$=oak:QueryIndexDefinition]")) {
                    String xmlPath = getXmlPath(element);
                    String jcrPath = StringUtils.removeStart(xmlPath, "/jcr:root");
                    foundIndexes.put(basePath + jcrPath, absoluteEntryName);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error while trying to parse xml " + absoluteEntryName, e);
        }
    }

    private static String getXmlPath(Element element) {
        if (element.ownerDocument() == element) {
            return "";
        } else {
            return getXmlPath(element.parent()) + "/" + element.nodeName();
        }
    }

    public String getMessageWithPathsOfIndexDef() {
        StringBuilder msg = new StringBuilder();

        for (String path : indexPaths) {
            String completePath = foundIndexes.get(path);

            if (completePath != null) {
                msg.append(" Package contains index ").append(path).append(" in ").append(completePath).append(".").append(System.lineSeparator());
            } else {
                msg.append(" Package contains index ").append(path).append(" in filter, but not in package. Existing index will be removed.").append(System.lineSeparator());
            }
        }
        return msg.toString();
    }
}
