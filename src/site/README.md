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

FileVault Documentation
=======================
The FileVault documentation lives as Markdown files in `src/site/markdown` such
that it easy to view e.g. from GitHub. Alternatively the Maven site plugin
can be used to build and deploy a web site as follows:

1. From the reactor build the site with javadoc:

   ````
   $ mvn site
   ````

2. Review the site at `site/target/site`

3. Deploy the site to `http://jackrabbit.apache.org/filevault-package-maven-plugin` using:

   ````
   $ mvn site-deploy
   ````

4. Finally review the site at `http://jackrabbit.apache.org/filevault-package-maven-plugin-archives/${project.version}/index.html`.


Note: To skip the final commit use `-Dscmpublish.skipCheckin=true`. You can then
review all pending changes in `target/scmpublish-checkout` and follow
up with `svn commit` manually.

Note: Every committer should be able to deploy the site. No fiddling with
credentials needed since deployment is done via svn commit to
`https://svn.apache.org/repos/asf/jackrabbit/site/live/filevault-package-maven-plugin`.


Update Documentation After Release
==================================

1. Switch to release tag

2. Adjust the links to the site versions in `src/site/site.xml` (don't yet commit)

3. Deploy the site of the released version with `mvn site-deploy`

4. Copy the released site version to the root site:

  ```
  $ svn rm https://svn.apache.org/repos/asf/jackrabbit/site/live/filevault-package-maven-plugin -m "Remove old website version"
  $ svn cp https://svn.apache.org/repos/asf/jackrabbit/site/live/filevault-package-maven-plugin-archives/${project.version} \
           https://svn.apache.org/repos/asf/jackrabbit/site/live/filevault-package-maven-plugin -m "Add new website version"
  ```
5. Switch to master branch

6. Commit the changes from 1.

7. Deploy the snapshot site