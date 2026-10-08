# Architecture and reference study

Reference: [Skidfuscator Community Edition](https://github.com/skidfuscatordev/skidfuscator-java-obfuscator), read from its public `master` checkout during implementation. The checkout in `.reference/` is excluded from distribution/source control.

Examined `Skidfuscator.java` (import, hierarchy caching, exemptions, transform phases and dumping), `transform/Transformer.java`, `transform/AbstractTransformer.java` (registration, pass statistics and size heuristics), hierarchy/invocation resolvers, StringTransformerV2 generators and NumberTransformer predicate binding. The [official transformer documentation](https://docs.skidfuscator.dev/transformers.html) describes number masking, per-character string masking and outlining; [ASM release notes](https://asm.ow2.io/versions.html) informed the pinned ASM 9.10.1 choice.

Useful ideas carried into this independent design:

1. Resolve hierarchy and compatibility/exemptions before changing identities.
2. Separate bytecode passes and their settings/statistics from front ends.
3. Use an ordered lifecycle and defer identity/resource remapping until the transform plan is known.
4. Enforce growth limits and validate graph/stack effects before publication.
5. Treat dispatch groups, loaders and reflection as constraints, rather than blindly renaming every member.
6. Bind numerical masking to a runtime opaque value rather than relying only on constant XOR layers.

React-Fuscator uses explicit constructor injection and an instance registry instead of a global event bus. It uses ASM tree/dataflow analysis rather than MapleIR/SSA. Class/member remapping operates on an immutable-by-convention per-run plan; output frames are recomputed from a metadata-only hierarchy. The verifier overrides ASM's reflective type-resolution paths, including interface subtype checks, so it never initializes user code.

The pipeline runs cover classes → invokedynamic strings → classic strings → numbers → branch/switch flow → guarded CFG flattening → predicates → junk → indirection → proxies → debug metadata. Profile defaults filter passes; individual settings can override that selection. Cover classes are selected through their source class rules and receive subsequent passes. Helpers generated within a class are tracked to prevent recursive expansion. Size rollback restores the entire class, newly added classes and pass counters. Verification failures are errors, not silently ignored transformations.

`MemberMappingPlanner` forms owned dispatch families, including superclass methods that implement an interface without a child declaration. External callbacks, annotations, serialized members and native binding constrain those families. Reflection analysis resolves Class receivers and private helper string arguments before deciding keep scope; unrelated external reflection and ordinary literals do not freeze input names. Supplied interacting artifacts are scanned for references to exported input classes.

`PackageLayoutPlanner` uses disjoint sets for actual non-public type/member accesses, invokedynamic/constant-dynamic method handles, package-private overrides and nestmates. Independent classes scatter into separate opaque packages. Kept classes anchor their access group instead of their entire original package. Package resources keep a coherent relocation; absolute resource paths pin packages. Generated cover classes have real incoming call edges, ordinary class flags and diverse fields/accessors. Pure static bodies can be extracted into them while synchronization remains on the original wrapper.

For Mixin systems, loader restrictions matter beyond the `@Mixin` annotation: anonymous inner classes and accessor helpers inside a Mixin package also need code/selector preservation. Their class identities can change; dedicated roots ensure the Mixin loader prefix never captures ordinary application classes. Config package/lists and refmap keys/descriptors are rewritten together. Refmap soft targets remain pinned, preserving existing Minecraft namespace mappings. [Mixin's own documentation](https://github.com/SpongePowered/Mixin/wiki/Introduction-to-Mixins---Obfuscation-and-Mixins) explains why soft references need independent handling. [Fabric metadata](https://docs.fabricmc.net/develop/loader/fabric-mod-json) and [Paper descriptors](https://docs.papermc.io/paper/dev/plugin-yml/) define entrypoint discovery.

Strong/Extreme invokedynamic strings store ciphertext/key material in bootstrap arguments and cache interned results in ConstantCallSites. Numerical decoding uses a per-method local initialized from class identity and an even-parity expression. Both are independent ASM implementations. Skidfuscator's MIT license permits reuse with its copyright/license notice, but no Skidfuscator or MapleIR source is embedded in this project.

CFG flattening has an explicit eligibility boundary. All block entries must have empty operand stacks; primitive local slots must have consistent categories. Object construction and exception/monitor regions are excluded. Dispatcher merges initialize locals and reference loads regain the original analyzed type through casts. Original lexical debug scope intervals are removed when blocks are reordered. JVM execution tests cover actual switch/loop/reference/category-2 behavior.

Publication stages the JAR, mapping and report beside the destination, replaces sidecars with rollback data, and publishes the JAR last. Each file move is atomic where the filesystem supports it. This is not a filesystem-wide transaction across process crashes, but routine verification, cancellation and write errors preserve the previous destination JAR.
