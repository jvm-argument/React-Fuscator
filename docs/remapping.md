# Полный ремап и обязательные имена

Раньше public/protected и virtual методы сохранялись слишком широко. Дополнительно динамический reflection в стороннем коде сохранял все member names, а обычные строковые литералы вроде `"unhook"` ошибочно считались reflection-контрактом. Эти причины устранены: переименовываются public поля и собственные virtual/interface семейства, reflection анализируется по получателю Class и аргументам вызова. Сохранение имени класса больше не означает автоматическое сохранение всех его полей/методов.

В GUI полный режим: **Keep public API** выключен, **Rename Mixins** и **Scatter packages** включены, **Keep serialization ABI** выключен. В CLI:

```powershell
java -jar dist/React-Fuscator.jar obfuscate input.jar -o protected.jar -p extreme -l libraries --rename-serialization
```

Или загрузите `examples/full-remapping.json`, добавив зависимости своей платформы. Первая настройка позволяет public/protected ремап, последняя разрешает менять имена enum/record/Serializable классов. Имена в ранее сохранённых сериализованных данных могут потребовать миграции.

У проверенного Xeron переименованы все 611 исходных классов, включая 52 класса Mixin-пакета, 4238 методов и 3271 поле. В mapping есть переименования `initializer`, `moduleStorage`, `initStorage`, `unhook`. Старой структуры class-пакетов `ru/whylol` в новом JAR нет. Mixin config и refmap обновлены одновременно с bytecode.

Разброс сохраняет группы, которым нужны package-private/protected доступы или nestmate-доступ. Случайное разнесение каждой пары таких классов нарушило бы JVM access checks. Mixin-пакеты имеют отдельные общие корни, чтобы loader не принимал обычные классы за Mixins. Package-relative resources следуют своей группе.

Не каждое оставшееся имя является недоработкой ремапа. Сохраняются:

- внешние callbacks, например `onEnable`, `onInitialize`, `run`, если они реализуют контракт внешнего API;
- Mixin selectors/accessors и аннотированные member contracts;
- Enum constant names, record/annotation members и Java serialization fields/hooks;
- JNA Structure/Union fields и Library/Callback methods, JNI native names;
- подтверждённые reflection lookup names, явно заданные keep/exclude и имена, используемые переданными внешними зависимостями.

JNA-проверка на реальном клиенте выявила связь `getFieldOrder()` с исходными именами полей Discord-структур. Теперь эти поля сохраняются адресно; остальные поля проекта продолжают переименовываться. Просто заменить их все было бы недостаточно для рабочего мода.

Для протокола, читающего имена через `Field.getName()`/JavaBeans или автоматически сериализующего имена в JSON, добавьте `keepMembers`, например `com/example/config/**#**`. Для API, используемого отдельным неизвестным плагином, добавьте `keep` либо передайте этот плагин как library. Неопределённые runtime-контракты нельзя надёжно вывести только из bytecode.

Extreme `classnoise` создаёт обычные классы с живыми входящими вызовами, вынесенными реализациями или арифметическими адаптерами. Дополнительные поля/методы варьируются, классы получают остальные трансформеры и не помечаются `ACC_SYNTHETIC`. Удалять их по отсутствию ссылок уже нельзя. Это усложняет отделение cover code, но не гарантирует невозможность его распознавания.
