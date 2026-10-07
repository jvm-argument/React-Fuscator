# React-Fuscator

Java + ASM obfuscator for ordinary JARs, Bukkit/Spigot/Paper plugins and Fabric mods. The desktop GUI and CLI share one pipeline and one compatibility policy. The application runs on Java 17+; transformed artifacts retain their original bytecode version. Java 8 output has been run under a real Java 8 JVM.

Инструкция на русском: [быстрый запуск](docs/quickstart.md) и [результаты реальных проверок](docs/validation.md).

## Build and launch

```powershell
mvn -B -ntp package
java -jar target/react-fuscator.jar gui
java -jar target/react-fuscator.jar --help
```

`React-Fuscator.bat` launches the packaged desktop application. `scripts/Build.ps1` builds, tests and copies the standalone JAR into `dist/React-Fuscator.jar`.

![Desktop interface](docs/gui-preview.png)

## CLI

```powershell
java -jar dist/React-Fuscator.jar inspect input.jar -l dependencies
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -p strong -l dependencies
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -p extreme -l dependencies --seed 42 --exclude 'vendor/**' --keep 'api/**'
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -p extreme -l dependencies --rename-serialization
java -jar dist/React-Fuscator.jar verify protected.jar -l dependencies
java -jar dist/React-Fuscator.jar init-config config.json
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -c config.json
java -jar dist/React-Fuscator.jar retrace protected.jar.mapping.json stacktrace.txt
```

`--library` accepts repeated JAR paths or directories. Nested dependency JARs are indexed, including Fabric API modules. Provide the dependencies for the actual platform/version and any interacting plugins or mods. Minecraft intermediary artifacts must use the same namespace as the input mod. Libraries supply metadata; they are not copied into the output.

Transformer switches: `--enable flatten`, `--disable strings`, `--set numbers.rounds=4`, `--set flow.density=80`. Run `transformers` to list registered passes. Configuration paths for libraries are relative to the configuration file. Unknown config keys and unsupported settings are rejected.

Each successful run produces the JAR, `*.mapping.json` and `*.report.json`. The report contains the seed, counts, size, timing, keep reasons and compatibility warnings. The JAR is published last after verification and staging; failures restore any sidecars replaced during publication. A fixed seed produces reproducible JAR bytes for an unchanged input, configuration and dependency set.

## Protection profiles

| Profile | Default passes | Density | Numeric/predicate rounds |
|---|---|---:|---:|
| Light | Strings, debug metadata | 15% | 1 |
| Normal | Light + numbers, branch/switch flow | 35% | 2 |
| Strong | Normal + invokedynamic strings, opaque predicates, junk, indirection | 65% | 3 |
| Extreme | Strong + interwoven cover classes, CFG flattening, proxy methods | 100% | 4 |

Class/package and eligible private/public/protected-member renaming are enabled in every profile. Owned interface/override dispatch families share one new name. External API callbacks retain their names. `--keep-public-api` restores conservative private-only member renaming. `--rename-serialization` also permits changing enum/record/Serializable class identities; existing serialized data can require migration. Mixin class renaming and package scattering are enabled by default; `--keep-mixin-names` and `--no-scatter` opt out. Each pass can be independently enabled, excluded or tuned. Density governs the fraction of eligible literals, instructions or methods selected. Junk method count and predicate/number expansion use rounds. GUI controls expose only settings actually used by that transformer.

## Implemented transformations

- Class/package mapping and eligible member renaming, including owned virtual dispatch families. External override/interface/SAM contracts remain stable. Inherited static/field references resolve to the original declaration; shadowed members remain distinct. Scattering groups classes by actual package access, method-handle references and nestmate constraints, instead of preserving the original directory tree.
- Referenced cover classes contain extracted pure static implementations or live arithmetic adapters, plus varied state/method shapes. They have ordinary class access flags, receive the remaining protection passes and share the scattered output namespace. Removing them breaks real call edges; this raises detection cost without promising that cover code is indistinguishable from application code.
- Strong/Extreme string literals use encrypted UTF-16 data in invokedynamic bootstrap arguments, with private decoders and cached, interned ConstantCallSites. This preserves Java 8 compatibility and string identity while moving decoding away from ordinary call sites.
- Per-literal UTF-16 string masking with a decoder injected into the same class. Unicode, NUL and interned-string identity are preserved. Private `ConstantValue` strings move into initializer code; public constant values and annotation values retain their contracts. Literals over 16,000 UTF-16 units are retained to avoid constant-pool overflow.
- Integer/long XOR layers and float/double reconstruction from their raw IEEE-754 bits. Strong/Extreme tie masking to a per-method runtime-derived opaque seed, so straight constant folding is insufficient to recover values.
- Conditional inversion, jump-to-switch conversion and encoded switch keys.
- Extreme CFG flattening: analyze stack-neutral block entries, initialize compatible locals, preserve reference types at use sites, shuffle blocks and route transitions through a state dispatcher. Constructors, exception regions, object construction, explicit monitor regions, incompatible reused locals and oversized graphs are excluded from flattening.
- Runtime-dependent parity predicates and verifier-valid decoy branches; synthetic junk methods.
- Typed same-owner invocation and field-access bridges. Final writes remain inside constructors/class initializers.
- Private unannotated method outlining behind forwarding methods; synchronization remains on the original entry method.
- Source/line removal and local-variable name scrambling; public parameter metadata remains intact. Flattened scopes are removed because lexical intervals no longer describe reordered blocks.

The transforms increase analysis cost, with additional code size and runtime work at stronger profiles. The string mechanism is reversible runtime obfuscation, not a cryptographic secret store.

## Compatibility and rules

Globs use JVM internal names (`com/example/**`) and member notation (`com/example/Owner#method(I)V`). `**` crosses package boundaries, `*` does not. `include` selects classes; `exclude` retains original names/code for matching classes or methods. All classes still receive reference remapping so calls from excluded code to renamed code remain valid. `keep` retains identities while allowing bytecode transforms; `keepMembers` retains matching member names.

Paper `plugin.yml` and `paper-plugin.yml` are parsed with safe YAML. Main, loader and bootstrapper class names are remapped in their descriptors. Their inherited platform callbacks remain compatible through hierarchy analysis.

Fabric entrypoints, adapters and `Class::method` declarations are recognized and remapped. Mixin classes and package helpers can be renamed and scattered under dedicated common roots separate from ordinary application classes. Config `package`/lists, refmap owner keys/descriptors and resource paths follow the mapping. Mixin bytecode/selectors and local soft-reference targets retain their contracts; external Minecraft symbols remain unchanged. Access widener v1/v2 declarations remap local owner/member/descriptors.

Manifest entrypoints and service descriptor paths/providers are remapped. Configured UTF-8 text resources replace complete class-name tokens, preserving unrelated substrings. Package-relative resources follow relocated packages; literal absolute resource paths pin their packages. Binary resources and embedded JARs are preserved. Old signatures/digests and the JAR index are removed; re-sign a transformed artifact when signatures are part of its deployment contract.

Enum constant names, record/annotation/serialized member contracts, JNI and Kotlin metadata are preserved. Enum/record/annotation/Serializable class identities are kept by default and can be explicitly released through `--rename-serialization`. JNA Structure/Union fields and Library/Callback functions retain the names used by native binding. Ordinary string literals do not pin unrelated members. Named reflection lookup is analyzed using its Class receiver and supplied string arguments; external reflection no longer freezes every input member. Unresolved dynamic class loading or unresolved receiver/name combinations use conservative keeps and report the reason. Member enumeration alone does not pin names.

Arbitrary reflection assembled from external data, JavaBean/getName-based protocols, name-based JSON persistence, external plugins that are not supplied, dynamically assembled resource paths and code that depends on the exact count of members require explicit keep/exclude rules and application tests. No static analyzer can infer all such runtime contracts. See [the remapping guide](docs/remapping.md) for the new settings and intentional exceptions.

Multi-release variants and embedded application JARs are retained byte-for-byte; root identities are kept for multi-release ABI consistency. Embedded archives are preserved rather than recursively obfuscated. Standard root-layout JARs are supported; executable Spring Boot/WAR layouts require separate handling of their contained application JARs. The diagnostic `--allow-missing-dependencies` mode marks its output uncertified; normal runs fail on unresolved hierarchy dependencies. ASM checking does not replace a real platform/application run.

## Architecture and extension

`core` owns orchestration; `analysis` owns hierarchy/compatibility/CFG analysis; `mapping` owns plans and artifacts; `remap` owns bytecode/resources; `platform` owns loader-specific metadata; `transform/impl` owns individual passes; `verification` owns ASM dataflow and frame computation; `io` owns archive/publication; `registry` owns registration; `cli` and `gui` own presentation.

Dependencies are injected through constructors in `ApplicationFactory`. A run receives its own context, seeded generators, models, settings, keep policy and statistics. No application class is loaded or initialized for ASM verification. Missing types never silently use the obfuscator's own dependency classpath.

To add a pass, implement `Transformer` and register it with `TransformerRegistry`, or expose it through `META-INF/services/dev.reactfuscator.transform.Transformer`. Its descriptor controls order, profile and available settings. The pipeline checks method-size growth and rolls back that class/pass when necessary. Structural or dataflow verification errors stop the run before publishing a JAR.

The Skidfuscator reference study is recorded in [docs/architecture.md](docs/architecture.md). This project has its own ASM implementation and does not embed Skidfuscator/MapleIR code.

## Validation

Run `mvn test`. Tests compare execution before/after processing under `-Xverify:all`, covering all profiles, Java 8/11/17/21/25 class versions, recursive calls, lambdas, exceptions, switch/loop flow, synchronization, array merges, numeric bits, scoped reflection, owned virtual dispatch, native contracts, package/method-handle/nest access, cover classes, invokedynamic strings, service providers, relative/absolute resources, serialization, records/enums, member shadowing, deterministic flattening, constant values, signatures, multi-release preservation, exclusions, cancellation and publication failures.

[docs/validation.md](docs/validation.md) records actual platform versions, real supplied artifacts and test limitations. Integration fixture projects and the Fabric verification probe are in `integration/`; optional runtime harness commands are in `scripts/`.
