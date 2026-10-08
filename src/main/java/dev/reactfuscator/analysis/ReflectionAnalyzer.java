package dev.reactfuscator.analysis;

import dev.reactfuscator.model.ArchiveModel;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class ReflectionAnalyzer {
    public void analyze(
            ClassNode owner,
            MethodNode method,
            ArchiveModel archive,
            KeepPolicy keeps,
            HierarchyService hierarchy) {
        if (method.instructions.size() == 0) {
            return;
        }
        boolean relevant = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals("java/lang/Class")
                    && Set.of("getMethod", "getDeclaredMethod", "getField", "getDeclaredField")
                            .contains(invocation.name)) {
                relevant = true;
            }
        }
        if (!relevant) {
            return;
        }
        try {
            Frame<SourceValue>[] frames =
                    new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
            for (int i = 0; i < method.instructions.size(); i++) {
                if (method.instructions.get(i) instanceof MethodInsnNode call
                        && call.owner.equals("java/lang/Class")
                        && Set.of("getMethod", "getDeclaredMethod", "getField", "getDeclaredField")
                                .contains(call.name)) {
                    Frame<SourceValue> frame = frames[i];
                    if (frame == null) {
                        continue;
                    }
                    int arguments = Type.getArgumentTypes(call.desc).length;
                    SourceValue receiver = frame.getStack(frame.getStackSize() - arguments - 1);
                    Set<String> targets = classTargets(receiver, owner, method, frames, 0);
                    SourceValue name = frame.getStack(frame.getStackSize() - arguments);
                    Set<String> literals = strings(name, method, frames, 0);
                    if (literals.isEmpty() && (method.access & Opcodes.ACC_PRIVATE) != 0) {
                        int parameter = parameterOf(name, method, frames, 0);
                        if (parameter >= 0) {
                            literals = argumentStrings(owner.name, method, parameter, archive);
                        }
                    }
                    if (targets.isEmpty()) {
                        if (!literals.isEmpty()) {
                            literals.forEach(keeps::keepMemberName);
                        } else {
                            keeps.dynamicMembers(
                                    owner.name
                                            + "#"
                                            + method.name
                                            + ": unresolved Class receiver and member name for "
                                            + call.name);
                        }
                        continue;
                    }
                    Set<String> affected = new HashSet<>();
                    for (String target : targets) {
                        Set<String> ownedReceivers = new HashSet<>();
                        for (String candidate : archive.classes().keySet()) {
                            if (hierarchy.assignable(target, candidate)) {
                                ownedReceivers.add(candidate);
                            }
                        }
                        Deque<String> parents = new ArrayDeque<>(ownedReceivers);
                        Set<String> visited = new HashSet<>();
                        while (!parents.isEmpty()) {
                            String current = parents.remove();
                            if (!visited.add(current)) {
                                continue;
                            }
                            affected.add(current);
                            ClassInfo info = hierarchy.resolve(current);
                            if (info.parent() != null) {
                                parents.add(info.parent());
                            }
                            parents.addAll(info.interfaces());
                        }
                    }
                    for (String candidate : affected) {
                        if (archive.classes().containsKey(candidate)) {
                            if (literals.isEmpty()) {
                                keeps.keepMembersOf(candidate);
                            } else {
                                ClassNode node = archive.classes().get(candidate).node();
                                if (call.name.endsWith("Field")) {
                                    for (FieldNode field : node.fields) {
                                        if (literals.contains(field.name)) {
                                            keeps.keepMember(
                                                    candidate,
                                                    field.name,
                                                    field.desc,
                                                    "Named reflection lookup");
                                        }
                                    }
                                } else {
                                    for (MethodNode member : node.methods) {
                                        if (literals.contains(member.name)) {
                                            keeps.keepMember(
                                                    candidate,
                                                    member.name,
                                                    member.desc,
                                                    "Named reflection lookup");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (AnalyzerException | RuntimeException failure) {
            keeps.dynamicMembers(
                    owner.name
                            + "#"
                            + method.name
                            + ": reflection analysis failed: "
                            + failure.getMessage());
        }
    }

    private int parameterOf(
            SourceValue value, MethodNode method, Frame<SourceValue>[] frames, int depth) {
        if (depth > 12) {
            return -1;
        }
        for (AbstractInsnNode instruction : value.insns) {
            if (instruction instanceof VarInsnNode variableInstruction
                    && variableInstruction.getOpcode() == Opcodes.ALOAD) {
                int i = method.instructions.indexOf(instruction);
                if (i < 0 || frames[i] == null) {
                    continue;
                }
                SourceValue local = frames[i].getLocal(variableInstruction.var);
                if (local.insns.isEmpty()) {
                    int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0, index = 0;
                    for (Type valueType : Type.getArgumentTypes(method.desc)) {
                        if (slot == variableInstruction.var) {
                            return index;
                        }
                        slot += valueType.getSize();
                        index++;
                    }
                } else {
                    return parameterOf(local, method, frames, depth + 1);
                }
            }
        }
        return -1;
    }

    private Set<String> argumentStrings(
            String owner, MethodNode target, int argument, ArchiveModel archive)
            throws AnalyzerException {
        Set<String> result = new HashSet<>();
        boolean found = false;
        for (var model : archive.classes().values()) {
            for (MethodNode caller : model.node().methods) {
                boolean relevant = false;
                for (AbstractInsnNode instruction : caller.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals(owner)
                            && call.name.equals(target.name)
                            && call.desc.equals(target.desc)) {
                        relevant = true;
                    }
                }
                if (!relevant) {
                    continue;
                }
                Frame<SourceValue>[] frames =
                        new Analyzer<>(new SourceInterpreter()).analyze(model.node().name, caller);
                for (int i = 0; i < caller.instructions.size(); i++) {
                    if (caller.instructions.get(i) instanceof MethodInsnNode call
                            && call.owner.equals(owner)
                            && call.name.equals(target.name)
                            && call.desc.equals(target.desc)
                            && frames[i] != null) {
                        found = true;
                        Frame<SourceValue> frame = frames[i];
                        Set<String> values =
                                strings(
                                        frame.getStack(
                                                frame.getStackSize()
                                                        - Type.getArgumentTypes(call.desc).length
                                                        + argument),
                                        caller,
                                        frames,
                                        0);
                        if (values.isEmpty()) {
                            return Set.of();
                        }
                        result.addAll(values);
                    }
                }
            }
        }
        return found ? result : Set.of();
    }

    private Set<String> classTargets(
            SourceValue value,
            ClassNode owner,
            MethodNode method,
            Frame<SourceValue>[] frames,
            int depth) {
        if (depth > 12) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (AbstractInsnNode source : value.insns) {
            int i = method.instructions.indexOf(source);
            Frame<SourceValue> frame = i < 0 ? null : frames[i];
            if (source instanceof LdcInsnNode ldc
                    && ldc.cst instanceof Type valueType
                    && valueType.getSort() == Type.OBJECT) {
                result.add(valueType.getInternalName());
            } else if (source instanceof VarInsnNode var
                    && frame != null
                    && var.getOpcode() == Opcodes.ALOAD) {
                result.addAll(
                        classTargets(frame.getLocal(var.var), owner, method, frames, depth + 1));
            } else if (frame != null
                    && ((source instanceof VarInsnNode var && var.getOpcode() == Opcodes.ASTORE)
                            || source.getOpcode() == Opcodes.CHECKCAST
                            || source.getOpcode() == Opcodes.DUP)) {
                result.addAll(
                        classTargets(
                                frame.getStack(frame.getStackSize() - 1),
                                owner,
                                method,
                                frames,
                                depth + 1));
            } else if (source instanceof MethodInsnNode invocation
                    && invocation.name.equals("getClass")
                    && frame != null) {
                result.addAll(
                        objectTypes(
                                frame.getStack(frame.getStackSize() - 1),
                                owner,
                                method,
                                frames,
                                depth + 1));
            } else if (source instanceof MethodInsnNode c
                    && c.owner.equals("java/lang/Class")
                    && c.name.equals("forName")
                    && frame != null) {
                for (String text :
                        strings(
                                frame.getStack(
                                        frame.getStackSize()
                                                - Type.getArgumentTypes(c.desc).length),
                                method,
                                frames,
                                depth + 1)) {
                    result.add(text.replace('.', '/'));
                }
            }
        }
        return result;
    }

    private Set<String> objectTypes(
            SourceValue value,
            ClassNode owner,
            MethodNode method,
            Frame<SourceValue>[] frames,
            int depth) {
        if (depth > 12) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (AbstractInsnNode source : value.insns) {
            int i = method.instructions.indexOf(source);
            Frame<SourceValue> frame = i < 0 ? null : frames[i];
            Type type = null;
            if (source instanceof FieldInsnNode fieldAccess) {
                type = Type.getType(fieldAccess.desc);
            } else if (source instanceof MethodInsnNode invocation) {
                type = Type.getReturnType(invocation.desc);
            } else if (source instanceof TypeInsnNode t
                    && (t.getOpcode() == Opcodes.NEW || t.getOpcode() == Opcodes.CHECKCAST)) {
                type = Type.getObjectType(t.desc);
            } else if (source instanceof VarInsnNode var
                    && var.getOpcode() == Opcodes.ALOAD
                    && frame != null) {
                if (var.var == 0 && (method.access & Opcodes.ACC_STATIC) == 0) {
                    result.add(owner.name);
                } else {
                    result.addAll(
                            objectTypes(frame.getLocal(var.var), owner, method, frames, depth + 1));
                    if (frame.getLocal(var.var).insns.isEmpty()) {
                        int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                        for (Type parameter : Type.getArgumentTypes(method.desc)) {
                            if (slot == var.var
                                    && parameter.getSort() == Type.OBJECT
                                    && !parameter.getInternalName().equals("java/lang/Object")
                                    && !parameter.getInternalName().equals("java/lang/Class")) {
                                result.add(parameter.getInternalName());
                            }
                            slot += parameter.getSize();
                        }
                    }
                }
            } else if (frame != null
                    && source instanceof VarInsnNode var
                    && var.getOpcode() == Opcodes.ASTORE) {
                result.addAll(
                        objectTypes(
                                frame.getStack(frame.getStackSize() - 1),
                                owner,
                                method,
                                frames,
                                depth + 1));
            }
            if (type != null && type.getSort() == Type.OBJECT) {
                result.add(type.getInternalName());
            }
        }
        return result;
    }

    private Set<String> strings(
            SourceValue value, MethodNode method, Frame<SourceValue>[] frames, int depth) {
        if (depth > 12) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (AbstractInsnNode instruction : value.insns) {
            if (instruction instanceof LdcInsnNode l && l.cst instanceof String s) {
                result.add(s);
            } else if (instruction instanceof VarInsnNode variableInstruction) {
                int i = method.instructions.indexOf(instruction);
                if (i >= 0 && frames[i] != null) {
                    if (variableInstruction.getOpcode() == Opcodes.ALOAD) {
                        result.addAll(
                                strings(
                                        frames[i].getLocal(variableInstruction.var),
                                        method,
                                        frames,
                                        depth + 1));
                    } else if (variableInstruction.getOpcode() == Opcodes.ASTORE) {
                        result.addAll(
                                strings(
                                        frames[i].getStack(frames[i].getStackSize() - 1),
                                        method,
                                        frames,
                                        depth + 1));
                    }
                }
            }
        }
        return result;
    }
}
