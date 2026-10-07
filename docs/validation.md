# Проверки React-Fuscator 1.0.0

Дата: 8 октября 2026 года. Все описанные запуски выполнены локально на Windows в изолированных каталогах `work/`. Исходные JAR, установленный Minecraft и существующий сервер не изменялись. Каждый процесс JVM запускался с `-Xverify:all`.

## Автоматические проверки

`mvn -B -ntp package` на Java 25.0.3: **38 тестов, 0 failures, 0 errors**.

- `PipelineIntegrationTest`: 16 проверок, включая четыре профиля и сравнение результата исполнения исходного/обработанного кода.
- `AdditionalRegressionTest`: 7 проверок констант, CFG flattening, повторяемости, multi-release/signatures, retrace, правил исключения и отказа публикации.
- `PlatformMetadataTest`: 4 проверки Paper/Fabric metadata, entrypoints, Mixins/refmap, access widener и glob-правил.
- `RemappingRegressionTest`: 11 проверок public/virtual ремапа, наследуемой реализации интерфейса, scoped reflection, обычных строковых литералов, JNA, package/nest/метод-handle доступа, связанных cover classes и Java 8 invokedynamic strings.

Функциональный GUI harness на Java 17 нажал настоящий Protect JAR action, дождался background worker, проверил JAR/mapping/report, завершённый progress и статистику. Swing screenshot обновлён через реальный рендер компонентов. Headless harness не проверяет системный file chooser или физический drag & drop.

На этой машине проверены class-file версии Java 8, 11, 17, 21 и 25. Тесты компиляции более новых class-file версий требуют соответствующего JDK; на старом JDK такие ветви не выполняются. Сам обфускатор собран с `--release 17`; CLI отдельно запущен на JDK 17. CI для Windows/Linux и Java 17/21/25 настроен, но удалённый GitHub CI в этой сессии не запускался.

## Реальные платформы

| Платформа | JVM | Артефакт Extreme | Выполнено |
|---|---|---|---|
| Paper 1.16.5, build 794 | Java 8u421 | `integration/paper-fixture` | onEnable, Bukkit listener, команда `reacttest`, остановка: exit 0 |
| Paper 1.21.11, build 132 | Java 25.0.3 | AperEvent 1.6.2 + LegendaryWeapon 2.0.6 | совместная загрузка, plugin listing, admin info, reload обоих плагинов, start/stop события, остановка: exit 0 |
| Paper 26.3, build 159 | Java 25.0.3 | `integration/paper-fixture` | onEnable, Bukkit listener, команда `reacttest`, остановка: exit 0 |
| Fabric server 1.16.5 / Loader 0.19.3 | Java 17 | `integration/fabric-fixture` | main entrypoint, поведенческие assertions, запуск мира, list, остановка: exit 0 |
| Fabric client 1.21.4 / Loader 0.19.3 / API 0.119.4+1.21.4 | Java 21.0.7 | Xeron 1.0.0, полный ремап | entrypoint, переименованные Mixins, рендер/ресурсы, проверка классов, штатная остановка: exit 0 |
| Fabric server 26.3 / Loader 0.19.3 | Java 25.0.3 | `integration/fabric-fixture` | main entrypoint, поведенческие assertions, запуск мира, list, остановка: exit 0 |

Paper fixture выдал `RF_PLUGIN_ENABLED`, `RF_EVENT_HANDLER_OK`, `RF_COMMAND_OK` на обеих граничных версиях. Fabric fixture выдал `RF_FABRIC_MAIN_OK` на обеих граничных версиях.

Xeron probe использует настоящий Knot/Mixin class loader: загружает классы без массовой инициализации и запрашивает declared methods/fields/constructors. Итог финального полного ремапа: **916 классов проверено, 52 переименованных класса Mixin-пакета пропущено, 0 ошибок**. Это 559 обычных исходных классов и 357 cover classes. Mixin-классы нельзя принудительно загружать как обычные классы; их загрузку/применение проверяет реальный игровой bootstrap. Проверка завершает клиент через `MinecraftClient.scheduleStop`, по [официальным Yarn mappings 1.21.4](https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.4/net/minecraft/client/MinecraftClient.html). На предыдущем, более консервативном варианте дополнительно запускался локальный мир; финальный полный ремап проверен до главного меню и штатного завершения probe.

## Результаты обработки предоставленных JAR

Все три обработаны профилем Extreme, seed `20261008`, с обязательной ASM verification после проходов и перед записью.

| JAR | Исходных + добавленных классов | Переименовано исходных классов / методов / полей | Строк | Flattened методов | Размер до → после, байт |
|---|---:|---:|---:|---:|---:|
| AperEvent 1.6.2 | 94 + 57 | 71 / 551 / 298 | 637 | 165 | 255982 → 711556 |
| LegendaryWeapon 2.0.6 | 128 + 81 | 116 / 525 / 261 | 824 | 176 | 313885 → 798376 |
| Xeron 1.0.0, полный ремап | 611 + 357 | 611 / 4238 / 3271 | 5977 | 1218 | 10053840 → 13328346 |

Xeron обработан с `--rename-serialization`: переименованы все исходные классы, включая Mixin-пакет, а JSON config/refmap обновлены. Public/virtual ремап включён по умолчанию. `initializer`, `moduleStorage`, `initStorage`, `unhook` присутствуют в mapping; class paths под `ru/whylol` отсутствуют. Reflection внешних библиотек больше не блокирует весь member remapping. Обнаруженный при тестировании JNA native field-order контракт теперь сохраняется отдельно. Взаимодействующие Paper-плагины передавались друг другу как библиотеки для сохранения экспортируемых имён; enum/record class identities в них сохранены штатной настройкой. Для Fabric использован соответствующий client-intermediary JAR и зависимости установленной версии.

Готовые артефакты находятся в `work/outputs/`, mapping/report — рядом с каждым JAR. Новый результат Xeron называется **xeron-full-extreme.jar**. Старые strong/xeron-extreme результаты сохранены как история. Актуальные снимки отчётов, логов, сборки и SHA-256 сохранены в `docs/validation/`.

## Воспроизведение

```powershell
./scripts/Build.ps1
./scripts/Obfuscate-TestJars.ps1

# Fixture projects
mvn -B -ntp -f integration/paper-fixture/pom.xml package
mvn -B -ntp -f integration/fabric-fixture/pom.xml package
mvn -B -ntp -f integration/fabric-probe/pom.xml package

# Runtime harness sends commands only to its own child process.
java -cp target/test-classes dev.reactfuscator.testing.RuntimeSmokeRunner work/paper-1.21.11 scripts/paper-smoke-commands.txt
./scripts/Start-FabricTest.ps1
```

Версии клиента, пути Java и Minecraft меняются параметрами `Start-FabricTest.ps1`; старый Paper запускается через третий аргумент harness с путём к Java 8. Серверные дистрибутивы, dependency caches и принятый EULA должны уже находиться в соответствующих тестовых каталогах. Серверы слушают localhost в offline test mode; клиент использует отдельную тестовую identity и game directory.

## Пределы проведённых проверок

Проверены граничные версии диапазона и версии реальных предоставленных JAR. Это не полный перебор каждого выпуска Minecraft между 1.16 и 26.3. Граничные Fabric-проверки выполнены на сервере; клиентские Mixins проверены на 1.21.4. Обфускация сохраняет совместимость исходного проекта и не переносит проект с одной версии Minecraft API на другую.

Для AperEvent проверены начало/остановка события, а не полный игровой цикл с игроками. Для LegendaryWeapon не проверены все способности оружия в бою. Для финального Xeron проверены старт, переименованные Mixins, ресурсы и загрузка классов, а не все игровые модули, gameplay и сетевые взаимодействия. Проверка declared members не исполняет каждое тело метода; исполнение отдельных сложных конструкций покрывают JVM behavior tests.

В Paper 26.3 лог содержит OSHI exception при чтении неисправных Windows performance counters; сервер продолжил работу, fixture assertions прошли, процесс завершился с кодом 0. В остальных итоговых логах ошибок загрузки плагина/мода, ASM/JVM verifier и Mixin application не обнаружено.

Динамические контракты, которые нельзя вывести из JAR и переданных зависимостей, настраиваются через keep/exclude. Подробные правила и границы преобразований описаны в корневом README.
