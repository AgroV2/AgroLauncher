# AgroLauncher

A cross-platform Minecraft launcher written in Java, with English and Russian UI, automatic Java management, loader installation, Modrinth integration (mods, resource packs, shaders and modpacks), isolated instances, independent mod builds, backups and repair tools, and extensive appearance customization including Liquid Glass and Material You styles.

The launcher targets **Windows** and **Linux** and includes macOS-aware code paths. Maven produces a self-contained executable JAR and can package a Windows x64 portable distribution with a bundled Java runtime.

## Features

- Releases, snapshots, and old versions can be shown independently.
- Vanilla, Fabric, Quilt, Forge, NeoForge, and OptiFine installation and launch.
- Automatic Java requirement detection, Adoptium runtime download, AUTO/managed/system/custom Java modes, RAM and launch-argument controls, and launch preflight checks.
- **Isolated instances**: every Minecraft + loader + loader-version combination gets its own game directory, managed on the dedicated Instances screen (play, open folder, delete) together with independent named mod builds.
- Offline profiles, Ely.by password/2FA authentication, and Ely.by OAuth2 with PKCE S256 (tokens refresh automatically before they expire).
- Tokens are stored in Windows Credential Manager, macOS Keychain, or Linux Secret Service (`secret-tool`), with migration from legacy plaintext storage where possible.
- A shared download manager beside the game console shows progress and speed, supports pause/resume/cancel, appears when downloads start, and auto-hides after activity finishes.
- Resumable partial downloads use HTTP Range when supported, with parallel transfers, retries, limits, and hash verification; corrupted partial files are discarded on hash mismatch instead of poisoning retries.
- **Modrinth modpacks**: browse the catalog, install modpacks as separate instances, import local `.mrpack` files (with a visible badge, unpack button and progress bar), and export your current setup as a `.mrpack`.
- Modrinth browsing and installation for mods, resource packs, and shaders, including optional automatic dependency installation (downloads run in parallel).
- **Quick server join**: enter an address on the Play tab and PLAY connects via `--server/--port`. The MargyCraft button joins the MargyCraft server on the latest vanilla release in one click.
- Launch diagnostics detect common Java, memory, dependency, Mixin, native/OpenGL, archive, authentication, and network failures. Sanitized TXT/JSON reports redact sensitive data.
- Installation manifests track managed files; Repair re-downloads managed content, reinstalls loaders, uses staged transactions/rollback, and creates a verified backup first — backups can be browsed and restored from Settings → Backups.
- Nine theme presets, custom accent color, gradients (including Material You and four new presets), and custom gradients with up to ten colors.
- **Interface styles as preset cards**: Default, Material You, and Liquid Glass — translucent theme-tinted glass panels. Theme and gradient changes recolor the entire UI instantly, without a restart.
- PNG/JPG/GIF backgrounds, blur (FPS-capped animation), dimming, panel opacity, and separate window opacity. The frameless window can be dragged and resized.
- Proxy support (HTTP/SOCKS) for launcher traffic; GitHub release checks with staged updates, optional SHA-256 verification, rollback and in-app updates from the About screen.
- English and Russian UI with live language switching; all service messages are localized.

## Java compatibility fallback

| Minecraft | Java |
|---|---|
| 1.16.5 and older | 8 |
| 1.17–1.20.4 | 17 |
| 1.20.5–1.21.x | 21 |
| 26.x and newer | 25 |

The exact requirement is taken from the version manifest when available.

## Build and run

Requirements: JDK 17+, Maven 3.8+.

```bash
mvn clean package
java -jar target/AgroLauncher.jar
```

The shaded JAR (~6 MB) contains runtime dependencies. Maven also includes Windows x64 portable packaging with a bundled Java 17 runtime.

### Command-line arguments

```
--help, -h                 help
--version                  launcher version
--data-dir <path>          custom data directory (portable mode)
--dry-run <version> [nick] prepare a version and print the launch command without starting the game
```

## Configuration notes

- Ely.by OAuth requires `elyOAuthAuthorizationEndpoint`, `elyOAuthTokenEndpoint`, `elyOAuthProfileEndpoint`, and `elyOAuthClientId` in `settings.json`; `elyOAuthScope`, `elyOAuthCallbackPort`, and `elyOAuthTimeoutSeconds` are optional. **OAuth endpoints must point to `ely.by`** — any other domain is rejected to prevent phishing through a tampered settings file. The registered redirect URI must allow `http://127.0.0.1:<port>/oauth/callback`; port `0` selects a free port.
- `elySkinUrlTemplate` supports `{uuid}` and `{username}` and is used only when the profile has no skin URL.
- `proxyMode` (`NONE`/`HTTP`/`SOCKS`), `proxyHost` and `proxyPort` route launcher downloads through a proxy.
- `quickPlayServer` remembers the quick-join address; `updateAutoCheck` toggles the startup update check.
- Updates use `githubRepository` (default: `AgroV2/AgroLauncher`).

## Where files are stored

| System | Path |
|---|---|
| Windows | `%APPDATA%\.agrolauncher` |
| Linux | `~/.agrolauncher` (or `$XDG_DATA_HOME/agrolauncher`) |
| macOS | `~/Library/Application Support/agrolauncher` |

```
.agrolauncher/
├── minecraft/                 shared game files (versions, libraries, assets)
├── instances/installations/   isolated game directories per version+loader
├── mod-builds/                independent named mod builds
├── backups/                   verified launcher backups
├── runtimes/                  automatically downloaded JREs
├── authlib/                   authlib-injector.jar for Ely.by
├── cache/                     metadata, icons, installers, downloaded modpacks
├── logs/                      sanitized launch session logs
├── transactions/              repair staging area
├── settings.json              settings (owner-only permissions)
└── accounts.json              non-secret account metadata only
```

## First launch

1. Open **Accounts** → enter a nickname → add a profile, or sign in via Ely.by.
2. Open **Versions** → pick a Minecraft version and a mod loader.
3. Optionally open **Mods** → the Modpacks tab to install a Modrinth modpack, or press "Import .mrpack" for a local file — modpacks appear on the **Instances** screen and are selected automatically.
4. Set RAM and other options in **Settings** (the Network card holds the proxy).
5. Return to **Play** and press PLAY. The mod loader is installed automatically on first launch of a combination.

Mods and modpacks live in the installation's own `mods` directory; the Mods screen always shows which directory is active, warns about `.mrpack` files dropped into `mods`, and offers one-click switching to the installation that has your mods.

## Windows x64 EXE: cross-building on Linux

The `windows-portable` Maven profile builds the Windows launcher with Launch4j, downloads a pinned Eclipse Temurin JRE 17 for Windows x64 and places it next to the JAR. End users do not need Java installed.

```bash
mvn clean verify -Pwindows-portable
```

Results: a portable folder `target/AgroLauncher-Windows-x64/` (with `AgroLauncher.exe`, `AgroLauncher.jar` and `runtime/`) and `target/AgroLauncher-Windows-x64-Portable.zip`. This is not a single self-contained EXE: `AgroLauncher.exe` uses the adjacent `AgroLauncher.jar` and `runtime/`. The profile does not use `jpackage`, Windows, or WiX.

Data from the previous launcher generation (`.emeraldlauncher`) is migrated automatically on first start.

## Important limitation

The launcher works **only with `online-mode=false` servers** (offline servers). Offline profiles do not pass Mojang license verification and cannot join official servers; the launcher does not circumvent Mojang/Microsoft authentication — it only generates the same local UUID a server generates in `online-mode=false`. For skins and third-party account services use **Ely.by**, a legal third-party provider.

## Architecture

```
by.agro.launcher
├── Main                    entry point, CLI modes
├── LauncherContext         shared service context
├── core/                   platform, paths, settings, downloader (DNS-pinned
│                           HTTPS, resume, verification cache), JSON, hashes
├── version/                Mojang manifests, library rules, version chain
│                           inheritance, assets, natives
├── loaders/                Fabric, Quilt, Forge, NeoForge, OptiFine installers
├── auth/                   offline profiles, Ely.by, OAuth (ely.by-only
│                           endpoints), authlib-injector, credential stores
├── jvm/                    Java detection and download, archive extraction
├── launch/                 launch command assembly, process, preflight
├── modrinth/               catalog client, mod installer, modpacks (.mrpack
│                           import/export), icon cache
├── mods/                   mod file management, named mod builds
├── integrity/              installation manifests (SHA-256, provenance)
├── repair/                 staged repair transactions with rollback
├── backup/                 verified backups with restore
├── cleanup/                reachability and cache cleanup plans
├── diagnostics/            launch sessions, log analysis, redaction
├── offline/                offline-mode policy
├── update/                 GitHub release checks, staged self-update
├── i18n/                   RU/EN texts (with a MessageFormat cache)
└── ui/                     themes and palettes, interface styles, background
                            with blur, window, screens, instances/backups UI
```

**Key technical decisions**

- **Version inheritance.** Loader profiles reference the vanilla version via `inheritsFrom`; the resolver merges the chain, deduplicating libraries only across manifests to keep OS-specific LWJGL builds in 1.7–1.12.
- **Vanilla client verification before Forge installs.** The vanilla jar is checked against Mojang's hash and re-downloaded when modified; intermediate files are cleaned and the install retried once on processor hash mismatches.
- **Three argument formats**: `arguments` (1.13+), `minecraftArguments` (1.7–1.12), and pre-1.6 with `${auth_session}`.
- **Own tar.gz extractor** for JRE archives with symlink handling and path-traversal protection.
- **Verification cache.** Successfully verified files are remembered for the session, so repeated launches do not re-hash libraries and assets; large checks run in parallel.
- **Dynamic palettes and live theming.** Colors derive from a background+accent pair in HSB; on theme or gradient change, colors (including derived accent shades and borders) are migrated across the whole component tree instantly.
- **Liquid Glass rendering** is cached per card and rebuilds only on resize/theme/opacity changes; window resize uses a simplified fast mode.
- **Security baseline.** HTTPS-only downloads with pre-validated addresses (DNS-rebinding protection), ely.by-only OAuth endpoints, zip-bomb limits for modpacks and natives, response size limits, owner-only permissions for settings and accounts, and HTML-neutralized remote strings.
- **Localization lives in code**; screens are rebuilt on language switch, which is more reliable than walking the component tree.

## Dependencies

- [FlatLaf](https://www.formdev.com/flatlaf/) 3.6 — modern Swing look and feel
- [Gson](https://github.com/google/gson) 2.11.0 — JSON handling
- [JNA](https://github.com/java-native-access/jna) 5.16 — Windows Credential Manager, macOS Keychain, memory detection

The mod catalog works through the open [Modrinth API](https://docs.modrinth.com/) — no key required. All libraries are bundled into the final JAR.

## License

Code provided as is. Minecraft is a trademark of Mojang AB; the launcher is not affiliated with Mojang or Microsoft.

---

# AgroLauncher (рус.)

Кроссплатформенный лаунчер Minecraft на Java: русский и английский интерфейс, автоматическое управление Java, загрузчики модов, каталог Modrinth (моды, ресурспаки, шейдеры и модпаки), изолированные инстансы, независимые мод-сборки, бэкапы и восстановление, широкая настройка оформления, включая стили Liquid Glass и Material You.

Работает на **Windows** и **Linux** (код также учитывает macOS). Maven собирает один исполняемый JAR без внешних зависимостей и умеет паковать Windows x64 portable-дистрибутив со встроенной Java.

## Возможности

**Загрузчики модов — все версии игры**

| Загрузчик | Версии | Как устанавливается |
|---|---|---|
| Fabric | 1.14+ | Fabric Meta API |
| Quilt | 1.14+ | Quilt Meta API |
| Forge | 1.1 – новейшие | официальный установщик в headless-режиме |
| NeoForge | 1.20.2+ | официальный установщик в headless-режиме |
| OptiFine | все | патчинг через `optifine.Patcher` либо как мод к Forge |

**Инстансы и модпаки**

- Каждая связка «версия + загрузчик + версия загрузчика» — своя игровая папка; экран **Инстансы** управляет установками и мод-сборками (запустить, открыть папку, удалить).
- **Модпаки Modrinth**: каталог, установка отдельными инстансами, импорт локальных `.mrpack` (бейдж «модпак», кнопки «Распаковать»/«Импорт», шкала прогресса) и экспорт текущей сборки в `.mrpack`.
- Экран **Моды** всегда показывает, в какую папку ставятся моды, предупреждает о `.mrpack` в папке mods и переключает на установку с модами в один клик.

**Аккаунты**

- **Оффлайн-профиль** — UUID вычисляется тем же алгоритмом, что использует сервер при `online-mode=false`.
- **Ely.by** — логин/пароль и 2FA; скины и плащи через authlib-injector.
- **Ely.by OAuth2 + PKCE S256** — вход через системный браузер, автоматическое обновление токенов. Endpoint'ы настраиваются в `settings.json` и **разрешены только на домене `ely.by`**.
- Токены — в системном хранилище: Windows Credential Manager, macOS Keychain, Linux Secret Service (`secret-tool`).

**Управление Java**

| Версия Minecraft | Требуемая Java |
|---|---|
| 1.16.5 и старше | 8 |
| 1.17 – 1.20.4 | 17 |
| 1.20.5 – 1.21.x | 21 |
| 26.x и новее | 25 |

Точное требование берётся из манифеста версии; недостающие JRE скачиваются с Adoptium.

**Прочее**

- Быстрый вход на сервер (поле на вкладке «Играть») и кнопка **MargyCraft** — вход на `craft.narez.xyz` на свежей vanilla-версии в один клик.
- Менеджер загрузок (пауза/возобновление/отмена, скорость, прогресс), докачка через HTTP Range, параллельные загрузки; повреждённые partial-файлы отбрасываются при несовпадении хеша.
- Диагностика запусков (Java, память, зависимости, Mixin, OpenGL, архивы, сеть) с обезличенными отчётами; Repair с транзакциями и откатом; вкладка **Бэкапы** с восстановлением.
- Девять тем, свой цвет, градиенты (включая Material You и 4 новых пресета), стили интерфейса **Обычный / Material You / Liquid Glass** — стекло подстраивается под тему; смена темы перекрашивает всё мгновенно.
- Фон PNG/JPG/GIF с размытием и ограничением FPS анимации; окно без рамки можно перетаскивать и растягивать.
- Прокси HTTP/SOCKS; проверка обновлений GitHub с установкой прямо из вкладки «О лаунчере» и тумблером автопроверки.
- Русский и английский, переключение без перезапуска; все сервисные сообщения локализованы.

## Сборка и запуск

Нужны **JDK 17+** и **Maven 3.8+**.

```bash
git clone <репозиторий>
cd agro-launcher
mvn clean package
java -jar target/AgroLauncher.jar
```

Готовый файл: `target/AgroLauncher.jar` (~6 МБ).

```
--help, -h                 справка
--version                  версия
--data-dir <путь>          свой каталог данных (портативный режим)
--dry-run <версия> [ник]   подготовить версию и показать команду запуска
```

## Где хранятся файлы

| Система | Путь |
|---|---|
| Windows | `%APPDATA%\.agrolauncher` |
| Linux | `~/.agrolauncher` (или `$XDG_DATA_HOME/agrolauncher`) |
| macOS | `~/Library/Application Support/agrolauncher` |

```
.agrolauncher/
├── minecraft/                 общие игровые файлы (versions, libraries, assets)
├── instances/installations/   изолированные игровые папки по версиям
├── mod-builds/                независимые мод-сборки
├── backups/                   проверенные бэкапы
├── runtimes/                  скачанные JRE
├── authlib/                   authlib-injector.jar
├── cache/                     метаданные, иконки, установщики, модпаки
├── logs/                      журналы запусков
├── transactions/              staging для Repair
├── settings.json              настройки (доступ только владельцу)
└── accounts.json              только несекретные метаданные аккаунтов
```

## Первый запуск

1. **Аккаунты** → ник → «Добавить профиль», либо вход через Ely.by.
2. **Версии** → версия Minecraft и загрузчик.
3. При желании **Моды** → «Модпаки» или «Импорт .mrpack» — модпак появится в **Инстансах** и выберется автоматически.
4. **Настройки** → память, сеть (прокси), интерфейс (язык, тема, стиль, фон).
5. **Играть** → «ИГРАТЬ». Загрузчик ставится автоматически при первом запуске связки.

## Windows x64 EXE: кросс-сборка на Linux

```bash
mvn clean verify -Pwindows-portable
```

Результат: `target/AgroLauncher-Windows-x64/` (`AgroLauncher.exe` + `AgroLauncher.jar` + `runtime/`) и архив `AgroLauncher-Windows-x64-Portable.zip`. Это не один самодостаточный EXE: он использует соседние JAR и рантайм. Профиль не использует `jpackage`, Windows и WiX.

Данные предыдущего поколения лаунчера (`.emeraldlauncher`) переносятся автоматически.

## Важное ограничение

Лаунчер работает **только с серверами `online-mode=false`**. Оффлайн-профиль не проходит проверку лицензии Mojang; лаунчер не обходит платную авторизацию — он лишь генерирует локальный UUID, как это делает сам сервер. Для скинов и сторонних сервисов аккаунтов используйте **Ely.by**.

## Архитектура

```
by.agro.launcher
├── Main                    точка входа, режимы CLI
├── LauncherContext         общий контекст сервисов
├── core/                   платформа, пути, настройки, загрузчик (HTTPS
│                           с защитой от DNS-rebinding, докачка, кэш проверок)
├── version/                манифесты Mojang, правила библиотек, наследование
│                           версий, ассеты, нативные библиотеки
├── loaders/                установщики Fabric, Quilt, Forge, NeoForge, OptiFine
├── auth/                   оффлайн, Ely.by, OAuth (только ely.by),
│                           authlib-injector, системные хранилища
├── jvm/                    определение и скачивание Java, распаковка архивов
├── launch/                 сборка команды запуска, процесс, preflight
├── modrinth/               клиент каталога, установщик модов, модпаки
│                           (.mrpack импорт/экспорт), кэш иконок
├── mods/                   управление модами, мод-сборки
├── integrity/              манифесты установки (SHA-256, provenance)
├── repair/                 транзакции восстановления с откатом
├── backup/                 проверенные бэкапы с восстановлением
├── cleanup/                планы очистки
├── diagnostics/            сессии запуска, анализ логов, редактирование
├── offline/                политика оффлайн-режима
├── update/                 проверки GitHub, самообновление
├── i18n/                   тексты RU/EN (с кэшем MessageFormat)
└── ui/                     темы и палитры, стили интерфейса, фон с размытием,
                            окно, экраны, инстансы и бэкапы
```

**Ключевые технические решения**

- **Наследование версий** с дедупликацией библиотек только между манифестами цепочки (иначе теряется LWJGL в 1.7–1.12).
- **Проверка ванильного клиента перед установкой Forge** и один повтор после очистки при несовпадении промежуточных хешей.
- **Три формата аргументов**: `arguments`, `minecraftArguments` и pre-1.6.
- **Собственный распаковщик tar.gz** с защитой от path traversal.
- **Кэш проверок**: хеш успешно проверенного файла не пересчитывается при повторных запусках; большие проверки идут параллельно.
- **Живая смена темы**: цвета (включая производные акценты и рамки) мигрируют по всему дереву компонентов при смене темы и градиента.
- **Liquid Glass** кэширует отрисовку и пересобирает её только при изменении размера/темы/прозрачности; при ресайзе окна — упрощённый режим.
- **Безопасность**: только HTTPS с проверенными адресами, OAuth только на ely.by, лимиты против zip-бомб, лимиты размеров ответов, права «только владелец» для настроек и аккаунтов, нейтрализация HTML в удалённых строках.
- **Локализация в коде**; при смене языка экраны пересобираются целиком.

## Зависимости

- [FlatLaf](https://www.formdev.com/flatlaf/) 3.6 — современный внешний вид Swing
- [Gson](https://github.com/google/gson) 2.11.0 — работа с JSON
- [JNA](https://github.com/java-native-access/jna) 5.16 — Windows Credential Manager, macOS Keychain, определение памяти

Каталог модов работает через открытый [API Modrinth](https://docs.modrinth.com/) — ключ не требуется. Всё встроено в итоговый JAR.

## Лицензия

Код предоставляется как есть. Minecraft — торговая марка Mojang AB; лаунчер не связан с Mojang и Microsoft.
