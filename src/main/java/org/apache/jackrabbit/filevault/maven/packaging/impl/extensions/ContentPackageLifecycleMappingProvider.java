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
package org.apache.jackrabbit.filevault.maven.packaging.impl.extensions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.jackrabbit.filevault.maven.packaging.mojo.VaultMojo;
import org.apache.maven.lifecycle.mapping.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

@Singleton
@Named(VaultMojo.PACKAGE_TYPE)
public class ContentPackageLifecycleMappingProvider implements Provider<LifecycleMapping> {

    private static final String DEFAULT_LIFECYCLE_KEY = "default";
    private static final Map<String, LifecyclePhase> BINDINGS;
    static {
        BINDINGS = new HashMap<>();
        BINDINGS.put("process-resources", new LifecyclePhase("org.apache.maven.plugins:maven-resources-plugin:3.2.0:resources"));
        BINDINGS.put("compile", new LifecyclePhase("org.apache.maven.plugins:maven-compiler-plugin:3.10.1:compile"));
        BINDINGS.put("generate-test-sources", new LifecyclePhase("org.apache.jackrabbit:filevault-package-maven-plugin:generate-metadata"));
        BINDINGS.put("process-test-sources", new LifecyclePhase("org.apache.jackrabbit:filevault-package-maven-plugin:validate-files"));
        BINDINGS.put("process-test-resources", new LifecyclePhase("org.apache.maven.plugins:maven-resources-plugin:3.2.0:testResources"));
        BINDINGS.put("test-compile", new LifecyclePhase("org.apache.maven.plugins:maven-compiler-plugin:3.10.1:testCompile"));
        BINDINGS.put("test", new LifecyclePhase("org.apache.maven.plugins:maven-surefire-plugin:2.22.2:test"));
        BINDINGS.put("package", new LifecyclePhase("org.apache.jackrabbit:filevault-package-maven-plugin:package"));
        BINDINGS.put("verify", new LifecyclePhase("org.apache.jackrabbit:filevault-package-maven-plugin:validate-package"));
        BINDINGS.put("install", new LifecyclePhase("org.apache.maven.plugins:maven-install-plugin:2.5.2:install"));
        BINDINGS.put("deploy", new LifecyclePhase("org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy"));
    }

    private final Lifecycle defaultLifecycle;

    private final LifecycleMapping lifecycleMapping;

    public ContentPackageLifecycleMappingProvider() {
        this.defaultLifecycle = new Lifecycle();
        this.defaultLifecycle.setId(DEFAULT_LIFECYCLE_KEY);
        this.defaultLifecycle.setLifecyclePhases(BINDINGS);

        this.lifecycleMapping = new LifecycleMapping() {
            @Override
            public Map<String, Lifecycle> getLifecycles() {
                return Collections.singletonMap(DEFAULT_LIFECYCLE_KEY, defaultLifecycle);
            }

            @Override
            public List<String> getOptionalMojos(String lifecycle) {
                return null;
            }

            @Override
            public Map<String, String> getPhases(String lifecycle) {
                if (DEFAULT_LIFECYCLE_KEY.equals(lifecycle)) {
                    return defaultLifecycle.getPhases();
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public LifecycleMapping get() {
        return lifecycleMapping;
    }

}
