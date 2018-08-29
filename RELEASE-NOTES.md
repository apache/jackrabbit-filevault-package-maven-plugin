<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
  -->

Release Notes -- Apache Jackrabbit FileVault Package Maven Plugin -- Version 1.0.2
==================================================================================

Introduction
------------
The Apache Jackrabbit FileVault package maven plugin is an Apache maven plugin that simplifies the creation of
content package maven artifacts. The content packages can later be used to install content into a JCR repository
using the Apache Jackrabbit FileVault packaging runtime.


Changes in Jackrabbit FileVault Package Maven Plugin 1.0.2
----------------------------------------------------------

#### Bug Fixes
* [JCRVLT-256] Package Maven Plugin: NPE when dependency has no manifest
* [JCRVLT-276] Switch to timezone designators being understood by ISO8601.parse(...)
* [JCRVLT-272] analyze-classes mojo can fail with "Access denied" for multi-module projects
* [JCRVLT-268] scanning for oak index does not work on windows systems

#### New Features
* [JCRVLT-288] Support XML Docview formatting in a dedicated goal

#### Improvement
* [JCRVLT-246] Generate metadata like filter.xml and properties.xml in dedicated goal
* [JCRVLT-274] Package Maven Plugin: Support multiple types and classifiers as filter within embedded/subpackage section
* [JCRVLT-315] Improve error message in case of embedding invalid sub packages


Changes in Jackrabbit FileVault Package Maven Plugin 1.0.1
----------------------------------------------------------

#### Bug Fixes
* [JCRVLT-218] Repository structure package satisfies too aggressively
* [JCRVLT-219] Internal ancestors not used for dependency validation
* [JCRVLT-222] analyze-classes goal should be marked as ignored for m2e
* [JCRVLT-237] Fix description on how import-package manifests are generated
* [JCRVLT-241] Goal analyze-classes: Dependencies with type "bundle" not correctly considered
* [JCRVLT-242] Link for the web access of Jackrabbit's FileVault Package source repository is wrong
* [JCRVLT-244] Package Maven Plugin: Fix resource leaks in integration tests
* [JCRVLT-245] Package Maven Plugin: ProjectBuilder.verifyPackageProperty swaps expected/actual value
* [JCRVLT-253] Problems with Configuration inside an Execution with Filters

#### Improvements
* [JCRVLT-217] Create tests for JCRVLT-207 (HTL) as soon as plugin is released
* [JCRVLT-224] Use filevault-core instead of copy-pasting code
* [JCRVLT-231] Clarify repositoryStructurePackages parameter
* [JCRVLT-234] Remove irrelevant @Parameter annotations on field in classes which are not mojos
* [JCRVLT-236] Package Maven Plugin: Improve packageType param documentation
* [JCRVLT-238] Remove classesDirectory parameter
* [JCRVLT-243] VaultMojo: extend javadoc for all complex type parameters
* [JCRVLT-252] Package Maven Plugin: Update to htl-maven-plugin 1.1.2

#### New Features
* [JCRVLT-230] Allow to give an explicit directory as source for META-INF/vault
* [JCRVLT-239] Package Maven Plugin: Add "accessControlHandling" property
* [JCRVLT-240] Package Maven Plugin: Support for package thumbnails


Changes in Jackrabbit FileVault Package Maven Plugin 1.0.0
----------------------------------------------------------

#### Notes
Version 1.0.0 is the initial version of this contribution to the Apache
Jackrabbit project.

For more detailed information about all the changes in this and other
FileVault releases, please see the FileVault issue tracker at
https://issues.apache.org/jira/browse/JCRVLT

#### Bug Fixes
* [JCRVLT-206] Auto import-statement too big in case no compiled classes found

#### Improvements
* [JCRVLT-202] Import Adobe's content package maven plugin
* [JCRVLT-204] Create documentation site for maven plugin
* [JCRVLT-207] Make auto-import package a analyzer work with htl scripts
* [JCRVLT-209] Always write to the filter.xml inside the vaultDir but never to filter-plugin-generated.xml

#### New Features
* [JCRVLT-205] Add support for cleanup filter entries
* [JCRVLT-210] Allow to define excludes for the ContentPackageArchiver to prevent copying of script files from src to target
* [JCRVLT-214] auto-import package analyzer needs option to exclude compiletime bundles


Release Contents
----------------
This release consists of a single source archive packaged as a zip file.
The archive can be unpacked with the jar tool from your JDK installation.
See the [README](./README.md) file for instructions on how to build this release.

The source archive is accompanied by SHA1 and MD5 checksums and a PGP
signature that you can use to verify the authenticity of your download.
The public key used for the PGP signature can be found at
https://www.apache.org/dist/jackrabbit/KEYS

About Apache Jackrabbit
-----------------------
Apache Jackrabbit is a fully conforming implementation of the Content
Repository for Java Technology API (JCR). A content repository is a
hierarchical content store with support for structured and unstructured
content, full text search, versioning, transactions, observation, and
more.

For more information, visit http://jackrabbit.apache.org/

About The Apache Software Foundation
------------------------------------
Established in 1999, The Apache Software Foundation provides organizational,
legal, and financial support for more than 100 freely-available,
collaboratively-developed Open Source projects. The pragmatic Apache License
enables individual and commercial users to easily deploy Apache software;
the Foundation's intellectual property framework limits the legal exposure
of its 2,500+ contributors.

For more information, visit http://www.apache.org/

Trademarks
----------
Apache Jackrabbit, Jackrabbit, Apache, the Apache feather logo, and the Apache
Jackrabbit project logo are trademarks of The Apache Software Foundation.
