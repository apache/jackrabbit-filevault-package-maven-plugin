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

Release Notes -- Apache Jackrabbit FileVault Package Maven Plugin -- Version 1.1.0
==================================================================================

Introduction
------------
The Apache Jackrabbit FileVault package Maven plugin is an Apache Maven plugin that simplifies the creation of
content package Maven artifacts. The content packages can later be used to install content into a JCR repository
using the Apache Jackrabbit FileVault packaging runtime.


Changes in Jackrabbit FileVault Package Maven Plugin 1.1.0
----------------------------------------------------------

#### Bug Fixes
* [JCRVLT-342] - File leak in ProjectBuilder.getPluginVersion()
* [JCRVLT-343] - Check for index definitions also considers ACL entries
* [JCRVLT-351] - IOE thrown in case of no filter and <failOnEmptyFilter>false</failOnEmptyFilter>
* [JCRVLT-354] - False warnings for files not being included in the package due to being outside of filter roots
* [JCRVLT-355] - False error in case embedded file/subpackage is overwritten by jcrRootSourceDirectory
* [JCRVLT-363] - .content.xml of ancestor nodes not included in package
* [JCRVLT-381] - m2e Integration for goal "format-xml" might lead to endless loops
* [JCRVLT-386] - Eclipse/m2e: Could not get metadata for dependencies which are resolved via m2e workspace resolution or for multimodule builds with phases < package
* [JCRVLT-387] - Support reproducible builds

#### New Features
* [JCRVLT-232] - Enforce constraints for package types
* [JCRVLT-345] - Support pluggable node/file/filter validators

#### Improvements
* [JCRVLT-350] - Make all goals support parallel builds (mark as threadSafe)
* [JCRVLT-361] - Validate that all includes/excludes are below the filter root
* [JCRVLT-365] - Migrate from JSR 305 to Jetbrains annotations
* [JCRVLT-370] - New filter option below embeddeds and subpackages which removes other versions
* [JCRVLT-371] - Include Maven groupId and artifactId of each dependency in the MANIFEST.MF and the properties.xml
* [JCRVLT-373] - Log overlapping files from workDirectory and metaInf source directory
* [JCRVLT-389] - Optionally support Maven Filtering during packaging
* [JCRVLT-399] - Update to Jackrabbit 2.20.0  / Oak 1.20.0

#### Tasks
* [JCRVLT-392] - Support Matrix builds on Windows/Linux with Travis
* [JCRVLT-393] - Java 13 Build Support


Changes in Jackrabbit FileVault Package Maven Plugin 1.0.4
----------------------------------------------------------

#### Bug Fixes
* [JCRVLT-279] - Emit warning/error in case embedded file/subpackage is overwritten by jcrRootSourceDirectory
* [JCRVLT-320] - filevault-package-maven-plugin:package does not work with Java >= 9
* [JCRVLT-324] - In case of a long project description in CDATA the resulting MANIFEST.MF is invalid
* [JCRVLT-326] - Embedded files Map stored in project properties breaks Jenkins+Artifactory integration

#### Improvements
* [JCRVLT-321] - Warn for files not being included in the package due to being outside of filter roots
* [JCRVLT-331] - Incorrect embedded base name used in generate-metadata goal during CLI execution of `mvn test`


Changes in Jackrabbit FileVault Package Maven Plugin 1.0.3
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
