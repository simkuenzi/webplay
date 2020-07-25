package com.github.simkuenzi.webplay;

import com.github.simkuenzi.webplay.record.Recorder;
import com.github.simkuenzi.webplay.record.Recording;
import io.javalin.Javalin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

class TestEnv {

    private static final int PORT_OF_APP = 10022;
    public static final int PORT_OF_RECORDER = 10011;
    private final TestFs testFs;

    TestEnv(TestFs testFs) {
        this.testFs = testFs;
    }

    void record(Test test) throws Exception {
        try (Recording recording = open()) {
            record(recording);
            test.run();
        }
    }

    Javalin javalin() {
        return Javalin.create().start(PORT_OF_APP);
    }

    URI recorderUri() throws URISyntaxException {
        return recorderUri("/");
    }

    URI recorderUri(String path) throws URISyntaxException {
        return new URI("http://localhost:" + PORT_OF_RECORDER + path);
    }

    String appUri() {
        return "http://localhost:" + PORT_OF_APP + "/";
    }

    interface Test {
        void run() throws Exception;
    }

    Recording open() throws Exception {
        return new Recorder().open(PORT_OF_RECORDER, "/");
    }

    Thread record(Recording recording) {
        Thread t = new Thread(() -> {
            try {
                recording.run(PORT_OF_APP, testFs.outputFile(),  List.of("text/html", "application/x-www-form-urlencoded"), testFs.stopFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return t;
    }
}
