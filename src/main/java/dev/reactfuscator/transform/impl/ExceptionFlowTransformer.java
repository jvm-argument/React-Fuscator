package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.ExceptionSupportFactory;
import dev.reactfuscator.transform.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** A local, genuinely executed handler transition before the original body/exception regions. */
public final class ExceptionFlowTransformer implements Transformer {
    private final ExceptionSupportFactory factory;
    public ExceptionFlowTransformer(ExceptionSupportFactory factory){this.factory=factory;}
    public TransformerDescriptor descriptor(){return new TransformerDescriptor("exceptions","Exception flow","Add bounded stackless exception dispatch outside original protected/monitor regions.",45,ProtectionProfile.EXTREME);}
    public void transform(ObfuscationContext context,ClassModel model){
        if(model.node().version<Opcodes.V1_7)return;
        for(MethodNode method:model.node().methods)if(!method.name.startsWith("<") && context.eligible(model,method,"exceptions") && method.instructions.size()>=80 && context.choose("exceptions")){
            boolean monitor=false;for(AbstractInsnNode instruction:method.instructions)if(instruction.getOpcode()==Opcodes.MONITORENTER || instruction.getOpcode()==Opcodes.MONITOREXIT)monitor=true;if(monitor)continue;
            var support=context.attachment(ExceptionSupportFactory.Support.class,()->factory.create(context,model));LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();InsnList transition=new InsnList();transition.add(start);transition.add(new FieldInsnNode(Opcodes.GETSTATIC,support.owner(),support.field(),"L"+support.owner()+";"));transition.add(new InsnNode(Opcodes.ATHROW));transition.add(end);transition.add(new JumpInsnNode(Opcodes.GOTO,resume));transition.add(handler);transition.add(new InsnNode(Opcodes.POP));transition.add(new JumpInsnNode(Opcodes.GOTO,resume));transition.add(resume);method.instructions.insert(transition);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,support.owner()));context.changed("exceptions");break;
        }
    }
}
