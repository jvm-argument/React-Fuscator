package dev.reactfuscator.platform.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.platform.PlatformHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FabricMetadataHandler implements PlatformHandler {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String id() {
        return "fabric";
    }

    public boolean matches(ArchiveModel archive) {
        return archive.resources().containsKey("fabric.mod.json");
    }

    private JsonObject read(ArchiveModel archive, String path) throws IOException {
        byte[] bytes = archive.resources().get(path);
        if (bytes == null) {
            throw new IOException("Missing Fabric resource: " + path);
        }
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Invalid Fabric JSON: " + path, e);
        }
    }

    private List<String> mixinConfigs(JsonObject mod) {
        List<String> paths = new ArrayList<>();
        if (mod.has("mixins")) {
            for (JsonElement item : mod.getAsJsonArray("mixins")) {
                paths.add(
                        item.isJsonObject()
                                ? item.getAsJsonObject().get("config").getAsString()
                                : item.getAsString());
            }
        }
        return paths;
    }

    public void analyze(ArchiveModel archive, KeepPolicy keeps) throws IOException {
        JsonObject mod = read(archive, "fabric.mod.json");
        if (mod.has("entrypoints")) {
            for (var entry : mod.getAsJsonObject("entrypoints").entrySet()) {
                for (JsonElement value : entry.getValue().getAsJsonArray()) {
                    String name =
                            value.isJsonObject()
                                    ? value.getAsJsonObject().get("value").getAsString()
                                    : value.getAsString();
                    String owner = name.split("::", 2)[0].replace('.', '/');
                    if (!archive.classes().containsKey(owner)) {
                        throw new IOException("Fabric entrypoint missing: " + name);
                    }
                    if (name.contains("::")) {
                        String memberName = name.split("::", 2)[1];
                        for (var method : archive.classes().get(owner).node().methods) {
                            if (method.name.equals(memberName)) {
                                keeps.keepMember(
                                        owner, method.name, method.desc, "Fabric named entrypoint");
                            }
                        }
                        for (var field : archive.classes().get(owner).node().fields) {
                            if (field.name.equals(memberName)) {
                                keeps.keepMember(
                                        owner, field.name, field.desc, "Fabric named entrypoint");
                            }
                        }
                    }
                }
            }
        }
        for (String path : mixinConfigs(mod)) {
            JsonObject mixin = read(archive, path);
            String pkg = mixin.has("package") ? mixin.get("package").getAsString() + "." : "";
            if (!pkg.isEmpty()) {
                keeps.mixinPackage(pkg.substring(0, pkg.length() - 1));
                for (String owner : archive.classes().keySet()) {
                    if (owner.startsWith(pkg.replace('.', '/'))) {
                        keeps.preserveCode(owner);
                        keeps.keepMembersOf(owner);
                        if (!keeps.renameMixins()) {
                            keeps.keepClass(
                                    owner, "Mixin package, nested classes and loader restrictions");
                        }
                    }
                }
            }
            for (String key : List.of("mixins", "client", "server")) {
                if (mixin.has(key)) {
                    for (JsonElement name : mixin.getAsJsonArray(key)) {
                        String owner = (pkg + name.getAsString()).replace('.', '/');
                        if (!archive.classes().containsKey(owner)) {
                            throw new IOException("Mixin class missing: " + owner);
                        }
                        keeps.preserveCode(owner);
                        keeps.keepMembersOf(owner);
                        if (!keeps.renameMixins()) {
                            keeps.keepClass(owner, "Mixin selectors and member contracts");
                        }
                    }
                }
            }
            if (mixin.has("refmap")) {
                JsonObject refmap = read(archive, mixin.get("refmap").getAsString());
                pinReferences(refmap, archive, keeps);
            }
        }
        if (mod.has("accessWidener")) {
            String path = mod.get("accessWidener").getAsString();
            byte[] bytes = archive.resources().get(path);
            if (bytes == null) {
                throw new IOException("Access widener missing: " + path);
            }
            String first =
                    new String(bytes, StandardCharsets.UTF_8)
                            .lines()
                            .filter(l -> !l.isBlank() && !l.startsWith("#"))
                            .findFirst()
                            .orElse("");
            if (!first.startsWith("accessWidener ") && !first.startsWith("accessWidener\t")) {
                throw new IOException("Unsupported access widener header: " + first);
            }
        }
    }

    private void pinReferences(JsonElement element, ArchiveModel archive, KeepPolicy keeps) {
        if (element.isJsonObject()) {
            element.getAsJsonObject()
                    .entrySet()
                    .forEach(
                            e -> {
                                pinText(e.getKey(), archive, keeps);
                                pinReferences(e.getValue(), archive, keeps);
                            });
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> pinReferences(e, archive, keeps));
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            pinText(element.getAsString(), archive, keeps);
        }
    }

    private void pinText(String text, ArchiveModel archive, KeepPolicy keeps) {
        for (String owner : archive.classes().keySet()) {
            if ((text.contains(owner) || text.contains(owner.replace('/', '.')))
                    && keeps.mixinPackages().stream().noneMatch(p -> owner.startsWith(p + "/"))) {
                keeps.keepClass(owner, "Mixin refmap target");
                keeps.keepMembersOf(owner);
            }
        }
    }

    public void remap(ArchiveModel archive, MappingModel mapping) throws IOException {
        JsonObject mod = read(archive, "fabric.mod.json");
        if (mod.has("entrypoints")) {
            for (var entry : mod.getAsJsonObject("entrypoints").entrySet()) {
                JsonArray values = entry.getValue().getAsJsonArray();
                for (int i = 0; i < values.size(); i++) {
                    JsonElement item = values.get(i);
                    if (item.isJsonObject()) {
                        JsonObject value = item.getAsJsonObject();
                        value.addProperty(
                                "value", mapEntrypoint(value.get("value").getAsString(), mapping));
                    } else {
                        values.set(
                                i, new JsonPrimitive(mapEntrypoint(item.getAsString(), mapping)));
                    }
                }
            }
        }
        if (mod.has("languageAdapters")) {
            for (var entry : mod.getAsJsonObject("languageAdapters").entrySet()) {
                entry.setValue(
                        new JsonPrimitive(mapping.mapBinary(entry.getValue().getAsString())));
            }
        }
        if (mod.has("mixins")) {
            JsonArray configs = mod.getAsJsonArray("mixins");
            for (int i = 0; i < configs.size(); i++) {
                JsonElement item = configs.get(i);
                String path =
                        item.isJsonObject()
                                ? item.getAsJsonObject().get("config").getAsString()
                                : item.getAsString();
                JsonObject config = read(archive, path);
                String oldPackage =
                        config.has("package") ? config.get("package").getAsString() : "";
                String mappedPackage =
                        mapping.mixinPackages()
                                .getOrDefault(
                                        oldPackage.replace('.', '/'), oldPackage.replace('.', '/'))
                                .replace('/', '.');
                for (String key : List.of("mixins", "client", "server")) {
                    if (config.has(key)) {
                        JsonArray list = config.getAsJsonArray(key);
                        for (int j = 0; j < list.size(); j++) {
                            String old =
                                    (oldPackage.isEmpty() ? "" : oldPackage + ".")
                                            + list.get(j).getAsString();
                            String target = mapping.mapBinary(old);
                            String prefix = mappedPackage.isEmpty() ? "" : mappedPackage + ".";
                            if (!target.startsWith(prefix)) {
                                throw new IOException(
                                        "Mixin moved outside configured package: " + target);
                            }
                            list.set(j, new JsonPrimitive(target.substring(prefix.length())));
                        }
                    }
                }
                if (config.has("package")) {
                    config.addProperty("package", mappedPackage);
                }
                if (config.has("plugin")) {
                    config.addProperty(
                            "plugin", mapping.mapBinary(config.get("plugin").getAsString()));
                }
                if (config.has("refmap")) {
                    String refmapPath = config.get("refmap").getAsString();
                    JsonElement refmap = remapRefmap(read(archive, refmapPath), mapping);
                    archive.resources()
                            .put(refmapPath, gson.toJson(refmap).getBytes(StandardCharsets.UTF_8));
                    config.addProperty("refmap", mapping.mapResourcePath(refmapPath));
                }
                archive.resources().put(path, gson.toJson(config).getBytes(StandardCharsets.UTF_8));
                if (item.isJsonObject()) {
                    item.getAsJsonObject().addProperty("config", mapping.mapResourcePath(path));
                } else {
                    configs.set(i, new JsonPrimitive(mapping.mapResourcePath(path)));
                }
            }
        }
        if (mod.has("accessWidener")) {
            String path = mod.get("accessWidener").getAsString();
            AccessWidenerHandler handler = new AccessWidenerHandler();
            archive.resources().put(path, handler.remap(archive.resources().get(path), mapping));
            mod.addProperty("accessWidener", mapping.mapResourcePath(path));
        }
        if (mod.has("icon") && mod.get("icon").isJsonPrimitive()) {
            mod.addProperty("icon", mapping.mapResourcePath(mod.get("icon").getAsString()));
        }
        archive.resources()
                .put("fabric.mod.json", gson.toJson(mod).getBytes(StandardCharsets.UTF_8));
    }

    private String mapEntrypoint(String name, MappingModel mapping) {
        String[] parts = name.split("::", 2);
        return mapping.mapBinary(parts[0]) + (parts.length > 1 ? "::" + parts[1] : "");
    }

    private JsonElement remapRefmap(JsonElement value, MappingModel mapping) {
        if (value.isJsonObject()) {
            JsonObject out = new JsonObject();
            value.getAsJsonObject()
                    .entrySet()
                    .forEach(
                            e ->
                                    out.add(
                                            remapText(e.getKey(), mapping),
                                            remapRefmap(e.getValue(), mapping)));
            return out;
        }
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            value.getAsJsonArray().forEach(e -> out.add(remapRefmap(e, mapping)));
            return out;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(remapText(value.getAsString(), mapping));
        }
        return value.deepCopy();
    }

    private String remapText(String text, MappingModel mapping) {
        if (mapping.classes().containsKey(text)) {
            return mapping.mapClass(text);
        }
        if (mapping.classes().containsKey(text.replace('.', '/'))) {
            return mapping.mapBinary(text);
        }
        var matcher = java.util.regex.Pattern.compile("L([^;]+);").matcher(text);
        return matcher.replaceAll(
                m ->
                        java.util.regex.Matcher.quoteReplacement(
                                "L" + mapping.mapClass(m.group(1)) + ";"));
    }
}
