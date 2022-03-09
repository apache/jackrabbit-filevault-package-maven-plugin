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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

public class ValidationHelper implements Closeable {

    /**
     * Set to {@code true} if at least one {@link ValidationViolation} has been given out
     */
    private int noOfEmittedValidationMessagesWithLevelWarn = 0;
    
    /**
     * Set to {@code true} if at least one validation violation with severity {@link ValidationMessageSeverity#ERROR} has been given out
     */
    private int noOfEmittedValidationMessagesWithLevelError = 0;
    
    private CSVPrinter csvPrinter = null;

    public ValidationHelper() {
    }


    /**
     * 
     * @param violations
     * @param log
     * @param buildContext
     * @param baseDirectory the directory to which all absolute paths should be made relative (i.e. the Maven basedir)
     * @throws IOException 
     */
    public void printMessages(Collection<ValidationViolation> violations, Log log, BuildContext buildContext, Path baseDirectory) throws IOException {
        for (ValidationViolation violation : violations) {
            final int buildContextSeverity;
                switch (violation.getSeverity()) {
                    case ERROR:
                        log.error(getDetailMessage(violation, baseDirectory));
                        if (violation.getThrowable() != null) {
                            log.debug(violation.getThrowable());
                        }
                        buildContextSeverity = BuildContext.SEVERITY_ERROR;
                        noOfEmittedValidationMessagesWithLevelError++;
                        break;
                    case WARN:
                        log.warn(getDetailMessage(violation, baseDirectory));
                        if (violation.getThrowable() != null) {
                            log.debug(violation.getThrowable());
                        }
                        noOfEmittedValidationMessagesWithLevelWarn++;
                        buildContextSeverity = BuildContext.SEVERITY_WARNING;
                        break;
                    case INFO:
                        log.info(getDetailMessage(violation, baseDirectory));
                        buildContextSeverity = -1;
                        break;
                    default:
                        log.debug(getDetailMessage(violation, baseDirectory));
                        buildContextSeverity = -1;
                        break;
            }
               
            if (buildContextSeverity > 0) {
                // only emit via build context inside eclipse, otherwise log from above is better!
                if (!(buildContext instanceof DefaultBuildContext)) {
                    Path file;
                    if (violation.getAbsoluteFilePath() != null) {
                        file = violation.getAbsoluteFilePath();
                    } else {
                        // take the base path
                        file = baseDirectory;
                    }
                    buildContext.addMessage(file.toFile(), violation.getLine(), violation.getColumn(), getMessage(violation), buildContextSeverity, violation.getThrowable());
                }
                if (!buildContext.isIncremental() && csvPrinter != null) {
                    printToCsvFile(violation);
                }
            }
        }
    }

    private static String getMessage(ValidationViolation violation) {
        StringBuilder message = new StringBuilder();
        if (violation.getValidatorId() != null) {
            message.append(violation.getValidatorId()).append(": ");
        }
        message.append(violation.getMessage());
        return message.toString();
    }

    public void printUsedValidators(Log log, ValidationExecutor executor, ValidationContext context, boolean printUnusedValidators) {
        String packageType = context.getProperties().getPackageType() != null ? context.getProperties().getPackageType().toString() : "unknown";
        log.info("Using " + executor.getAllValidatorsById().entrySet().size() + " validators for package of type " + packageType + ": " + ValidationHelper.getValidatorNames(executor, ", "));
        if (printUnusedValidators) {
            Map<String, Validator> unusedValidatorsById = executor.getUnusedValidatorsById();
            if (!unusedValidatorsById.isEmpty()) {
                log.warn("There are unused validators among those which are not executed: " + StringUtils.join(unusedValidatorsById.keySet(), "."));
            }
        }
    }

    private static String getDetailMessage(ValidationViolation violation, Path baseDirectory) {
        StringBuilder message = new StringBuilder("ValidationViolation: ");
        message.append("\"").append(getMessage(violation)).append("\"");
        if (violation.getFilePath() != null) {
            message.append(", filePath=").append(baseDirectory.relativize(violation.getAbsoluteFilePath()));
        }
        if (violation.getNodePath() != null) {
            message.append(", nodePath=").append(violation.getNodePath());
        }
        if (violation.getLine() > 0) {
            message.append(", line=").append(violation.getLine());
        }
        if (violation.getColumn() > 0) {
            message.append(", column=").append(violation.getColumn());
        }
        return message.toString();
    }

    private static String getValidatorNames(ValidationExecutor executor, String separator) {
        StringBuilder validatorNames = new StringBuilder();
        boolean isFirstItem = true;
        for (Map.Entry<String, Validator> validatorById : executor.getAllValidatorsById().entrySet()) {
            if (!isFirstItem) {
                validatorNames.append(separator);
            } else {
                isFirstItem = false;
            }
            validatorNames.append(validatorById.getKey()).append(" (").append(validatorById.getValue().getClass().getName()).append(")");
        }
        return validatorNames.toString();
    }

    public void clearPreviousValidationMessages(BuildContext context, File file) {
        context.removeMessages(file);
    }

    public void failBuildInCaseOfViolations(boolean failForWarning) throws MojoFailureException {
        if (failForWarning && (noOfEmittedValidationMessagesWithLevelWarn > 0 || noOfEmittedValidationMessagesWithLevelError > 0)) {
            throw new MojoFailureException("Found " +noOfEmittedValidationMessagesWithLevelWarn+noOfEmittedValidationMessagesWithLevelError + " violation(s) (either ERROR or WARN). Check above warnings/errors for details");
        } else if (noOfEmittedValidationMessagesWithLevelError > 0) {
            throw new MojoFailureException("Found " + noOfEmittedValidationMessagesWithLevelError + " violation(s) (with severity=ERROR). Check above errors for details");
        }
    }

    public void setCsvFile(File csvReportFile, Charset charset, CSVFormat format) throws IOException {
        csvPrinter = new CSVPrinter(new OutputStreamWriter(new FileOutputStream(csvReportFile), charset), format);
        csvPrinter.printRecord("Severity", "Validator ID", "Message", "File", "Line:Column", "Node Path");
    }

    private void printToCsvFile(ValidationViolation violation) throws IOException {
        csvPrinter.printRecord(violation.getSeverity(), violation.getValidatorId(), violation.getMessage(), violation.getAbsoluteFilePath(), MessageFormat.format("{0}:{1}", violation.getLine(), violation.getColumn()), violation.getNodePath());
    }

    @Override
    public void close() throws IOException {
        if (csvPrinter != null) {
            csvPrinter.close();
        }
    }
}
