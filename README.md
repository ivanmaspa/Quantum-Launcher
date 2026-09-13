# Quantum Launcher

**Quantum Launcher** — быстрый, современный и лёгкий [лаунчер](https://www.minecraftl.net) Minecraft на основе **HMCL** (Hello Minecraft! Launcher), переименованный в **Quantum**.

Форк распространяется под лицензией **GPL-3.0** — как и оригинальный [HMCL](https://github.com/HMCL-dev/HMCL).

## Возможности

- Запуск всех версий Minecraft Java Edition.
- Поддержка загрузчиков модов: **Forge**, **Fabric**, **Quilt**, LiteLoader.
- Поиск и установка модов: **Modrinth**, **CurseForge**.
- Встроенная авторизация: Microsoft, offline, **Ely.by**.
- Автономная установка Java и извлечение ассетов.
- Настройка параметров запуска, памяти и графики.
- Просмотр скинов и плащей прямо в лаунчере.

## Возможности форка

### Ely.by: вход и скины

- **Вход через аккаунт Ely.by** — ник или e-mail + пароль.
- **Двухфакторная аутентификация** — пароль передаётся в формате `пароль:код`.
- **Скины Ely.by в игре** — скины Ely.by подставляются из `skinsystem.ely.by` через **authlib-injector**, поэтому работают не только в лаунчере, но и внутри игры на серверах с поддержкой authlib-injector.

### Страница Bedrock (Linux)

На Linux доступна отдельная страница **Bedrock** для управления [Minecraft Bedrock Edition](https://minecraft-linux.github.io) через рантайм **mcpelauncher**:

- показывает статус установки рантайма, ссылку на установку через `flatpak` (`io.mrarm.mcpelauncher`);
- отображает список **установленных** версий Bedrock с действиями «Запустить» и «Удалить»;
- показывает **онлайновый** список доступных версий из versiondb mcpelauncher (`versions.<arch>.json.min`);
- загрузку и управление версиями делегирует менеджеру версий `mcpelauncher-ui-qt`.

**Ограничения Bedrock:**

- работает **только на Linux** (архитектуры x86_64 / arm64 / x86);
- для входа в Minecraft Bedrock требуется **аккаунт с покупкой в Google Play** (как и в оригинальном mcpelauncher);
- **Windows не поддерживается**.

## Сборка

Требуется **JDK 21** (например, `JAVA_HOME=/usr`).

```bash
JAVA_HOME=/usr ./gradlew :HMCL:build
```

Результат — в `HMCL/build/libs/`:

| Артефакт | Платформа |
|---|---|
| `Quantum-3.17.SNAPSHOT.jar` | кросс-платформенный (Java 17+, нужен JavaFX) |
| `Quantum-3.17.SNAPSHOT.sh` | самодостаточный загрузчик для Linux |
| `Quantum-3.17.SNAPSHOT.exe` | нативный запускатель для Windows |
| `Quantum-3.17.SNAPSHOT.deb` | пакет для Debian/Ubuntu |

Для Windows достаточно использовать `Quantum-3.17.SNAPSHOT.exe` из сборки — он запускается без отдельной установки Java.

## Лицензия

- Форк **HMCL** под лицензией **GPL-3.0** (см. `LICENSE`).
- Большое спасибо команде [HMCL](https://github.com/HMCL-dev/HMCL) за открытый исходный код-основу, команде [Ely.by](https://ely.by) за сервис скинов и авторизации и проекту [minecraft-linux](https://minecraft-linux.github.io) за mcpelauncher.