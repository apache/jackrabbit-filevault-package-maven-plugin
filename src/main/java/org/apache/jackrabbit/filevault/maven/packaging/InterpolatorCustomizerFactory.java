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
package org.apache.jackrabbit.filevault.maven.packaging;

import java.util.function.Consumer;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.ValueSource;

@FunctionalInterface
public interface InterpolatorCustomizerFactory {

    /**
     * Creates a new customizer for an {@link Interpolator} based on the given Maven context.
     * The functional interface being returned is called for each new interpolator used for filtering.
     * @param mavenSession the Maven session in which the Interpolator is used
     * @param mavenProject the Maven project in which the Interpolator is used
     * @return the customizer functional interface (which may register {@link InterpolationPostProcessor} and or {@link ValueSource} on the given interpolator)
     */
    Consumer<Interpolator> create(MavenSession mavenSession, MavenProject mavenProject);
}
