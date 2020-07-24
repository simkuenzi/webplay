package com.github.simkuenzi.webplay.record;

import javax.xml.stream.XMLStreamException;
import java.util.Map;

public interface RequestBuilder extends CompleteTestScenario {
    Request request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException;
}
