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
package org.apache.jackrabbit.filevault.maven.packaging.impl;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.jackrabbit.filevault.maven.packaging.InterpolatorCustomizerFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.filtering.AbstractMavenFilteringRequest;
import org.apache.maven.shared.filtering.DefaultMavenFileFilter;
import org.apache.maven.shared.filtering.FilterWrapper;
import org.apache.maven.shared.filtering.FilteringUtils;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MultiDelimiterInterpolatorFilterReaderLineEnding;
import org.apache.maven.shared.filtering.PropertyUtils;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.PrefixAwareRecursionInterceptor;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.RecursionInterceptor;
import org.codehaus.plexus.interpolation.SimpleRecursionInterceptor;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.ValueSource;
import org.codehaus.plexus.interpolation.multi.MultiDelimiterStringSearchInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * Mostly a copy from
 * <a href="https://github.com/apache/maven-filtering/blob/9d528d0871d93e317ae21a4b6922cd795502364c/src/main/java/org/apache/maven/shared/filtering/BaseFilter.java#L46">BaseFilter</a>.
 * Necessary due to <a href="https://issues.apache.org/jira/browse/MSHARED-1412">MSHARED-1412</a> 
 * Allows to customize the underlying {@link Interpolator}, otherwise identical to the default filtering returned from {@link DefaultMavenFileFilter#getDefaultFilterWrappers(AbstractMavenFilteringRequest)}
 */
public class DefaultFilterWrapper extends FilterWrapper {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFilterWrapper.class);

    public static FilterWrapper createDefaultFilterWrapperWithInterpolationProcessors(final AbstractMavenFilteringRequest request, Set<InterpolatorCustomizerFactory> interpolationCustomizerFactory) throws MavenFilteringException {
        // copy of
        // https://github.com/apache/maven-filtering/blob/9d528d0871d93e317ae21a4b6922cd795502364c/src/main/java/org/apache/maven/shared/filtering/BaseFilter.java#L74C56-L74C100
        // backup values
        boolean supportMultiLineFiltering = request.isSupportMultiLineFiltering();

        request.setSupportMultiLineFiltering(supportMultiLineFiltering);

        // Here we build some properties which will be used to read some properties files
        // to interpolate the expression ${ } in this properties file

        // Take a copy of filterProperties to ensure that evaluated filterTokens are not propagated
        // to subsequent filter files. Note: this replicates current behaviour and seems to make sense.

        final Properties baseProps = new Properties();

        // Project properties
        if (request.getMavenProject() != null) {
            baseProps.putAll(
                    request.getMavenProject().getProperties() == null
                            ? Collections.emptyMap()
                            : request.getMavenProject().getProperties());
        }
        // TODO this is NPE free but do we consider this as normal
        // or do we have to throw an MavenFilteringException with mavenSession cannot be null
        //
        // khmarbaise: 2016-05-21:
        // If we throw an MavenFilteringException tests will fail which is
        // caused by for example:
        // void copyFile( File from, final File to, boolean filtering, List<FileUtils.FilterWrapper> filterWrappers,
        // String encoding )
        // in MavenFileFilter interface where no MavenSession is given.
        // So changing here to throw a MavenFilteringException would make
        // it necessary to change the interface or we need to find a better solution.
        //
        if (request.getMavenSession() != null) {
            // User properties have precedence over system properties
            putAll(baseProps, request.getMavenSession().getSystemProperties());
            putAll(baseProps, request.getMavenSession().getUserProperties());
        }

        // now we build properties to use for resources interpolation

        final Properties filterProperties = new Properties();

        File basedir = request.getMavenProject() != null ? request.getMavenProject().getBasedir() : new File(".");

        loadProperties(filterProperties, basedir, request.getFileFilters(), baseProps);
        if (filterProperties.isEmpty()) {
            putAll(filterProperties, baseProps);
        }

        if (request.getMavenProject() != null) {
            if (request.isInjectProjectBuildFilters()) {
                List<String> buildFilters = new ArrayList<>(request.getMavenProject().getBuild().getFilters());

                // JDK-8015656: (coll) unexpected NPE from removeAll
                if (request.getFileFilters() != null) {
                    buildFilters.removeAll(request.getFileFilters());
                }

                loadProperties(filterProperties, basedir, buildFilters, baseProps);
            }

            // Project properties
            filterProperties.putAll(
                    request.getMavenProject().getProperties() == null
                            ? Collections.emptyMap()
                            : request.getMavenProject().getProperties());
        }
        if (request.getMavenSession() != null) {
            // User properties have precedence over system properties
            putAll(filterProperties, request.getMavenSession().getSystemProperties());
            putAll(filterProperties, request.getMavenSession().getUserProperties());
        }

        if (request.getAdditionalProperties() != null) {
            // additional properties wins
            putAll(filterProperties, request.getAdditionalProperties());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("properties used:");
            for (String s : new TreeSet<>(filterProperties.stringPropertyNames())) {
                logger.debug(s + ": " + filterProperties.getProperty(s));
            }
        }

        final ValueSource propertiesValueSource = new PropertiesBasedValueSource(filterProperties);

        Collection<Consumer<Interpolator>> interpolatorCustomizers = interpolationCustomizerFactory.stream().map( f -> f.create(request.getMavenSession(), request.getMavenProject())).collect(Collectors.toList());
        FilterWrapper wrapper = new DefaultFilterWrapper(
                request.getDelimiters(),
                request.getMavenProject(),
                request.getMavenSession(),
                propertiesValueSource,
                request.getProjectStartExpressions(),
                request.getEscapeString(),
                request.isEscapeWindowsPaths(),
                request.isSupportMultiLineFiltering(),
                interpolatorCustomizers);

        return wrapper;
    }

    private static void putAll(Properties filterProperties, Properties request) {
        synchronized (request) {
            filterProperties.putAll(request);
        }
    }

    /** default visibility only for testing reason ! */
    private static void loadProperties(
            Properties filterProperties, File basedir, List<String> propertiesFilePaths, Properties baseProps)
            throws MavenFilteringException {
        if (propertiesFilePaths != null) {
            Properties workProperties = new Properties();
            putAll(workProperties, baseProps);

            for (String filterFile : propertiesFilePaths) {
                if (filterFile == null || filterFile.trim().isEmpty()) {
                    // skip empty file name
                    continue;
                }
                try {
                    File propFile = FilteringUtils.resolveFile(basedir, filterFile);
                    Properties properties = PropertyUtils.loadPropertyFile(propFile, workProperties, logger);
                    putAll(filterProperties, properties);
                    putAll(workProperties, properties);
                } catch (IOException e) {
                    throw new MavenFilteringException("Error loading property file '" + filterFile + "'", e);
                }
            }
        }
    }

    private LinkedHashSet<String> delimiters;

    private MavenProject project;

    private ValueSource propertiesValueSource;

    private List<String> projectStartExpressions;

    private String escapeString;

    private boolean escapeWindowsPaths;

    private final MavenSession mavenSession;

    private boolean supportMultiLineFiltering;

    private final Collection<Consumer<Interpolator>> interpolatorCustomizers;

    DefaultFilterWrapper(
            LinkedHashSet<String> delimiters,
            MavenProject project,
            MavenSession mavenSession,
            ValueSource propertiesValueSource,
            List<String> projectStartExpressions,
            String escapeString,
            boolean escapeWindowsPaths,
            boolean supportMultiLineFiltering,
            Collection<Consumer<Interpolator>> interpolatorCustomizers) {
        super();
        this.delimiters = delimiters;
        this.project = project;
        this.mavenSession = mavenSession;
        this.propertiesValueSource = propertiesValueSource;
        this.projectStartExpressions = projectStartExpressions;
        this.escapeString = escapeString;
        this.escapeWindowsPaths = escapeWindowsPaths;
        this.supportMultiLineFiltering = supportMultiLineFiltering;
        this.interpolatorCustomizers = interpolatorCustomizers;
    }

    @Override
    public Reader getReader(Reader reader) {
        Interpolator interpolator = createInterpolator(
                delimiters,
                projectStartExpressions,
                propertiesValueSource,
                project,
                mavenSession,
                escapeString,
                escapeWindowsPaths);

        customizeInterpolator(interpolator);

        MultiDelimiterInterpolatorFilterReaderLineEnding filterReader = new MultiDelimiterInterpolatorFilterReaderLineEnding(
                reader, interpolator, supportMultiLineFiltering);

        final RecursionInterceptor ri;
        if (projectStartExpressions != null && !projectStartExpressions.isEmpty()) {
            ri = new PrefixAwareRecursionInterceptor(projectStartExpressions, true);
        } else {
            ri = new SimpleRecursionInterceptor();
        }

        filterReader.setRecursionInterceptor(ri);
        filterReader.setDelimiterSpecs(delimiters);

        filterReader.setInterpolateWithPrefixPattern(false);
        filterReader.setEscapeString(escapeString);

        return filterReader;
    }

    private void customizeInterpolator(Interpolator interpolator) {
        interpolatorCustomizers.forEach(c -> c.accept(interpolator));
    }

    private Interpolator createInterpolator(
            LinkedHashSet<String> delimiters,
            List<String> projectStartExpressions,
            ValueSource propertiesValueSource,
            MavenProject project,
            MavenSession mavenSession,
            String escapeString,
            boolean escapeWindowsPaths) {
        MultiDelimiterStringSearchInterpolator interpolator = new MultiDelimiterStringSearchInterpolator();
        interpolator.setDelimiterSpecs(delimiters);

        interpolator.addValueSource(propertiesValueSource);

        if (project != null) {
            interpolator.addValueSource(new PrefixedObjectValueSource(projectStartExpressions, project, true));
        }

        if (mavenSession != null) {
            interpolator.addValueSource(new PrefixedObjectValueSource("session", mavenSession));

            final Settings settings = mavenSession.getSettings();
            if (settings != null) {
                interpolator.addValueSource(new PrefixedObjectValueSource("settings", settings));
                interpolator.addValueSource(
                        new SingleResponseValueSource("localRepository", settings.getLocalRepository()));
            }
        }

        interpolator.setEscapeString(escapeString);

        if (escapeWindowsPaths) {
            interpolator.addPostProcessor(
                    (expression, value) -> (value instanceof String) ? FilteringUtils.escapeWindowsPath((String) value) : value);
        }
        return interpolator;
    }
}
