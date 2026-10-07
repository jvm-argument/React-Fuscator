package dev.reactfuscator.analysis;

import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ClassInfo(String name, String parent, List<String> interfaces, int access, Map<String,Integer> methods, Map<String,Integer> fields) {
    public ClassInfo(String name,String parent,List<String> interfaces,int access) {this(name,parent,interfaces,access,Map.of(),Map.of());}
    public ClassInfo(ClassNode node) { this(node.name, node.superName, List.copyOf(node.interfaces), node.access,node.methods.stream().collect(Collectors.toMap(m->m.name+"\0"+m.desc,m->m.access)),node.fields.stream().collect(Collectors.toMap(f->f.name+"\0"+f.desc,f->f.access))); }
}
