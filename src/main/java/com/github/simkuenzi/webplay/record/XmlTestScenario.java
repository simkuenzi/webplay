package com.github.simkuenzi.webplay.record;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public class XmlTestScenario implements TestScenario {
    private final Writer out;

    public XmlTestScenario(Writer out) {
        this.out = out;
    }

    @Override
    public RequestBuilder testScenario() throws XMLStreamException {
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
        writer.writeStartDocument();
        writeScenarioStart(writer);
        return new RequestBuilder() {
            @Override
            public Request request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
                writeTestStart(writer);
                writeRequest(writer, urlPath, method, headers, payload);
                return new XmlRequest(writer);
            }

            @Override
            public void end() throws XMLStreamException {
               writeScenarioEnd(writer);
            }
        };
    }

    private void writeScenarioStart(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("scenario");
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

    private void writeScenarioEnd(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
    }

    private void writeTestEnd(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
    }

    private void writeRequest(XMLStreamWriter writer, String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
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

        writer.writeEndElement();
    }

    private void writeTestStart(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("test");
    }

    public static void main(String[] args) throws XMLStreamException, TransformerException {
        StringWriter writer = new StringWriter();
        XmlTestScenario xmlTest = new XmlTestScenario(writer);
        xmlTest.testScenario()
                .request("/", "GET", Map.of("Header", "value"), "")
                .assertion("hallo", "input[greet]")
                .request("/", "POST", Map.of("Header", "valueX"), "formValues")
                .assertion("hallo", "input[greet]")
                .assertion("Bob", "input[name]")
                .end();

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StreamResult result = new StreamResult(new StringWriter());
        StreamSource source = new StreamSource(new ByteArrayInputStream(writer.toString().getBytes()));
        transformer.transform(source, result);
        String xmlString = result.getWriter().toString();
        System.out.println(xmlString);
    }

    private class XmlAssertion implements Assertion {

        private final XMLStreamWriter writer;

        public XmlAssertion(XMLStreamWriter writer) {
            this.writer = writer;
        }

        @Override
        public Assertion assertion(String expectedText, String selector) throws XMLStreamException {
            writeAssertion(writer, expectedText, selector);
            return new XmlAssertion(writer);
        }

        @Override
        public Assertion assertion(String expectedAttrName, String expectedAttrValue, String selector) throws XMLStreamException {
            writeAssertion(writer, expectedAttrName, expectedAttrValue, selector);
            return new XmlAssertion(writer);
        }

        @Override
        public Request request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
            writeTestEnd(writer);
            writeTestStart(writer);
            writeRequest(writer, urlPath, method, headers, payload);
            return new XmlRequest(writer);
        }
        @Override
        public void end() throws XMLStreamException {
            writeTestEnd(writer);
            writeScenarioEnd(writer);
        }

    }

    private class XmlRequest implements Request {
        private final XMLStreamWriter writer;

        public XmlRequest(XMLStreamWriter writer) {
            this.writer = writer;
        }

        @Override
        public Assertion assertion(String expectedAttrName, String expectedAttrValue, String selector) throws XMLStreamException {
            writeAssertion(writer, expectedAttrName, expectedAttrValue, selector);
            return new XmlAssertion(writer);
        }

        @Override
        public Assertion assertion(String expectedText, String selector) throws XMLStreamException {
            writeAssertion(writer, expectedText, selector);
            return new XmlAssertion(writer);
        }

        @Override
        public Request request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
            writeTestEnd(writer);
            writeTestStart(writer);
            writeRequest(writer, urlPath, method, headers, payload);
            return new XmlRequest(writer);
        }

        @Override
        public void end() throws XMLStreamException {
            writeTestEnd(writer);
            writeScenarioEnd(writer);
        }
    }
}
