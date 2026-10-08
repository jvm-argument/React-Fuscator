package dev.reactfuscator.transform.impl;

import dev.reactfuscator.analysis.ControlFlowGraphAnalyzer;
import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.model.FlowBlockModel;
import dev.reactfuscator.model.FlowGraphModel;
import dev.reactfuscator.transform.Transformer;
import dev.reactfuscator.transform.TransformerDescriptor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public final class ControlFlowFlatteningTransformer implements Transformer {
    private final ControlFlowGraphAnalyzer analyzer;

    public ControlFlowFlatteningTransformer(ControlFlowGraphAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "flatten",
                "Control-flow flattening",
                "Shuffle stack-neutral blocks behind single/partitioned dispatchers before branch"
                        + " expansion.",
                25,
                ProtectionProfile.EXTREME);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        List<MethodNode> methods = model.node().methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodNode original = methods.get(i);
            if (!context.eligible(model, original, "flatten")
                    || original.name.startsWith("<")
                    || !context.choose("flatten")) {
                continue;
            }
            MethodNode candidate =
                    new MethodNode(
                            original.access,
                            original.name,
                            original.desc,
                            original.signature,
                            original.exceptions.toArray(String[]::new));
            original.accept(candidate);
            Optional<FlowGraphModel> analyzed =
                    analyzer.analyze(model.node(), candidate, context.hierarchy());
            if (analyzed.isEmpty()) {
                continue;
            }
            flatten(candidate, analyzed.get(), context);
            methods.set(i, candidate);
            context.changed("flatten");
        }
    }

    private void flatten(MethodNode method, FlowGraphModel graph, ObfuscationContext context) {
        int stateSlot = method.maxLocals++;
        LabelNode dispatch = new LabelNode(), invalid = new LabelNode();
        Map<LabelNode, Integer> states = new IdentityHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (FlowBlockModel block : graph.blocks()) {
            int value;
            do {
                value = context.random().nextInt();
            } while (!used.add(value));
            states.put(block.entry(), value);
        }
        InsnList out = new InsnList();
        int arguments = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type valueType : Type.getArgumentTypes(method.desc)) {
            arguments += valueType.getSize();
        }
        for (int slot = arguments; slot < graph.localTypes().length; slot++) {
            Type type = graph.localTypes()[slot];
            if (type == null) {
                continue;
            }
            int opcode =
                    switch (type.getSort()) {
                        case Type.LONG -> Opcodes.LCONST_0;
                        case Type.DOUBLE -> Opcodes.DCONST_0;
                        case Type.FLOAT -> Opcodes.FCONST_0;
                        case Type.OBJECT, Type.ARRAY -> Opcodes.ACONST_NULL;
                        default -> Opcodes.ICONST_0;
                    };
            out.add(new InsnNode(opcode));
            out.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), slot));
            if (type.getSize() == 2) {
                slot++;
            }
        }
        transition(out, states.get(graph.blocks().get(0).entry()), stateSlot, dispatch, context);
        out.add(dispatch);
        out.add(new VarInsnNode(Opcodes.ILOAD, stateSlot));
        SortedMap<Integer, LabelNode> table = new TreeMap<>();
        states.forEach((label, value) -> table.put(value, label));
        if (context.random().nextBoolean()) {
            out.add(
                    new LookupSwitchInsnNode(
                            invalid,
                            table.keySet().stream().mapToInt(Integer::intValue).toArray(),
                            table.values().toArray(LabelNode[]::new)));
            context.statistics().flowVariants.merge("flatSingle", 1L, Long::sum);
        } else {
            out.add(new InsnNode(Opcodes.ICONST_3));
            out.add(new InsnNode(Opcodes.IAND));
            LabelNode[] buckets = {
                new LabelNode(), new LabelNode(), new LabelNode(), new LabelNode()
            };
            out.add(new TableSwitchInsnNode(0, 3, invalid, buckets));
            for (int bucket = 0; bucket < 4; bucket++) {
                out.add(buckets[bucket]);
                out.add(new VarInsnNode(Opcodes.ILOAD, stateSlot));
                SortedMap<Integer, LabelNode> subset = new TreeMap<>();
                for (var entry : table.entrySet()) {
                    if ((entry.getKey() & 3) == bucket) {
                        subset.put(entry.getKey(), entry.getValue());
                    }
                }
                out.add(
                        new LookupSwitchInsnNode(
                                invalid,
                                subset.keySet().stream().mapToInt(Integer::intValue).toArray(),
                                subset.values().toArray(LabelNode[]::new)));
            }
            context.statistics().flowVariants.merge("flatPartitioned", 1L, Long::sum);
        }
        List<FlowBlockModel> shuffled = new ArrayList<>(graph.blocks());
        for (int i = shuffled.size() - 1; i > 0; i--) {
            Collections.swap(shuffled, i, context.random().nextInt(i + 1));
        }
        method.instructions.clear();
        for (FlowBlockModel block : shuffled) {
            AbstractInsnNode terminal = null;
            for (AbstractInsnNode node : block.instructions()) {
                if (node.getOpcode() >= 0) {
                    terminal = node;
                }
            }
            for (AbstractInsnNode node : block.instructions()) {
                if (node instanceof FrameNode || node instanceof LineNumberNode) {
                    continue;
                }
                if (node == terminal && node instanceof JumpInsnNode jump) {
                    if (jump.getOpcode() == Opcodes.GOTO) {
                        transition(out, states.get(jump.label), stateSlot, dispatch, context);
                    } else {
                        LabelNode branch = new LabelNode();
                        out.add(new JumpInsnNode(jump.getOpcode(), branch));
                        transition(
                                out, states.get(block.fallthrough()), stateSlot, dispatch, context);
                        out.add(branch);
                        transition(out, states.get(jump.label), stateSlot, dispatch, context);
                    }
                } else if (node == terminal
                        && (node instanceof LookupSwitchInsnNode
                                || node instanceof TableSwitchInsnNode)) {
                    Map<LabelNode, LabelNode> bridges = new LinkedHashMap<>();
                    LabelNode fallback;
                    int[] keys;
                    List<LabelNode> targets;
                    if (node instanceof LookupSwitchInsnNode s) {
                        fallback = s.dflt;
                        keys = s.keys.stream().mapToInt(Integer::intValue).toArray();
                        targets = s.labels;
                    } else {
                        TableSwitchInsnNode s = (TableSwitchInsnNode) node;
                        fallback = s.dflt;
                        keys = new int[s.labels.size()];
                        for (int i = 0; i < keys.length; i++) {
                            keys[i] = s.min + i;
                        }
                        targets = s.labels;
                    }
                    LabelNode defaultBridge =
                            bridges.computeIfAbsent(fallback, k -> new LabelNode());
                    LabelNode[] labels =
                            targets.stream()
                                    .map(t -> bridges.computeIfAbsent(t, k -> new LabelNode()))
                                    .toArray(LabelNode[]::new);
                    out.add(new LookupSwitchInsnNode(defaultBridge, keys, labels));
                    for (var e : bridges.entrySet()) {
                        out.add(e.getValue());
                        transition(out, states.get(e.getKey()), stateSlot, dispatch, context);
                    }
                } else {
                    out.add(node);
                    Type cast = graph.referenceLoads().get(node);
                    if (cast != null) {
                        out.add(new TypeInsnNode(Opcodes.CHECKCAST, cast.getInternalName()));
                    }
                }
            }
            int op = terminal == null ? -1 : terminal.getOpcode();
            boolean transfers =
                    terminal instanceof JumpInsnNode
                            || terminal instanceof LookupSwitchInsnNode
                            || terminal instanceof TableSwitchInsnNode
                            || op == Opcodes.ATHROW
                            || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN);
            if (!transfers) {
                if (block.fallthrough() == null) {
                    throw new IllegalStateException("Invalid CFG fallthrough");
                }
                transition(out, states.get(block.fallthrough()), stateSlot, dispatch, context);
            }
        }
        out.add(invalid);
        out.add(new InsnNode(Opcodes.ACONST_NULL));
        out.add(new InsnNode(Opcodes.ATHROW));
        method.instructions = out;
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }

    private void transition(
            InsnList out, int state, int slot, LabelNode dispatch, ObfuscationContext context) {
        out.add(context.bytecode().integer(state));
        out.add(new VarInsnNode(Opcodes.ISTORE, slot));
        out.add(new JumpInsnNode(Opcodes.GOTO, dispatch));
    }
}
