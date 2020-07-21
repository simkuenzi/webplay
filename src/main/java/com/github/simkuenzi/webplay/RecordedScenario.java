package com.github.simkuenzi.webplay;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RecordedScenario {
    private final Path scenarioFile;

    public RecordedScenario(Path scenarioFile) {
        this.scenarioFile = scenarioFile;
    }

    public List<RecordedTest> tests() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(scenarioFile.toFile());
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
