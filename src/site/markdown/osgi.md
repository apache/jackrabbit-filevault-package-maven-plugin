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

# OSGi Support

Although neither the [JCR specification](https://s.apache.org/jcr-2.0-spec) nor the FileVault serialization format natively support OSGi bundles and configurations those are still supported to be automatically deployed from a certain repository location through 3rd party modules. This allows to embed them also in content packages so that the full code and content belonging to an OSGi application based on Apache Sling can be wrapped in a single content package.

## Bundles and Configurations

Embedding OSGi bundles and configurations is possible in the JCR (and therefore also in FileVault Content Packages). If they follow a certain format and location they can be automatically deployed/extracted into an OSGi feature model with either
1. [Sling OSGi Installer](https://sling.apache.org/documentation/bundles/osgi-installer.html) and its [JCR Installer Provider](https://sling.apache.org/documentation/bundles/jcr-installer-provider.html) or
2. [Sling Content-Package to Feature Model Converter](https://github.com/apache/sling-org-apache-sling-feature-cpconverter).

For details on the OSGi configuration format refer to [Configuration Serialization Formats](https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#configuration-serialization-formats). Bundles are embedded as simple files (i.e. `nt:file` nodes).

## Packages

Although only useful for edge cases (e.g. to install a package only on Sling distributions with a certain [run mode](https://sling.apache.org/documentation/bundles/sling-settings-org-apache-sling-settings.html)) also content packages can be embedded in other container packages. The difference to regular sub packages is that those are not installed synchronously along with the container package, but are being picked up asynchronously through the JCR Installer Provider and then being queued for installation through the OSGi installer via its [Content Package Installer Factory](https://sling.apache.org/documentation/bundles/content-package-installer-factory.html).

## Repository Location

By default the Sling JCR Installer Provider and Sling Content-Package to Feature Model Converter only consider embedded bundles, configurations and packages inside the nodes `/libs/../install`/`/apps/../install` or `/libs/../config`/`/apps/../config` optionally suffixed by `.` followed by one or multiple run modes.

## Maven Dependencies

The FileVault Package Maven plugin supports automatically embedding OSGi bundles or packages in the right location in the generated package which are filtered from the project's Maven dependencies through its [`embeddeds` parameter](generate-metadata-mojo.html#embeddeds).
It needs to be ensured that the embedded target path is set accordingly for the Sling JCR Installer Provider to pick it up.

## Checking Unsafe OSGi Configurations

The [`package` goal](package-mojo.html) can fail the build if the package contains OSGi configurations which would make an Apache Sling instance unsafe. The checks are disabled by default. They read OSGi configurations in the formats `<pid>.cfg.json` and `<pid>.config` below `jcr_root`; other formats (`.cfg` files and `sling:OsgiConfig` nodes) are not checked. Every problem is logged as an error naming the file, then the build fails. The package files are never changed.

| Parameter | What fails the build |
| --- | --- |
| [`failOnUnsafeAdministrativePrincipals`](package-mojo.html#failOnUnsafeAdministrativePrincipals) | In `org.apache.jackrabbit.oak.security.authorization.AuthorizationConfigurationImpl`: the principal `everyone` or the anonymous principal in `administrativePrincipals`. Otherwise every request without credentials would get full permissions on the repository. The name of the anonymous principal is the property `anonymousId` of `org.apache.jackrabbit.oak.security.user.UserConfigurationImpl` in the package, or `anonymous` (Oak's default) if it is not set there. If the package contains several user configurations (e.g. for different run modes), the anonymous ID of each of them is checked. Names must match exactly, like in Oak's own check. |

Example:

```
<plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    <configuration>
        <failOnUnsafeAdministrativePrincipals>true</failOnUnsafeAdministrativePrincipals>
    </configuration>
</plugin>
```

A configuration like

```
{
    "administrativePrincipals": ["administrators", "anonymous"],
    "importBehavior": "besteffort"
}
```

then fails the build with an error naming the file and the principal `anonymous`.
