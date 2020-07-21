package com.github.simkuenzi.webplay;

import javax.xml.stream.XMLStreamException;
import java.util.Map;

public interface RequestBuilder {
    Request request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException;
}
