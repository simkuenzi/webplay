package com.github.simkuenzi.webplay;

import com.github.simkuenzi.webplay.record.Recording;
import io.javalin.Javalin;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.*;

public class RecorderTest {

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void startStop() throws Exception {
        TestFs.use(testFs -> {
            new TestEnv(testFs).record(() -> {
                Thread.sleep(5000);
            });
            assertOutput(testFs, "<test/>");
        });
    }

    @Test
    public void startWait() throws Exception {
        TestFs.use(testFs -> {
            Files.createFile(testFs.stopFile());
            Files.createFile(testFs.anyFile());

            TestEnv testEnv = new TestEnv(testFs);
            Recording recording = testEnv.open();
            Thread t = testEnv.record(recording);
            Thread.sleep(1_000); // Wait for the thread to listen on file.

            Files.writeString(testFs.anyFile(), "stop");
            t.join(3_000);
            assertTrue(t.isAlive());

            Files.writeString(testFs.stopFile(), "stop");
            t.join(3_000);
            assertFalse(t.isAlive());
        });
    }

    @Test
    public void get() throws Exception {
        String html = "<html><body><input name='myTextfield' value='textValue' /><textarea name='myTextarea'>someText</textarea></body></html>";
        TestFs.use(testFs -> {
            TestEnv testEnv = new TestEnv(testFs);
            Javalin app = testEnv.javalin().get("/", ctx -> ctx.html(html));
            try {
                testEnv.record(() -> {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET().uri(testEnv.recorderUri())
                            .build();
                    String response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(html, response);
                });
                assertOutput(testFs,
                        "<test>\n" +
                                "  <request urlPath=\"/\" method=\"GET\">\n" +
                                "    <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "    <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "    <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "    <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "    <header name=\"Content-Length\" value=\"0\"/>\n" +
                                "    <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    <assertion selector=\"input[name=myTextfield]\">\n" +
                                "      <expectedAttr xml:space=\"preserve\" name=\"value\">textValue</expectedAttr></assertion>\n" +
                                "    <assertion selector=\"textarea[name=myTextarea]\">\n" +
                                "      <expectedText xml:space=\"preserve\">someText</expectedText></assertion>\n" +
                                "  </request>\n" +
                                "</test>");

            } finally {
                app.stop();
            }
        });
    }

    @Test
    public void post() throws Exception {
        TestFs.use(testFs -> {
            TestEnv testEnv = new TestEnv(testFs);
            Javalin app = testEnv.javalin().post("/", ctx -> ctx.redirect("redirect"));
            try {
                testEnv.record(() -> {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString("myField=Hello"))
                            .uri(testEnv.recorderUri())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    assertEquals(302, response.statusCode());
                });
                assertOutput(testFs,
                        "<test>\n" +
                                "  <request urlPath=\"/\" method=\"POST\">\n" +
                                "    <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "    <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "    <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "    <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "    <header name=\"Content-Length\" value=\"13\"/>\n" +
                                "    <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    <header name=\"Content-Type\" value=\"application/x-www-form-urlencoded\"/>\n" +
                                "    <payload xml:space=\"preserve\">myField=Hello</payload></request>\n" +
                                "</test>");
            } finally {
                app.stop();
            }
        });

    }

    @Test
    public void getMultiline() throws Exception {
        String html = "<html><body><input name='myTextfield' value='textValue' /><textarea name='myTextarea'>line1\n\tline2</textarea></body></html>";
        TestFs.use(testFs -> {
            TestEnv testEnv = new TestEnv(testFs);
            Javalin app = testEnv.javalin().get("/", ctx -> ctx.html(html));
            try {
                testEnv.record(() -> {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET().uri(testEnv.recorderUri())
                            .build();
                    String response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(html, response);
                });
                assertOutput(testFs,
                        "<test>\n" +
                                "  <request urlPath=\"/\" method=\"GET\">\n" +
                                "    <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "    <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "    <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "    <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "    <header name=\"Content-Length\" value=\"0\"/>\n" +
                                "    <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    <assertion selector=\"input[name=myTextfield]\">\n" +
                                "      <expectedAttr xml:space=\"preserve\" name=\"value\">textValue</expectedAttr></assertion>\n" +
                                "    <assertion selector=\"textarea[name=myTextarea]\">\n" +
                                "      <expectedText xml:space=\"preserve\">line1\n" +
                                "\tline2</expectedText></assertion>\n" +
                                "  </request>\n" +
                                "</test>");
            } finally {
                app.stop();
            }
        });
    }

    @Test
    public void filter() throws Exception {
        String html = "<html><body><input name='myTextfield' value='textValue' /></body></html>";
        String css = ".class { font-size: 12pt; }";
        byte[] image = new byte[0];

        TestFs.use(testFs -> {
            TestEnv testEnv = new TestEnv(testFs);
            Javalin app = testEnv.javalin().get("/html", ctx -> ctx.html(html))
                    .get("/css", ctx -> ctx.contentType("text/css").result(css))
                    .get("/image", ctx -> ctx.contentType("image/png").result(image));

            try {
                testEnv.record(() -> {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest requestHtml = HttpRequest.newBuilder()
                            .GET().uri(testEnv.recorderUri("/html"))
                            .build();
                    String responseHtml = httpClient.send(requestHtml, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(html, responseHtml);
                    HttpRequest requestCss = HttpRequest.newBuilder()
                            .GET().uri(testEnv.recorderUri("/css"))
                            .build();
                    String responseCss = httpClient.send(requestCss, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(css, responseCss);
                    HttpRequest requestImage = HttpRequest.newBuilder()
                            .GET().uri(testEnv.recorderUri("/image"))
                            .build();
                    byte[] responseImage = httpClient.send(requestImage, HttpResponse.BodyHandlers.ofByteArray()).body();
                    assertArrayEquals(image, responseImage);
                });
                assertOutput(testFs,
                        "<test>\n" +
                                "  <request urlPath=\"/html\" method=\"GET\">\n" +
                                "    <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "    <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "    <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "    <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "    <header name=\"Content-Length\" value=\"0\"/>\n" +
                                "    <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    <assertion selector=\"input[name=myTextfield]\">\n" +
                                "      <expectedAttr xml:space=\"preserve\" name=\"value\">textValue</expectedAttr></assertion>\n" +
                                "  </request>\n" +
                                "</test>\n");
            } finally {
                app.stop();
            }
        });
    }

    @Test
    public void multipleRequests() throws Exception {
        String html = "<html><body><input name='myTextfield' value='textValue' /><textarea name='myTextarea'>someText</textarea></body></html>";
        TestFs.use(testFs -> {
            TestEnv testEnv = new TestEnv(testFs);
            Javalin app = testEnv.javalin().get("/", ctx -> ctx.html(html));
            try {
                testEnv.record(() -> {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET().uri(testEnv.recorderUri())
                            .build();
                    String response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(html, response1);
                    String response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(html, response2);
                });
                assertOutput(testFs,
                        "<test>\n" +
                                "  <request urlPath=\"/\" method=\"GET\">\n" +
                                "    <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "    <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "    <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "    <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "    <header name=\"Content-Length\" value=\"0\"/>\n" +
                                "    <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    <assertion selector=\"input[name=myTextfield]\">\n" +
                                "      <expectedAttr xml:space=\"preserve\" name=\"value\">textValue</expectedAttr></assertion>\n" +
                                "    <assertion selector=\"textarea[name=myTextarea]\">\n" +
                                "      <expectedText xml:space=\"preserve\">someText</expectedText></assertion>\n" +
                                "  </request>\n" +
                                "  <request urlPath=\"/\" method=\"GET\">\n" +
                                "    <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "    <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "    <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "    <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "    <header name=\"Content-Length\" value=\"0\"/>\n" +
                                "    <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    <assertion selector=\"input[name=myTextfield]\">\n" +
                                "      <expectedAttr xml:space=\"preserve\" name=\"value\">textValue</expectedAttr></assertion>\n" +
                                "    <assertion selector=\"textarea[name=myTextarea]\">\n" +
                                "      <expectedText xml:space=\"preserve\">someText</expectedText></assertion>\n" +
                                "  </request>\n" +
                                "</test>");
            } finally {
                app.stop();
            }
        });
    }

    private void assertOutput(TestFs testFs, String expected) throws IOException, SAXException {
        try (Reader actual = Files.newBufferedReader(testFs.outputFile())) {
            assertXMLEqual(new StringReader(expected), actual);
        }
    }
}
