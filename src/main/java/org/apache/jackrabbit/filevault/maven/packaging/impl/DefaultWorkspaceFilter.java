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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.filevault.maven.packaging.Filter;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Holds a list of {@link PathFilterSet}s.
 *
 */
public class DefaultWorkspaceFilter  {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultWorkspaceFilter.class);

    private final List<PathFilterSet> filterSets = new LinkedList<PathFilterSet>();

    public static final String ATTR_VERSION = "version";

    public static final double SUPPORTED_VERSION = 1.0;

    protected double version = SUPPORTED_VERSION;

    private byte[] source;

    /**
     * globally ignored paths. they are not persisted, yet
     */
    private PathFilter globalIgnored;

    public void add(PathFilterSet set) {
        filterSets.add(set);
    }

    public List<PathFilterSet> getFilterSets() {
        return filterSets;
    }

    public PathFilterSet getCoveringFilterSet(String path) {
        if (isGloballyIgnored(path)) {
            return null;
        }
        for (PathFilterSet set: filterSets) {
            if (set.covers(path)) {
                return set;
            }
        }
        return null;
    }

    public boolean contains(String path) {
        if (isGloballyIgnored(path)) {
            return false;
        }
        for (PathFilterSet set: filterSets) {
            if (set.contains(path)) {
                return true;
            }
        }
        return false;
    }

    public boolean covers(String path) {
        if (isGloballyIgnored(path)) {
            return false;
        }
        for (PathFilterSet set: filterSets) {
            if (set.covers(path)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAncestor(String path) {
        for (PathFilterSet set: filterSets) {
            if (set.isAncestor(path)) {
                return true;
            }
        }
        return false;
    }

    public boolean isGloballyIgnored(String path) {
        return globalIgnored != null && globalIgnored.matches(path);
    }

    public void addFilter(Filter filter) {
        add(filter.toPathFilterSet());
    }

    // added for Maven 2.2.1 compatibility
    public void setFilter(Filter filter) {
        add(filter.toPathFilterSet());
    }

    /**
     * Loads the workspace filter from the given file
     * @param file source
     * @throws IOException if an I/O error occurs
     */
    public void load(File file) throws IOException {
        load(new FileInputStream(file));
    }

    public InputStream getSource() {
        if (source == null) {
            generateSource();
        }
        return new ByteArrayInputStream(source);
    }

    public String getSourceAsString() {
        if (source == null) {
            generateSource();
        }
        try {
            return new String(source, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Loads the workspace filter from the given input source
     * @param in source
     * @throws IOException if an I/O error occurs
     */
    public void load(InputStream in) throws IOException {
        try {

            source = IOUtil.toByteArray(in);
            in = getSource();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            //factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(in);
            Element doc = document.getDocumentElement();
            if (!"workspaceFilter".equals(doc.getNodeName())) {
                throw new IOException("<workspaceFilter> expected.");
            }
            String v = doc.getAttribute(ATTR_VERSION);
            if (v == null || "".equals(v)) {
                v = "1.0";
            }
            version = Double.parseDouble(v);
            if (version > SUPPORTED_VERSION) {
                throw new IOException("version " + version + " not supported.");
            }
            read(doc);
        } catch (ParserConfigurationException e) {
            IOException ioe = new IOException("Unable to create configuration XML parser");
            e.initCause(e);
            throw ioe;
        } catch (SAXException e) {
            IOException ioe = new IOException("Configuration file syntax error.");
            e.initCause(e);
            throw ioe;
        } finally {
            IOUtil.close(in);
        }

    }

    private void read(Element elem) throws IOException {
        NodeList nl = elem.getChildNodes();
        for (int i=0; i<nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (!"filter".equals(child.getNodeName())) {
                    throw new IOException("<filter> expected.");
                }
                PathFilterSet def = readDef((Element) child);
                filterSets.add(def);
            }
        }
    }

    private PathFilterSet readDef(Element elem) throws IOException {
        String root = elem.getAttribute("root");
        PathFilterSet def = new PathFilterSet(root == null || root.length() == 0 ? "/" : root);
        // check for import mode
        String mode = elem.getAttribute("mode");
        if (mode != null && mode.length() > 0) {
            def.setImportMode(ImportMode.valueOf(mode.toUpperCase()));
        }
        // check for type
        def.setCleanUp("cleanup".equals(elem.getAttribute("type")));

        // check for filters
        NodeList n1 = elem.getChildNodes();
        for (int i=0; i<n1.getLength(); i++) {
            Node child = n1.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if ("include".equals(child.getNodeName())) {
                    def.addInclude(readFilter((Element) child));
                } else if ("exclude".equals(child.getNodeName())) {
                    def.addExclude(readFilter((Element) child));
                } else {
                    throw new IOException("either <include> or <exclude> expected.");
                }
            }
        }
        return def;
    }

    private PathFilter readFilter(Element elem) throws IOException {
        String pattern = elem.getAttribute("pattern");
        if (pattern == null || "".equals(pattern)) {
            throw new IOException("Filter pattern must not be empty");
        }
        return new DefaultPathFilter(pattern);
    }

    public void generateSource() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            XmlSerializer ser = new MXSerializer();
            ser.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", "    ");
            ser.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-line-separator", "\n");
            ser.setOutput(out, "UTF-8");
            ser.startDocument("UTF-8", null);
            ser.text("\n");
            ser.startTag(null, "workspaceFilter");
            ser.attribute(null, ATTR_VERSION, String.valueOf(version));
            for (PathFilterSet set: filterSets) {

                ser.startTag(null, "filter");
                //attrs = new AttributesImpl();
                //attrs.addAttribute(null, null, "root", "CDATA", set.getRoot());
                ser.attribute(null, "root", set.getRoot());
                if (set.isCleanUp()) {
                    ser.attribute(null, "type", "cleanup");
                }
                if (set.getImportMode() != ImportMode.REPLACE) {
                    //attrs.addAttribute(null, null, "mode", "CDATA", set.getImportMode().name().toLowerCase());
                    ser.attribute(null, "mode", set.getImportMode().name().toLowerCase());
                }
                //ser.startElement(null, null, "filter", attrs);
                for (PathFilterSet.Entry<PathFilter> entry: set.getEntries()) {
                    // only handle path filters
                    PathFilter filter = entry.getFilter();
                    if (filter instanceof DefaultPathFilter) {
                        if (entry.isInclude()) {
                            ser.startTag(null, "include");
                            ser.attribute(null, "pattern", ((DefaultPathFilter) filter).getPattern());
                            ser.endTag(null, "include");
                        } else {
                            ser.startTag(null, "exclude");
                            ser.attribute(null, "pattern", ((DefaultPathFilter) filter).getPattern());
                            ser.endTag(null, "exclude");
                        }
                    } else {
                        throw new IllegalArgumentException("Can only export default path filters, yet.");
                    }
                }
                ser.endTag(null, "filter");
            }
            ser.endTag(null, "workspaceFilter");
            ser.endDocument();
            source = out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setGlobalIgnored(PathFilter ignored) {
        globalIgnored = ignored;
    }

    public void merge(DefaultWorkspaceFilter source) {
        for (PathFilterSet fs: source.getFilterSets()) {
            // check for collision
            for (PathFilterSet mfs: getFilterSets()) {
                if (mfs.getRoot().equals(fs.getRoot())) {
                    throw new IllegalArgumentException("Merging of equal filter roots not allowed for: " + fs.getRoot());
                }
            }
            add(fs);
        }
    }
}