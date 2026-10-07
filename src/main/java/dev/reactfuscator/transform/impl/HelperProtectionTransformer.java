package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.FlowTemplateFactory;
import dev.reactfuscator.transform.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Final protection for newly generated decoders/bridges without recursively generating helpers. */
public final class HelperProtectionTransformer implements Transformer {
    private final FlowTemplateFactory templates;
    public HelperProtectionTransformer(FlowTemplateFactory templates){this.templates=templates;}
    public TransformerDescriptor descriptor(){return new TransformerDescriptor("helpers","Helper protection","Mask late helper constants and vary decoder/dispatcher/bridge flow without recursive expansion.",78,ProtectionProfile.EXTREME);}
    public void transform(ObfuscationContext context,ClassModel model){
        for(MethodNode method:model.node().methods)if(context.isGenerated(method) && method.instructions.size()>0 && !method.name.startsWith("<") && context.choose("helpers")){
            // Outlined application bodies already carry all earlier numeric masks; do not expand them again.
            if(method.instructions.size()<500)for(AbstractInsnNode instruction:method.instructions.toArray()){
                Number value=instruction instanceof LdcInsnNode literal && literal.cst instanceof Number n?n:instruction instanceof IntInsnNode integer && (integer.getOpcode()==Opcodes.BIPUSH || integer.getOpcode()==Opcodes.SIPUSH)?integer.operand:instruction.getOpcode()>=Opcodes.ICONST_M1 && instruction.getOpcode()<=Opcodes.ICONST_5?instruction.getOpcode()-Opcodes.ICONST_0:null;
                if(value instanceof Integer || value instanceof Long){InsnList mask=new InsnList();if(value instanceof Integer){int key=context.random().nextInt();mask.add(context.bytecode().integer(value.intValue()^key));mask.add(context.bytecode().integer(key));mask.add(new InsnNode(Opcodes.IXOR));}else{long key=context.random().nextLong();mask.add(new LdcInsnNode(value.longValue()^key));mask.add(new LdcInsnNode(key));mask.add(new InsnNode(Opcodes.LXOR));}method.instructions.insertBefore(instruction,mask);method.instructions.remove(instruction);context.changed("helperconstants");}
            }
            method.instructions.insert(templates.guard(context,model.node().name));context.changed("helpers");
        }
    }
}
