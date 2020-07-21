package com.github.simkuenzi.webplay;

import javax.xml.stream.XMLStreamException;

public interface AssertionBuilder {
    Assertion assertion(String expected, String selector) throws XMLStreamException;
}
