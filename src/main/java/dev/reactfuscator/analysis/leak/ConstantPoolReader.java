package dev.reactfuscator.analysis.leak;

import java.io.*;
import java.util.*;

/** Reads modified UTF-8, including unused constants; ASM tree visitors only see referenced constants. */
public final class ConstantPoolReader {
    public List<String> strings(byte[] bytes)throws IOException{
        try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes))){if(input.readInt()!=0xcafebabe)throw new IOException("Invalid class magic");input.readUnsignedShort();input.readUnsignedShort();int count=input.readUnsignedShort();List<String> strings=new ArrayList<>();
            for(int index=1;index<count;index++)switch(input.readUnsignedByte()){
                case 1->strings.add(input.readUTF());case 3,4->input.skipNBytes(4);case 5,6->{input.skipNBytes(8);index++;}case 7,8,16,19,20->input.skipNBytes(2);case 9,10,11,12,17,18->input.skipNBytes(4);case 15->input.skipNBytes(3);default->throw new IOException("Unknown constant pool tag at "+index);
            }return strings;
        }
    }
}
