package com.github.simkuenzi.webplay;

import org.custommonkey.xmlunit.XMLAssert;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;

public class RecorderTest{

    @Test
    public void startStop() throws Exception {
        TestFs.use((testFs -> {
            recorder(testFs).start().stop();
            try (Reader actual = Files.newBufferedReader(testFs.outputFile())) {
                XMLAssert.assertXMLEqual(new StringReader("<scenario/>"), actual);
            }
        }));
    }

    private Recorder recorder(TestFs testFs) {
        return new Recorder(testFs.outputFile(), 9033, 9000,
                List.of("text/html", "application/x-www-form-urlencoded"));
    }
}
