package com.github.simkuenzi.webplay;

import com.github.simkuenzi.webplay.play.RecordedTest;
import io.javalin.Javalin;
import org.junit.Assert;
import org.junit.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.Assert.assertEquals;

public class FullTest {
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

                RecordedTest recordedTest = new RecordedTest(testFs.outputFile());
                recordedTest.play(testEnv.appUri(), Assert::assertEquals);
            } finally {
                app.stop();
            }
        });
    }
}
