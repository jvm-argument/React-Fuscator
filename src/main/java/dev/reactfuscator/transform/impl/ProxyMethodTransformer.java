package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.transform.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

public final class ProxyMethodTransformer implements Transformer {
    public TransformerDescriptor descriptor() { return new TransformerDescriptor("proxy","Proxy methods","Outline private unannotated implementations behind stable typed forwarding methods.",70,ProtectionProfile.EXTREME); }
    public void transform(ObfuscationContext context,ClassModel model) {
        for(MethodNode method:List.copyOf(model.node().methods)) if(context.eligible(model,method,"proxy") && (method.access & (Opcodes.ACC_PRIVATE|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==Opcodes.ACC_PRIVATE && !method.name.startsWith("<") && (method.visibleAnnotations==null || method.visibleAnnotations.isEmpty()) && (method.invisibleAnnotations==null || method.invisibleAnnotations.isEmpty()) && context.choose("proxy")) {
            MethodNode implementation=new MethodNode(Opcodes.ACC_PRIVATE|(method.access & Opcodes.ACC_STATIC),context.names().next(),method.desc,method.signature,method.exceptions.toArray(String[]::new));
            implementation.instructions=method.instructions; implementation.tryCatchBlocks=method.tryCatchBlocks; implementation.localVariables=method.localVariables; implementation.maxLocals=method.maxLocals; implementation.maxStack=method.maxStack;
            implementation.visibleLocalVariableAnnotations=method.visibleLocalVariableAnnotations; implementation.invisibleLocalVariableAnnotations=method.invisibleLocalVariableAnnotations;
            method.instructions=new InsnList(); method.tryCatchBlocks=new ArrayList<>(); method.localVariables=null; method.visibleLocalVariableAnnotations=null; method.invisibleLocalVariableAnnotations=null;
            boolean isStatic=(method.access & Opcodes.ACC_STATIC)!=0; int slot=0;
            if(!isStatic) { method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0)); slot=1; }
            for(Type argument:Type.getArgumentTypes(method.desc)) { method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD),slot)); slot+=argument.getSize(); }
            method.instructions.add(new MethodInsnNode(isStatic?Opcodes.INVOKESTATIC:Opcodes.INVOKESPECIAL,model.node().name,implementation.name,implementation.desc,false));
            method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN))); method.maxLocals=slot;
            model.node().methods.add(implementation); context.generated(implementation); context.changed("proxy");
        }
    }
}
