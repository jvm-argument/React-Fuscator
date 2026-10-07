package dev.reactfuscator.testing;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.reactfuscator.config.*;
import dev.reactfuscator.core.*;
import dev.reactfuscator.model.*;
import dev.reactfuscator.service.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class PipelineIntegrationTest {
    @TempDir Path directory;

    private Map<String, String> sources() {
        return Map.of(
                "sample.Main",
"""
package sample;
import java.util.*;
import java.util.function.*;
public class Main {
    private int field=7;
    private long wide=Long.MIN_VALUE;
    private static int staticField=9;
    private static String text() { return "secret: Привет 🚀\\u0000"; }
    private synchronized int work(int n) {
        int result=field;
        for(int i=0;i<n;i++) { switch(i%4) {case 0:result+=3;break;case 1:result-=8;break;case 2:result^=17;break;default:result+=i;} }
        try { if(n<0)throw new IllegalArgumentException();result+=Integer.parseInt("23"); }catch(IllegalArgumentException e){result-=4;}finally{result+=staticField;}
        field=result;staticField++;return result;
    }
    private int recursion(int n){return n<2?n:recursion(n-1)+recursion(n-2);}
    private static int flat(int n){int result=0;for(int i=0;i<n;i++){if((i&1)==0)result+=i*7;else result^=i*13;}return result;}
    public static void main(String[] args) throws Exception {
        Main main=new Main();int checksum=0;for(int i=-2;i<20;i++)checksum=checksum*31+main.work(i);
        IntUnaryOperator fn=main::recursion;checksum^=fn.applyAsInt(9);checksum^=flat(20);
        String one=text(),two=text();if(one!=two)throw new AssertionError("intern identity");
        Object[] array=checksum%2==0?new String[]{"a","b"}:new Integer[]{4,5};
        Object nested=checksum%2==0?new String[2][3]:new Integer[2][3];
        java.util.List<?> list=checksum%2==0?new ArrayList<String>():new LinkedList<Integer>();
        double value=-0.0d;float f=-0.0f;long bits=Double.doubleToRawLongBits(value)^Float.floatToRawIntBits(f);
        String extra=asset.Reader.read();int service=0;for(Service s:ServiceLoader.load(Service.class))service+=s.value();
        java.lang.reflect.Method reflection=Class.forName("sample.Reflect").getDeclaredMethod("secret");reflection.setAccessible(true);
        System.out.println(checksum+":"+one+":"+bits+":"+main.wide+":"+array.length+":"+((Object[])nested).length+":"+list.size()+":"+extra+":"+service+":"+reflection.invoke(null)+":"+new Child().run());
    }
}
""",
                "sample.Service",
                "package sample; public interface Service { int value(); }",
                "sample.Provider",
                "package sample; public class Provider implements Service { public"
                        + " Provider(){} public int value(){return 193;} }",
                "sample.Reflect",
                "package sample;public class Reflect {private static String secret(){return"
                        + " \"reflected\";}}",
                "sample.Parent",
                "package sample; public class Parent {private int value=17;private static"
                        + " int helper(){return 33;}public int run(){return value+helper();}}",
                "sample.Child",
                "package sample;public class Child extends Parent {public int"
                        + " value=12;public static int helper(){return 41;}public int"
                        + " run(){return super.run()+value+helper();}}",
                "asset.Reader",
                "package asset;public class Reader {public static String read()throws"
                        + " Exception {try(java.io.InputStream"
                        + " in=Reader.class.getResourceAsStream(\"data.txt\")){return"
                        + " \"R\"+(char)in.read();}}}");
    }

    @ParameterizedTest
    @EnumSource(ProtectionProfile.class)
    void preservesBehaviorAndRunsUnderJvmVerifier(ProtectionProfile profile) throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        sources(),
                        17,
                        Map.of(
                                "META-INF/services/sample.Service",
                                "sample.Provider\n".getBytes(StandardCharsets.UTF_8),
                                "asset/data.txt",
                                new byte[] {65}),
                        "sample.Main");
        String expected = support.execute(input);
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = profile;
        config.seed = 743567L;
        ObfuscationResult result = support.protect(input, config);
        assertEquals(expected, support.execute(result.output()));
        assertTrue(result.statistics().renamedClasses > 0);
        assertTrue(result.statistics().renamedMethods > 0);
        assertTrue(result.statistics().renamedFields > 0);
        assertTrue(
                result.statistics().transformations.getOrDefault("strings", 0L)
                                + result.statistics()
                                        .transformations
                                        .getOrDefault("indystrings", 0L)
                        > 0);
        if (profile == ProtectionProfile.EXTREME) {
            for (String id :
                    List.of(
                            "classnoise",
                            "indystrings",
                            "numbers",
                            "flow",
                            "flatten",
                            "opaque",
                            "junk",
                            "indirection",
                            "proxy",
                            "debug")) {
                assertTrue(result.statistics().transformations.getOrDefault(id, 0L) > 0, id);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 11, 17, 21, 25})
    void preservesBytecodeVersions(int release) throws Exception {
        if (Runtime.version().feature() < release) {
            return;
        }
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "v.Main",
                                "package v; public class Main {private static int f(int i){return"
                                        + " i+734;} public static void main(String[]"
                                        + " a){System.out.println(\"Hello\"+f(77));}}"),
                        release,
                        Map.of(),
                        "v.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = ProtectionProfile.EXTREME;
        config.seed = 1L;
        var result = support.protect(input, config);
        assertEquals(support.execute(input), support.execute(result.output()));
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.getName().endsWith(".class")) {
                    ClassNode c = new ClassNode();
                    new ClassReader(zip.getInputStream(entry)).accept(c, ClassReader.SKIP_CODE);
                    assertEquals(release + 44, c.version);
                }
            }
        }
    }

    @Test
    void deterministicSeedProducesIdenticalArchives() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "d.Main",
                                "package d;public class Main {public static void main(String[]"
                                        + " args){System.out.println(\"determinism\"+123);}}"),
                        17,
                        Map.of(),
                        "d.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = ProtectionProfile.EXTREME;
        config.seed = 15L;
        assertArrayEquals(
                Files.readAllBytes(support.protect(input, config).output()),
                Files.readAllBytes(support.protect(input, config).output()));
    }

    @Test
    void excludedCodeStillReceivesReferenceRemapping() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "exc.Main",
                                "package exc;public class Main {public static void main(String[]"
                                        + " a){System.out.println(new Hidden().get());}}",
                                "exc.Hidden",
                                "package exc;public class Hidden {public String get(){return"
                                        + " \"changed reference\";}}"),
                        17,
                        Map.of(),
                        "exc.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.exclude = List.of("exc/Main");
        config.profile = ProtectionProfile.EXTREME;
        var result = support.protect(input, config);
        assertEquals(support.execute(input), support.execute(result.output()));
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            assertNotNull(zip.getEntry("exc/Main.class"));
            assertNull(zip.getEntry("exc/Hidden.class"));
        }
    }

    @Test
    void absoluteResourceReferencesKeepPackagePaths() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "absolute.Main",
                                "package absolute;public class Main {public static void"
                                    + " main(String[] a)throws Exception {try(java.io.InputStream"
                                    + " in=Main.class.getResourceAsStream(\"/absolute/data.txt\")){System.out.println(in.read());}}}"),
                        17,
                        Map.of("absolute/data.txt", new byte[] {66}),
                        "absolute.Main");
        assertEquals(
                support.execute(input),
                support.execute(support.protect(input, new ObfuscationConfig()).output()));
    }

    @Test
    void cancellationDoesNotOverwriteExistingOutput() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of("c.Main", "package c;public class Main{}"), 17, Map.of(), null);
        Path output = directory.resolve("output.jar");
        byte[] sentinel = {1, 2, 3};
        Files.write(output, sentinel);
        CancellationToken token = new CancellationToken();
        token.cancel();
        ApplicationFactory factory = new ApplicationFactory();
        assertThrows(
                java.util.concurrent.CancellationException.class,
                () ->
                        factory.manager(factory.registry())
                                .obfuscate(
                                        input,
                                        output,
                                        new ObfuscationConfig(),
                                        new ProgressListener() {
                                            public void log(String s) {
                                            }

                                            public void progress(double f, String s) {
                                            }
                                        },
                                        token));
        assertArrayEquals(sentinel, Files.readAllBytes(output));
    }

    @Test
    void serializedEnumAndRecordContractsAreStable() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "contract.Main",
"""
package contract;import java.io.*;public class Main {
    public record Data(String text,int n) implements Serializable{}
    public enum Mode{ON,OFF}
    public static void main(String[] a)throws Exception {Data data=new Data("value",13);ByteArrayOutputStream out=new ByteArrayOutputStream();new ObjectOutputStream(out).writeObject(data);System.out.println(new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())).readObject()+":"+Mode.valueOf("ON"));}
}
"""),
                        17,
                        Map.of(),
                        "contract.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = ProtectionProfile.EXTREME;
        assertEquals(
                support.execute(input), support.execute(support.protect(input, config).output()));
    }

    @Test
    void missingDependencyFailsBeforePublishing() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path original =
                support.compile(
                        Map.of(
                                "dep.Main",
                                "package dep;public class Main extends Missing{}",
                                "dep.Missing",
                                "package dep;public class Missing{}"),
                        17,
                        Map.of(),
                        null);
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipFile zip = new ZipFile(original.toFile())) {
            entries.put(
                    "dep/Main.class",
                    zip.getInputStream(zip.getEntry("dep/Main.class")).readAllBytes());
        }
        Path broken = directory.resolve("missing.jar");
        new dev.reactfuscator.io.JarWriter(new dev.reactfuscator.io.AtomicFileWriter())
                .write(broken, entries);
        assertThrows(
                IllegalStateException.class,
                () -> support.protect(broken, new ObfuscationConfig()));
    }

    @Test
    void rootTextResourcesAreRemappedWithoutCorruptingSubstrings() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of("resources.Name", "package resources;public class Name{}"),
                        17,
                        Map.of(
                                "types.properties",
                                "type=resources.Name\nother=resources.NameExtra\n"
                                        .getBytes(StandardCharsets.UTF_8)),
                        null);
        var result = support.protect(input, new ObfuscationConfig());
        JsonObject mapping =
                JsonParser.parseString(Files.readString(result.mapping()))
                        .getAsJsonObject()
                        .getAsJsonObject("classes");
        String target = mapping.get("resources/Name").getAsString().replace('/', '.');
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            String text =
                    new String(
                            zip.getInputStream(zip.getEntry("types.properties")).readAllBytes(),
                            StandardCharsets.UTF_8);
            assertEquals("type=" + target + "\nother=resources.NameExtra\n", text);
        }
    }
}
