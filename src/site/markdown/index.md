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

# Introduction

Welcome to Apache Jackrabbit FileVault Package Maven Plugin.

The Apache Jackrabbit FileVault Package Maven Plugin is an Apache Maven Plugin that simplifies the creation of
content package artifacts. The content packages can later be used to install content into a JCR repository
using the Apache Jackrabbit FileVault packaging runtime.

Apache Jackrabbit FileVault is a project of the Apache Software Foundation.

## Usage

As this Maven plugin comes with [Maven extensions](https://maven.apache.org/guides/mini/guide-using-extensions.html) (for defining custom bindings for `default` lifecycle and a custom artifact handler for type/packaging `content-package`) it needs to be loaded accordingly

```
<plugin>
  <groupId>${project.groupId}</groupId>
  <artifactId>${project.artifactId}</artifactId>
  <version>${project.version}</version>
  <extensions>true</extensions>
</plugin>
```

Further details on the individual goals are available at [Goals](plugin-info.html).

## Plugin bindings for `content-package` packaging

```
<phases>
  <process-resources>org.apache.maven.plugins:maven-resources-plugin:resources</process-resources>
  <compile>org.apache.maven.plugins:maven-compiler-plugin:compile</compile>
  <generate-test-sources>org.apache.jackrabbit:filevault-package-maven-plugin:generate-metadata</generate-test-sources>
  <process-test-sources>org.apache.jackrabbit:filevault-package-maven-plugin:validate-files</process-test-sources>
  <process-test-resources>org.apache.maven.plugins:maven-resources-plugin:testResources</process-test-resources>
  <test-compile>org.apache.maven.plugins:maven-compiler-plugin:testCompile</test-compile>
  <test>org.apache.maven.plugins:maven-surefire-plugin:test</test>
  <package>org.apache.jackrabbit:filevault-package-maven-plugin:package</package>
  <verify>org.apache.jackrabbit:filevault-package-maven-plugin:validate-package</verify>
  <install>org.apache.maven.plugins:maven-install-plugin:install</install>
  <deploy>org.apache.maven.plugins:maven-deploy-plugin:deploy</deploy>
</phases>
```

Those bindings are defined in [`ContentPackageLifecycleMappingProvider`](https://github.com/apache/jackrabbit-filevault-package-maven-plugin/blob/master/src/main/java/org/apache/jackrabbit/filevault/maven/packaging/impl/extensions/ContentPackageLifecycleMappingProvider.java).
The default bindings for other packagings are documented at [Plugin Bindings for Default Lifecycle Reference](https://maven.apache.org/ref/3.8.6/maven-core/default-bindings.html).

## Downloads

The latest FileVault Package Maven Plugin sources are available at <https://github.com/apache/jackrabbit-filevault-package-maven-plugin>.

See also our [releases](https://jackrabbit.apache.org/downloads.html) on the Jackrabbit
download page for slightly more stable versions of the codebase.

## Mailing Lists

To get involved with the Apache Jackrabbit project, start by having a
look at our website and joining our mailing lists. For more details about
Jackrabbit mailing lists as well as links to list archives, please see the [Mailing List](mail-lists.html) section.


## Latest development

See the [development overview](dev.html) page for more information.

## Credits

See <https://jackrabbit.apache.org/jackrabbit-team.html> for the list of
Jackrabbit committers and main contributors.
