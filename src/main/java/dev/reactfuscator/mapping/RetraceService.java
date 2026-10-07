package dev.reactfuscator.mapping;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class RetraceService {
    public String retrace(Path mappingPath,String trace)throws IOException{
        JsonObject mapping=JsonParser.parseString(Files.readString(mappingPath)).getAsJsonObject();
        if(!mapping.has("format") || !mapping.get("format").getAsString().equals("react-fuscator-mapping-v1"))throw new IllegalArgumentException("Unsupported mapping format");
        Map<String,String> classes=new HashMap<>(),methods=new HashMap<>();
        mapping.getAsJsonObject("classes").entrySet().forEach(e->classes.put(e.getValue().getAsString().replace('/','.'),e.getKey().replace('/','.')));
        mapping.getAsJsonObject("methods").entrySet().forEach(e->{String key=e.getKey();int hash=key.indexOf('#'),space=key.indexOf(' ',hash);String originalOwner=key.substring(0,hash),oldName=key.substring(hash+1,space);String targetOwner=mapping.getAsJsonObject("classes").get(originalOwner).getAsString().replace('/','.');methods.put(targetOwner+"#"+e.getValue().getAsString(),oldName);});
        Matcher frames=Pattern.compile("\\b([\\w.$]+)\\.([\\w$<>]+)\\(([^)]*)\\)").matcher(trace);
        return frames.replaceAll(match->{String owner=match.group(1),name=match.group(2);return Matcher.quoteReplacement(classes.getOrDefault(owner,owner)+"."+methods.getOrDefault(owner+"#"+name,name)+"("+match.group(3)+")");});
    }
}
