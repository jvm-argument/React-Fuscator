# React-Fuscator

[Русский](README.md) · **English**

An ASM-based Java obfuscator for ordinary JARs, Bukkit/Spigot/Paper plugins and Fabric mods. Includes a desktop interface and CLI. **Extreme** is the default profile.

## Features

- Class, package, method and field renaming, including coordinated Mixin class remapping.
- String encryption and numeric/constant obfuscation.
- Control-flow transformations, opaque predicates, junk code and additional classes with live references.
- Indirection, proxy and bridge methods, debug metadata removal.
- Updates to `plugin.yml`, `paper-plugin.yml`, `fabric.mod.json`, Mixin config/refmap, access wideners, Manifest, resources and `META-INF/services`.
- Include/exclude/keep rules, individual transformer settings and Light, Normal, Strong, Extreme profiles.
- Mapping files, stack trace retracing, processing reports and mandatory ASM verification.
- A GUI with JAR drag and drop, settings, progress, logs and statistics.

Platform callbacks and detected reflection contracts retain required names. Use `keep` and `--keep-member` rules for external interactions that cannot be inferred from bytecode.

## Build

Requires JDK 17 or newer and Maven.

```shell
mvn -B -ntp package
```

Executable JAR: `target/react-fuscator.jar`.

## Usage

Desktop interface:

```shell
java -jar target/react-fuscator.jar gui
```

CLI processing:

```shell
java -jar target/react-fuscator.jar obfuscate input.jar -o protected.jar
java -jar target/react-fuscator.jar obfuscate plugin.jar -o protected.jar -p extreme -l dependencies
java -jar target/react-fuscator.jar obfuscate input.jar -o protected.jar -p strong --exclude "vendor/**" --keep "api/**"
```

Repeat `-l` / `--library` for dependency JARs or directories. For Paper/Fabric, supply the matching API, Minecraft and libraries; Fabric requires a Minecraft JAR in the input mod's namespace. Dependencies are used for analysis and are not bundled in the output.

Processing writes `*.mapping.json` and `*.report.json` beside the output JAR.

Additional commands:

```shell
java -jar target/react-fuscator.jar --help
java -jar target/react-fuscator.jar transformers
java -jar target/react-fuscator.jar init-config config.json
java -jar target/react-fuscator.jar obfuscate input.jar -o protected.jar -c config.json
java -jar target/react-fuscator.jar verify protected.jar -l dependencies
java -jar target/react-fuscator.jar retrace protected.jar.mapping.json stacktrace.txt
```

[Download a release](https://github.com/jvm-argument/React-Fuscator/releases/latest) · [Report an issue](https://github.com/jvm-argument/React-Fuscator/issues)
