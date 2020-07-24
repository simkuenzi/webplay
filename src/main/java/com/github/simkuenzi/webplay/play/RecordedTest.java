package com.github.simkuenzi.webplay.play;

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

public class RecordedTest {
    private final URL testFile;

    public RecordedTest(Path testFile) throws MalformedURLException {
        this(testFile.toUri().toURL()) ;
    }

    public RecordedTest(URL testFile) {
        this.testFile = testFile;
    }

    public void play(String baseUrl, RecordedRequest.AssertionMethod assertionMethod) throws Exception {
        for (RecordedRequest request : requests()) {
            request.play(baseUrl, assertionMethod);
        }
    }

    private List<RecordedRequest> requests() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = testFile.openStream()) {
            Document document = builder.parse(in);
            NodeList testNodes = document.getDocumentElement().getElementsByTagName("test");
            return IntStream.range(0, testNodes.getLength())
                    .mapToObj(i -> {
                        try {
                            return new RecordedRequest(testNodes.item(i));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
        }
    }
}
