package dev.reactfuscator.testing;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Verifies classes through the real Knot/Mixin class loader, without initializing every mod class. */
public final class FabricVerificationProbe implements ClientModInitializer {
    @Override public void onInitializeClient() {
        Thread probe=new Thread(()-> {
            Path game=FabricLoader.getInstance().getGameDir();Path report=game.resolve("react-fuscator-verification.txt");
            try {
                Thread.sleep(20000);List<String> results=new ArrayList<>();int verified=0,skipped=0;
                ClassLoader loader=Thread.currentThread().getContextClassLoader();
                try(ZipFile zip=new ZipFile(game.resolve("mods/xeron.jar").toFile())) {
                    Set<String> mixinPrefixes=new HashSet<>();
                    JsonObject mod=JsonParser.parseReader(new InputStreamReader(zip.getInputStream(zip.getEntry("fabric.mod.json")),StandardCharsets.UTF_8)).getAsJsonObject();
                    for(JsonElement item:mod.getAsJsonArray("mixins")){String path=item.isJsonObject()?item.getAsJsonObject().get("config").getAsString():item.getAsString();JsonObject config=JsonParser.parseReader(new InputStreamReader(zip.getInputStream(zip.getEntry(path)),StandardCharsets.UTF_8)).getAsJsonObject();if(config.has("package"))mixinPrefixes.add(config.get("package").getAsString().replace('.','/')+"/");}
                    for(ZipEntry entry:Collections.list(zip.entries())) if(entry.getName().endsWith(".class")) {
                        final boolean[] mixin={false};ClassReader reader=new ClassReader(zip.getInputStream(entry));
                        reader.accept(new ClassVisitor(Opcodes.ASM9) { @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) { if(descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;"))mixin[0]=true;return null; } },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                        if(mixin[0] || mixinPrefixes.stream().anyMatch(reader.getClassName()::startsWith)) {skipped++;continue;}
                        String name=reader.getClassName().replace('/','.');
                        try {Class<?> type=Class.forName(name,false,loader);type.getDeclaredMethods();type.getDeclaredFields();type.getDeclaredConstructors();verified++;}
                        catch(Throwable error) {results.add("FAIL "+name+": "+error);error.printStackTrace();}
                    }
                }
                results.add(0,"JVM verified "+verified+" classes; mixins skipped "+skipped+"; failures "+results.size());Files.write(report,results);
                results.forEach(System.out::println);
                if(results.size()>1)System.exit(2);
                else {
                    // Yarn 1.21.4: getInstance / scheduleStop. Stop on the client's normal lifecycle.
                    Class<?> clientType=Class.forName("net.minecraft.class_310",false,loader);
                    Object client=clientType.getMethod("method_1551").invoke(null);
                    clientType.getMethod("method_1592").invoke(client);
                }
            } catch(Throwable error) {try{Files.writeString(report,error.toString());}catch(Exception ignored){}error.printStackTrace();System.exit(3);}
        },"react-fuscator-verification");probe.setDaemon(true);probe.start();
    }
}
