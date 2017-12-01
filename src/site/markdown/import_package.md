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
Generating import-package MANIFEST entries
==========================================

The Filevault content package maven plugin can analyze the classpath of the compiled classes, derived from the content and create an `import-package` MANIFEST entry. Although this is rather something to be expected in the OSGi world, declaring the java packages that the content potentially needs can help detecting deployment errors early.

How it works
------------
_(todo: add more detailed explanation)_

The generation of the `import-package` entry is performed in 3 steps:

#### 1. generate classes
1. JSP or HTL plugin transpile the scripts
2. Maven compiler plugin compiles the generated java sources


#### 2. analyze classes
The classpath analysis is done in the `analyze-classes` goal:

1. scan the java classes and extract the used java packages
2. scan the dependencies and extract package version information from the MANIFEST if present
3. cross reference the packages with the version information from the bundles
4. write preliminary `import-package` entry to `${vault.generatedImportPackage}` 


#### 3. post-process and merge with project properties
The final step is performed in the `package` goal:

1. read `${vault.generatedImportPackage}`
2. merge with instructions in `<importPackage>`
3. add `import-package` entry to `META-INF/MANIFEST.MF`


Working with JSPs
-----------------
(todo)

Working with HTL
----------------

Where as the HTL use classes provided through bundles or via java classes in the content are simple to handle, the HTL scripts needs more treatment. similar to the JSPs, the HTL maven plugin can be used to transpile the scripts to java and then the maven compiler will create the classes.

### Note on the analyzed packages
The transpiled HTL scripts will contain some sightly compiler and runtime references, which are not true dependencies of the scripts. Nevertheless, they are needed to compile the scripts for the validation and will end up in the analyzed packages. by default, the following packages are excluded from the final `import-package`:

```
org.apache.sling.scripting.sightly.compiler.expression.nodes
org.apache.sling.scripting.sightly.java.compiler
org.apache.sling.scripting.sightly.render
```

_Note: The excludes are present in the default value of the `<importPackage>` property and are lost if it is defined in the project. In this cases they need to be added manually._

### Example configuration for HTL

```
<build>
    <sourceDirectory>src/content/jcr_root</sourceDirectory>
    <plugins>
        <plugin>
            <groupId>org.apache.jackrabbit</groupId>
            <artifactId>filevault-package-maven-plugin</artifactId>
            <version>1.0.0</version>
            <extensions>true</extensions>
            <configuration>
                <filterSource>${basedir}/src/content/META-INF/vault/filter.xml</filterSource>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.sling</groupId>
            <artifactId>htl-maven-plugin</artifactId>
            <version>1.1.0</version>
            <executions>
                <execution>
                    <id>validate-scripts</id>
                    <goals>
                        <goal>validate</goal>
                    </goals>
                    <phase>generate-sources</phase>
                    <configuration>
                        <sourceDirectory>src/content/jcr_root</sourceDirectory>
                        <includes>
                            <include>**/*.html</include>
                        </includes>
                        <failOnWarnings>true</failOnWarnings>
                        <generateJavaClasses>true</generateJavaClasses>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>

<dependencies>
    <!-- needed for HTL compilation validation -->
    <dependency>
        <groupId>org.apache.sling</groupId>
        <artifactId>org.apache.sling.scripting.sightly.compiler</artifactId>
        <version>1.0.14</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.sling</groupId>
        <artifactId>org.apache.sling.scripting.sightly.compiler.java</artifactId>
        <version>1.0.16</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

