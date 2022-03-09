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
package org.apache.jackrabbit.filevault.maven.packaging.mojo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeDefinitionProvider;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.nodetype.compact.CompactNodeTypeDefWriter;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.StandaloneManagerProvider;
import org.apache.jackrabbit.vault.validation.spi.util.classloaderurl.CndUtil;
import org.apache.jackrabbit.vault.validation.spi.util.classloaderurl.URLFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

/**
 * Generates a <a href="https://jackrabbit.apache.org/jcr/node-type-notation.html">CND file</a> containing all
 * used node types and namespaces. It uses the <a href="https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.7.11%20Standard%20Application%20Node%20Types">default namespaces and node types</a>
 * and in addition some provided ones as source node type and namespace registry.
 * From those only the ones being really leveraged in the package end up in the generated CND.
 * The generated CND will end up in the <a href="https://jackrabbit.apache.org/filevault/nodetypes.html">package metadata</a> and 
 * all contained namespaces and node types are automatically registered during installation (in case they are not yet registered).
 * @since 1.3.0
 */
@Mojo(
        name = "generate-cnd",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES, 
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class GenerateCndMojo extends AbstractSourceAndMetadataPackageMojo {
    /**
     * List of URLs pointing to a <a href="https://jackrabbit.apache.org/jcr/node-type-notation.html">CND</a>
     * which define the additional namespaces and node types potentially used in this package apart from the 
     * <a href="https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.7.11%20Standard%20Application%20Node%20Types">default ones defined in JCR 2.0</a>.
     * If a URI is pointing to a JAR it will leverage all the node types being mentioned in the 
     * <a href="https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#declared-node-type-registration">{@code Sling-Nodetypes} manifest header</a>.
     * part from the <a href="https://docs.oracle.com/javase/8/docs/api/java/net/URL.html#URL-java.lang.String-java.lang.String-int-java.lang.String-"standard protocols</a> the scheme {@code tccl} 
     * can be used to reference names from the <a href="https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#getContextClassLoader--">Thread's context class loader</a>.
     * In the Maven plugin context this is the <a href="http://maven.apache.org/guides/mini/guide-maven-classloading.html?ref=driverlayer.com/web#3-plugin-classloaders">plugin classloader.
     */
    @Parameter(property = "vault.inputCndUrls")
    List<String> additionalInputCndUrls = new LinkedList<>();

    public GenerateCndMojo() {
        
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Retrieving used node types and namespaces...");
        try {
            StandaloneManagerProvider managerProvider = new StandaloneManagerProvider();
            URLFactory.processUrlStreams(CndUtil.resolveJarUrls(additionalInputCndUrls), t -> {
                try {
                    managerProvider.registerNodeTypes(t);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                catch (ParseException | RepositoryException e) {
                    throw new IllegalArgumentException(e);
                }
            });
            File cndOutputFile = new File(getGeneratedVaultDir(true), Constants.NODETYPES_CND);
            File jcrSourceDirectory = getJcrSourceDirectory();
            if (jcrSourceDirectory == null) {
                getLog().warn("None of the configured jcrRootSourceDirectory directories exists, skipping generating the CND!");
            } else {
                int numWrittenNodeTypes = generateCnd(managerProvider, cndOutputFile.toPath(), getJcrSourceDirectory().toPath());
                getLog().info("Written " + numWrittenNodeTypes + " node types to CND file " + getProjectRelativeFilePath(cndOutputFile));
            }
        } catch (IOException | RepositoryException | ParseException | IllegalStateException e) {
            throw new MojoExecutionException("Error while writing CND: " + e.toString(), e);
        }
    }

    public int generateCnd(StandaloneManagerProvider managerProvider, Path cndOutputFile, Path jcrSourceDirectory) throws IOException, RepositoryException, ParseException {
        DocViewParser docViewParser = new DocViewParser(managerProvider.getNamespaceResolver());
        // traverse relevant package files
        final Set<String> nodeTypes;
        try {
            nodeTypes = collectNodeTypes(jcrSourceDirectory, docViewParser);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        getLog().info("Found " + nodeTypes.size() + " unique node types" );
        Collection<? extends QNodeTypeDefinition> ntDefinitons = resolveNodeTypes(nodeTypes, managerProvider.getNameResolver(),
                managerProvider.getNodeTypeDefinitionProvider());

        // make sure parent directories exist
        Files.createDirectories(cndOutputFile.getParent());
        // writes the CND into the given file
        try (Writer writer = Files.newBufferedWriter(cndOutputFile, StandardCharsets.US_ASCII)) {
            return writeCnd(ntDefinitons, managerProvider.getNodeTypeDefinitionProvider(), managerProvider.getNamespaceResolver(), writer);
        }
    }

    private Set<String> collectNodeTypes(Path jcrRootPath, DocViewParser docViewParser) throws IOException {
        Set<String> nodeTypes = new HashSet<>();
        // add default ones for simple file aggregates: https://jackrabbit.apache.org/filevault/vaultfs.html#simple-file-aggregates 
        nodeTypes.add(JcrConstants.NT_FILE);
        nodeTypes.add(JcrConstants.NT_FOLDER);

        NodeTypeCollectorHandler nodeTypeCollectorHandler = new NodeTypeCollectorHandler(nodeTypes);
        // extract types from docview files
        try (Stream<Path> filePaths = Files.find(jcrRootPath, 50, (path, attributes) -> !attributes.isDirectory() && path.getFileName().toString().endsWith(".xml"))) {
            filePaths.forEach(p -> {
                nodeTypeCollectorHandler.setFile(p);
                getNodeTypes(jcrRootPath, p, docViewParser, nodeTypeCollectorHandler);
            });
        }
        return nodeTypes;
    }

    private void getNodeTypes(Path jcrRootPath, Path docViewFile, DocViewParser docViewParser, NodeTypeCollectorHandler nodeTypeCollectorHandler) {
        try (InputStream is = Files.newInputStream(docViewFile);
             BufferedInputStream bufferedIs = new BufferedInputStream(is)) {
            String documentViewXmlRootNodePath = DocViewParser.getDocumentViewXmlRootNodePath(bufferedIs, jcrRootPath.relativize(docViewFile));
            if (documentViewXmlRootNodePath == null) {
                return;
            }
            InputSource inputSource = new InputSource(bufferedIs);
            docViewParser.parse(documentViewXmlRootNodePath, inputSource, nodeTypeCollectorHandler);
        } catch (XmlParseException|IOException e) {
            getLog().warn("Could not parse " + docViewFile + ". Ignore for node type definition generation!", e);
        }
    }

    final class NodeTypeCollectorHandler implements DocViewParserHandler {
        private final Set<String> nodeTypes;
        private Path file;
        public NodeTypeCollectorHandler(Set<String> nodeTypes) {
            this.nodeTypes = nodeTypes;
        }

        public void setFile(Path file) {
            this.file = file;
        }

        @Override
        public void startDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode, @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column)
                throws IOException, RepositoryException {
            Optional<String> primaryType = docViewNode.getPrimaryType();
            if (primaryType.isPresent()) {
                if (nodeTypes.add(primaryType.get())) {
                    getLog().debug("Found primary type " + primaryType.get() + " in node '" + nodePath + "' contained in file " + getProjectRelativeFilePath(file.toFile()));
                }
            }
            if (nodeTypes.addAll(docViewNode.getMixinTypes())) {
                getLog().debug("Found mixin types " + docViewNode.getMixinTypes() + " in node '" + nodePath + "' contained in file " + getProjectRelativeFilePath(file.toFile()));
            }
        }

        @Override
        public void endDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode, @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column)
                throws IOException, RepositoryException {
            // do nothing
        }
    }

    // resolve from cnd file
    private Collection<QNodeTypeDefinition> resolveNodeTypes(Set<String> nodeTypeNames, NameResolver nameResolver, NodeTypeDefinitionProvider ntDefinitionProvider) throws IOException, RepositoryException, ParseException {
        return resolveNodeTypesFromNames(nodeTypeNames.stream()
                .map(name -> {
                    try {
                        return nameResolver.getQName(name);
                    } catch (IllegalNameException|NamespaceException e) {
                        throw new IllegalStateException("Cannot get expanded name for type " + name, e);
                    }
                })
                .collect(Collectors.toSet()), 
                ntDefinitionProvider);
    }

    static Collection<QNodeTypeDefinition> resolveNodeTypesFromNames(Set<Name> nodeTypeNames, NodeTypeDefinitionProvider ntDefinitionProvider) throws IOException, RepositoryException, ParseException {
        return nodeTypeNames.stream().map(name -> {
            try {
                return ntDefinitionProvider.getNodeTypeDefinition(name);
            } catch (RepositoryException e) {
                throw new IllegalStateException("RepositoryException ", e);
            }
        }).collect(Collectors.toList());
    }

    private int writeCnd(Collection<? extends QNodeTypeDefinition> nodeTypeDefinitions, NodeTypeDefinitionProvider ntDefinitionProvider, NamespaceResolver nsResolver, Writer writer) throws IOException, RepositoryException {
        CompactNodeTypeDefWriter cndWriter = new CompactNodeTypeDefWriter(writer, nsResolver, true);
        Set<Name> written = new HashSet<>();
        int numWrittenNodeTypes = 0;
        for (QNodeTypeDefinition nodeTypeDefinition : nodeTypeDefinitions) {
            numWrittenNodeTypes += writeNodeType(nodeTypeDefinition, cndWriter, written, ntDefinitionProvider);
        }
        cndWriter.close();
        return numWrittenNodeTypes;
    }

    private int writeNodeType(Name nodeType, CompactNodeTypeDefWriter cndWriter, Set<Name> written, NodeTypeDefinitionProvider ntDefinitionProvider) throws IOException, RepositoryException {
        if (nodeType == null || written.contains(nodeType)) {
            return 0;
        }
        QNodeTypeDefinition ntDefinition = ntDefinitionProvider.getNodeTypeDefinition(nodeType);
        return writeNodeType(ntDefinition, cndWriter, written, ntDefinitionProvider);
    }

    private int writeNodeType(QNodeTypeDefinition ntDefinition, CompactNodeTypeDefWriter cndWriter, Set<Name> written, NodeTypeDefinitionProvider ntDefinitionProvider)
            throws IOException, RepositoryException {
        int numWrittenNodeTypes = 1;
        cndWriter.write(ntDefinition);
        written.add(ntDefinition.getName());
        // also write all referenced node types
        for (Name superType: ntDefinition.getSupertypes()) {
            numWrittenNodeTypes += writeNodeType(superType, cndWriter, written, ntDefinitionProvider);
        }
        for (QNodeDefinition cntDefinition: ntDefinition.getChildNodeDefs()) {
            writeNodeType(cntDefinition.getDefaultPrimaryType(), cndWriter, written, ntDefinitionProvider);
            if (cntDefinition.getRequiredPrimaryTypes() != null) {
                for (Name name: cntDefinition.getRequiredPrimaryTypes()) {
                    numWrittenNodeTypes += writeNodeType(name, cndWriter, written, ntDefinitionProvider);
                }
            }
        }
        return numWrittenNodeTypes;
    }
}
