package dev.reactfuscator.service;

import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** One immutable, stackless exception instance per run; actual exception edges avoid repeated allocations. */
public final class ExceptionSupportFactory {
    public record Support(String owner,String field){}
    public Support create(ObfuscationContext context,ClassModel source){
        String owner="r/"+context.names().next()+"/"+context.names().next(),field=context.names().next();ClassNode type=new ClassNode(Opcodes.ASM9);type.version=source.node().version;type.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_SUPER;type.name=owner;type.superName="java/lang/RuntimeException";
        type.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL,field,"L"+owner+";",null,null));
        MethodNode constructor=new MethodNode(Opcodes.ACC_PRIVATE,"<init>","()V",null,null);constructor.maxLocals=1;constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));constructor.instructions.add(new InsnNode(Opcodes.ACONST_NULL));constructor.instructions.add(new InsnNode(Opcodes.ACONST_NULL));constructor.instructions.add(new InsnNode(Opcodes.ICONST_0));constructor.instructions.add(new InsnNode(Opcodes.ICONST_0));constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,type.superName,"<init>","(Ljava/lang/String;Ljava/lang/Throwable;ZZ)V",false));constructor.instructions.add(new InsnNode(Opcodes.RETURN));type.methods.add(constructor);context.generated(constructor);
        MethodNode initializer=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);initializer.instructions.add(new TypeInsnNode(Opcodes.NEW,owner));initializer.instructions.add(new InsnNode(Opcodes.DUP));initializer.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,owner,"<init>","()V",false));initializer.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,owner,field,"L"+owner+";"));initializer.instructions.add(new InsnNode(Opcodes.RETURN));type.methods.add(initializer);context.generated(initializer);
        context.archive().classes().put(owner,new ClassModel(owner+".class",new byte[0],type));context.keeps().generatedClass(owner,source.originalName());context.hierarchy().update(type);context.statistics().generatedClasses++;return new Support(owner,field);
    }
}
