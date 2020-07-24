package com.github.simkuenzi.webplay;

import io.javalin.Javalin;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class FullTest {
    public static final int PORT_OF_APP = 10022;
    public static final int PORT_OF_RECORDER = 10011;

    @Test
    public void get() throws Exception{
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

                RecordedScenario recordedScenario = new RecordedScenario(testFs.outputFile());
                for (RecordedTest test : recordedScenario.tests()) {
                    test.play("http://localhost:" + PORT_OF_APP + "/", Assert::assertEquals);
                }
            });
        } finally {
            app.stop();
        }
    }

    private Recorder recorder(TestFs testFs) {
        return new Recorder(testFs.outputFile(), PORT_OF_RECORDER, PORT_OF_APP,
                List.of("text/html", "application/x-www-form-urlencoded"));
    }
}
