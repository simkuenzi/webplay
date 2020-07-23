package com.github.simkuenzi.webplay;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

public class RecorderTest {

    @Test
    public void startStop() throws Exception {
        recorder().start().stop();
    }

    private Recorder recorder() {
        return new Recorder(Paths.get("scenario.xml"), 9033, 9000,
                List.of("text/html", "application/x-www-form-urlencoded"));
    }
}
