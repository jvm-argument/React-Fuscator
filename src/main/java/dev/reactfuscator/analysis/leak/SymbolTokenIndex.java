package dev.reactfuscator.analysis.leak;

import java.util.*;
import java.util.function.BiConsumer;

/** Aho-Corasick token index: scan time is linear in text plus matches, rather than symbols times text. */
public final class SymbolTokenIndex {
    public record Symbol(String token,String category,String original,boolean ambiguous){}
    private final Node root=new Node();
    private final Map<String,List<Symbol>> tokens=new TreeMap<>();
    public void add(Symbol symbol){if(!symbol.token().isEmpty())tokens.computeIfAbsent(symbol.token(),k->new ArrayList<>()).add(symbol);}
    public void build(){
        for(var entry:tokens.entrySet()){Node node=root;for(char character:entry.getKey().toCharArray())node=node.edges.computeIfAbsent(character,c->new Node());node.outputs.addAll(entry.getValue());}
        root.failure=root;Deque<Node> queue=new ArrayDeque<>();for(Node node:root.edges.values()){node.failure=root;queue.add(node);}
        while(!queue.isEmpty()){Node node=queue.remove();for(var edge:node.edges.entrySet()){Node child=edge.getValue(),fallback=node.failure;while(fallback!=root && !fallback.edges.containsKey(edge.getKey()))fallback=fallback.failure;Node next=fallback.edges.get(edge.getKey());child.failure=next==null?root:next;child.outputs.addAll(child.failure.outputs);queue.add(child);}}
    }
    public void scan(String text,BiConsumer<Symbol,Integer> consumer){
        Node node=root;for(int offset=0;offset<text.length();offset++){char character=text.charAt(offset);while(node!=root && !node.edges.containsKey(character))node=node.failure;node=node.edges.getOrDefault(character,root);for(Symbol symbol:node.outputs){int start=offset-symbol.token().length()+1;if(boundaries(text,start,offset+1,symbol.category()))consumer.accept(symbol,start);}}
    }
    private boolean boundaries(String text,int start,int end,String category){
        if(start>0 && Character.isJavaIdentifierPart(text.charAt(start-1))){boolean descriptor=text.charAt(start-1)=='L' && (start==1 || ";[()".indexOf(text.charAt(start-2))>=0);if(!descriptor)return false;}
        if(end<text.length() && Character.isJavaIdentifierPart(text.charAt(end)) && text.charAt(end)!='$')return false;
        if(!category.equals("PACKAGE") && end<text.length() && (text.charAt(end)=='/' || text.charAt(end)=='.'))return false;
        return true;
    }
    private static final class Node{final Map<Character,Node> edges=new HashMap<>();final List<Symbol> outputs=new ArrayList<>();Node failure;}
}
