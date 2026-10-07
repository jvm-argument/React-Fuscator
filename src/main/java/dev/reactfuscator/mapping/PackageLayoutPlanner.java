package dev.reactfuscator.mapping;

import dev.reactfuscator.analysis.*;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.model.*;
import dev.reactfuscator.util.NameFactory;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Scatters independent classes; package access and nestmates remain in one JVM package. */
public final class PackageLayoutPlanner {
    public Map<String,String> plan(ArchiveModel archive,ObfuscationConfig config,KeepPolicy keeps,HierarchyService hierarchy,MappingModel mapping,NameFactory names) {
        Map<String,String> roots=new HashMap<>();archive.classes().keySet().forEach(n->roots.put(n,n));Set<String> anchored=new HashSet<>();
        for(ClassModel model:archive.classes().values()) {
            ClassNode c=model.node();String source=c.name;
            if(c.nestHostClass!=null)join(roots,source,c.nestHostClass);if(c.nestMembers!=null)c.nestMembers.forEach(n->join(roots,source,n));
            for(MethodNode method:c.methods)if((method.access&(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC))==0){
                Set<String> seen=new HashSet<>();for(String parent=c.superName;parent!=null && seen.add(parent);parent=hierarchy.resolve(parent).parent()){
                    Integer access=hierarchy.resolve(parent).methods().get(method.name+"\0"+method.desc);
                    if(access!=null && (access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC))==0 && pkg(source).equals(pkg(parent)))link(roots,anchored,source,parent);
                }
            }
            Remapper references=new Remapper(Opcodes.ASM9){@Override public String map(String target){
                if(!target.equals(source) && !target.startsWith("[") && pkg(source).equals(pkg(target))){ClassInfo info=hierarchy.resolve(target);if((info.access()&Opcodes.ACC_PUBLIC)==0)link(roots,anchored,source,target);}return target;
            }};
            c.accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){
                @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf){member(source,owner,name,desc,true,roots,anchored,hierarchy);}
                @Override public void visitFieldInsn(int opcode,String owner,String name,String desc){member(source,owner,name,desc,false,roots,anchored,hierarchy);}
                @Override public void visitLdcInsn(Object value){handleReferences(source,value,roots,anchored,hierarchy);}
                @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... arguments){handleReferences(source,bootstrap,roots,anchored,hierarchy);for(Object argument:arguments)handleReferences(source,argument,roots,anchored,hierarchy);}
            };}},references));
        }
        Map<String,List<String>> packages=new TreeMap<>();archive.classes().keySet().forEach(n->packages.computeIfAbsent(pkg(n),x->new ArrayList<>()).add(n));
        for(var entry:packages.entrySet())if(!config.scatterPackages || archive.resources().keySet().stream().anyMatch(p->!entry.getKey().isEmpty() && p.startsWith(entry.getKey()+"/"))) {List<String> owners=entry.getValue();for(int i=1;i<owners.size();i++)join(roots,owners.get(0),owners.get(i));}
        Map<String,List<String>> groups=new TreeMap<>();archive.classes().keySet().forEach(n->groups.computeIfAbsent(root(roots,n),x->new ArrayList<>()).add(n));
        Map<String,String> mixinRoots=new TreeMap<>();for(String p:keeps.mixinPackages())if(config.renameMixins){boolean kept=archive.classes().keySet().stream().anyMatch(n->n.startsWith(p+"/") && keeps.keepClass(n));String target=kept?p:"q/"+names.next();mixinRoots.put(p,target);mapping.mixinPackages().put(p,target);mapping.packages().put(p,target);}
        Map<String,String> result=new LinkedHashMap<>();Map<String,String> bases=new HashMap<>();
        for(List<String> group:groups.values()) {
            String original=pkg(group.get(0));boolean pinned=group.stream().anyMatch(n->keeps.keepClass(n) || anchored.contains(n) || keeps.keepPackageName(pkg(n)));
            String target=original;
            if(config.renameClasses && config.renamePackages && !pinned) {
                String mixin=group.stream().map(n->mixinRoots.keySet().stream().filter(p->n.startsWith(p+"/")).max(Comparator.comparingInt(String::length)).orElse(null)).filter(Objects::nonNull).findFirst().orElse(null);
                String base=mixin!=null?mixinRoots.get(mixin):bases.computeIfAbsent(original,p->"r/"+names.next());
                target=config.scatterPackages?base+"/"+names.next():base;
            }
            for(String owner:group)result.put(owner,target);
        }
        for(var entry:packages.entrySet()){Set<String> targets=new HashSet<>();entry.getValue().forEach(n->targets.add(result.get(n)));if(targets.size()==1 && !targets.contains(entry.getKey()))mapping.packages().put(entry.getKey(),targets.iterator().next());}
        return result;
    }
    private void member(String source,String owner,String name,String desc,boolean method,Map<String,String> roots,Set<String> anchored,HierarchyService hierarchy){
        Set<String> seen=new HashSet<>();Deque<String> pending=new ArrayDeque<>();pending.add(owner);
        while(!pending.isEmpty()) {String type=pending.remove();if(!seen.add(type) || type.startsWith("["))continue;ClassInfo info=hierarchy.resolve(type);Integer access=(method?info.methods():info.fields()).get(name+"\0"+desc);
            if(access!=null){if((access&Opcodes.ACC_PUBLIC)==0 && pkg(source).equals(pkg(type)))link(roots,anchored,source,type);return;}if(info.parent()!=null)pending.add(info.parent());pending.addAll(info.interfaces());}
    }
    private void handleReferences(String source,Object value,Map<String,String> roots,Set<String> anchored,HierarchyService hierarchy){
        if(value instanceof Handle handle)member(source,handle.getOwner(),handle.getName(),handle.getDesc(),handle.getTag()>Opcodes.H_PUTSTATIC,roots,anchored,hierarchy);
        else if(value instanceof ConstantDynamic constant){handleReferences(source,constant.getBootstrapMethod(),roots,anchored,hierarchy);for(int i=0;i<constant.getBootstrapMethodArgumentCount();i++)handleReferences(source,constant.getBootstrapMethodArgument(i),roots,anchored,hierarchy);}
    }
    private void link(Map<String,String> roots,Set<String> anchored,String a,String b){if(roots.containsKey(b))join(roots,a,b);else anchored.add(a);}
    private void join(Map<String,String> roots,String a,String b){if(roots.containsKey(a) && roots.containsKey(b))roots.put(root(roots,a),root(roots,b));}
    private String root(Map<String,String> roots,String key){String parent=roots.get(key);if(parent.equals(key))return key;String result=root(roots,parent);roots.put(key,result);return result;}
    private String pkg(String name){int i=name.lastIndexOf('/');return i<0?"":name.substring(0,i);}
}
