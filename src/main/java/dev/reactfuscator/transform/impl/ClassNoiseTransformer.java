package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.transform.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Referenced cover classes contain real extracted implementations or live arithmetic adapters. */
public final class ClassNoiseTransformer implements Transformer {
    public TransformerDescriptor descriptor(){return new TransformerDescriptor("classnoise","Interwoven cover classes","Normal classes with live call edges and extracted pure implementations; processed by the remaining pipeline.",5,ProtectionProfile.EXTREME,true,true);}
    public void transform(ObfuscationContext context,ClassModel model) {
        if(!context.choose("classnoise"))return;
        MethodNode source=model.node().methods.stream().filter(m->context.eligible(model,m,"classnoise") && !m.name.startsWith("<") && !m.name.equals("main") && (m.access&Opcodes.ACC_STATIC)!=0 && pure(m)).findFirst().orElse(null);
        MethodNode caller=source;AbstractInsnNode literal=null;
        if(source==null)for(MethodNode m:model.node().methods)if(context.eligible(model,m,"classnoise") && !m.name.startsWith("<")){for(AbstractInsnNode n:m.instructions)if(integer(n)){caller=m;literal=n;break;}if(literal!=null)break;}
        if(caller==null)return;
        String name="r/"+context.names().next()+"/"+context.names().next();while(context.archive().classes().containsKey(name))name="r/"+context.names().next()+"/"+context.names().next();
        ClassNode cover=new ClassNode(Opcodes.ASM9);cover.version=model.node().version;cover.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER;cover.name=name;cover.superName="java/lang/Object";
        MethodNode constructor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));constructor.instructions.add(new InsnNode(Opcodes.RETURN));constructor.maxLocals=1;cover.methods.add(constructor);
        String implementation=context.names().next();
        if(source!=null){
            MethodNode moved=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,implementation,source.desc,source.signature,source.exceptions.toArray(String[]::new));moved.instructions=source.instructions;moved.tryCatchBlocks=source.tryCatchBlocks;moved.localVariables=source.localVariables;moved.maxLocals=source.maxLocals;moved.maxStack=source.maxStack;moved.visibleLocalVariableAnnotations=source.visibleLocalVariableAnnotations;moved.invisibleLocalVariableAnnotations=source.invisibleLocalVariableAnnotations;cover.methods.add(moved);
            source.instructions=new InsnList();source.tryCatchBlocks=new ArrayList<>();source.localVariables=null;source.visibleLocalVariableAnnotations=null;source.invisibleLocalVariableAnnotations=null;int slot=0;
            for(Type arg:Type.getArgumentTypes(source.desc)){source.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD),slot));slot+=arg.getSize();}
            source.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,name,implementation,source.desc,false));source.instructions.add(new InsnNode(Type.getReturnType(source.desc).getOpcode(Opcodes.IRETURN)));
        }else{
            String key=context.names().next();cover.fields.add(new FieldNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,key,"I",null,null));
            MethodNode init=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);init.instructions.add(context.bytecode().integer(context.random().nextInt()));init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,name,key,"I"));init.instructions.add(new InsnNode(Opcodes.RETURN));cover.methods.add(init);
            MethodNode adapter=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,implementation,"(I)I",null,null);adapter.maxLocals=1;adapter.instructions.add(new VarInsnNode(Opcodes.ILOAD,0));adapter.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,name,key,"I"));adapter.instructions.add(new InsnNode(Opcodes.IXOR));adapter.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,name,key,"I"));adapter.instructions.add(new InsnNode(Opcodes.IXOR));adapter.instructions.add(new InsnNode(Opcodes.IRETURN));cover.methods.add(adapter);
            caller.instructions.insert(literal,new MethodInsnNode(Opcodes.INVOKESTATIC,name,implementation,"(I)I",false));
        }
        for(int i=0,variants=context.random().nextInt(1,context.rounds("classnoise")+3);i<variants;i++){String field=context.names().next();cover.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,field,"J",null,null));MethodNode accessor=new MethodNode(Opcodes.ACC_PUBLIC,context.names().next(),"(J)J",null,null);accessor.maxLocals=3;accessor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));accessor.instructions.add(new VarInsnNode(Opcodes.LLOAD,1));accessor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,name,field,"J"));accessor.instructions.add(new VarInsnNode(Opcodes.LLOAD,1));accessor.instructions.add(new LdcInsnNode(context.random().nextLong()));accessor.instructions.add(new InsnNode(switch(context.random().nextInt(3)){case 0->Opcodes.LXOR;case 1->Opcodes.LADD;default->Opcodes.LSUB;}));accessor.instructions.add(new InsnNode(Opcodes.LRETURN));cover.methods.add(accessor);}
        context.archive().classes().put(name,new ClassModel(name+".class",new byte[0],cover));context.keeps().generatedClass(name,model.originalName());context.hierarchy().update(cover);context.statistics().generatedClasses++;context.changed("classnoise");
    }
    private boolean integer(AbstractInsnNode n){int op=n.getOpcode();return (op>=Opcodes.ICONST_M1 && op<=Opcodes.ICONST_5) || op==Opcodes.BIPUSH || op==Opcodes.SIPUSH || (n instanceof LdcInsnNode l && l.cst instanceof Integer);}
    private boolean pure(MethodNode method){
        for(Type t:Type.getArgumentTypes(method.desc))if(!safeType(t))return false;if(!safeType(Type.getReturnType(method.desc)))return false;
        for(TryCatchBlockNode t:method.tryCatchBlocks)if(t.type!=null && !t.type.startsWith("java/"))return false;
        for(AbstractInsnNode n:method.instructions){if(n instanceof InvokeDynamicInsnNode || n instanceof FieldInsnNode)return false;if(n instanceof MethodInsnNode m && !m.owner.startsWith("java/"))return false;if(n instanceof TypeInsnNode t && !t.desc.startsWith("java/"))return false;if(n instanceof LdcInsnNode l && l.cst instanceof Type t && !safeType(t))return false;}
        return method.instructions.size()>2;
    }
    private boolean safeType(Type t){if(t.getSort()==Type.ARRAY)return safeType(t.getElementType());return t.getSort()!=Type.OBJECT || t.getInternalName().startsWith("java/");}
}

