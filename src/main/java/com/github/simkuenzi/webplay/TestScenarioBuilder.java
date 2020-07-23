package com.github.simkuenzi.webplay;

import javax.xml.stream.XMLStreamException;

public interface TestScenarioBuilder {
    RequestBuilder testScenario() throws XMLStreamException;
}
