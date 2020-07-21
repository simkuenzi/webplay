package com.github.simkuenzi.webplay;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class RecordedTest {

    private static final List<String> restrictedHeaders = Arrays.asList("host", "connection");

    private final XPathFactory factory = XPathFactory.newInstance();
    private final XPathExpression urlPathExpr = factory.newXPath().compile("request/urlPath/text()");
    private final XPathExpression methodExpr = factory.newXPath().compile("request/method/text()");
    private final XPathExpression payloadExpr = factory.newXPath().compile("request/playload/text()");
    private final XPathExpression headerExpr = factory.newXPath().compile("request/header");
    private final XPathExpression headerNameExpr = factory.newXPath().compile("name/text()");
    private final XPathExpression headerValueExpr = factory.newXPath().compile("value/text()");
    private final XPathExpression assertionExpr = factory.newXPath().compile("assertion");
    private final XPathExpression selectorExpr = factory.newXPath().compile("selector/text()");
    private final XPathExpression expectedTextExpr = factory.newXPath().compile("expectedText");
    private final XPathExpression expectedAttrNameExpr = factory.newXPath().compile("expectedAttr/name");
    private final XPathExpression expectedAttrValueExpr = factory.newXPath().compile("expectedAttr/value");

    private final Node testNode;

    public RecordedTest(Node testNode) throws Exception {
        this.testNode = testNode;
    }

    public void play(String baseUrl, AssertionMethod assertionMethod) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .method(methodExpr.evaluate(testNode), HttpRequest.BodyPublishers.ofString(payloadExpr.evaluate(testNode)))
                .uri(new URI(baseUrl + urlPathExpr.evaluate(testNode)));

        NodeList headerNodes = (NodeList) headerExpr.evaluate(testNode, XPathConstants.NODESET);
        IntStream.range(0, headerNodes.getLength()).mapToObj(headerNodes::item).forEach(headerNode -> {
            try {
                String name = headerNameExpr.evaluate(headerNode);
                if (!restrictedHeaders.contains(name.toLowerCase())) {
                    requestBuilder.header(
                            name,
                            headerValueExpr.evaluate(headerNode)
                    );
                }
            } catch (XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        });

        HttpRequest httpRequest = requestBuilder.build();
        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        Document doc = Jsoup.parse(httpResponse.body());

        NodeList assertions = (NodeList) assertionExpr.evaluate(testNode, XPathConstants.NODESET);
        IntStream.range(0, assertions.getLength()).mapToObj(assertions::item).forEach(assertion -> {
            try {
                Node expectedText = (Node) expectedTextExpr.evaluate(assertion, XPathConstants.NODE);
                Node expectedAttrName = (Node) expectedAttrNameExpr.evaluate(assertion, XPathConstants.NODE);
                Node expectedAttrValue = (Node) expectedAttrValueExpr.evaluate(assertion, XPathConstants.NODE);
                String selector = selectorExpr.evaluate(assertion);
                Element actual = doc.selectFirst(selector);

                if (expectedText != null) {
                    String expected = expectedText.getTextContent();
                    assertionMethod.call(
                            String.format("The element %s does evaluate to %s, but %s is expected.", selector, actual, expected),
                            expected, actual.text()
                    );
                } else if (expectedAttrName != null && expectedAttrValue != null) {
                    String expected = expectedAttrValue.getTextContent();
                    String attrName = expectedAttrName.getTextContent();
                    String actualValue = actual.attr(attrName);
                    assertionMethod.call(
                            String.format("The attribute %s of the element %s does evaluate to %s, but %s is expected.", attrName, selector, actualValue, expected),
                            expected, actualValue
                    );
                }
            } catch (XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    interface AssertionMethod {
        void call(String message, String expected, String actual);
    }
}
