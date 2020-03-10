/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.filevault.maven.packaging.it;

import org.junit.Test;

public class CheckSignatureIT {

    /**
     * The check-signature goal ends up in a NPE in the animal-sniffer SignatureChecker, when the animal-sniffer plugin
     * version is 1.14 in the build, breaking the build:
     */
    @Test
    public void package_builds() throws Exception {
        new ProjectBuilder()
                .setTestProjectDir("/check-signature")
                .build();
    }
    
    /**
     * 
     */
    @Test
    public void testJava9Module() throws Exception {
            new ProjectBuilder()
                    .setTestProjectDir("/check-signature2")
                    .build();
    }
   
}
