package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.*;
import dev.reactfuscator.transform.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Encrypt StringConcatFactory recipes and string bootstrap constants, retaining its implementation. */
public final class ConcatStringTransformer implements Transformer {
    private final StringCipherService cipher;
    private final ConcatBootstrapFactory bootstraps;
    public ConcatStringTransformer(StringCipherService cipher,ConcatBootstrapFactory bootstraps){this.cipher=cipher;this.bootstraps=bootstraps;}
    public TransformerDescriptor descriptor(){return new TransformerDescriptor("concatstrings","Concat bootstrap strings","Encrypt JDK concat recipes/constants through private variable-arity bootstraps.",7,ProtectionProfile.STRONG);}
    public void transform(ObfuscationContext context,ClassModel model){
        if(model.node().version<Opcodes.V9)return;record Pair(String link,String decode){}Map<Integer,Pair> helpers=new LinkedHashMap<>();
        for(MethodNode method:List.copyOf(model.node().methods))if(context.eligible(model,method,"concatstrings"))for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof InvokeDynamicInsnNode call && call.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory") && call.bsm.getName().equals("makeConcatWithConstants") && call.bsmArgs.length>0 && call.bsmArgs[0] instanceof String recipe && recipe.length()<=16000 && context.choose("concatstrings")){
            int strings=0;boolean oversized=false;for(Object value:call.bsmArgs)if(value instanceof String text){strings++;if(text.length()>16000)oversized=true;}if(oversized || call.bsmArgs.length+strings>248)continue;
            int variant=context.random().nextInt(1,4);Pair pair=helpers.computeIfAbsent(variant,v->new Pair(context.names().next(),context.names().next()));List<Object> arguments=new ArrayList<>();StringBuilder flags=new StringBuilder();for(int index=1;index<call.bsmArgs.length;index++)flags.append(call.bsmArgs[index] instanceof String?'\ue201':'\ue200');
            for(int index=0;index<call.bsmArgs.length;index++){Object argument=call.bsmArgs[index];if(argument instanceof String text){int key=context.random().nextInt();arguments.add(cipher.encrypt(text,key,variant));arguments.add(key);context.changed("concatstrings");context.statistics().cipherVariants.merge("variant"+variant,1L,Long::sum);}else arguments.add(argument);if(index==0)arguments.add(flags.toString());}
            call.bsm=new Handle(Opcodes.H_INVOKESTATIC,model.node().name,pair.link(),"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;ILjava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",false);call.bsmArgs=arguments.toArray();context.changed("concatcalls");
        }
        for(var entry:helpers.entrySet()){MethodNode decode=cipher.decoder(entry.getValue().decode(),entry.getKey()),link=bootstraps.create(model.node().name,entry.getValue().link(),decode.name);model.node().methods.add(decode);model.node().methods.add(link);context.generated(decode);context.generated(link);}
    }
}
