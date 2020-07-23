package com.github.simkuenzi.webplay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

class TestFs {

    private Path root;

    TestFs(Path root) {
        this.root = root;
    }

    static void use(Test test) throws Exception {
        Path testPath = Path.of(System.getProperty("com.github.simkuenzi.webplay.testfs"));
        if (Files.exists(testPath)) {
            Files.walk(testPath).sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Files.createDirectories(testPath);
        test.run(new TestFs(testPath));
    }

    Path outputFile() {
        return root.resolve("scenario.xml");
    }

    public Path stopFile() {
        return root.resolve("stop");
    }

    interface Test {
        void run(TestFs testFs) throws Exception;
    }
}
