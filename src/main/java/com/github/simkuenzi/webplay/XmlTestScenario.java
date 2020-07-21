package com.github.simkuenzi.webplay;

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
    public Request request(String urlPath, String method, Map<String, String> headers, String payload) throws XMLStreamException {
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
        writer.writeStartDocument();
        writeScenarioStart(writer);
        writeTestStart(writer);
        writeRequest(writer, urlPath, method, headers, payload);
        return new XmlRequest(writer);
    }

    private void writeScenarioStart(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("scenario");
    }

    private void writeAssertion(XMLStreamWriter writer, String expected, String selector) throws XMLStreamException {
        writer.writeStartElement("assertion");
        writer.writeAttribute("selector", selector);
        writer.writeEmptyElement("expectedAttr");
        writer.writeAttribute("name", "value");
        writer.writeAttribute("value", expected);
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
        if (!payload.isEmpty()) {
            writer.writeAttribute("payload", payload);
        }

        for (Map.Entry<String, String> header : headers.entrySet()) {
            writer.writeEmptyElement("header");
            writer.writeAttribute("name", header.getKey());
            writer.writeAttribute("value", header.getValue());
        }

        writer.writeEndElement();
    }

    private void writeTestStart(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("test");
    }

    public static void main(String[] args) throws XMLStreamException, TransformerException {
        StringWriter writer = new StringWriter();
        XmlTestScenario xmlTest = new XmlTestScenario(writer);
        xmlTest
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
        public Assertion assertion(String expected, String selector) throws XMLStreamException {
            writeAssertion(writer, expected, selector);
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
        public Assertion assertion(String expected, String selector) throws XMLStreamException {
            writeAssertion(writer, expected, selector);
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
