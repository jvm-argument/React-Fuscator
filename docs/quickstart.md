# Быстрый запуск

Нужна Java 17 или новее для React-Fuscator. Версия Java обрабатываемого плагина/мода при этом сохраняется.

Дважды нажмите `React-Fuscator.bat` в корне проекта либо выполните:

```powershell
java -jar dist/React-Fuscator.jar gui
```

Перетащите JAR в окно, укажите output, выберите профиль. Во вкладке Libraries добавьте по одному dependency JAR или каталогу на строку. Для Paper нужны библиотеки конкретного сервера и взаимодействующие плагины; для Fabric — Minecraft JAR в namespace исходного мода и его зависимости. Нажмите Protect JAR. Mapping и подробный JSON-отчёт появятся рядом с результатом.

Light/Normal подходят для начала проверки совместимости; Strong добавляет invokedynamic-строки, runtime-зависимые числа, predicates/junk/indirection; Extreme включает также связанные с рабочим кодом дополнительные классы, CFG flattening и proxy methods. Настройки проходов и исключения доступны справа. При недостатке зависимостей штатный режим прекращает обработку с указанием missing type.

Для полного ремапа, как в новом проверенном Xeron: выберите Extreme, оставьте **Keep public API** выключенным, включите **Rename Mixins** и **Scatter packages**, выключите **Keep serialization ABI**. Последняя настройка разрешает переименование enum/record/Serializable классов и может потребовать миграции ранее сохранённых данных. Обязательные имена API, native/reflection и Mixin selectors всё равно сохраняются. Подробности — в [remapping.md](remapping.md).

CLI использует тот же pipeline:

```powershell
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -p strong -l libraries
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -p extreme -l libraries --rename-serialization
java -jar dist/React-Fuscator.jar transformers
java -jar dist/React-Fuscator.jar --help
```

В `work/outputs/` уже находятся обработанные Extreme-версии AperEvent, LegendaryWeapon и **xeron-full-extreme.jar** вместе с mapping/report. Старые `xeron-extreme.jar`/`xeron-strong.jar` сохранены как предыдущие результаты; новые настройки демонстрирует именно `xeron-full-extreme.jar`. Проверки платформ и их пределы описаны в [validation.md](validation.md), архитектура — в [architecture.md](architecture.md), полная конфигурация и API расширения — в [README](../README.md).

Пересобрать проект с тестами: `./scripts/Build.ps1`. Повторно обработать предоставленные JAR с подготовленными зависимостями: `./scripts/Obfuscate-TestJars.ps1`. Fabric verification probe собирается на JDK 21+.
