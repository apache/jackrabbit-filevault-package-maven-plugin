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

Filtering
===================================================

<!-- MACRO{toc} -->

Overview
--------
The `package` goal supports filtering (i.e. placeholder replacement) similar to the [maven-resources-plugin](https://maven.apache.org/plugins/maven-resources-plugin/examples/filter.html) in arbitrary package source files.

*As filtering may destroy binary files you should make sure that filtering is only applied to textual files.*

Configuration
--------
You need to set plugin parameter `enableJcrRootFiltering` and/or `enableMetaInfFiltering` to `true` to enable filtering at all.

There are [several other parameters](package-mojo.html) which allow to further tweak the filtering behavior.

An example configuration which enables filtering on all `.content.xml` files in the jcr root source folder looks like this:

```
<plugin>
  <groupId>${project.groupId}</groupId>
  <artifactId>${project.artifactId}</artifactId>
  <version>${project.version}</version>
  <executions>
    <execution>
      <!-- default execution id for primary artifact: https://maven.apache.org/guides/mini/guide-default-execution-ids.html#default-executionids-for-implied-executions -->
      <id>default-package</id>
      <configuration>
        <enableJcrRootFiltering>true</enableJcrRootFiltering><!--only enable filtering on jcr root source files-->
        <filterFiles>
          <!-- additional properties file specifying keys and values -->
          <filterFile>filter.properties</filterFile>
        </filterFiles>
        <!-- only enable filtering on .content xml files -->
        <filteredFilePatterns>
          <filteredFilePattern>**/.content.xml</filteredFilePattern>
        </filteredFilePatterns>
      </configuration>
    </execution>
  </executions>
</plugin>
```

