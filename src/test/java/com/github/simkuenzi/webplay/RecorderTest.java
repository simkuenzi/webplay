package com.github.simkuenzi.webplay;

import io.javalin.Javalin;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RecorderTest {

    public static final int PORT_OF_APP = 10022;
    public static final int PORT_OF_RECORDER = 10011;

    @Test
    public void startStop() throws Exception {
        TestFs.use(testFs -> {
            recorder(testFs).start().stop();
            assertOutput(testFs, "<scenario/>");
        });
    }

    @Test
    public void startWait() throws Exception {
        TestFs.use(testFs -> {
            Files.createFile(testFs.stopFile());
            Recorder.Recording recording = recorder(testFs).start();
            Thread waitThread = new Thread(handle(() -> recording.waitTillStop(testFs.stopFile())));
            waitThread.start();
            Thread.sleep(1_000); // Wait for the thread to listen on file.
            Files.writeString(testFs.stopFile(), "stop");
            waitThread.join(3_000);
            assertFalse(waitThread.isAlive());
        });
    }

    @Test
    public void get() throws Exception {
        String html = "<html><body><input name='myTextfield' value='textValue' /><textarea name='myTextarea'>someText</textarea></body></html>";
        Javalin app = Javalin.create().start(PORT_OF_APP).get("/", ctx -> ctx.html(html));
        try {
            TestFs.use(testFs -> {
                try (Recorder.Recording ignored = recorder(testFs).start()) {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET().uri(new URI("http://localhost:" + PORT_OF_RECORDER + "/"))
                            .build();
                    String response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    assertEquals(html, response);
                }
                assertOutput(testFs,
                        "<scenario>\n" +
                                "  <test>\n" +
                                "    <request urlPath=\"/\" method=\"GET\">\n" +
                                "      <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "      <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "      <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "      <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "      <header name=\"Content-Length\" value=\"0\"/>\n" +
                                "      <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "    </request>\n" +
                                "    <assertion selector=\"input[name=myTextfield]\">\n" +
                                "      <expectedAttr name=\"value\" value=\"textValue\"/>\n" +
                                "    </assertion>\n" +
                                "    <assertion selector=\"textarea[name=myTextarea]\">\n" +
                                "      <expectedText text=\"someText\"/>\n" +
                                "    </assertion>\n" +
                                "  </test>\n" +
                                "</scenario>\n");
            });
        } finally {
            app.stop();
        }

    }

    @Test
    public void post() throws Exception {
        Javalin app = Javalin.create().start(PORT_OF_APP).post("/", ctx -> ctx.redirect("redirect"));
        try {
            TestFs.use(testFs -> {
                try (Recorder.Recording ignored = recorder(testFs).start()) {
                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString("myField=Hello"))
                            .uri(new URI("http://localhost:" + PORT_OF_RECORDER + "/"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    assertEquals(302, response.statusCode());
                }
                assertOutput(testFs,
                        "<scenario>\n" +
                                "  <test>\n" +
                                "    <request urlPath=\"/\" method=\"POST\" payload=\"myField=Hello\">\n" +
                                "      <header name=\"Connection\" value=\"Upgrade, HTTP2-Settings\"/>\n" +
                                "      <header name=\"User-Agent\" value=\"Java-http-client/14.0.1\"/>\n" +
                                "      <header name=\"Host\" value=\"localhost:10011\"/>\n" +
                                "      <header name=\"HTTP2-Settings\" value=\"AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA\"/>\n" +
                                "      <header name=\"Content-Length\" value=\"13\"/>\n" +
                                "      <header name=\"Upgrade\" value=\"h2c\"/>\n" +
                                "      <header name=\"Content-Type\" value=\"application/x-www-form-urlencoded\"/>\n" +
                                "    </request>\n" +
                                "  </test>\n" +
                                "</scenario>");
            });
        } finally {
            app.stop();
        }
    }

    private void assertOutput(TestFs testFs, String expected) throws IOException, SAXException {
        try (Reader actual = Files.newBufferedReader(testFs.outputFile())) {
            assertXMLEqual(new StringReader(expected), actual);
        }
    }

    private Recorder recorder(TestFs testFs) {
        return new Recorder(testFs.outputFile(), PORT_OF_RECORDER, PORT_OF_APP,
                List.of("text/html", "application/x-www-form-urlencoded"));
    }

    private Runnable handle(UnsafeRunnable unsafeRunnable) {
        return () -> {
            try {
                unsafeRunnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private interface UnsafeRunnable {
        void run() throws Exception;
    }
}