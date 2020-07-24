package com.github.simkuenzi.webplay.record;

import javax.xml.stream.XMLStreamException;

public interface TestScenarioBuilder {
    RequestBuilder testScenario() throws XMLStreamException;
}
