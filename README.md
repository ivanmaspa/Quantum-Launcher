# Quantum Launcher

**Quantum Launcher** — быстрый, современный и лёгкий лаунчер Minecraft на базе [HMCL](https://github.com/HMCL-dev/HMCL) (Hello Minecraft! Launcher), переименованный в **Quantum**.

## Возможности

- Запуск всех версий Minecraft Java Edition.
- Поддержка загрузчиков: **Forge**, **Fabric**, **Quilt**, LiteLoader.
- Поиск и установка модов: **Modrinth**, **CurseForge**.
- Встроенная авторизация: Microsoft, offline, **Ely.by**.
- Автономная установка Java и извлечение ассетов.
- Настройка параметров запуска, памяти и графики.
- Просмотр скинов и плащей прямо в лаунчере.

## Особенности форка: Ely.by

- **Вход через аккаунт Ely.by** — ник или E-mail + пароль.
- Поддержка двухфакторной аутентификации: пароль передаётся в формате `пароль:код`.
- **Скины Ely.by для офлайн-аккаунтов** — данные скинов тянутся с `skinsystem.ely.by`.

## Сборка

Требуется JDK 21:

```bash
./gradlew :HMCL:build
```

Результат — в `HMCL/build/libs/` (`Quantum-3.17.SNAPSHOT.jar` и сопутствующие артефакты).

## Лицензия

Форк [HMCL](https://github.com/HMCL-dev/HMCL) под лицензией **GPL-3.0**.

Большое спасибо команде HMCL за открытый исходный код-основу и команде Ely.by за сервис скинов и авторизации.
