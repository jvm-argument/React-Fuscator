# React-Fuscator

**Русский** · [English](README.en.md)

Обфускатор Java на базе ASM для обычных JAR, Bukkit/Spigot/Paper-плагинов и Fabric-модов. Доступны графический интерфейс и CLI. Профиль **Extreme** выбран по умолчанию.

## Возможности

- Переименование классов, пакетов, методов и полей, включая согласованный ремап Mixin-классов.
- Шифрование строк, обфускация чисел и констант.
- Преобразования control flow, opaque predicates, junk code и дополнительные классы с рабочими ссылками.
- Indirection, proxy и bridge-методы, удаление debug metadata.
- Обновление `plugin.yml`, `paper-plugin.yml`, `fabric.mod.json`, Mixin config/refmap, access widener, Manifest, ресурсов и `META-INF/services`.
- Правила include/exclude/keep, отдельные настройки трансформеров и профили Light, Normal, Strong, Extreme.
- Mapping-файлы, восстановление имён в stack trace, отчёт об обработке и обязательная ASM verification.
- GUI с drag & drop JAR, настройками, прогрессом, логами и статистикой.

Платформенные callbacks и обнаруженные reflection-контракты сохраняют требуемые имена. Для внешних взаимодействий, которые нельзя определить из bytecode, доступны правила `keep` и `--keep-member`.

## Сборка

Требуются JDK 17 или новее и Maven.

```shell
mvn -B -ntp package
```

Готовый исполняемый JAR: `target/react-fuscator.jar`.

## Запуск

Графический интерфейс:

```shell
java -jar target/react-fuscator.jar gui
```

Обработка через CLI:

```shell
java -jar target/react-fuscator.jar obfuscate input.jar -o protected.jar
java -jar target/react-fuscator.jar obfuscate plugin.jar -o protected.jar -p extreme -l dependencies
java -jar target/react-fuscator.jar obfuscate input.jar -o protected.jar -p strong --exclude "vendor/**" --keep "api/**"
```

`-l` / `--library` принимает JAR или каталог зависимостей и может повторяться. Для Paper/Fabric укажите API, Minecraft и библиотеки соответствующей версии; для Fabric Minecraft JAR должен соответствовать namespace входного мода. Зависимости используются для анализа и не добавляются в результат.

Рядом с обработанным JAR создаются `*.mapping.json` и `*.report.json`.

Дополнительные команды:

```shell
java -jar target/react-fuscator.jar --help
java -jar target/react-fuscator.jar transformers
java -jar target/react-fuscator.jar init-config config.json
java -jar target/react-fuscator.jar obfuscate input.jar -o protected.jar -c config.json
java -jar target/react-fuscator.jar verify protected.jar -l dependencies
java -jar target/react-fuscator.jar retrace protected.jar.mapping.json stacktrace.txt
```

[Скачать релиз](https://github.com/jvm-argument/React-Fuscator/releases/latest) · [Сообщить об ошибке](https://github.com/jvm-argument/React-Fuscator/issues)
