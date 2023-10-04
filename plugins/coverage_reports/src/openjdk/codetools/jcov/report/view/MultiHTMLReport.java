package openjdk.codetools.jcov.report.view;

import openjdk.codetools.jcov.report.Coverage;
import openjdk.codetools.jcov.report.FileCoverage;
import openjdk.codetools.jcov.report.FileSet;
import openjdk.codetools.jcov.report.LineRange;
import openjdk.codetools.jcov.report.filter.SourceFilter;
import openjdk.codetools.jcov.report.source.SourceHierarchy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static java.lang.System.currentTimeMillis;

public class MultiHTMLReport extends HightlightFilteredReport {
    private final String title;
    private final String header;
    private final Function<String, String> folderHeader;
    private final Function<String, String> fileHeader;

    protected MultiHTMLReport(SourceHierarchy source, FileSet files, FileCoverage coverage,
                              String title, String header,
                              Function<String, String> folderHeader, Function<String, String> fileHeader,
                              SourceFilter highlight, SourceFilter include) {
        super(source, files, new CoverageHierarchy(files.files(), source, coverage, highlight),
                highlight, include);
        this.title = title;
        this.header = header;
        this.folderHeader = folderHeader;
        this.fileHeader = fileHeader;
    }

    public void report(Path dest) throws Exception {
        if (Files.isDirectory(dest)) {
            if (Files.list(dest).findAny().isPresent()) {
                throw new IllegalStateException("Not empty: " + dest);
            }
        } else if (Files.isRegularFile(dest)) {
            throw new IllegalStateException("Is a file: " + dest);
        } else {
            Files.createDirectories(dest);
        }
        try (HtmlOut out = new HtmlOut(dest)) {
            toc(out, "");
            code(out, "");
        }
    }

    private class HtmlOut implements HightlightFilteredReport.TOCOut, HightlightFilteredReport.FileOut,
            AutoCloseable {
        private final Path dest;
        private final BufferedWriter toc;
        private BufferedWriter folderOut = null;
        private String prevFolder = null;
        private BufferedWriter fileOut = null;
        private String prevFile = null;

        private HtmlOut(Path dest) throws IOException {
            this.dest = dest;
            toc = Files.newBufferedWriter(dest.resolve("index.html"));
            init(title, header, toc, false);
        }

        private void init(String title, String header, BufferedWriter out, boolean css) throws IOException {
            out.write("<html><head>"); out.newLine();
            out.write("<title>" + title + "</title>"); out.newLine();
            if (css)
                out.write("<style>\n" +
                    ".context {\n" +
                    "  font-weight: lighter;\n" +
                    "}\n" +
                    ".highlight {\n" +
                    "  font-weight: bold;\n" +
                    "}\n" +
                    ".covered {\n" +
                    "  font-weight: bold;\n" +
                    "  background-color: palegreen;\n" +
                    "}\n" +
                    ".uncovered {\n" +
                    "  font-weight: bold;\n" +
                    "  background-color: salmon;\n" +
                    "}\n" +
                    ".filename {\n" +
                    "  font-weight: bold;\n" +
                    "  font-size: larger;\n" +
                    "}\n" +
                    "</style>"); out.newLine();
            out.write("</head><body>\n"); out.newLine();
            out.write(header + "\n"); out.newLine();
            out.write("<table><tbody>"); out.newLine();
        }

        private void close(BufferedWriter out) throws IOException {
            out.write("</tbody></table>"); out.newLine();
            out.write("<hr>"); out.newLine();
            out.write("<body></html>");out.newLine();
        }

        @Override
        public void printFolderLine(String s, Coverage cov) throws IOException {
            String folderFile;
            if (!s.isEmpty()) {
                folderFile = s.replace('/', '_') + ".html";
                if (!s.equals(prevFolder)) {
                    if (folderOut != null) {
                        close(folderOut);
                        folderOut.close();
                    }
                    folderOut = Files.newBufferedWriter(dest.resolve(folderFile));
                    prevFolder = s;
                }
            } else folderFile = null;
            if (s.isEmpty()) toc.write("<tr><td><a>total</a></td><td>" +
                    cov + "</td></tr>");
            else toc.write("<tr><td><a href=\"" + folderFile + "\">" + s + "</a></td><td>" +
                    cov + "</td></tr>");
            toc.newLine();
            if (!s.isEmpty()) init(title, folderHeader.apply(s), folderOut, false);
        }

        @Override
        public void printFileLine(String s) throws IOException {
            System.out.println("in printFileLine " + currentTimeMillis());
            String fileFile = s.replace('/', '_') + ".html";
            var cov = coverage().get(s);
            folderOut.write("<tr><td><a href=\"" + fileFile + "\">" + s + "</a></td><td>" +
                    cov + "</td></tr>");
            folderOut.newLine();
        }

        @Override
        public void startFile(String file) throws IOException {
            System.out.println("in startFile " + currentTimeMillis());
            String fileFile = file.replace('/', '_') + ".html";
            if (!file.equals(prevFile)) {
                if (fileOut != null) {
                    close(fileOut);
                    fileOut.close();
                }
                fileOut = Files.newBufferedWriter(dest.resolve(fileFile));
                init(title, fileHeader.apply(file), fileOut, true);
                prevFile = file;
            }
        }

        @Override
        public void startLineRange(LineRange range) throws IOException {
            fileOut.write("<pre>"); fileOut.newLine();
        }

        @Override
        public void printSourceLine(int lineNo, String line, boolean highlight, Coverage coverage) throws IOException {
            fileOut.write("<a");
            if (coverage != null) {
                if (coverage.covered() > 0)
                    fileOut.write(" class=\"covered\"");
                else
                    fileOut.write(" class=\"uncovered\"");
            } else if (highlight) {
                fileOut.write(" class=\"highlight\"");
            } else
                fileOut.write(" class=\"context\"");
            fileOut.write(">");
            fileOut.write((lineNo + 1) + ": ");
            fileOut.write(line.replaceAll("</?\\s*pre\\s*>", ""));
            fileOut.write("</a>");
            fileOut.newLine();
        }

        @Override
        public void endLineRange(LineRange range) throws IOException {
            fileOut.write("</pre>"); fileOut.newLine();
            fileOut.write("<hr/>"); fileOut.newLine();
        }

        @Override
        public void endFile(String s) throws IOException {
        }

        @Override
        public void startDir(String s, Coverage cov) throws IOException {
        }

        @Override
        public void close() throws Exception {
            toc.close();
            if (folderOut != null) folderOut.close();
            if (fileOut != null) fileOut.close();
        }
    }

    public static class Builder {
        private FileSet files;
        private SourceHierarchy source;
        private SourceFilter highlight;
        private SourceFilter include;
        private String title;
        private String header;
        private Function<String, String> folderHeader;
        private Function<String, String> fileHeader;
        private FileCoverage coverage;

        public Builder setFiles(FileSet files) {
            this.files = files;
            return this;
        }

        public Builder setSource(SourceHierarchy source) {
            this.source = source;
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

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setHeader(String header) {
            this.header = header;
            return this;
        }

        public Builder setCoverage(FileCoverage coverage) {
            this.coverage = coverage;
            return this;
        }

        public MultiHTMLReport report() {
            return new MultiHTMLReport(source, files, coverage, title, header,
                    folderHeader, fileHeader, highlight, include);
        }

        public Builder setFolderHeader(Function<String, String> prefix) {
            this.folderHeader = prefix;
            return this;
        }

        public Builder setFileHeader(Function<String, String> prefix) {
            this.fileHeader = prefix;
            return this;
        }
    }
}
