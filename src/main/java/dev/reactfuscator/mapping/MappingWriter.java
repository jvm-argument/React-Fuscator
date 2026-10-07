package dev.reactfuscator.mapping;

import com.google.gson.*;
import dev.reactfuscator.io.AtomicFileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class MappingWriter {
    private final AtomicFileWriter files;
    public MappingWriter(AtomicFileWriter files) { this.files=files; }
    public void write(Path path,MappingModel mapping,long seed) throws IOException {
        JsonObject json=new JsonObject(); json.addProperty("format","react-fuscator-mapping-v1"); json.addProperty("seed",seed);
        JsonObject classes=new JsonObject(), methods=new JsonObject(), fields=new JsonObject();
        mapping.classes().forEach(classes::addProperty); mapping.methods().forEach((key,value)->methods.addProperty(key.text(),value)); mapping.fields().forEach((key,value)->fields.addProperty(key.text(),value));
        json.add("classes",classes); json.add("methods",methods); json.add("fields",fields);
        files.write(path,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json).getBytes(StandardCharsets.UTF_8));
    }
}
