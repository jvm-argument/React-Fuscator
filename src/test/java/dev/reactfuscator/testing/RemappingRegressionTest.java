package dev.reactfuscator.testing;

import com.google.gson.*;
import dev.reactfuscator.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

public final class RemappingRegressionTest {
    @TempDir Path directory;
    @Test void scatteringPreservesPackageAccessThroughLambdaMethodHandles()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("handles.Target","package handles;public class Target{static int value(){return 47;}}","handles.Main","package handles;public class Main{public static void main(String[] a){java.util.function.IntSupplier supplier=Target::value;System.out.println(supplier.getAsInt());}}"),8,Map.of(),"handles.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));
    }
    @Test void renamesPublicFieldsAndOwnedDispatchButPreservesExternalCallbacks()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("owned.Api","package owned;public interface Api{int value();}","owned.Base","package owned;public class Base implements Api{public int storage=7;public int value(){return storage;}public String toString(){return \"external\";}}","owned.Child","package owned;public class Child extends Base{public int value(){return super.value()+3;}}","owned.Main","package owned;public class Main{public static void main(String[] a){Api x=new Child();System.out.println(x.value()+\":\"+x.toString());}}"),17,Map.of(),"owned.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String mapping=Files.readString(result.mapping());assertTrue(mapping.contains("#storage"));assertTrue(mapping.contains("#value"));assertFalse(mapping.contains("#toString"));
    }
    @Test void dynamicExternalReflectionDoesNotPinInputMemberNames()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("reflection.Main","""
            package reflection;public class Main{public int initializer=12;public int initStorage(){return initializer+2;}
            public static void main(String[] a)throws Exception{String name=System.getProperty("field","MAX_VALUE");Class<?> type=Integer.class;System.out.println(type.getField(name).get(null)+":"+new Main().initStorage());}}
            """),17,Map.of(),"reflection.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String mapping=Files.readString(result.mapping());assertTrue(mapping.contains("#initializer"));assertTrue(mapping.contains("#initStorage"));
    }
    @Test void optionalExternalReflectionDoesNotResolveAbsentDependencyOrFreezeNames()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("optional.Main","""
            package optional;public class Main{public int settings=12;public int getModule(){return settings;}
            public static void main(String[] args)throws Exception{try{Class.forName("absent.optional.Integration").getMethod("getModule").invoke(null);}catch(ClassNotFoundException expected){}System.out.println(new Main().getModule());}}
            """),17,Map.of(),"optional.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String mapping=Files.readString(result.mapping());assertTrue(mapping.contains("optional/Main#getModule"));assertTrue(mapping.contains("optional/Main#settings"));assertTrue(result.statistics().warnings.stream().noneMatch(w->w.contains("INCOMPLETE") || w.contains("Dynamic member")));
    }
    @Test void namedInheritedReflectionKeepsOnlyItsOwnerFamily()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("scoped.Base","package scoped;public class Base{public int getModule(){return 17;}}","scoped.Child","package scoped;public class Child extends Base{}","scoped.Other","package scoped;public class Other{public int getModule(){return 23;}}","scoped.Main","package scoped;public class Main{public static void main(String[] a)throws Exception{System.out.println(Child.class.getMethod(\"getModule\").invoke(new Child())+\":\"+new Other().getModule());}}"),17,Map.of(),"scoped.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String mapping=Files.readString(result.mapping());assertFalse(mapping.contains("scoped/Base#getModule"));assertTrue(mapping.contains("scoped/Other#getModule"));
    }
    @Test void dynamicInternalReflectionRetainsTheAffectedOwnerOnly()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("scope.Target","package scope;public class Target{public int value=17;}","scope.Other","package scope;public class Other{public int storage=21;}","scope.Main","package scope;public class Main{public static void main(String[] a)throws Exception{String name=System.getProperty(\"field\",\"value\");System.out.println(Target.class.getField(name).get(new Target())+\":\"+new Other().storage);}}"),17,Map.of(),"scope.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String map=Files.readString(result.mapping());assertFalse(map.contains("scope/Target#value"));assertTrue(map.contains("scope/Other#storage"));
    }
    @Test void scatterPreservesPackageAccessAndNestsWithoutPinningIndependentClasses()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("layout.Kept","package layout;public class Kept{int value=17;static class Inner{static int get(Kept k){return k.value;}}public int get(){return Inner.get(this);}}","layout.Free","package layout;public class Free{public int get(){return 23;}}","layout.Main","package layout;public class Main{public static void main(String[] a){System.out.println(new Kept().get()+new Free().get());}}"),17,Map.of(),"layout.Main");ObfuscationConfig c=new ObfuscationConfig();c.keep=List.of("layout/Kept");var result=s.protect(input,c);assertEquals(s.execute(input),s.execute(result.output()));JsonObject classes=JsonParser.parseString(Files.readString(result.mapping())).getAsJsonObject().getAsJsonObject("classes");assertEquals("layout/Kept",classes.get("layout/Kept").getAsString());assertTrue(classes.get("layout/Kept$Inner").getAsString().startsWith("layout/"));assertFalse(classes.get("layout/Free").getAsString().startsWith("layout/"));
    }
    @Test void generatedCoverClassesHaveLiveIncomingEdgesAndReceiveEncryptionWithIncludes()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("cover.Main","package cover;public class Main{public static String calculate(int n){return \"cover-secret\"+Integer.toString(n+31);}public static void main(String[] a){System.out.println(calculate(9));}}"),8,Map.of(),"cover.Main");ObfuscationConfig c=new ObfuscationConfig();c.profile=ProtectionProfile.EXTREME;c.include=List.of("cover/**");var result=s.protect(input,c);assertEquals(s.execute(input),s.execute(result.output()));assertTrue(result.statistics().generatedClasses>0);Set<String> owners=new HashSet<>(),referenced=new HashSet<>();
        try(ZipFile z=new ZipFile(result.output().toFile())){for(ZipEntry e:Collections.list(z.entries()))if(e.getName().endsWith(".class")){byte[] bytes=z.getInputStream(e).readAllBytes();assertFalse(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("cover-secret"));ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);assertEquals(0,n.access&Opcodes.ACC_SYNTHETIC);owners.add(n.name);for(MethodNode m:n.methods)for(AbstractInsnNode instruction:m.instructions){if(instruction instanceof MethodInsnNode call && !call.owner.equals(n.name))referenced.add(call.owner);if(instruction instanceof FieldInsnNode field && !field.owner.equals(n.name))referenced.add(field.owner);}}}
        JsonObject mapped=JsonParser.parseString(Files.readString(result.mapping())).getAsJsonObject().getAsJsonObject("classes");Set<String> originals=new HashSet<>();mapped.entrySet().forEach(e->originals.add(e.getValue().getAsString()));owners.removeAll(originals);assertFalse(owners.isEmpty());assertTrue(referenced.containsAll(owners));
    }
    @Test void encryptionBootstrapRemapsAndPreservesInternIdentity()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("bootstrap.Main","package bootstrap;public class Main{private static String f(){return \"unicode Привет 🚀\\u0000\";}public static void main(String[] a){String x=f(),y=f();if(x!=y)throw new AssertionError();System.out.println(x);}}"),8,Map.of(),"bootstrap.Main");ObfuscationConfig c=new ObfuscationConfig();c.profile=ProtectionProfile.EXTREME;var result=s.protect(input,c);assertEquals(s.execute(input),s.execute(result.output()));assertTrue(result.statistics().transformations.getOrDefault("indystrings",0L)>0);
    }
    @Test void packagePrivateOverridesKeepTheirOriginalDispatch()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("dispatch.Base","package dispatch;public class Base{int value(){return 1;}public int call(){return value();}}","dispatch.Child","package dispatch;public class Child extends Base{public int value(){return 7;}}","dispatch.Main","package dispatch;public class Main{public static void main(String[] a){System.out.println(new Child().call());}}"),8,Map.of(),"dispatch.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));
    }
    @Test void ordinaryStringLiteralsDoNotPinUnrelatedMemberNames()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("tokens.Main","package tokens;public class Main{public int storage=3;public int unhook(){return storage;}public static void main(String[] a){System.out.println(\"unhook\"+\"storage\"+new Main().unhook());}}"),17,Map.of(),"tokens.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String map=Files.readString(result.mapping());assertTrue(map.contains("#unhook"));assertTrue(map.contains("#storage"));
    }
    @Test void inheritedSuperclassImplementationSatisfiesRenamedInterface()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("inherit.Api","package inherit;public interface Api{int value();}","inherit.Parent","package inherit;public class Parent{public int value(){return 41;}}","inherit.Child","package inherit;public class Child extends Parent implements Api{}","inherit.Main","package inherit;public class Main{public static void main(String[] a){Api x=new Child();System.out.println(x.value()+new Child().value());}}"),8,Map.of(),"inherit.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));
    }
    @Test void nativeLayoutAndFunctionNamesAreKeptWithoutPinningOrdinaryFields()throws Exception{
        TestSupport s=new TestSupport(directory);Path input=s.compile(Map.of("com.sun.jna.Structure","package com.sun.jna;public class Structure{}","com.sun.jna.Library","package com.sun.jna;public interface Library{}","nativeabi.Message","package nativeabi;public class Message extends com.sun.jna.Structure{public int nativeField=8;}","nativeabi.NativeApi","package nativeabi;public interface NativeApi extends com.sun.jna.Library{int nativeFunction();}","nativeabi.Main","package nativeabi;public class Main{public int storage=17;public static void main(String[] a){System.out.println(new Message().nativeField+new Main().storage);}}"),8,Map.of(),"nativeabi.Main");var result=s.protect(input,new ObfuscationConfig());assertEquals(s.execute(input),s.execute(result.output()));String map=Files.readString(result.mapping());assertFalse(map.contains("#nativeField"));assertFalse(map.contains("#nativeFunction"));assertTrue(map.contains("#storage"));
    }
}
