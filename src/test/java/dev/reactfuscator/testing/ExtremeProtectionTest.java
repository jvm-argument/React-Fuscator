package dev.reactfuscator.testing;

import dev.reactfuscator.analysis.leak.DebugAttributeInspector;
import dev.reactfuscator.config.*;
import dev.reactfuscator.runtime.StringCipherTemplate;
import dev.reactfuscator.service.StringCipherService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ExtremeProtectionTest {
    @TempDir Path directory;

    @Test void everyCipherPreservesAllUtf16ValuesAndInternIdentity() {
        StringCipherService cipher=new StringCipherService();char[] values=new char[65536];
        for(int i=0;i<values.length;i++)values[i]=(char)i;
        String plain=new String(values);
        for(int key:new int[]{0,1,-1,Integer.MIN_VALUE,0x735ac190})for(int variant=0;variant<4;variant++) {
            String encoded=cipher.encrypt(plain,key,variant);
            String decoded=switch(variant){case 0->StringCipherTemplate.decode(encoded,key);case 1->StringCipherTemplate.reverseXor(encoded,key);case 2->StringCipherTemplate.rotateSubtract(encoded,key);default->StringCipherTemplate.chained(encoded,key);};
            assertEquals(plain,decoded);assertSame(plain.intern(),decoded);
        }
    }

    @Test void defaultExtremeRemovesDebugFromInterfacesAndEncryptsConcatRecipes()throws Exception {
        assertEquals(ProtectionProfile.EXTREME,new ObfuscationConfig().profile);
        TestSupport support=new TestSupport(directory);
        Path input=support.compile(Map.of("audit.Api","package audit;public interface Api{String message(String argument);}","audit.Main","""
            package audit;public class Main implements Api{
            public String message(String argument){return "sensitive-password="+argument+":end";}
            public static void main(String[] args){System.out.println(new Main().message("Привет"));}}
            """),17,Map.of(),"audit.Main");
        var result=support.protect(input,new ObfuscationConfig());assertEquals(support.execute(input),support.execute(result.output()));
        assertTrue(result.statistics().transformations.getOrDefault("concatstrings",0L)>0);
        assertTrue(result.statistics().protection.removedDebugAttributes>0);
        try(ZipFile archive=new ZipFile(result.output().toFile())) {
            for(ZipEntry entry:Collections.list(archive.entries()))if(entry.getName().endsWith(".class")) {
                byte[] bytes=archive.getInputStream(entry).readAllBytes();ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                assertTrue(new DebugAttributeInspector().count(node).isEmpty(),entry.getName());
                assertFalse(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("sensitive-password="));
            }
        }
        assertEquals(0,result.statistics().protection.findingsByCategory.getOrDefault("DEBUG",0L));
    }

    @Test void typedDispatchersPreserveWideValuesReferenceIdentityAndVoidEffects()throws Exception {
        TestSupport support=new TestSupport(directory);Path input=support.compile(Map.of("typed.Main","""
            package typed;public class Main{static long state;static final Object shared=new Object();
            static long a(long x,double y){return x+(long)y;}static long b(long x,double y){return x-(long)y;}
            static Object c(Object x){return x;}static Object d(Object x){return shared;}
            static void e(long x){state+=x;}static void f(long x){state-=x;}
            public static void main(String[] args){e(19);f(7);long first=a(1234567890123L,5.75),second=b(888888888888L,3.5);if(c(shared)!=d(shared))throw new AssertionError();System.out.println(first+":"+second+":"+state);}}
            """),17,Map.of(),"typed.Main");var result=support.protect(input,new ObfuscationConfig());
        assertEquals(support.execute(input),support.execute(result.output()));assertTrue(result.statistics().protection.generatedDispatchers>0);
    }

    @Test void leakScannerRemapsTextMetadataAndRetainsRequiredRecordBootstrapNames()throws Exception {
        TestSupport support=new TestSupport(directory);Path input=support.compile(Map.of("scan.Point","package scan;public record Point(int config,int settings){}","scan.Main","package scan;public class Main{public static void main(String[] args){System.out.println(new Point(3,7));}}"),17,Map.of("custom.txt","scan.Main\nscan.Point".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"scan.Main");var result=support.protect(input,new ObfuscationConfig());
        // Renamed record identity changes its generated toString prefix; component names are ABI.
        assertTrue(support.execute(result.output()).contains("config=3, settings=7"));
        assertEquals(0,result.statistics().protection.metadataLeaksRemaining);assertTrue(result.statistics().protection.metadataLeaksResolved>0);
        try(ZipFile archive=new ZipFile(result.output().toFile())){String text=new String(archive.getInputStream(archive.getEntry("custom.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertFalse(text.contains("scan.Main"));assertFalse(text.contains("scan.Point"));}
        assertTrue(result.statistics().protection.leaks.stream().anyMatch(f->f.symbol.equals("config;settings") && f.status.equals("RETAINED_CONTRACT")));
    }
}
