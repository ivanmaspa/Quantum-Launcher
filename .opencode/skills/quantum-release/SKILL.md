---
name: quantum-release
description: Сборка, commit/push и создание GitHub-релизов Quantum Launcher. Использовать при подготовке релиза (сборка бинарников), публикации тега/release на GitHub, настройке авто-апдейтера, и при работе с репозиторием ivanmaspa/Quantum-Launcher (Minecraft-лаунчер на Rust/Tauri, форк Modrinth App / AstralRinth).
---

# Quantum Launcher — релизы и автоматизация

Этот скилл описывает повторяемые операции для проекта **Quantum Launcher**
(репозиторий `ivanmaspa/Quantum-Launcher`) — Minecraft-лаунчера на
Rust + Tauri 2 (бэкенд) и Vue 3 (фронтенд), являющегося форком Modrinth App
через AstralRinth.

## Контекст проекта

- Монорепо: `pnpm` + `turbo` + Cargo workspace.
- Лаунчер: `apps/app` (theseus_gui, Tauri), `apps/app-frontend` (Vue),
  ядро `packages/app-lib` (theseus).
- Авто-апдейтер лаунчера в `apps/app-frontend/src/helpers/update.js` читает
  release-артефакты с GitHub (`ivanmaspa/Quantum-Launcher/releases/latest`),
  формирует ветки из `/branches`, ищет установщики по ОС:
  - macOS: `.dmg`
  - Windows: `.msi`
  - Linux: `.deb`
  - игнорирует build-префиксы: `dev`, `nightly`, `dirty*`.
- Учётка GitHub: `ivanmaspa`. Доступ — через GitHub fine-grained PAT
  (`github_pat_*`). Использовать токен только через переменную окружения,
  НЕ хардкодить и не коммитить.

## Правила безопасности (критично)

- Никогда не коммитить секреты: `apps/daedalus_client/.env` содержит S3/Cloudflare
  токены — он уже в `.gitignore`, но при `git add` убедиться, что он исключён.
- GitHub-токен передавать только через env (`export GH_PAT=...`) в команде,
  не записывать в файлы проекта и не оставлять в истории.
- Если токен был скомпрометирован (попал в чат/логи) — рекомендовать
  пользователю отозвать его на https://github.com/settings/tokens.

## Сборка лаунчера

```bash
# Установка зависимостей
pnpm install

# Сборка (frontend + tauri) — создаёт биндинги и бинарники
pnpm app:build
# эквивалент: pnpm turbo run build --filter=@modrinth/app

# Проверка типов frontend без сборки
pnpm --filter=@modrinth/app-frontend tsc:check

# Rust check (не собирает биндинги tauri, быстрее; требует pnpm install заранее)
cargo check --workspace
```

Бинарники после `pnpm app:build` обычно в `apps/app/src-tauri/target/release/`
(проверить фактический путь по структуре — может быть `apps/app/target/`).

## Commit / push

```bash
git add -A
# НЕ добавлять: *.env, node_modules, target, dist, .turbo (покрыто .gitignore)
git status   # проверить, что .env и секреты не попали в staging
git commit -m "<краткое описание>"
git push
```

## Создание GitHub-релиза (для авто-апдейтера)

Авто-апдейтер ожидает, что:
1. Есть **тег/релиз** версии, начинающийся с `v` (например `v0.9.204`),
   совпадающий с номером версии приложения (см. `apps/app/tauri.conf.json`).
   `update.js` сравнивает `remoteVersion.startsWith('v' + localVersion)`.
2. К релизу прикреплены установщики: `.dmg` (macOS), `.msi` (Windows),
   `.deb` (Linux) — без префиксов `dev`/`nightly`/`dirty`.

Создать release через GitHub API (gh не всегда установлен):

```bash
export GH_PAT='github_pat_...'

# 1. Тег+релиз
curl -sS -X POST \
  -H "Authorization: Bearer $GH_PAT" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/ivanmaspa/Quantum-Launcher/releases \
  -d '{
    "tag_name": "v0.9.204",
    "target_commitish": "main",
    "name": "Quantum Launcher v0.9.204",
    "body": "Описание релиза",
    "draft": false,
    "prerelease": false
  }'
```

2. Загрузить артефакты (установщики) в release. Получить `upload_url`
   из ответа выше (форма `https://uploads.github.com/repos/.../releases/{id}/assets{?name,label}`),
   затем для каждого файла:
```bash
ASSET="/path/to/Quantum-Launcher_0.9.204_x64-setup.msi"
curl -sS -X POST \
  -H "Authorization: Bearer $GH_PAT" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@$ASSET" \
  "https://uploads.github.com/repos/ivanmaspa/Quantum-Launcher/releases/{ID}/assets?name=$(basename "$ASSET")"
```

## Интеграция Ely.by (TODO)

Формат авторизации Quantum: лицензия (Microsoft/Modrinth) / пиратка
(Ely.by + оффлайн). Ely.by-интеграция ещё не реализована — добавить точку
входа рядом с `offline_auth` в `packages/app-lib/src/api/minecraft_auth.rs`
и соответствующую команду в `apps/app/src/api/auth.rs`, плюс UI-элемент
в `apps/app-frontend/src/components/ui/AccountsCard.vue`. Смотреть на
существующую Microsoft-авторизацию (`state/minecraft_auth.rs`) как образец.

## Discord App ID (TODO)

В `packages/app-lib/src/state/discord.rs` стоит заглушка App ID
(`1190718475832918136` — от AstralRinth). Заменить на собственный Discord
Application ID для Quantum и переименовать `quantum_logo` ассеты под свой
Discord-аpp.

## Прочее

- Ребрендинг выполнялся вручную: `AstralRinth`→`Quantum` (tauri.conf.json,
  Cargo.toml, main.rs, discord.rs, dirs.rs, App.vue, AppSettingsModal.vue,
  RunningAppBar.vue, Index.vue, index.html, Info.plist, assets/quantum-logo.svg).
- При портировании большего объёма кода использовать этот скилл как карту
  точек интеграции.
