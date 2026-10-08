<div align="center">

![React-Fuscator](docs/assets/banner.svg)

[Русский](README.md) · **English**

[![Build](https://github.com/jvm-argument/React-Fuscator/actions/workflows/build.yml/badge.svg)](https://github.com/jvm-argument/React-Fuscator/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/jvm-argument/React-Fuscator?color=white)](https://github.com/jvm-argument/React-Fuscator/releases/latest)
![Java](https://img.shields.io/badge/Java-17%2B-white)
![ASM](https://img.shields.io/badge/ASM-9.10.1-white)

[Download](https://github.com/jvm-argument/React-Fuscator/releases/latest) · [Report an issue](https://github.com/jvm-argument/React-Fuscator/issues) · [Architecture](docs/architecture.md)

</div>

Java + ASM obfuscation for ordinary JARs, Bukkit/Spigot/Paper plugins and Fabric mods. GUI and CLI share an extensible pipeline, compatibility analysis and mandatory ASM verification. **Extreme is the default.**

## Quick start

Install Java 17 or newer and download `React-Fuscator.jar` from the [release](https://github.com/jvm-argument/React-Fuscator/releases/latest):

```shell
java -jar React-Fuscator.jar gui
```

Drop a JAR, select output and click **Obfuscate**. Add the matching platform API and dependencies in **Libraries** for platform code; standalone JARs without external dependencies need none. The portable ZIP includes `React-Fuscator.bat` for Windows.

![Desktop interface](docs/gui-preview.png)

## CLI

```shell
java -jar React-Fuscator.jar obfuscate input.jar -o protected.jar
java -jar React-Fuscator.jar obfuscate plugin.jar -o protected.jar -l dependencies --seed 42
java -jar React-Fuscator.jar obfuscate input.jar -o protected.jar -p strong --exclude "vendor/**" --keep "api/**"
java -jar React-Fuscator.jar inspect input.jar -l dependencies
java -jar React-Fuscator.jar verify protected.jar -l dependencies
java -jar React-Fuscator.jar transformers
java -jar React-Fuscator.jar init-config config.json
java -jar React-Fuscator.jar obfuscate input.jar -o protected.jar -c config.json
java -jar React-Fuscator.jar retrace protected.jar.mapping.json stacktrace.txt
```

Repeat `-l` / `--library` for JARs or directories. Fabric needs a Minecraft JAR in the input mod's namespace, such as intermediary, with matching dependencies. Libraries provide metadata and are not bundled in output. Missing hierarchy dependencies stop normal processing; `--allow-missing-dependencies` is diagnostic and marks output uncertified.

A successful run writes the JAR, `*.mapping.json` and `*.report.json`. Mapping contains original names and supports retrace. Publication follows verification. A fixed seed reproduces bytes with unchanged inputs, configuration and libraries.

## Profiles and transformers

| Profile | Additional passes |
|---|---|
| Light | String encryption, debug metadata removal |
| Normal | Light + numbers, conditional/switch flow |
| Strong | Normal + invokedynamic/concat strings, opaque predicates, junk code, typed indirection |
| **Extreme · default** | Strong + cover classes, ConstantValue, CFG flattening, exception flow, proxies and helper protection |

Class/package/method/field remapping is enabled in every profile. Owned virtual/interface families share new names; external API callbacks remain stable. Scattering respects package access, nestmates and method handles.

- Several UTF-16 decryptors distributed randomly, cached/interned invokedynamic strings and encrypted StringConcatFactory recipes.
- Numeric masking tied to runtime-derived values with raw IEEE-754 bits preserved.
- Varied opaque/branch/switch templates, one- and two-level CFG dispatchers and constrained exception-based flow.
- Typed bridges and multi-target dispatchers without boxing; proxies retain synchronization at the original entry method.
- Cover classes with live incoming edges, extracted implementations and ordinary access flags.
- Removal of LocalVariableTable, LocalVariableTypeTable, LineNumberTable, MethodParameters, SourceFile and SourceDebugExtension. Semantic attributes remain intact.

Configure passes independently: `--disable strings`, `--enable flatten`, `--set numbers.rounds=4`, `--set flow.density=80`. Run `transformers` for the registry; see [examples](examples) for configuration.

## Compatibility and rules

Supported metadata includes `plugin.yml`, `paper-plugin.yml`, `fabric.mod.json`, entrypoints, Mixins, refmap, access widener, Manifest and `META-INF/services`. Mixin classes/packages follow config/refmap mappings while selectors and required contracts remain stable. UTF-8 resources replace complete class-name tokens; binary resources and nested JARs are preserved.

Reflection analysis precedes renaming. Named lookups retain affected owners and their hierarchy; unresolved dynamic lookup uses conservative keeps and warnings. JNI/JNA, enum constants, record components, serialization fields/hooks and unknown annotation contracts are retained. Enum/record/Serializable class identities may change by default; enable `preserveSerializationNames` or GUI **Keep serialization ABI** when existing serialized data requires stable identities.

Rules use JVM internal names: `com/example/**`, `com/example/Owner#method(I)V`. `include` selects classes, `exclude` retains names/code, `keep` retains names while permitting transforms, and `keepMembers` retains selected members. Excluded code still receives updated references. See the [remapping guide](docs/remapping.md).

Old signatures are removed after modifying JAR contents. Multi-release variants and nested application JARs are not recursively transformed. Reflection from external data, name-based JSON, unspecified interacting mods/plugins and Spring Boot/WAR layouts need rules and application tests. Runtime string protection is reversible; Extreme increases code size and JVM work.

## Leak Scanner and report

After remapping and encoding, scan constant pools, debug attributes and text resources for original packages/classes/members, sensitive plaintext and stale Paper/Fabric/Mixin references.

Findings distinguish `UNRESOLVED`, `RETAINED_CONTRACT`, `EXTERNAL_CONTRACT`, `AMBIGUOUS_TOKEN`, `RESOURCE_DATA` and `EXCLUDED`. Required ABI names appear separately from unresolved leaks. The protection report includes renamed classes/methods/fields, encrypted strings, unique transformed methods, removed debug attributes, predicates/proxies/dispatchers, detected/resolved metadata leaks, cipher/flow distributions and locations. Details are capped at 25,000 records; full counters remain available.

## Build and style

```powershell
mvn -B -ntp verify
./scripts/Build.ps1
./scripts/Format-Java.ps1
./scripts/Format-Java.ps1 -Check
```

Building needs JDK 17+; formatting needs JDK 21+. Style uses Google Java Format with AOSP indentation: four spaces, expanded blocks, separate field declarations, blank lines between methods and no comments. CI checks style and JVM behavior on Linux/Windows with Java 17/21/25.

`ApplicationFactory` wires constructor dependencies. `core`, `analysis`, `mapping`, `remap`, `platform`, `transform`, `verification`, `io`, `cli` and `gui` separate responsibilities. Implement `Transformer` and register it in `TransformerRegistry` or ServiceLoader to add a pass. Oversized expansions roll back per class/pass; verifier failures stop publication.

Skidfuscator informed the architectural study; the implementation is independent and bundles no Skidfuscator/MapleIR code. Dependencies and licenses are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Validation

44 automated tests cover execution before/after with `-Xverify:all`, all profiles, reflection, dispatch, Unicode/NUL, numbers, exceptions, synchronization, resources, metadata, reproducibility and publication.

The new Extreme was run against real Xeron 1.0.0 / Fabric 1.21.4: the client entered a local world, JVM inspection verified 917 ordinary classes without errors, Mixins and entrypoints loaded and the process exited with code 0. This validates that artifact, not every Minecraft/API combination. The earlier Paper/Fabric 1.16.5–26.3 matrix and limitations are recorded in [validation results](docs/validation.md). User mods, plugins and servers are excluded from releases.
