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

Classifier Handling
===================================================

<!-- MACRO{toc} -->

Overview
--------
All goals of this plugin have support for [Maven classifiers](https://maven.apache.org/pom.html#dependencies). Classifiers are used in Maven if there are multiple target artifacts built from the same `pom.xml`. Examples are content packages for slightly different distributions (e.g. supporting different API versions or Java versions).

As the goals are by default only bound at most once for certain lifecycle phase of the `content-package` packaging but different classifiers require different configuration, you would need to configure explicit additional [plugin executions](https://maven.apache.org/guides/mini/guide-configuring-plugins.html#Using_the_executions_Tag) for the secondary artifacts with classifiers.

Package Build
--------
You should only use classifiers if more than package is generated out of the same `pom.xml`.

*If you just want to deploy the only final artifact with a classifier rather use [maven-deploy-plugin:deploy-file with parameter `classifier`](https://maven.apache.org/plugins/maven-deploy-plugin/deploy-file-mojo.html#classifier).*

Each execution of goals `generate-metadata` and `package` generate only (metadata for) one package, therefore you need to to configure explicit additional [plugin executions](https://maven.apache.org/guides/mini/guide-configuring-plugins.html#Using_the_executions_Tag) for the secondary artifacts with classifiers. The different executions don't conflict with each other as each classifier configuration uses a dedicated workDirectory.

An example configuration for generating two packages out of the same pom.xml might look like this

```
<plugin>
  <groupId>org.apache.jackrabbit</groupId>
  <artifactId>filevault-package-maven-plugin</artifactId>
  <executions>
    <execution>
      <!-- default execution id for primary artifact: https://maven.apache.org/guides/mini/guide-default-execution-ids.html#default-executionids-for-implied-executions -->
      <id>default-generate-metadata</id>
      <configuration>
        <!-- optional configuration -->
      </configuration>
    </execution>
    <execution>
      <id>type-b-generate-metadata</id>
      <goals>
        <goal>generate-metadata</goal>
      </goals>
      <configuration>
        <!-- adjust configuration: e.g. different filter, properties -->
        <classifier>type-b</classifier>
      </configuration>
    </execution>
    <execution>
      <!-- default execution id for primary artifact: https://maven.apache.org/guides/mini/guide-default-execution-ids.html#default-executionids-for-implied-executions -->
      <id>default-package</id>
      <configuration>
        <!-- optional configuration -->
      </configuration>
    </execution>
    <execution>
      <id>type-b-package</id>
      <goals>
        <goal>package</goal>
      </goals>
      <configuration>
        <!-- adjust configuration: e.g. different jcrSource -->
        <classifier>type-b</classifier>
      </configuration>
    </execution>
    <execution>
      <id>default-validate-package</id>
      <configuration>
        <!-- validate both primary and secondary artifact (with classifier "type-b") -->
        <classifier>type-b</classifier>
      </configuration>
    <execution
  </executions>
</plugin>
```

The explicit configuration of the default executions (for the primary artifact) is only necessary if configuration should be set.

Validation
------

In contrast to metadata and package generation, the validation-package goal supports multiple classifiers (and by that multiple packages) in one execution.
You can either use configuration parameter `classifier` (for validating the attached artifact with the given classifier) or `classifiers` (for validating all attached artifacts matching one of the given classifiers).