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

import com.sun.javatest.Harness;
import com.sun.javatest.Parameters;
import com.sun.javatest.TestResult;
import com.sun.javatest.TestResultTable;
import com.sun.javatest.TestDescription;
import com.sun.javatest.TestFilter;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A jtreg observer that forwards callbacks to multiple observers named by the
 * {@code jtreg.observers.to.chain} system property.
 */
public class CompositeObserver implements Harness.Observer {

    private static List<Harness.Observer> instantiateObservers() {
        String prop = System.getProperty("jtreg.observers.to.chain", "");
        return Stream.of(prop.split(",")).map(n -> {
            try {
                return (Harness.Observer)Class.forName(n).getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private final List<Harness.Observer> observers;

    private CompositeObserver(List<Harness.Observer> observers) {
        this.observers = observers;
    }

    /**
     * This is the constructor which is called by default.
     */
    public CompositeObserver() {
        this(instantiateObservers());
    }

    public void startingTestRun(Parameters params) {
        observers.forEach(o -> o.startingTestRun(params));
    }

    public void startingTest(TestResult tr) {
        observers.forEach(o -> o.startingTest(tr));
    }

    public void finishedTest(TestResult tr) {
        observers.forEach(o -> o.finishedTest(tr));
    }

    public void stoppingTestRun() {
        observers.forEach(o -> o.stoppingTestRun());
    }

    public void finishedTesting(TestResultTable.TreeIterator treeIterator) {
        observers.forEach(o -> o.finishedTesting(treeIterator));
    }

    public void finishedTestRun(boolean allOK) {
        observers.forEach(o -> o.finishedTestRun(allOK));
    }

    public void error(String msg) {
        observers.forEach(o -> o.error(msg));
    }

    public void notifyOfTheFinalStats(Map<TestFilter, List<TestDescription>> filters, int... stats) {
        observers.forEach(o -> o.notifyOfTheFinalStats(filters, stats));
    }

    public void finishedTesting() {
        observers.forEach(o -> o.finishedTesting());
    }
}
