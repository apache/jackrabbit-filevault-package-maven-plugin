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
Migrating From Adobe's Content Package Maven Plugin
===================================================

Overview
--------
Some of the functionality of former `com.day.jcr.vault:content-package-maven-plugin` (Adobe's plugin) was not retained
when the code was migrated to the  `org.apache.jackrabbit:filevault-package-maven-plugin` (Jackrabbit's plugin).
In particular, all the goals dealing with the Adobe CRX Package Manager interoperability were removed.

Starting with the [1.0.2 release][0] of Adobe's plugin, all the content packaging functionality
was removed, so that both plugins can now be used in the same project (pom).

Projects that want to migrate to Jackrabbit's plugin just need to replace the maven coordinates of the
content package plugin. And, if the package manager goals are sill needed, add Adobe's plugin again.

Example
-------
An example plugin section could look like this:

```
    <!-- this plugin creates the content package artifact --> 
    <plugin>
        <groupId>org.apache.jackrabbit</groupId>
        <artifactId>filevault-package-maven-plugin</artifactId>
        <version>1.0.0</version>
        <configuration>
            <filterSource>${basedir}/META-INF/vault/filter.xml</filterSource>
        </configuration>
    </plugin>

    <!-- this plugin is only needed for crx package manager deployment -->
    <plugin>
        <groupId>com.day.jcr.vault</groupId>
        <artifactId>content-package-maven-plugin</artifactId>
        <version>1.0.2</version>
    </plugin>

``` 

Next Steps
----------
Currently there is no roadmap for implementing a package manager in Jackrabbit. Some ideas are tracked in [JCRVLT-151][1], 
but until then, the plugin will not support any deployment options.
  
Alternatively, projects can use the 3rd party [Composum Package Manager][2] together with Adobe's plugin.


[0]: https://repo.adobe.com/nexus/content/groups/public/com/day/jcr/vault/content-package-maven-plugin/1.0.2/
[1]: https://issues.apache.org/jira/browse/JCRVLT-151
[2]: https://ist-software.atlassian.net/wiki/spaces/CMP/pages/46140125/Package+Manager