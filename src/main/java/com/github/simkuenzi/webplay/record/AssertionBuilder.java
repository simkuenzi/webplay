package com.github.simkuenzi.webplay.record;

import javax.xml.stream.XMLStreamException;

public interface AssertionBuilder extends RequestBuilder {
    Assertion assertion(String expectedAttrName, String expectedAttrValue, String selector) throws XMLStreamException;
    Assertion assertion(String expectedText, String selector) throws XMLStreamException;
}
