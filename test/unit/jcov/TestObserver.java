package jcov;

import com.sun.javatest.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link Harness.Observer} used by {@link CompositeObserverTest} as a
 * delegate of {@link CompositeObserver}. Each instance records its jtreg
 * callbacks in a separate log file, allowing the test to compare the callback
 * sequences received by multiple delegates.
 */
public class TestObserver implements Harness.Observer {

    private static final AtomicInteger nextId = new AtomicInteger();

    private final List<String> callBacks = new ArrayList<>();
    private final int id = nextId.incrementAndGet();

    private void record(String callback) {
        callBacks.add(callback);
        Path logs = Path.of(System.getProperty("jcov.testObserver.files"));
            try {
                Files.writeString(logs.resolve(id + ".log"),
                        callback + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

    public List<String> getCallBacks() {
        return callBacks;
    }

    public void startingTestRun(Parameters params) {
        record("startingTestRun(" + params + ")");
    }

    public void startingTest(TestResult tr) {
        record("startingTest(" + tr.getTestName() + ")");
    }

    public void finishedTest(TestResult tr) {
        record("finishedTest(" + tr.getTestName() + ")");
    }

    public void stoppingTestRun() {
        record("stoppingTestRun()");
    }

    public void finishedTesting(TestResultTable.TreeIterator treeIterator) {
        record("finishedTesting(" + treeIterator + ")");
    }

    public void finishedTestRun(boolean allOK) {
        record("finishedTestRun(" + allOK + ")");
    }

    public void error(String msg) {
        record("error(" + msg + ")");
    }

    public void notifyOfTheFinalStats(Map<TestFilter, List<TestDescription>> filters, int... stats) {
        record("notifyOfTheFinalStats(" + filters + ", " + Arrays.toString(stats) + ")");
    }

    public void finishedTesting() {
        record("finishedTesting()");
    }
}
