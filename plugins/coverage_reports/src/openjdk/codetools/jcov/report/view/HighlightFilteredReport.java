/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package openjdk.codetools.jcov.report.view;

import openjdk.codetools.jcov.report.Coverage;
import openjdk.codetools.jcov.report.FileItems;
import openjdk.codetools.jcov.report.FileSet;
import openjdk.codetools.jcov.report.LineRange;
import openjdk.codetools.jcov.report.filter.SourceFilter;
import openjdk.codetools.jcov.report.source.SourceHierarchy;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This support class allows to create reports which only <b>include</b> code which is selected by a filter.
 * Additionally, this class allows to <b>highlight</b> some portion of the included source code, leaving the
 * non-highlighted code in the report for context.
 * The implementation uses visitor pattern twice: once for creating a table of content and then
 * for the body of the report.
 * @see #toc(TOCOut, String)
 * @see #code(FileOut, String)
 * @see TOCOut
 * @see FileOut
 */
class HighlightFilteredReport extends FilteredReport {
    private final SourceFilter highlight;

    /**
     *
     * @param source
     * @param files
     * @param items
     * @param coverage
     * @param highlight
     * @param include
     */
    protected HighlightFilteredReport(SourceHierarchy source, FileSet files, FileItems items,
                                      CoverageHierarchy coverage,
                                      SourceFilter highlight, SourceFilter include) {
        super(source, files, items, coverage, include);
        this.highlight = highlight;
    }

    public SourceFilter highlight() {
        return highlight;
    }

    protected void code(FileOut out, String s) throws Exception {
        out.start();
        Coverage cov = coverage.get(s);
        if (cov != null) {
            out.startFolder(s, cov);
            for (var f : files.folders(s).stream().sorted().collect(Collectors.toList())) {
                code(out, f);
            }
            for (var file : files.files(s).stream().sorted().collect(Collectors.toList())) {
                var fileCov = coverage.getLineRanges(file);
                if (fileCov != null) {
                    out.startFile(file);
                    if (items != null) {
                        List<FileItems.FileItem> itemss = this.items.items(file).stream()
                                .sorted((o, a) -> o.item().compareTo(a.item())).collect(Collectors.toList());
                        if (itemss != null && !itemss.isEmpty()) {
                            out.startItems();
                            for (var fi : itemss) out.printItem(fi);
                            out.endItems();
                        }
                    }
                    var source = this.source.readFile(file);
                    var highlightRanges = highlight != null ?
                            highlight.ranges(file).iterator() :
                            List.<LineRange>of().iterator();
                    var highlightRange = highlightRanges.hasNext() ? highlightRanges.next() : null;
                    List<LineRange> ranges = include != null ?
                            include.ranges(file) : List.of(new LineRange(1, source.size() + 1));
                    for (var range : ranges) {
                        out.startLineRange(range);
                        for (int line = range.first() - 1; line < range.last() && line < source.size(); line++) {
                            while (highlightRange != null && highlightRange.compare(line) > 0)
                                highlightRange = highlightRanges.hasNext() ? highlightRanges.next() : null;
                            boolean highlight = highlightRange != null && highlightRange.compare(line + 1) == 0;
                            out.printSourceLine(line, source.get(line), highlight,
                                    fileCov.containsKey(line + 1) ? fileCov.get(line + 1).coverage() : null,
                                    findItem(file, line + 1));
                        }
                        out.endLineRange(range);
                    }
                    out.endFile(s);
                }
            }
            out.endFolder(s, cov);
        }
        out.end();
    }

    public static class Highlighter {
        private final SourceFilter highlight;
        private Iterator<LineRange> lastFileRanges;
        private LineRange lastRange = null;

        public Highlighter(SourceFilter highlight) {
            this.highlight = highlight;
        }

        public void visitFile(String file) {
            lastFileRanges = highlight.ranges(file).iterator();
        }

        public boolean isHighlighted(int line) {
            if (lastRange == null)
                if (lastFileRanges.hasNext()) lastRange = lastFileRanges.next();
                else return false;
            while (lastRange.compare(line) > 0 && lastFileRanges.hasNext()) lastRange = lastFileRanges.next();
            return lastRange.compare(line) == 0;
        }
    }

    public abstract static class FileOut implements FilteredReport.FileOut {
        private final SourceFilter highlight;
        private Iterator<LineRange> lastFileRanges;
        String lastFile;
        private LineRange lastRange = null;

        protected FileOut(SourceFilter highlight) {
            this.highlight = highlight;
        }

        @Override
        public final void printSourceLine(int line, String s, Coverage coverage,
                                          List<FileItems.FileItem> items) throws Exception {
            boolean isHighlight;
            if (highlight != null) {
                if (!Objects.equals(lastFile, s)) {
                    lastFile = s;
                    List<LineRange> ranges = highlight.ranges(s);
                    lastFileRanges = ranges != null ? ranges.iterator() : List.<LineRange>of().iterator();
                }
                if (lastRange == null || lastRange.compare(line) < 0) {
                    while (lastFileRanges.hasNext()) {
                        lastRange = lastFileRanges.next();
                        if (lastRange.compare(line) >= 0) break;
                    }
                }
                isHighlight = lastRange != null && lastRange.compare(line) == 0;
            } else isHighlight = false;
            printSourceLine(line, s, isHighlight, coverage, items);
        }
        public abstract void printSourceLine(int line, String s, boolean highlight, Coverage coverage,
                                          List<FileItems.FileItem> items) throws Exception;
    }
}
