package org.apache.jackrabbit.filevault.maven.packaging.it;

import java.io.File;
import java.io.IOException;

import org.apache.maven.it.VerificationException;
import org.junit.Test;

public class FormattingIT {

    private void verify(String project, String... formattedFiles) throws VerificationException, IOException {
        ProjectBuilder builder = new ProjectBuilder()
                .setTestProjectDir("format-xml-tests/" + project)
                .setTestGoals("filevault-package:format-xml")
                .setBuildExpectedToFail(false)
                .setVerifyPackageContents(false)
                .build();

        for (String formattedFile : formattedFiles) {
            String path = new File(builder.getTestProjectDir(), formattedFile).getAbsolutePath();
            builder.verifyExpectedLogLines(path);
        }
    }

    @Test
    public void test_format_xml_in_single_module() throws Exception {
        verify("singlemodule", "src/main/content/jcr_root/.content.xml");
    }

    @Test
    public void test_format_xml_in_reactor() throws Exception {
        verify("multimodule", "a/src/main/content/jcr_root/.content.xml", "b/src/main/content/jcr_root/.content.xml");
    }
}
