package com.github.simkuenzi.webplay.record;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Writer;
import java.util.Map;

public class XmlTest implements Test {
    private final Writer out;

    public XmlTest(Writer out) {
        this.out = out;
    }

    @Override
    public RequestBuilder test() throws XMLStreamException {
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
        writer.writeStartDocument();
        writeTestStart(writer);
        return new XmlRequestBuilder(writer);
    }

    private void writeTestStart(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("test");
    }

    private void writeTestEnd(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
    }

    private void writeRequestStart(XMLStreamWriter writer, String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
        writer.writeStartElement("request");
        writer.writeAttribute("urlPath", urlPath);
        writer.writeAttribute("method", method);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            writer.writeEmptyElement("header");
            writer.writeAttribute("name", header.getKey());
            writer.writeAttribute("value", header.getValue());
        }

        if (!payload.isEmpty()) {
            writer.writeStartElement("payload");
            writer.writeAttribute("xml:space", "preserve");
            writer.writeCharacters(payload);
            writer.writeEndElement();
        }
    }

    private void writeRequestEnd(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
    }

    private void writeAssertion(XMLStreamWriter writer, String expectedText, String selector) throws XMLStreamException {
        writer.writeStartElement("assertion");
        writer.writeAttribute("selector", selector);
        writer.writeStartElement("expectedText");
        writer.writeAttribute("xml:space", "preserve");
        writer.writeCharacters(expectedText);
        writer.writeEndElement();
        writer.writeEndElement();
    }

    private void writeAssertion(XMLStreamWriter writer, String expectedAttrName, String expectedAttrValue, String selector) throws XMLStreamException {
        writer.writeStartElement("assertion");
        writer.writeAttribute("selector", selector);
        writer.writeStartElement("expectedAttr");
        writer.writeAttribute("xml:space", "preserve");
        writer.writeAttribute("name", expectedAttrName);
        writer.writeCharacters(expectedAttrValue);
        writer.writeEndElement();
        writer.writeEndElement();
    }

    private class XmlRequestBuilder implements RequestBuilder {
        private final XMLStreamWriter writer;

        public XmlRequestBuilder(XMLStreamWriter writer) {
            this.writer = writer;
        }

        @Override
        public AssertionBuilder request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
            writeRequestStart(writer, urlPath, method, headers, payload);
            return new XmlAssertionBuilder(writer);
        }

        @Override
        public void end() throws XMLStreamException {
            writeTestEnd(writer);
        }
    }

    private class XmlAssertionBuilder implements AssertionBuilder {

        private final XMLStreamWriter writer;

        private XmlAssertionBuilder(XMLStreamWriter writer) {
            this.writer = writer;
        }

        @Override
        public AssertionBuilder assertion(String expectedAttrName, String expectedAttrValue, String selector) throws XMLStreamException {
            writeAssertion(writer, expectedAttrName, expectedAttrValue, selector);
            return new XmlAssertionBuilder(writer);
        }

        @Override
        public AssertionBuilder assertion(String expectedText, String selector) throws XMLStreamException {
            writeAssertion(writer, expectedText, selector);
            return new XmlAssertionBuilder(writer);
        }

        @Override
        public AssertionBuilder request(String urlPath, String method, Map<String, String> headers, String payload) throws Exception {
            writeRequestEnd(writer);
            writeRequestStart(writer, urlPath, method, headers, payload);
            return new XmlAssertionBuilder(writer);
        }

        @Override
        public void end() throws Exception {
            writeRequestEnd(writer);
            writeTestEnd(writer);
        }
    }
}
