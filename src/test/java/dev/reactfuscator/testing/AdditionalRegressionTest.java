package dev.reactfuscator.testing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.reactfuscator.config.ConfigParser;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ApplicationFactory;
import dev.reactfuscator.io.AtomicFileWriter;
import dev.reactfuscator.io.JarWriter;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.mapping.MappingWriter;
import dev.reactfuscator.mapping.MemberKey;
import dev.reactfuscator.mapping.RetraceService;
import dev.reactfuscator.service.CancellationToken;
import dev.reactfuscator.service.ProgressListener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AdditionalRegressionTest {
    @TempDir Path directory;

    @Test
    void encryptsPrivateConstantValuesAndPreservesFieldReflection() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "fields.Main",
"""
package fields;public class Main {
    private static final String hidden="private-pool-secret";
    private final String instance="instance-secret";
    public static final String publicApi="public-api-contract";
    public static void main(String[] a)throws Exception {java.lang.reflect.Field f=Main.class.getDeclaredField("hidden");f.setAccessible(true);System.out.println(f.get(null)+":"+new Main().instance+":"+publicApi);}
}
"""),
                        17,
                        Map.of(),
                        "fields.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = ProtectionProfile.EXTREME;
        config.keepMembers = List.of("fields/Main#publicApi**");
        var result = support.protect(input, config);
        assertEquals(support.execute(input), support.execute(result.output()));
        boolean publicConstant = false;
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.getName().endsWith(".class")) {
                    String bytes =
                            new String(
                                    zip.getInputStream(entry).readAllBytes(),
                                    StandardCharsets.ISO_8859_1);
                    assertFalse(bytes.contains("private-pool-secret"));
                    assertFalse(bytes.contains("instance-secret"));
                    publicConstant |= bytes.contains("public-api-contract");
                }
            }
        }
        assertTrue(publicConstant);
    }

    @Test
    void flatteningIsDeterministicWithSwitchesAndReferenceLocals() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "flat.Main",
"""
package flat;public class Main{
    private static long calculate(String input,int count){Object item=input;long sum=13;double d=1.25;for(int i=0;i<count;i++){switch(i%3){case 0:item=input;sum+=i;break;case 1:item=Integer.valueOf(i);sum^=i*17;break;default:sum-=i;}d+=i;}return sum+item.toString().length()+(long)d;}
    public static void main(String[] a){System.out.println(calculate("start",21));}
}
"""),
                        17,
                        Map.of(),
                        "flat.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = ProtectionProfile.EXTREME;
        config.seed = 812L;
        var first = support.protect(input, config);
        var second = support.protect(input, config);
        assertEquals(support.execute(input), support.execute(first.output()));
        assertArrayEquals(Files.readAllBytes(first.output()), Files.readAllBytes(second.output()));
        assertTrue(first.statistics().transformations.getOrDefault("flatten", 0L) > 0);
    }

    @Test
    void signedJarCleanupAndMultiReleaseAbiArePreserved() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path original =
                support.compile(
                        Map.of(
                                "mr.Main",
                                "package mr;public class Main {public static void main(String[]"
                                        + " a){System.out.println(\"multi release\");}}"),
                        8,
                        Map.of(),
                        "mr.Main");
        Map<String, byte[]> entries = new HashMap<>();
        byte[] variant;
        try (ZipFile zip = new ZipFile(original.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                entries.put(entry.getName(), zip.getInputStream(entry).readAllBytes());
            }
            variant = entries.get("mr/Main.class");
        }
        entries.put("META-INF/versions/17/mr/Main.class", variant);
        entries.put("META-INF/SIGNATURE.SF", "stale".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/SIGNATURE.RSA", new byte[] {1, 2, 3});
        entries.put(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\r\nMain-Class: mr.Main\r\nMulti-Release: true\r\n\r\nName: mr/Main.class\r\nSHA-256-Digest: stale\r\n\r\n"
                        .getBytes(StandardCharsets.UTF_8));
        Path input = directory.resolve("multi.jar");
        new JarWriter(new AtomicFileWriter()).write(input, entries);
        var result = support.protect(input, new ObfuscationConfig());
        assertEquals(support.execute(input), support.execute(result.output()));
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            assertArrayEquals(
                    variant,
                    zip.getInputStream(zip.getEntry("META-INF/versions/17/mr/Main.class"))
                            .readAllBytes());
            assertNotNull(zip.getEntry("mr/Main.class"));
            assertNull(zip.getEntry("META-INF/SIGNATURE.SF"));
            assertNull(zip.getEntry("META-INF/SIGNATURE.RSA"));
            assertFalse(
                    new String(
                                    zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))
                                            .readAllBytes(),
                                    StandardCharsets.UTF_8)
                            .contains("Digest"));
        }
    }

    @Test
    void sidecarPublicationFailureLeavesExistingJarIntact() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of("publish.Main", "package publish;public class Main{}"),
                        17,
                        Map.of(),
                        null);
        Path output = directory.resolve("output.jar");
        Files.write(output, new byte[] {4, 5, 6});
        Files.createDirectory(Path.of(output + ".report.json"));
        ApplicationFactory factory = new ApplicationFactory();
        assertThrows(
                java.io.IOException.class,
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
                                        new CancellationToken()));
        assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(output));
        assertFalse(Files.exists(Path.of(output + ".mapping.json")));
    }

    @Test
    void methodExclusionsProtectOriginalNamesAndCode() throws Exception {
        TestSupport support = new TestSupport(directory);
        Path input =
                support.compile(
                        Map.of(
                                "exc.Main",
                                "package exc;public class Main{private static String"
                                    + " unchanged(){return \"retain-method\";}public static void"
                                    + " main(String[] a){System.out.println(unchanged());}}"),
                        17,
                        Map.of(),
                        "exc.Main");
        ObfuscationConfig config = new ObfuscationConfig();
        config.profile = ProtectionProfile.EXTREME;
        config.exclude = List.of("exc/Main#unchanged**");
        var result = support.protect(input, config);
        assertEquals(support.execute(input), support.execute(result.output()));
        assertFalse(Files.readString(result.mapping()).contains("#unchanged"));
    }

    @Test
    void retraceRecoversOwnerAndMemberNames() throws Exception {
        MappingModel model = new MappingModel();
        model.classes().put("original/Owner", "r/_a");
        model.methods().put(new MemberKey("original/Owner", "calculate", "()V"), "_b");
        Path path = directory.resolve("mapping.json");
        new MappingWriter(new AtomicFileWriter()).write(path, model, 1L);
        assertEquals(
                "\tat plugin.jar//original.Owner.calculate(Unknown Source)\n",
                new RetraceService().retrace(path, "\tat plugin.jar//r._a._b(Unknown Source)\n"));
    }

    @Test
    void configurationRejectsMisspelledSettings() throws Exception {
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{\"transformers\":{\"strings\":{\"densitty\":50}}}");
        assertThrows(IllegalArgumentException.class, () -> new ConfigParser().read(path));
    }
}
