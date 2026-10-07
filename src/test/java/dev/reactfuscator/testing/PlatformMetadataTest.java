package dev.reactfuscator.testing;

import static org.junit.jupiter.api.Assertions.*;

import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.config.*;
import dev.reactfuscator.mapping.*;
import dev.reactfuscator.model.*;
import dev.reactfuscator.platform.fabric.*;
import dev.reactfuscator.platform.paper.PaperMetadataHandler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;

public final class PlatformMetadataTest {
    private ClassModel model(String name) {
        ClassNode node = new ClassNode();
        node.name = name;
        return new ClassModel(name + ".class", new byte[0], node);
    }

    @Test
    void preservesPaperMainLoaderAndBootstrapperContracts() throws Exception {
        ArchiveModel archive = new ArchiveModel();
        archive.classes().put("p/Main", model("p/Main"));
        archive.resources()
                .put(
                        "paper-plugin.yml",
                        "name: Test\nversion: 1\nmain: p.Main\nloader: p.Loader\nbootstrapper: p.Bootstrap\n"
                                .getBytes(StandardCharsets.UTF_8));
        KeepPolicy keep = new KeepPolicy(new ObfuscationConfig());
        PaperMetadataHandler handler = new PaperMetadataHandler();
        handler.analyze(archive, keep);
        assertFalse(keep.keepClass("p/Main"));
        MappingModel map = new MappingModel();
        map.classes().put("p/Main", "r/A");
        map.classes().put("p/Loader", "r/B");
        map.classes().put("p/Bootstrap", "r/C");
        handler.remap(archive, map);
        String descriptor =
                new String(archive.resources().get("paper-plugin.yml"), StandardCharsets.UTF_8);
        assertTrue(descriptor.contains("main: r.A"));
        assertTrue(descriptor.contains("loader: r.B"));
        assertTrue(descriptor.contains("bootstrapper: r.C"));
    }

    @Test
    void fabricMixinsRefmapsAndEntryMethodsStayStable() throws Exception {
        ArchiveModel archive = new ArchiveModel();
        archive.classes().put("mod/Entry", model("mod/Entry"));
        archive.classes().put("mod/mixin/Hook", model("mod/mixin/Hook"));
        archive.classes().put("mod/Target", model("mod/Target"));
        archive.resources()
                .put(
                        "fabric.mod.json",
                        "{\"entrypoints\":{\"main\":[{\"adapter\":\"default\",\"value\":\"mod.Entry::init\"}]},\"mixins\":[{\"config\":\"mod.mixins.json\"}],\"accessWidener\":\"mod.accesswidener\"}"
                                .getBytes(StandardCharsets.UTF_8));
        archive.resources()
                .put(
                        "mod.mixins.json",
                        "{\"package\":\"mod.mixin\",\"mixins\":[\"Hook\"],\"refmap\":\"mod.refmap.json\"}"
                                .getBytes(StandardCharsets.UTF_8));
        archive.resources()
                .put(
                        "mod.refmap.json",
                        "{\"mappings\":{\"mod.mixin.Hook\":{\"x\":\"Lmod/Target;call()V\"}}}"
                                .getBytes(StandardCharsets.UTF_8));
        archive.resources()
                .put(
                        "mod.accesswidener",
                        "accessWidener v2 intermediary\naccessible class mod/Target\n"
                                .getBytes(StandardCharsets.UTF_8));
        KeepPolicy keep = new KeepPolicy(new ObfuscationConfig());
        FabricMetadataHandler handler = new FabricMetadataHandler();
        handler.analyze(archive, keep);
        assertFalse(keep.keepClass("mod/Entry"));
        assertFalse(keep.keepClass("mod/mixin/Hook"));
        assertFalse(keep.transformClass("mod/mixin/Hook"));
        assertTrue(keep.keepClass("mod/Target"));
        assertTrue(keep.keepMember("mod/Entry", "init", "()V"));
        MappingModel map = new MappingModel();
        map.classes().put("mod/Entry", "r/A");
        map.classes().put("mod/mixin/Hook", "q/m/s/H");
        map.mixinPackages().put("mod/mixin", "q/m");
        handler.remap(archive, map);
        assertTrue(
                new String(archive.resources().get("fabric.mod.json"), StandardCharsets.UTF_8)
                        .contains("r.A::init"));
        String mixin =
                new String(archive.resources().get("mod.mixins.json"), StandardCharsets.UTF_8);
        assertTrue(mixin.contains("q.m"));
        assertTrue(mixin.contains("s.H"));
        assertTrue(
                new String(archive.resources().get("mod.refmap.json"), StandardCharsets.UTF_8)
                        .contains("q.m.s.H"));
    }

    @Test
    void accessWidenerRemapsOnlyKnownSymbols() {
        MappingModel mapping = new MappingModel();
        mapping.classes().put("m/Local", "r/A");
        mapping.fields().put(new MemberKey("m/Local", "value", "I"), "b");
        mapping.methods().put(new MemberKey("m/Local", "call", "(Lm/Local;)V"), "c");
        String source =
                "accessWidener v2 intermediary\n"
                        + "# preserved\n"
                        + "accessible class m/Local\n"
                        + "mutable field m/Local value I # field\n"
                        + "accessible method m/Local call (Lm/Local;)V\n"
                        + "accessible class net/minecraft/class_310\n";
        String result =
                new String(
                        new AccessWidenerHandler()
                                .remap(source.getBytes(StandardCharsets.UTF_8), mapping),
                        StandardCharsets.UTF_8);
        assertTrue(result.contains("class\tr/A"));
        assertTrue(result.contains("field\tr/A\tb\tI # field"));
        assertTrue(result.contains("method\tr/A\tc\t(Lr/A;)V"));
        assertTrue(result.contains("class\tnet/minecraft/class_310"));
    }

    @Test
    void globRulesCoverRootResourcesAndNestedPackages() {
        RuleMatcher matcher = new RuleMatcher(java.util.List.of("**/*.json", "a/**"));
        assertTrue(matcher.matches("root.json"));
        assertTrue(matcher.matches("x/y.json"));
        assertTrue(matcher.matches("a/b/C"));
        assertFalse(matcher.matches("x/y.txt"));
    }
}
