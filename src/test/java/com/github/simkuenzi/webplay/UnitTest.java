package com.github.simkuenzi.webplay;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Path;
import java.util.List;

@Ignore
@RunWith(Parameterized.class)
public class UnitTest {

    @Parameterized.Parameters
    public static List<RecordedTest> getParams() throws Exception {
        return new RecordedScenario(Path.of("scenario.xml")).tests();
    }

    private final RecordedTest recordedTest;

    public UnitTest(RecordedTest recordedTest) {
        this.recordedTest = recordedTest;
    }

    @Test
    public void test() throws Exception {
        recordedTest.play("http://localhost:9000", Assert::assertEquals);
    }
}
