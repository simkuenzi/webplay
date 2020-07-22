package com.github.simkuenzi.webplay;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RecordedScenario {
    private final URL scenarioFile;

    public RecordedScenario(Path scenarioFile) throws MalformedURLException {
        this(scenarioFile.toUri().toURL()) ;
    }

    public RecordedScenario(URL scenarioFile) {
        this.scenarioFile = scenarioFile;
    }

    public List<RecordedTest> tests() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = scenarioFile.openStream()) {
            Document document = builder.parse(in);
            NodeList testNodes = document.getDocumentElement().getElementsByTagName("test");
            return IntStream.range(0, testNodes.getLength())
                    .mapToObj(i -> {
                        try {
                            return new RecordedTest(testNodes.item(i));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }
}
