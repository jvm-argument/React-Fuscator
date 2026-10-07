package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.transform.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Move eligible ConstantValue fields into initializer code before literal encryption/masking. */
public final class ConstantFieldTransformer implements Transformer {
    public TransformerDescriptor descriptor(){return new TransformerDescriptor("constantfields","Constant fields","Hide owned static ConstantValue strings/numbers in initializer code before encryption.",6,ProtectionProfile.EXTREME);}
    public void transform(ObfuscationContext context,ClassModel model){
        InsnList initial=new InsnList();
        MethodNode initializer=model.node().methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElse(null);
        if(initializer!=null && !context.eligible(model,initializer,"constantfields"))return;
        for(FieldNode field:model.node().fields)if(field.value!=null && (field.access&Opcodes.ACC_STATIC)!=0 && !field.name.equals("serialVersionUID") && !context.keeps().keepMember(model.originalName(),field.name,field.desc) && context.keeps().transformField(model.originalName(),field) && context.choose("constantfields")){
            initial.add(new LdcInsnNode(field.value));initial.add(new FieldInsnNode(Opcodes.PUTSTATIC,model.node().name,field.name,field.desc));field.value=null;context.changed("constantfields");
        }
        if(initial.size()==0)return;
        if(initializer==null){initializer=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);initializer.instructions.add(new InsnNode(Opcodes.RETURN));model.node().methods.add(initializer);}
        initializer.instructions.insert(initial);
    }
}
