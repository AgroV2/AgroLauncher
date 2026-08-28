English
# AgroLauncher

A cross-platform Minecraft launcher written in Java with Russian and English interfaces, a Modrinth mod catalog, nine color themes with custom accent colors, customizable backgrounds, and Ely.by authentication.

Works on Windows, Linux, and macOS. Builds into a single executable JAR with no external dependencies.

---

## Features

**Mod Loaders — all game versions**

| Loader   | Versions     | Installation                                      |
| -------- | ------------ | ------------------------------------------------- |
| Fabric   | 1.14+        | Fabric Meta API                                   |
| Quilt    | 1.14+        | Quilt Meta API                                    |
| Forge    | 1.1 – latest | Official installer in headless mode               |
| NeoForge | 1.20.2+      | Official installer in headless mode               |
| OptiFine | all          | Patching via `optifine.Patcher` or as a Forge mod |

**Accounts**

* **Offline profile** — nickname only. The UUID is calculated using the same algorithm Minecraft servers use with `online-mode=false`, so server progress is preserved between launches.
* **Ely.by** — login and password authentication, two-factor authentication support, skins and capes through authlib-injector (downloaded automatically).

**Mod Catalog**

Built-in Modrinth browser: search with filters by loader, game version, and category, sorting by downloads or date, and one-click installation. Required dependencies are downloaded automatically — for example, installing Create will also automatically install Fabric API.

**Interface Language**

Russian and English are supported and can be switched in the settings without restarting the launcher. On the first launch, the language is detected based on the system locale.

**Appearance**

The logo and window icon are rendered as vectors and colored using the accent color of the active theme.

Nine built-in themes: Emerald Dark, Midnight Ember, Noir Rose, Vault Gold, Abyss Frost, Crimson Chalk, ElectricPurple Cream, RoyalPurple Gold, DeepForest Flame.

**QT Theme** — the launcher detects the current QT appearance and applies it to the launcher.

The **Custom Color** mode opens a color wheel where you can choose an accent color. The rest of the palette is calculated automatically with readability taken into account. The theme is applied immediately without restarting.

The background image is selected by specifying a file path (PNG, JPG, GIF). You can adjust blur with a customizable radius, darkness, and panel opacity. Animated GIFs are supported; when blur is enabled, a static first frame is displayed.

All appearance settings are saved and automatically applied on the next launch.

**Other**

* RAM allocation slider with 512 MB increments
* Mod manager: enable/disable mods without deleting their files
* Modpack system: create modpacks without having to enable and disable mods individually
* Game console with error and warning highlighting
* Full support for older versions, including 1.5.2 and below
* SHA-1 verification for all downloaded files
* Parallel downloads

---

## Building

You need **JDK 17 or newer** and **Maven 3.8+**.

```bash
git clone <repository>
cd agro-launcher
mvn clean package
```

---

## Running

### Windows

Double-click `AgroLauncher.jar`, or run:

```cmd
javaw -jar AgroLauncher.jar
```

### Linux

```bash
java -jar AgroLauncher.jar
```

### Command-Line Arguments

```
--help, -h                 show help
--version                  show launcher version
--data-dir <path>          use a custom data directory (portable mode)
--dry-run <version> [name] prepare the version and show the launch command without starting the game
```

Example: check the configuration without launching the game:

```bash
java -jar AgroLauncher.jar --dry-run 1.21.1 Steve
```

---

## File Locations

| System  | Path                                                 |
| ------- | ---------------------------------------------------- |
| Windows | `%APPDATA%\.agrolauncher`                            |
| Linux   | `~/.agrolauncher` (or `$XDG_DATA_HOME/agrolauncher`) |
| macOS   | `~/Library/Application Support/agrolauncher`         |

Directory structure:

```text
.agrolauncher/
├── minecraft/          game files (versions, libraries, assets, mods)
├── runtimes/            automatically downloaded JRE 8/17/21
├── authlib/             authlib-injector.jar for Ely.by
├── cache/installers/    Forge, NeoForge, and OptiFine installers
├── cache/icons/         mod icon cache
├── settings.json        settings
└── accounts.json        accounts (passwords are not stored)
```

---

## First Launch

1. Open **Accounts** → enter your nickname → click **Add Profile**. Alternatively, log in through Ely.by.
2. Open **Versions** → select a Minecraft version and mod loader.
3. Open **Settings** → **General** → set the amount of RAM using the slider.
4. If desired, go to **Settings** → **Interface** to configure the language, theme, custom color, and background.
5. Return to **Play** and click **PLAY**.

The selected mod loader is installed automatically the first time you launch the corresponding Minecraft version.

Mods can be installed from the **Mods** screen: the **Catalog** tab searches Modrinth, while the **Installed** tab manages local mod files. Your own `.jar` files can be added using the button or placed directly into `minecraft/mods`.

---

## Architecture

```text
by.agro.launcher
├── Main                    entry point, CLI modes
├── LauncherContext         shared service context
├── core/                   platform, paths, settings, downloader, JSON, hashes
├── version/                Mojang manifests, library rules, version inheritance,
│                           assets, native libraries
├── loaders/                Fabric, Quilt, Forge, NeoForge, OptiFine installers
├── auth/                   offline profiles, Ely.by, authlib-injector, storage
├── jvm/                    Java detection and downloading, archive extraction
├── launch/                 launch command construction, game process
├── modrinth/               catalog client, mod installer, icon cache
├── i18n/                   Russian and English interface text
├── mods/                   mod file management
└── ui/                     themes and palettes, blurred backgrounds,
                            window, screens
```

---

## Dependencies

* [FlatLaf](https://www.formdev.com/flatlaf/) 3.7.2 — modern Swing look and feel
* [Gson](https://github.com/google/gson) 2.14.0 — JSON processing

The mod catalog uses the open [Modrinth API](https://docs.modrinth.com/).

Both libraries are bundled into the final JAR.

---

## License

The code is provided as-is. Minecraft is a trademark of Mojang AB; this launcher is not affiliated with Mojang or Microsoft.

---------------------

Russian
# AgroLauncher

Кроссплатформенный лаунчер Minecraft на Java: русский и английский интерфейс, каталог модов Modrinth, девять цветовых тем со своим цветом, настраиваемый фон и вход через Ely.by.

Работает на Windows, Linux, MacOS. Собирается в один исполняемый JAR без внешних зависимостей.

---

## Возможности

**Загрузчики модов — все версии игры**

| Загрузчик | Версии | Как устанавливается |
|---|---|---|
| Fabric | 1.14+ | Fabric Meta API |
| Quilt | 1.14+ | Quilt Meta API |
| Forge | 1.1 – новейшие | официальный установщик в headless-режиме |
| NeoForge | 1.20.2+ | официальный установщик в headless-режиме |
| OptiFine | все | патчинг через `optifine.Patcher` либо как мод к Forge |

**Аккаунты**

- **Оффлайн-профиль** — только ник. UUID вычисляется тем же алгоритмом, что использует сервер Minecraft при `online-mode=false`, поэтому прогресс на серверах сохраняется между запусками.
- **Ely.by** — вход по логину и паролю, поддержка двухфакторной аутентификации, скины и плащи через authlib-injector (скачивается автоматически).

**Каталог модов**

Встроенный браузер Modrinth: поиск с фильтрами по загрузчику, версии игры и категории, сортировка по загрузкам или дате, установка в один клик. Обязательные зависимости подтягиваются автоматически — например, при установке Create сам добавится Fabric API.

**Язык интерфейса**

Русский и английский, переключаются в настройках без перезапуска. При первом запуске язык определяется по системной локали.

**Оформление**

Логотип и иконка окна отрисовываются векторно и окрашиваются акцентом активной темы.

Девять готовых тем: Emerald Dark, Midnight Ember, Noir Rose, Vault Gold, Abyss Frost, Crimson Chalk, ElectricPurple Cream, RoyalPurple Gold, DeepForest Flame.

Тема QT - лаунчер берет текущее оформление QT и подставляет его в лаунчер.
  
Режим «Свой цвет» открывает цветовой круг: выбираете акцент, остальные оттенки палитры рассчитываются автоматически с учётом читаемости текста. Тема применяется сразу, без перезапуска.

Фоновое изображение задаётся путём к файлу (PNG, JPG, GIF). Регулируются размытие с настраиваемым радиусом, затемнение и плотность панелей. Анимированные GIF воспроизводятся; при включённом размытии показывается статичный первый кадр.

Все настройки оформления сохраняются и применяются при следующем запуске.

**Прочее**

- Слайдер выделения оперативной памяти с шагом 512 МБ
- Менеджер модов: включение/отключение без удаления файлов
- Система сборок: вы можете делать сборки модов без надобности отключать и включать по одному
- Консоль игры с подсветкой ошибок и предупреждений
- Полная поддержка старых версий, включая 1.5.2 и ниже
- Проверка SHA-1 у всех скачанных файлов, параллельная загрузка

---

## Сборка

Нужны **JDK 17 или новее** и **Maven 3.8+**.

```bash
git clone <репозиторий>
cd agro-launcher
mvn clean package
```



---

## Запуск

### Windows

Двойной щелчок по `AgroLauncher.jar`, либо:

```cmd
javaw -jar AgroLauncher.jar
```

### Linux

```bash
java -jar AgroLauncher.jar
```


### Аргументы командной строки

```
--help, -h                 справка
--version                  версия лаунчера
--data-dir <путь>          свой каталог данных (портативный режим)
--dry-run <версия> [ник]   подготовить версию и показать команду запуска, не запуская игру
```

Пример проверки конфигурации без запуска игры:

```bash
java -jar AgroLauncher.jar --dry-run 1.21.1 Steve
```

---

## Где хранятся файлы

| Система | Путь |
|---|---|
| Windows | `%APPDATA%\.agrolauncher` |
| Linux | `~/.agrolauncher` (или `$XDG_DATA_HOME/agrolauncher`) |
| macOS | `~/Library/Application Support/agrolauncher` |

Структура каталога:

```
.agrolauncher/
├── minecraft/          игровые файлы (versions, libraries, assets, mods)
├── runtimes/           автоматически скачанные JRE 8/17/21
├── authlib/            authlib-injector.jar для Ely.by
├── cache/installers/   установщики Forge, NeoForge, OptiFine
├── cache/icons/        кэш иконок модов
├── settings.json       настройки
└── accounts.json       аккаунты (пароли не сохраняются)
```

---

## Первый запуск

1. Откройте **Аккаунты** → введите ник → «Добавить профиль». Либо войдите через Ely.by.
2. Откройте **Версии** → выберите версию Minecraft и загрузчик модов.
3. Откройте **Настройки** → «Общие» → выставьте объём оперативной памяти слайдером.
4. При желании зайдите в **Настройки** → «Интерфейс»: язык, тема, свой цвет и фон.
5. Вернитесь на **Играть** и нажмите «ИГРАТЬ».

Загрузчик модов устанавливается автоматически при первом запуске выбранной связки.

Моды устанавливаются на экране **Моды**: вкладка «Каталог» ищет их в Modrinth, вкладка «Установленные» управляет файлами. Свои `.jar` можно добавить кнопкой или положить прямо в `minecraft/mods`. 

---


## Архитектура

```
by.agro.launcher
├── Main                    точка входа, режимы CLI
├── LauncherContext         общий контекст сервисов
├── core/                   платформа, пути, настройки, загрузчик, JSON, хеши
├── version/                манифесты Mojang, правила библиотек, наследование
│                           версий, ассеты, нативные библиотеки
├── loaders/                установщики Fabric, Quilt, Forge, NeoForge, OptiFine
├── auth/                   оффлайн-профили, Ely.by, authlib-injector, хранилище
├── jvm/                    определение и скачивание Java, распаковка архивов
├── launch/                 сборка команды запуска, процесс игры
├── modrinth/               клиент каталога, установщик модов, кэш иконок
├── i18n/                   тексты интерфейса на двух языках
├── mods/                   управление файлами модов
└── ui/                     темы и палитры, фон с размытием, окно, экраны
```



## Зависимости

- [FlatLaf](https://www.formdev.com/flatlaf/) 3.7.2 — современный внешний вид Swing
- [Gson](https://github.com/google/gson) 2.14.0 — работа с JSON

Каталог модов работает через открытый [API Modrinth](https://docs.modrinth.com/) 

Обе библиотеки встроены в итоговый JAR.

---

## Лицензия

Код предоставлен как есть. Minecraft — торговая марка Mojang AB; лаунчер не связан с Mojang и Microsoft.
