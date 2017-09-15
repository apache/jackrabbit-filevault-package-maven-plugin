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

/**
 * Implements a package version.
 * @since 2.0
 */
public class Version implements Comparable<Version> {

    /**
     * The empty version
     */
    public static final Version EMPTY = new Version("", new String[0]);

    /**
     * internal string representation
     */
    private final String str;

    /**
     * All segments of this version
     */
    private final String[] segments;

    /**
     * Constructs a new version from the given string.
     * @param str the version string.
     * @deprecated use {@link Version#create(String)} instead.
     */
    public Version(String str) {
        this(str, str.split("\\."));
    }

    /**
     * Creates a new version from the given string.
     * @param str the version string.
     * @return the new version or {@link Version#EMPTY} if <code>str</code> is an empty string.
     * @since 2.2.4
     */
    public static Version create(String str) {
        if (str == null || str.length() == 0) {
            return Version.EMPTY;
        }else {
            return new Version(str, str.split("\\."));
        }
    }

    /**
     * Creates a new version from version segments
     * @param segments version segments
     * @return the new version or {@link Version#EMPTY} if <code>segments</code> is empty.
     * @since 2.2.4
     */
    public static Version create(String[] segments) {
        if (segments == null || segments.length == 0) {
            return Version.EMPTY;
        } else {
            StringBuilder b = new StringBuilder();
            String delim = "";
            for (String s:segments) {
                b.append(delim);
                b.append(s);
                delim=".";
            }
            return new Version(b.toString(), segments);
        }
    }

    /**
     * Internal constructor
     * @param str string
     * @param segments segments
     */
    private Version(String str, String[] segments) {
        if (str == null) {
            throw new NullPointerException("Version String must not be null.");
        }
        this.str = str;
        this.segments = segments;
    }

    @Override
    public int hashCode() {
        return str.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                o instanceof Version && str.equals(((Version) o).str);

    }

    @Override
    public String toString() {
        return str;
    }

    /**
     * Returns all segments.
     * @return all segments.
     */
    public String[] getNormalizedSegments() {
        return segments;
    }

    /**
     * Compares this version to the given one, segment by segment with a special
     * "SNAPSHOT" handling.
     *
     * Examples:
     * "1" < "2"
     * "1.0" < "2"
     * "2.0.1" < "2.1"
     * "2.1" < "2.1.1"
     * "2.9" < "2.11"
     * "2.1" > "2.1-SNAPSHOT"
     * "2.1" > "2.1-R1234556"
     * "2.1-R12345" < "2.1-SNAPSHOT"
     *
     * @param o the other version
     * @return  a negative integer, zero, or a positive integer as this version
     *		is less than, equal to, or greater than the specified version.
     */
    public int compareTo(Version o) {
        String[] oSegs = o.getNormalizedSegments();
        for (int i=0; i< Math.min(segments.length, oSegs.length); i++) {
            String s1 = segments[i];
            String s2 = oSegs[i];
            if (s1.equals(s2)) {
                continue;
            }
            try {
                int v1 = Integer.parseInt(segments[i]);
                int v2 = Integer.parseInt(oSegs[i]);
                if (v1 != v2) {
                    return v1 - v2;
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            String ss1[] = s1.split("-");
            String ss2[] = s2.split("-");
            for (int j=0; j< Math.min(ss1.length, ss2.length); j++) {
                String c1 = ss1[j];
                String c2 = ss2[j];
                try {
                    int v1 = Integer.parseInt(c1);
                    int v2 = Integer.parseInt(c2);
                    if (v1 != v2) {
                        return v1 - v2;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
                int c = c1.compareTo(c2);
                if (c!=0) {
                    return c;
                }
            }
            int c = ss1.length - ss2.length;
            if (c != 0) {
                return -c;
            }
        }
        return segments.length - oSegs.length;
    }
}