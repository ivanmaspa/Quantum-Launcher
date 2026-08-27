# Quantum Launcher

**Quantum Launcher** — быстрый, современный и лёгкий лаунчер Minecraft. Это форк [Modrinth App](https://github.com/modrinth/code) (через [AstralRinth](https://github.com/DIDIRUS4/AstralRinth)), доработанный для максимальной производительности, оффлайн-авторизации и удобного UI.

Разрабатывает [@ivanmaspa](https://github.com/ivanmaspa).

## Возможности

- **Modrinth Integration:** просмотр, установка и обновление модов, модпаков и ресурспаков через Modrinth API.
- **Гибкая авторизация:**
  - Лицензия: Microsoft (Mojang) / Modrinth
  - Оффлайн: локальная оффлайн-авторизация, Ely.by (скины) — *in progress*
- **Без телеметрии и рекламы:** принудительное отключение сбора статистики и персонализированной рекламы (hard patch).
- **Discord Rich Presence:** статус игры в Discord.
- **Modern UI:** чистый и интуитивный интерфейс на Vue 3 / Tauri 2.

## Разработка

Проект собран на **Rust + Tauri 2** с фронтендом на **Vue 3** (pnpm + Turbo + Cargo workspace).

### Требования

- Rust & Cargo (stable)
- Node.js (>= 20)
- pnpm 9

### Установка и запуск

```bash
# Клонировать репозиторий
git clone https://github.com/ivanmaspa/Quantum-Launcher.git
cd Quantum-Launcher

# Установить зависимости
pnpm install

# Запуск в режиме разработки
pnpm app:dev   # или: pnpm turbo run dev --filter=@modrinth/app
```

### Сборка

```bash
pnpm app:build
```

## Структура

- `apps/app` — Tauri-оболочка (Rust), GUI-слой
- `apps/app-frontend` — Vue-интерфейс лаунчера
- `apps/daedalus_client` — CLI генерации launcher-meta
- `packages/app-lib` — ядро этих (профили, запуск, авторизация, Modrinth API)
- `packages/daedalus` — модели метаданных Minecraft/загрузчиков
- `packages/ui`, `packages/utils`, `packages/assets` — UI-библиотека и утилиты

## TODO (планы)

- [ ] Интеграция **Ely.by** (скины + пиратская авторизация)
- [ ] Создание серверов из лаунчера
- [ ] Настоящий логотип и иконки Quantum
- [ ] Регистрация Discord Application и замена App ID в `state/discord.rs`
- [ ] Настроить релизы GitHub для авто-апдейтера

## Лицензия

Проект является форком [Modrinth App](https://github.com/modrinth/code) и распространяется под лицензией **GPL-3.0**. Брендинг и товарные знаки Modrinth не используются. Большое спасибо команде Modrinth и авторам AstralRinth за открытый исходный код-основу.
