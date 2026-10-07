package dev.reactfuscator.runtime;

import java.lang.invoke.*;
import java.util.*;

/** Injected bootstrap preserves the JDK's concat semantics while hiding recipes/constants. */
public final class ConcatBootstrapTemplate {
    private ConcatBootstrapTemplate(){}
    public static CallSite link(MethodHandles.Lookup lookup,String name,MethodType type,String recipe,int key,String flags,Object... packed)throws Throwable{
        List<Object> constants=new ArrayList<>();
        for(int index=0,constant=0;index<packed.length;index++,constant++){Object value=packed[index];if(flags.charAt(constant)=='\ue201')value=StringCipherTemplate.decode((String)value,(Integer)packed[++index]);constants.add(value);}
        return StringConcatFactory.makeConcatWithConstants(lookup,name,type,StringCipherTemplate.decode(recipe,key),constants.toArray());
    }
}
