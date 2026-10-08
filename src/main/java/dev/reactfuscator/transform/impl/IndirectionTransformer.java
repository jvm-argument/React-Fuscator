package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.BridgeMethodFactory;
import dev.reactfuscator.service.DispatcherMethodFactory;
import dev.reactfuscator.transform.Transformer;
import dev.reactfuscator.transform.TransformerDescriptor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndirectionTransformer implements Transformer {
    private final BridgeMethodFactory factory;
    private final DispatcherMethodFactory dispatchers;

    public IndirectionTransformer(BridgeMethodFactory factory) {
        this(factory, new DispatcherMethodFactory());
    }

    public IndirectionTransformer(
            BridgeMethodFactory factory, DispatcherMethodFactory dispatchers) {
        this.factory = factory;
        this.dispatchers = dispatchers;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "indirection",
                "Method & field indirection",
                "Type-correct same-owner invocation/access bridges; final field writes stay in"
                        + " constructors.",
                60,
                ProtectionProfile.STRONG);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        List<MethodNode> bridges = new ArrayList<>();
        Map<String, MethodNode> cache = new HashMap<>();
        for (MethodNode method : List.copyOf(model.node().methods)) {
            if (context.eligible(model, method, "indirection") && !method.name.startsWith("<")) {
                for (AbstractInsnNode node : method.instructions.toArray()) {
                    if (!context.choose("indirection")) {
                        continue;
                    }
                    MethodNode bridge;
                    String key;
                    if (node instanceof MethodInsnNode call
                            && call.owner.equals(model.node().name)
                            && !call.name.startsWith("<")
                            && (call.getOpcode() == Opcodes.INVOKESTATIC
                                    || call.getOpcode() == Opcodes.INVOKEVIRTUAL)) {
                        key = "m" + call.getOpcode() + call.owner + call.name + call.desc;
                        bridge = cache.get(key);
                        if (bridge == null) {
                            bridge = factory.invocation(context.names().next(), call);
                            cache.put(key, bridge);
                            bridges.add(bridge);
                        }
                    } else if (node instanceof FieldInsnNode field
                            && field.owner.equals(model.node().name)) {
                        FieldNode declaration =
                                model.node().fields.stream()
                                        .filter(
                                                f ->
                                                        f.name.equals(field.name)
                                                                && f.desc.equals(field.desc))
                                        .findFirst()
                                        .orElse(null);
                        if (declaration == null
                                || ((declaration.access & Opcodes.ACC_FINAL) != 0
                                        && (field.getOpcode() == Opcodes.PUTFIELD
                                                || field.getOpcode() == Opcodes.PUTSTATIC))) {
                            continue;
                        }
                        key = "f" + field.getOpcode() + field.owner + field.name + field.desc;
                        bridge = cache.get(key);
                        if (bridge == null) {
                            bridge = factory.field(context.names().next(), field);
                            cache.put(key, bridge);
                            bridges.add(bridge);
                        }
                    } else {
                        continue;
                    }
                    method.instructions.set(
                            node,
                            new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    model.node().name,
                                    bridge.name,
                                    bridge.desc,
                                    false));
                    context.changed("indirection");
                }
            }
        }
        if (context.config().profile == ProtectionProfile.EXTREME) {
            Map<String, List<MethodNode>> groups = new LinkedHashMap<>();
            bridges.forEach(m -> groups.computeIfAbsent(m.desc, d -> new ArrayList<>()).add(m));
            for (List<MethodNode> group : groups.values()) {
                for (int start = 0; start < group.size(); start += 8) {
                    List<MethodNode> targets =
                            group.subList(start, Math.min(start + 8, group.size()));
                    if (targets.size() < 2) {
                        continue;
                    }
                    var dispatcher = dispatchers.create(context, model.node().name, targets);
                    for (MethodNode caller : model.node().methods) {
                        for (AbstractInsnNode instruction : caller.instructions.toArray()) {
                            if (instruction instanceof MethodInsnNode call
                                    && call.owner.equals(model.node().name)
                                    && dispatcher.tokens().containsKey(call.name)) {
                                int token = dispatcher.tokens().get(call.name),
                                        mask = context.random().nextInt();
                                InsnList encoded = new InsnList();
                                encoded.add(context.bytecode().integer(token ^ mask));
                                encoded.add(context.bytecode().integer(mask));
                                encoded.add(new InsnNode(Opcodes.IXOR));
                                caller.instructions.insertBefore(call, encoded);
                                caller.instructions.set(
                                        call,
                                        new MethodInsnNode(
                                                Opcodes.INVOKESTATIC,
                                                model.node().name,
                                                dispatcher.method().name,
                                                dispatcher.method().desc,
                                                false));
                                context.changed("dispatchcalls");
                            }
                        }
                    }
                    bridges.add(dispatcher.method());
                    context.changed("dispatchers");
                }
            }
        }
        bridges.forEach(
                m -> {
                    model.node().methods.add(m);
                    context.generated(m);
                    context.changed("bridges");
                });
    }
}
