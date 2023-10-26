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
package openjdk.codetools.jcov.report.jcov;

import com.sun.tdk.jcov.Instr;
import com.sun.tdk.jcov.data.FileFormatException;
import com.sun.tdk.jcov.instrument.DataRoot;
import openjdk.codetools.jcov.report.FileItems;
import openjdk.codetools.jcov.report.FileSet;
import openjdk.codetools.jcov.report.source.SourcePath;
import openjdk.codetools.jcov.report.view.MultiHTMLReport;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.*;

public class LostKeptTest {
    List<Map<String, String>> classes = List.of(Map.of(
            "p/a/A.java", """
        package p.a; 
        public class A {
            public static void a() {System.out.println("a");}
            public static void b() {System.out.println("b");}
            public static void c() {System.out.println("c");}
            public static void d() {System.out.println("d");}
            public static void e() {System.out.println("e");}
            public static void main(String[] argv) {A.a();A.d();A.e();p.b.B.b();I.i1();}
            public static class I {
                public static void i1() {System.out.println("i1");}
                public static void i2() {System.out.println("i2");}
            }
        }
        """,
            "p/b/B.java", """
        package p.b; 
        public class B {
            public static void b() {System.out.println("b");}
        }        
        """),
        Map.of(
            "p/a/A.java", """
        package p.a; 
        public class A {
            public enum TYPE {ELEM_1, ELEM_2};
            public static TYPE type = TYPE.ELEM_1;
            public static void a() {
                System.out.println("a");
            }
            public static void b() {
                System.out.println("b");
            }
            public static void c() {
                System.out.println("c");
            }
            public static void d() {
                System.out.println("d");
            }
            public static void f() {
                System.out.println("e");
            }
            public static void main(String[] argv) {
                A.a();A.b();A.f();p.c.C.c();I.i2();
            }
            public static class I {
                public static void i1() {
                    System.out.println("i1");
                }
                public static void i2() {
                    System.out.println("i2");
                }
            }
            public static TYPE type1 = TYPE.ELEM_1;
        }
        """,
            "p/c/C.java", """
        package p.c; 
        public class C {
            public static void c() {System.out.println("c");}
        }        
        """));
    Path[] instrumentedDirs = new Path[2];
    Path wd;
    Path[] sourceDirs = new Path[2];
    JCovMethodCoverageComparison items;
    DataRoot coverage;
    @BeforeClass
    public void setup() throws IOException, InterruptedException, FileFormatException {
        wd = Files.createTempDirectory("lost_found_");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        for (int i = 0; i < 2; i++) {
            var source = wd.resolve("source_" + i);
            sourceDirs[i] = source;
            List<File> javaFiles = new ArrayList<>();
            for (var f : classes.get(i).keySet()) {
                var dirName = f.substring(0, f.lastIndexOf('/'));
                var fileName = f.substring(f.lastIndexOf('/') + 1);
                var packageDir = source.resolve(dirName);
                Files.createDirectories(packageDir);
                var javaFile = packageDir.resolve(fileName);
                Files.write(javaFile, List.of(classes.get(i).get(f)));
                javaFiles.add(javaFile.toFile());
            }
            Iterable<? extends JavaFileObject> cu = fileManager.getJavaFileObjectsFromFiles(javaFiles);
            compiler.getTask(null, fileManager, null, null, null, cu).call();
        }
        //instrument
        for (int i = 0; i < 2; i++) {
            var instrumented = wd.resolve("instrumented_" + i);
            instrumentedDirs[i] = instrumented;
            assertEquals(0, new ProcessBuilder(
                    Path.of(System.getProperty("java.home")).resolve("bin").resolve("java").toString(),
                    "-cp", System.getProperty("java.class.path"),
                    Instr.class.getName(),
                    "-rt", findRT(),
                    "-t", sourceDirs[i].resolve("template.xml").toString(),
                    "-o", instrumented.toString(),
                    sourceDirs[i].toString()).inheritIO().start().waitFor());
        }
        //run
        for (int i = 0; i < 2; i++) {
            Files.move(sourceDirs[i].resolve("template.xml"), instrumentedDirs[i].resolve("template.xml"));
            assertEquals(0, new ProcessBuilder(
                    Path.of(System.getProperty("java.home")).resolve("bin").resolve("java").toString(),
                    "-cp", instrumentedDirs[i].toString() + System.getProperty("path.separator") + System.getProperty("java.class.path"),
                    "p.a.A").inheritIO().directory(instrumentedDirs[i].toFile()).start().waitFor());
        }
        coverage = DataRoot.read(instrumentedDirs[1].resolve("result.xml").toString());
        items = new JCovMethodCoverageComparison(
                DataRoot.read(instrumentedDirs[0].resolve("result.xml").toString()),
                coverage, f -> f);
    }
    private String findRT() {
        return Arrays.stream(System.getProperty("java.class.path").split(":"))
                .filter(p -> p.endsWith("jcov_file_saver.jar")).findAny().get();
    }
    private FileItems.FileItem find(String clss, String method) {
        return items.items(clss).stream().filter(i -> i.item().equals(method + "()V")).findAny().get();
    }
    @Test
    void testItems() {
        assertEquals(find("p/a/A.java", "a").quality(), FileItems.Quality.GOOD);
        assertEquals(find("p/a/A.java", "b").quality(), FileItems.Quality.GOOD);
        assertEquals(find("p/a/A.java", "c").quality(), FileItems.Quality.SO_SO);
        assertEquals(find("p/a/A.java", "d").quality(), FileItems.Quality.BAD);
        assertEquals(find("p/a/A.java", "f").quality(), FileItems.Quality.IGNORE);
        assertEquals(find("p/a/A.java", "I$i1").quality(), FileItems.Quality.BAD);
        assertEquals(find("p/a/A.java", "I$i2").quality(), FileItems.Quality.GOOD);
        assertEquals(find("p/c/C.java", "c").quality(), FileItems.Quality.IGNORE);
        assertNull(items.items("p/B.java"));
        assertEquals(0, items.items("p/a/A.java").stream().filter(i -> i.item().equals("e()V")).count());
    }
    @Test(dependsOnMethods = "testItems")
    void testReport() throws Exception {
        var reportDir = wd.resolve("report");
        new MultiHTMLReport.Builder().setItems(items).setCoverage(new JCovLineCoverage(coverage))
                .setFolderHeader(s -> "<h1>Lost/kept method coverage</h1>")
                .setFileHeader(s -> "<h1>Lost/kept method coverage</h1>")
                .setTitle("Lost/kept method coverage")
                .setSource(new SourcePath(sourceDirs[1], sourceDirs[1]))
                .setFiles(new FileSet(classes.get(1).keySet()))
                .report().report(reportDir);
        var content = Files.readAllLines(reportDir.resolve("index.html"));
        assertTrue(content.contains("Line coverage: 61.00%(14/23)"));
        assertTrue(content.stream().anyMatch(l ->
                Pattern.matches(".*class=\"item_not_so_good\".*Lost.*2.*", l)));
        content = Files.readAllLines(reportDir.resolve("p_a_A.java.html"));
        assertTrue(content.stream().anyMatch(l ->
                Pattern.matches(".*item_not_so_good.*d\\(\\)V.*", l)));
        assertTrue(content.stream().anyMatch(l ->
                Pattern.matches(
                        ".*href=\"#item_f\\(\\)V\".*class=\"item_ignore\".*title=\"f\\(\\)" +
                                "V.*System.out.println\\(\"e\"\\);.*",
                        l)));
    }
}
