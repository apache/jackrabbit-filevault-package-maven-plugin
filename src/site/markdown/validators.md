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

Validators
===================================================

<!-- MACRO{toc} -->

Overview
--------
With the goals [`validate-package`](validate-package-mojo.html) and [`validate-files`](validate-files-mojo.html) it is possible to run validation on top of the given package or the package source files.
Validation itself is implemented with the [Jackrabbit FileVault Validation Framework][vlt.validation] which also contains a lot of standard validators.
Look there on which validators are contained and what options they provide.


Configuration
--------
It is possible to adjust every validator registered in the system (both default and external validators) with the parameter `validatorsSettings`. This is a map with the keys being the validator ids (optionally suffixed by `__` and some arbitrary string) and the values being complex objects.
Here is an example configuration

```
<configuration>
  <validatorsSettings>
    <jackrabbit-filter>
      <isDisabled>false</isDisabled><!-- false is default, true disables the validator completely, all other setting are not relevant then -->
      <defaultSeverity>error</defaultSeverity><!-- valid severities: debug, info, warn, error (default) -->
      <options>
        <severityForUncoveredAncestorNodes>error</severityForUncoveredAncestorNodes>
      </options>
    </jackrabbit-filter>
  </validatorsSettings>
</configuration>
```

The options for the individual validators together with the validator ids are documented at [Jackrabbit FileVault Validation Framework][vlt.validation].

### Package Restriction

It is possible to restrict a setting for a particular validator id to certain packages only.

```
<configuration>
  <validatorsSettings>
    <!-- more specific items  potentially overwrite more generic items -->
    <jackrabbit-filter__formypackage>
      <isDisabled>true</isDisabled><!-- disable the validator for a specific package -->
      <packageRestriction>
        <name>myPackageName</name><!-- optional, if set the surrounding settings apply only to packages with the given name -->
        <group>myPackageGroup</group><!-- optional, if set the surrounding settings apply only to packages with the given group -->
        <subPackageOnly>true</subPackageOnly><!-- optional, if set to true the surrounding settings apply only to subpackages otherwise to all package types -->
      </packageRestriction>
    </jackrabbit-filter__formypackage>
  </validatorsSettings>
</configuration>

```

**It is necessary that each element below `validatorsSettings` has a unique name (as otherwise it will overwrite previous elements with the same name**

In order to achieve that the validator id can be suffixed with `__` and an arbitrary string in the configuration element name.

Severities
------
The validation message severities have an impact on the build outcome. By default every validation message with severity `error` will fail the build. One can adjust this setting by leveraging the configuration parameter `failOnValidationWarnings`.

Dependencies
------
All package dependencies are resolved via the [Maven Artifact Resolver][maven.resolver]. If package dependencies are only given via their package id a best guess resolution is tried by using the `name` as `artifactId` and `group` as `groupId`. You can tweak this fallback behaviour by leveraging the configuration parameter `mapPackageDependencyToMavenGa`.

Reference External Validators
-------
To reference an external validator it just needs to be given as [plugin dependency][maven.plugindependencies] to the `filevault-package-maven-plugin`.

```
<plugin>
  <groupId>${project.groupId}</groupId>
  <artifactId>${project.artifactId}</artifactId>
  <version>${project.version}</version>
  <dependencies>
    <!-- some validator extension -->
    <dependency>
      <artifactId>myartifact</artifactId>
      <groupId>mygroup</groupId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>
</plugin>
``` 

[vlt.validation]: https://jackrabbit.apache.org/filevault/validation.html
[maven.plugindependencies]: https://maven.apache.org/guides/mini/guide-configuring-plugins.html#Using_the_dependencies_Tag
[maven.resolver]: https://maven.apache.org/resolver/index.html
