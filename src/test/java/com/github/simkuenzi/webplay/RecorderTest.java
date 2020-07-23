package com.github.simkuenzi.webplay;

import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertFalse;

public class RecorderTest {

    @Test
    public void startStop() throws Exception {
        TestFs.use(testFs -> {
            recorder(testFs).start().stop();
            try (Reader actual = Files.newBufferedReader(testFs.outputFile())) {
                assertXMLEqual(new StringReader("<scenario/>"), actual);
            }
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

    private Recorder recorder(TestFs testFs) {
        return new Recorder(testFs.outputFile(), 9033, 9000,
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
