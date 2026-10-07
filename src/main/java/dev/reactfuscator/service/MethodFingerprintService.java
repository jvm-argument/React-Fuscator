package dev.reactfuscator.service;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Structural bytecode fingerprints used only to count distinct transformed bodies. */
public final class MethodFingerprintService {
    public long fingerprint(MethodNode method){
        Map<LabelNode,Integer> labels=new IdentityHashMap<>();int index=0;for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof LabelNode label)labels.put(label,index++);long hash=1;
        for(AbstractInsnNode instruction:method.instructions){if(instruction.getOpcode()<0)continue;int value=instruction.getOpcode();if(instruction instanceof MethodInsnNode call)value=Objects.hash(value,call.owner,call.name,call.desc,call.itf);else if(instruction instanceof FieldInsnNode field)value=Objects.hash(value,field.owner,field.name,field.desc);else if(instruction instanceof LdcInsnNode literal)value=Objects.hash(value,literal.cst);else if(instruction instanceof VarInsnNode local)value=Objects.hash(value,local.var);else if(instruction instanceof IntInsnNode integer)value=Objects.hash(value,integer.operand);else if(instruction instanceof TypeInsnNode type)value=Objects.hash(value,type.desc);else if(instruction instanceof IincInsnNode increment)value=Objects.hash(value,increment.var,increment.incr);else if(instruction instanceof JumpInsnNode jump)value=Objects.hash(value,labels.get(jump.label));else if(instruction instanceof InvokeDynamicInsnNode dynamic)value=Objects.hash(value,dynamic.name,dynamic.desc,dynamic.bsm,Arrays.deepHashCode(dynamic.bsmArgs));else if(instruction instanceof LookupSwitchInsnNode dispatch)value=Objects.hash(value,dispatch.keys,dispatch.labels.stream().map(labels::get).toList(),labels.get(dispatch.dflt));else if(instruction instanceof TableSwitchInsnNode dispatch)value=Objects.hash(value,dispatch.min,dispatch.max,dispatch.labels.stream().map(labels::get).toList(),labels.get(dispatch.dflt));hash=hash*0x100000001b3L+value;}
        for(TryCatchBlockNode region:method.tryCatchBlocks)hash=hash*31+Objects.hash(region.type,labels.get(region.start),labels.get(region.end),labels.get(region.handler));return hash;
    }
}
