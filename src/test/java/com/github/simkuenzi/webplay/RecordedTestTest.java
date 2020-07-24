package com.github.simkuenzi.webplay;

import com.github.simkuenzi.webplay.play.RecordedTest;
import io.javalin.Javalin;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordedTestTest {

    private static final int PORT_OF_APP = 10022;

    @Test
    public void testAttrAssertion() throws Exception {
        RecordedTest recordedTest = new RecordedTest(getClass().getResource("recorded-get.xml"));
        String html = "<html><body><input name='myTextfield' value='wrong' /><textarea name='myTextarea'>someText</textarea></body></html>";
        Javalin app = Javalin.create().start(PORT_OF_APP).get("/", ctx -> ctx.html(html));
        List<String> assertions = new ArrayList<>();
        try {
            recordedTest.play("http://localhost:" + PORT_OF_APP + "/", (message, expected, actual) -> {
                assertions.add(message);
                assertTrue(message, actual.equals(expected) || actual.equals("wrong"));
            });
        } finally {
            app.stop();
        }

        assertEquals(2, assertions.size());
    }

    @Test
    public void testTextAssertion() throws Exception {
        RecordedTest recordedTest = new RecordedTest(getClass().getResource("recorded-get.xml"));
        String html = "<html><body><input name='myTextfield' value='textValue' /><textarea name='myTextarea'>wrong</textarea></body></html>";
        Javalin app = Javalin.create().start(PORT_OF_APP).get("/", ctx -> ctx.html(html));
        List<String> assertions = new ArrayList<>();
        try {
            recordedTest.play("http://localhost:" + PORT_OF_APP + "/", (message, expected, actual) -> {
                assertions.add(message);
                assertTrue(message, actual.equals(expected) || actual.equals("wrong"));
            });
        } finally {
            app.stop();
        }

        assertEquals(2, assertions.size());
    }
}