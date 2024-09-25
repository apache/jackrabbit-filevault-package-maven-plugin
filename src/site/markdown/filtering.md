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

Filtering Extensions
-----

Extensions allow to improve the simple placeholder replacement offered by Maven with domain specific features (useful for content packages). All features are opt-in and need to be explicitly enabled with a dedicated expression(-prefix).

### Standard Extensions

The following extensions ship with the ${project.artifactId}

Extension | Description | Expression | Usage Example | Since
--- | --- | --- | --- | ---
FileVault DocView XML Attribute Escaping | Escapes the interpolated value of the suffix according to [FileVault DocView Escaping Rules](https://jackrabbit.apache.org/filevault/docview.html#Escaping) for using it inside XML attribute values. | `vltattributeescape.<suffix>` | `<myNode myProperty="${vltattributeescape.customMavenProperty1}" />` | 1.4.0

### Custom Extensions

The filtering may be extended through JSR-330 annotated [Sisu][sisu] components implementing the interface [`org.apache.jackrabbit.filevault.maven.packaging.InterpolatorCustomizerFactory`](apidocs/org/apache/jackrabbit/filevault/maven/packaging/InterpolatorCustomizerFactory.html) which creates a `Consumer<Interpolator>` for every Maven project. That callback is called whenever the interpolator has been created for filtering but before it is being applied. A `InterpolatorCustomizerFactory` usually registers additional [`InterpolationPostProcessor`s][codehaus-interpolationpostprocessor] or [`ValueSource`s][codehaus-valuesource] on the given [`Interpolator`][codehaus-interpolator].

They need to be loaded as [plugin dependencies](https://maven.apache.org/guides/mini/guide-configuring-plugins.html#Using_the_.3Cdependencies.3E_Tag).

[codehaus-interpolator]: https://codehaus-plexus.github.io/plexus-interpolation/apidocs/org/codehaus/plexus/interpolation/Interpolator.html
[codehaus-valuesource]: https://codehaus-plexus.github.io/plexus-interpolation/apidocs/org/codehaus/plexus/interpolation/ValueSource.html
[codehaus-interpolationpostprocessor]: https://codehaus-plexus.github.io/plexus-interpolation/apidocs/org/codehaus/plexus/interpolation/InterpolationPostProcessor.html
[sisu]: https://eclipse.dev/sisu/org.eclipse.sisu.inject/index.html
