/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
        record("startingTestRun(" + params.getTestSuite().getName() + ")");
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
        record("finishedTesting(" + treeIterator.getResultStats().length + ")");
    }

    public void finishedTestRun(boolean allOK) {
        record("finishedTestRun(" + allOK + ")");
    }

    public void error(String msg) {
        record("error(" + msg + ")");
    }

    public void notifyOfTheFinalStats(Map<TestFilter, List<TestDescription>> filters, int... stats) {
        record("notifyOfTheFinalStats(" + filters.size() + ")");
    }

    public void finishedTesting() {
        record("finishedTesting()");
    }
}
