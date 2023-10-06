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

import java.io.IOException;
import java.util.stream.Collectors;

class HightlightFilteredReport {
    private final FileSet files;
    private final FileItems items;
    private final CoverageHierarchy coverage;
    private final SourceHierarchy source;
    private final SourceFilter highlight;
    private final SourceFilter include;

    protected HightlightFilteredReport(SourceHierarchy source, FileSet files, FileItems items,
                                       CoverageHierarchy coverage,
                                       SourceFilter highlight, SourceFilter include) {
        this.files = files;
        this.items = items;
        this.coverage = coverage;
        this.source = source;
        this.highlight = highlight;
        this.include = include;
    }

    protected FileItems items() {
        return items;
    }

    protected void toc(TOCOut out, String s) throws Exception {
        Coverage cov = coverage.get(s);
        if (cov != null) {
            out.printFolderLine(s.isEmpty() ? "" : s, cov);
            for (var f : files.folders(s).stream().sorted().collect(Collectors.toList())) {
                toc(out, f);
            }
            for (var f : files.files(s).stream().sorted().collect(Collectors.toList())) {
                out.printFileLine(f);
            }
        }
    }

    protected void code(FileOut out, String s) throws Exception {
        Coverage cov = coverage.get(s);
        if (cov != null) {
            out.startDir(s, cov);
            for (var f : files.folders(s).stream().sorted().collect(Collectors.toList())) {
                code(out, f);
            }
            for (var file : files.files(s).stream().sorted().collect(Collectors.toList())) {
                var fileCov = coverage.getLineRanges(file);
                if (fileCov != null) {
                    out.startFile(file);
                    if (items != null) {
                        out.startItems();
                        for (var fi : items.items(file)) out.printItem(fi);
                        out.endItems();
                    }
                    var source = this.source.readFile(file);
                    var highlightRanges = highlight.ranges(file).iterator();
                    var highlightRange = highlightRanges.next();
                    for (var range : include.ranges(file)) {
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
        }
    }

    private FileItems.FileItem findItem(String file, int line) {
        return items == null ? null : items.items(file).stream().filter(i ->
                i.ranges().stream().anyMatch(r -> r.compare(line) == 0)).findAny().orElse(null);
    }

    protected CoverageHierarchy coverage() {
        return coverage;
    }

    protected interface TOCOut {
        void printFileLine(String f) throws Exception;
        void printFolderLine(String s, Coverage cov) throws Exception;
    }
    protected interface FileOut {
        void startFile(String s) throws Exception;
        void startLineRange(LineRange range) throws Exception;
        void printSourceLine(int line, String s, boolean highlight, Coverage coverage, FileItems.FileItem item)
                throws Exception;
        void endLineRange(LineRange range) throws Exception;
        void endFile(String s) throws Exception;
        void startDir(String s, Coverage cov) throws Exception;
        void startItems() throws Exception;
        void printItem(FileItems.FileItem fi) throws Exception;
        void endItems() throws Exception;
    }

    public static class Builder {
        private SourceHierarchy source;
        private FileSet files;
        private FileItems items;
        private CoverageHierarchy coverage;
        private SourceFilter highlight;
        private SourceFilter include;

        public Builder setItems(FileItems items) {
            this.items = items;
            return this;
        }

        public Builder setSource(SourceHierarchy source) {
            this.source = source;
            return this;
        }

        public Builder setFiles(FileSet files) {
            this.files = files;
            return this;
        }

        public Builder setCoverage(CoverageHierarchy coverage) {
            this.coverage = coverage;
            return this;
        }

        public Builder setHighlight(SourceFilter highlight) {
            this.highlight = highlight;
            return this;
        }

        public Builder setInclude(SourceFilter include) {
            this.include = include;
            return this;
        }

        public HightlightFilteredReport report() {
            return new HightlightFilteredReport(source, files, items, coverage, highlight, include);
        }
    }
}
