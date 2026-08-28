package by.agro.launcher.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


public final class Strings {

    private static Language current = Language.detectSystem();
    private static final List<Runnable> listeners = new ArrayList<>();

    private static final Map<String, String[]> TEXTS = new LinkedHashMap<>();

    private static final int RU = 0;
    private static final int EN = 1;

    static {

        put("app.name", "AgroLauncher", "AgroLauncher");
        put("app.subtitle", "Быстрый и кастомизируемый лаунчер Minecraft",
                "Fast and customizable Minecraft launcher");
        put("app.offlineOnly", "Только offline-серверы", "Offline servers only");
        put("app.ready", "Готов к работе", "Ready");
        put("app.version", "версия {0}", "version {0}");

        put("common.ok", "ОК", "OK");
        put("common.cancel", "Отмена", "Cancel");
        put("common.close", "Закрыть", "Close");
        put("common.delete", "Удалить", "Delete");
        put("common.refresh", "Обновить", "Refresh");
        put("common.add", "Добавить", "Add");
        put("common.choose", "Выбрать…", "Browse…");
        put("common.apply", "Применить", "Apply");
        put("common.remove", "Убрать", "Remove");
        put("common.confirm", "Подтверждение", "Confirm");
        put("common.error", "Ошибка", "Error");
        put("common.warning", "Предупреждение", "Warning");
        put("common.loading", "Загрузка…", "Loading…");
        put("common.notSelected", "не выбрано", "not selected");
        put("common.notSet", "не задан", "not set");
        put("common.any", "любой", "any");
        put("common.openFolder", "Открыть папку", "Open folder");
        put("unit.gb", "ГБ", "GB");
        put("unit.mb", "МБ", "MB");
        put("unit.kb", "КБ", "KB");
        put("unit.b", "Б", "B");


        put("nav.play", "Играть", "Play");
        put("nav.versions", "Версии", "Versions");
        put("nav.accounts", "Аккаунты", "Accounts");
        put("nav.mods", "Моды", "Mods");
        put("nav.settings", "Настройки", "Settings");

        put("play.version", "Версия", "Version");
        put("play.loader", "Загрузчик", "Loader");
        put("play.account", "Аккаунт", "Account");
        put("play.memory", "Память", "Memory");
        put("play.button", "ИГРАТЬ", "PLAY");
        put("play.stop", "Остановить игру", "Stop game");
        put("play.console", "Консоль", "Console");
        put("play.clear", "Очистить", "Clear");
        put("play.noMods", "без модов", "no mods");
        put("play.versionNotSelected", "версия не выбрана", "no version selected");
        put("play.accountNotSelected", "не выбран", "none");
        put("play.needAccount", "Сначала добавьте аккаунт на вкладке «Аккаунты»",
                "Add an account on the Accounts tab first");
        put("play.needVersion", "Сначала выберите версию на вкладке «Версии»",
                "Select a version on the Versions tab first");
        put("play.preparing", "Подготовка к запуску {0}", "Preparing {0}");
        put("play.started", "Игра запущена", "Game started");
        put("play.startedPid", "Игра запущена (PID {0})", "Game running (PID {0})");
        put("play.exited", "Игра завершена (код {0})", "Game exited (code {0})");
        put("play.processExited", "Процесс игры завершён с кодом {0}",
                "Game process exited with code {0}");
        put("play.launchError", "Ошибка запуска", "Launch failed");
        put("play.launchErrorDetails", "Ошибка запуска: {0}", "Launch failed: {0}");
        put("play.stopping", "Останавливаем игру…", "Stopping the game…");
        put("play.killed", "Процесс завершён принудительно", "Process terminated forcibly");
        put("play.installingLoader", "Установка {0}", "Installing {0}");
        put("play.loaderRecommended", " (рекомендованная версия)", " (recommended version)");
        put("play.loaderUnavailable", "Загрузчик {0} недоступен, запускаем ванильную версию",
                "{0} is unavailable, launching vanilla");
        put("play.exitConfirm", "Игра ещё запущена. Закрыть лаунчер?\nПроцесс игры продолжит работу.",
                "The game is still running. Close the launcher?\nThe game process will keep running.");
        put("play.exitTitle", "Подтверждение выхода", "Confirm exit");


        put("versions.title", "Версии", "Versions");
        put("versions.subtitle", "Выберите версию Minecraft и загрузчик модов",
                "Choose a Minecraft version and mod loader");
        put("versions.minecraft", "Minecraft", "Minecraft");
        put("versions.snapshots", "Снапшоты", "Snapshots");
        put("versions.oldVersions", "Старые (альфа/бета)", "Old (alpha/beta)");
        put("versions.refreshList", "Обновить список", "Refresh list");
        put("versions.loader", "Загрузчик модов", "Mod loader");
        put("versions.loaderType", "Тип", "Type");
        put("versions.loaderVersion", "Версия загрузчика", "Loader version");
        put("versions.willLaunch", "Будет запущено", "Will launch");
        put("versions.noMods", "Моды не поддерживаются", "Mods are not supported");
        put("versions.loading", "Загрузка списка версий…", "Loading version list…");
        put("versions.loadingLoader", "Загрузка версий {0}…", "Loading {0} versions…");
        put("versions.available", "Версий доступно: {0} (последняя: {1})",
                "Versions available: {0} (latest: {1})");
        put("versions.loaderAvailable", "Версий доступно: {0}", "Versions available: {0}");
        put("versions.notSupported", "{0} не поддерживает {1}", "{0} does not support {1}");
        put("versions.loadFailed", "Не удалось загрузить список версий: {0}",
                "Failed to load version list: {0}");
        put("versions.offlineList", "Показаны установленные версии ({0}) — список Mojang недоступен",
                "Showing installed versions ({0}) — Mojang list unavailable");
        put("versions.noVersions", "Список версий недоступен: нет соединения и нет установленных версий",
                "No versions available: no connection and nothing installed");
        put("versions.loaderUnavailable", "Загрузчик недоступен", "Loader unavailable");
        put("versions.typeRelease", "релиз", "release");
        put("versions.typeSnapshot", "снапшот", "snapshot");
        put("versions.typeBeta", "бета", "beta");
        put("versions.typeAlpha", "альфа", "alpha");
        put("loader.recommended", "рекомендуется", "recommended");
        put("theme.qtSystem", "Тема QT (Нестабильная)", "QT Theme (Unstable)");
        put("theme.qtSystem.desc",
                "Цвета системной палитры рабочего стола (Qt/KDE через доступные Swing defaults)",
                "Desktop system palette (Qt/KDE through available Swing defaults)");


        put("accounts.title", "Аккаунты", "Accounts");
        put("accounts.subtitle",
                "Оффлайн-профиль подходит для серверов без проверки лицензии. Ely.by добавляет скины и плащи.",
                "An offline profile works on servers without license checks. Ely.by adds skins and capes.");
        put("accounts.saved", "Сохранённые аккаунты", "Saved accounts");
        put("accounts.makeActive", "Сделать активным", "Set as active");
        put("accounts.active", "активный", "active");
        put("accounts.offline", "Оффлайн", "Offline");
        put("accounts.offlineProfile", "Оффлайн-профиль", "Offline profile");
        put("accounts.nickname", "Ник в игре", "In-game name");
        put("accounts.nicknamePlaceholder", "например, Steve", "for example, Steve");
        put("accounts.nicknameHint", "3–16 символов: латиница, цифры и подчёркивание",
                "3–16 characters: letters, digits and underscore");
        put("accounts.addProfile", "Добавить профиль", "Add profile");
        put("accounts.invalidNickname",
                "Ник должен содержать от 3 до 16 символов: латиница, цифры или подчёркивание.",
                "The name must be 3 to 16 characters: letters, digits or underscore.");
        put("accounts.invalidNicknameTitle", "Некорректный ник", "Invalid name");
        put("accounts.added", "Профиль {0} добавлен и выбран", "Profile {0} added and selected");
        put("accounts.elyLogin", "Вход через Ely.by", "Sign in with Ely.by");
        put("accounts.emailOrName", "E-mail или ник", "Email or username");
        put("accounts.password", "Пароль", "Password");
        put("accounts.totp", "Код двухфакторной аутентификации (если включена)",
                "Two-factor authentication code (if enabled)");
        put("accounts.totpPlaceholder", "6 цифр из приложения", "6 digits from your app");
        put("accounts.signIn", "Войти в Ely.by", "Sign in to Ely.by");
        put("accounts.fillCredentials", "Заполните логин и пароль", "Enter your login and password");
        put("accounts.checking", "Проверяем данные…", "Checking credentials…");
        put("accounts.signedIn", "Вход выполнен: {0}", "Signed in: {0}");
        put("accounts.activeAccount", "Активный аккаунт: {0}", "Active account: {0}");
        put("accounts.deleteConfirm", "Удалить аккаунт {0}?", "Delete account {0}?");
        put("accounts.deleted", "Аккаунт удалён", "Account deleted");

        put("mods.title", "Моды", "Mods");
        put("mods.subtitle", "Каталог Modrinth: поиск, установка и автоматическая подтяжка зависимостей",
                "Modrinth catalog: search, install and automatic dependency resolution");
        put("mods.catalog", "Каталог", "Catalog");
        put("mods.installed", "Установленные", "Installed");
        put("mods.installedTitle", "Установленные моды", "Installed mods");
        put("mods.curseForge", "CurseForge в браузере", "CurseForge in browser");
        put("mods.curseForgeTooltip",
                "CurseForge не даёт доступа к каталогу без ключа разработчика, поэтому сайт "
                        + "открывается во внешнем браузере. Скачанные файлы добавьте кнопкой «Добавить файлы».",
                "CurseForge does not allow catalog access without a developer key, so the site "
                        + "opens in an external browser. Add downloaded files with the Add files button.");
        put("mods.curseForgeOpened",
                "CurseForge открыт в браузере — скачанные файлы добавьте кнопкой «Добавить файлы»",
                "CurseForge opened in browser — add downloaded files with the Add files button");
        put("mods.addFiles", "Добавить файлы", "Add files");
        put("mods.toggle", "Включить / отключить", "Enable / disable");
        put("mods.chooseFiles", "Выберите файлы модов", "Choose mod files");
        put("mods.jarFilter", "Моды Minecraft (*.jar)", "Minecraft mods (*.jar)");
        put("mods.addFailed", "Не удалось добавить {0}: {1}", "Failed to add {0}: {1}");
        put("mods.addedCount", "Добавлено модов: {0}", "Mods added: {0}");
        put("mods.toggleFailed", "Не удалось изменить {0}: {1}", "Failed to change {0}: {1}");
        put("mods.stateUpdated", "Состояние модов обновлено", "Mod state updated");
        put("mods.deleteConfirm", "Удалить выбранные файлы ({0})?", "Delete selected files ({0})?");
        put("mods.deleteFailed", "Не удалось удалить {0}: {1}", "Failed to delete {0}: {1}");
        put("mods.deleted", "Моды удалены", "Mods deleted");
        put("mods.summary", "Всего файлов: {0}, включено: {1}", "Total files: {0}, enabled: {1}");
        put("mods.enabled", "включён", "enabled");
        put("mods.disabled", "отключён", "disabled");
        put("mods.loaderWarning",
                "Выбран {0} — моды не будут загружены. Выберите Fabric, Forge, Quilt или NeoForge.",
                "{0} is selected — mods will not load. Choose Fabric, Forge, Quilt or NeoForge.");
        put("mods.folderTitle", "Папка модов", "Mods folder");
        put("mods.openFolderFailed", "Не удалось открыть папку: {0}", "Failed to open folder: {0}");
        put("builds.independent", "Независимые сборки:", "Independent builds:");
        put("builds.createSnapshot", "Создать снимок", "Create snapshot");
        put("builds.delete", "Удалить сборку", "Delete build");
        put("builds.namePrompt", "Название сборки:", "Build name:");
        put("builds.newTitle", "Новая сборка", "New build");
        put("builds.defaultName", "Сборка {0}", "Build {0}");
        put("builds.created", "Создан независимый снимок: {0}", "Independent snapshot created: {0}");
        put("builds.selected", "Выбрана сборка: {0}", "Build selected: {0}");
        put("builds.deleteConfirm", "Удалить сборку «{0}»?", "Delete build “{0}”?");
        put("builds.modCount", "{0} модов", "{0} mods");
        put("builds.selectVersion", "Выберите версию Minecraft", "Select a Minecraft version");
        put("builds.loaderUnsupported", "Выбранный загрузчик не поддерживает моды",
                "The selected loader does not support mods");


        put("browser.searchPlaceholder", "Поиск модов: sodium, jei, create…",
                "Search mods: sodium, jei, create…");
        put("browser.allCategories", "Все категории", "All categories");
        put("browser.loadMore", "Показать ещё", "Load more");
        put("browser.loadingCatalog", "Загрузка каталога…", "Loading catalog…");
        put("browser.nothingFound", "Ничего не найдено — измените запрос или фильтры",
                "Nothing found — change your query or filters");
        put("browser.shown", "Показано {0} из {1}", "Showing {0} of {1}");
        put("browser.loadFailed", "Не удалось загрузить каталог: {0}", "Failed to load catalog: {0}");
        put("browser.unavailable", "Каталог модов недоступен: {0}", "Mod catalog unavailable: {0}");
        put("browser.filterByProfile", "Фильтр по профилю: ", "Profile filter: ");
        put("browser.filterNotSet", "не задан — выберите версию и загрузчик на вкладке «Версии»",
                "not set — choose a version and loader on the Versions tab");
        put("browser.anyLoader", "любой загрузчик", "any loader");
        put("browser.anyVersion", "любая версия", "any version");
        put("browser.byAuthor", "от {0}", "by {0}");
        put("browser.installedMark", "установлен", "installed");
        put("browser.sortRelevance", "По совпадению", "Relevance");
        put("browser.sortDownloads", "По загрузкам", "Downloads");
        put("browser.sortFollows", "По подписчикам", "Followers");
        put("browser.sortNewest", "Сначала новые", "Newest");
        put("browser.sortUpdated", "По обновлению", "Recently updated");


        put("modDialog.version", "Версия мода", "Mod version");
        put("modDialog.install", "Установить", "Install");
        put("modDialog.installed", "Установлен", "Installed");
        put("modDialog.alreadyInstalled", "Уже установлен", "Already installed");
        put("modDialog.openPage", "Открыть страницу", "Open page");
        put("modDialog.withDependencies", "Установить обязательные зависимости",
                "Install required dependencies");
        put("modDialog.loadingVersions", "Загрузка версий…", "Loading versions…");
        put("modDialog.noVersions", "Нет версий под выбранный загрузчик",
                "No versions for the selected loader");
        put("modDialog.versionsFailed", "Ошибка загрузки версий: {0}", "Failed to load versions: {0}");
        put("modDialog.installing", "Установка…", "Installing…");
        put("modDialog.requiresDeps", "Требует зависимостей: {0}", "Requires dependencies: {0}");
        put("modDialog.depsWillInstall", " — будут установлены", " — will be installed");
        put("modDialog.depsDisabled", " — установка отключена", " — installation disabled");
        put("modDialog.browserFailed", "Не удалось открыть браузер: {0}", "Failed to open browser: {0}");

        put("install.alreadyInstalled", "Уже установлен: {0}", "Already installed: {0}");
        put("install.installed", "Установлен: {0} ({1})", "Installed: {0} ({1})");
        put("install.downloadFailed", "Ошибка загрузки {0}: {1}", "Failed to download {0}: {1}");
        put("install.fetchingDep", "Подтягиваем зависимость: {0}", "Fetching dependency: {0}");
        put("install.depNotFound", "Не найдена подходящая версия зависимости: {0}",
                "No suitable dependency version found: {0}");
        put("install.depError", "Ошибка зависимости {0}: {1}", "Dependency error {0}: {1}");
        put("install.stage", "Установка мода", "Installing mod");
        put("install.summaryInstalled", "Установлено: {0}", "Installed: {0}");
        put("install.summarySkipped", ", уже было: {0}", ", already present: {0}");
        put("install.summaryFailed", ", с ошибкой: {0}", ", failed: {0}");

        put("settings.title", "Настройки", "Settings");
        put("settings.subtitle", "Память, Java, оформление и параметры запуска",
                "Memory, Java, appearance and launch options");
        put("settings.general", "Общие", "General");
        put("settings.appearance", "Интерфейс", "Appearance");

        put("settings.memory", "Оперативная память", "Memory");
        put("settings.maxRam", "Выделено игре (-Xmx)", "Allocated to the game (-Xmx)");
        put("settings.minRam", "Начальный размер (-Xms), МБ", "Initial size (-Xms), MB");
        put("settings.minRamHint", "0 — не задавать", "0 — do not set");
        put("settings.ramAllocated", "Выделено памяти: {0}", "Memory allocated: {0}");
        put("settings.ramTotal", "всего в системе: {0}", "system total: {0}");
        put("settings.ramUnknown", "объём памяти определить не удалось", "could not detect system memory");
        put("settings.ramTooMuch", "Слишком много — системе может не хватить памяти",
                "Too much — the system may run out of memory");
        put("settings.ramTooLittle", "Для версий 1.18+ с модами рекомендуется от 4 ГБ",
                "For 1.18+ with mods, 4 GB or more is recommended");

        put("settings.java", "Java", "Java");
        put("settings.useManagedJava", "Использовать встроенную Java (скачивается автоматически)",
                "Use bundled Java (downloaded automatically)");
        put("settings.javaHint",
                "Лаунчер сам подберёт нужную версию: Java 8 для 1.16 и старше, 17 для 1.17–1.20.4, 21 для 1.20.5+",
                "The launcher picks the right version: Java 8 for 1.16 and older, 17 for 1.17–1.20.4, 21 for 1.20.5+");
        put("settings.javaDetected", "Найденные установки", "Detected installations");
        put("settings.javaPath", "Свой путь к java", "Custom java path");
        put("settings.javaDownload", "Скачать Java {0}", "Download Java {0}");
        put("settings.javaChoose", "Выберите исполняемый файл java", "Choose the java executable");
        put("settings.javaNotFound", "Файл не найден или не исполняемый", "File not found or not executable");
        put("settings.javaVersionDetected", "Определена Java {0}", "Detected Java {0}");
        put("settings.javaVersionUnknown", "Не удалось определить версию Java",
                "Could not determine the Java version");
        put("settings.javaNone", "Java не найдена — скачайте нужную версию",
                "No Java found — download a version");
        put("settings.javaDownloading", "Загрузка Java {0}…", "Downloading Java {0}…");
        put("settings.javaInstalled", "Java {0} установлена", "Java {0} installed");
        put("settings.javaReady", "Java {0} готова: {1}", "Java {0} ready: {1}");
        put("settings.javaSystem", "система", "system");
        put("settings.javaBundled", "встроенная", "bundled");

        put("settings.window", "Окно игры", "Game window");
        put("settings.width", "Ширина", "Width");
        put("settings.height", "Высота", "Height");
        put("settings.fullscreen", "Запускать в полноэкранном режиме", "Launch in fullscreen");

        put("settings.advanced", "Дополнительно", "Advanced");
        put("settings.optimizedFlags", "Оптимизированные флаги JVM (G1GC)", "Optimized JVM flags (G1GC)");
        put("settings.closeOnLaunch", "Закрывать лаунчер после запуска игры",
                "Close the launcher after the game starts");
        put("settings.showConsole", "Показывать консоль с логами игры", "Show the game log console");
        put("settings.jvmArgs", "Аргументы JVM", "JVM arguments");
        put("settings.gameArgs", "Аргументы игры", "Game arguments");

        put("settings.folders", "Папки", "Folders");
        put("settings.launcherData", "Данные лаунчера: {0}", "Launcher data: {0}");
        put("settings.gameFiles", "Игровые файлы: {0}", "Game files: {0}");
        put("settings.openGameFolder", "Открыть папку игры", "Open game folder");
        put("settings.openModsFolder", "Открыть папку модов", "Open mods folder");
        put("settings.openLauncherFolder", "Открыть папку лаунчера", "Open launcher folder");
        put("settings.folderPath", "Путь к папке", "Folder path");

        put("appearance.language", "Язык интерфейса", "Interface language");
        put("appearance.languageHint", "Изменения применяются сразу", "Changes apply immediately");
        put("appearance.languageChanged", "Язык интерфейса: {0}", "Interface language: {0}");
        put("appearance.theme", "Цветовая тема", "Color theme");
        put("appearance.themeSelected", "Тема: {0}", "Theme: {0}");
        put("appearance.customColor", "Свой акцентный цвет", "Custom accent color");
        put("appearance.customColorHint",
                "Остальные оттенки темы рассчитываются автоматически на основе выбранного цвета",
                "The rest of the palette is derived automatically from the chosen color");
        put("appearance.wheelHint", "Кликните по кругу или введите код",
                "Click the wheel or enter a hex code");
        put("appearance.brightness", "Яркость", "Brightness");
        put("appearance.colorCode", "Код цвета", "Color code");
        put("appearance.background", "Фоновое изображение", "Background image");
        put("appearance.backgroundPath", "Путь к файлу", "File path");
        put("appearance.backgroundPlaceholder", "Выберите изображение PNG, JPG или GIF",
                "Choose a PNG, JPG or GIF image");
        put("appearance.backgroundChoose", "Выберите изображение для фона", "Choose a background image");
        put("appearance.imageFilter", "Изображения (PNG, JPG, GIF)", "Images (PNG, JPG, GIF)");
        put("appearance.backgroundOff", "Фон отключён", "Background disabled");
        put("appearance.backgroundLoaded", "Фон загружен", "Background loaded");
        put("appearance.backgroundGif", " (GIF)", " (GIF)");
        put("appearance.backgroundGifBlur",
                " — при размытии показывается первый кадр без анимации",
                " — blur shows the first frame without animation");
        put("appearance.backgroundFailed", "Не удалось загрузить изображение", "Failed to load the image");
        put("appearance.backgroundRemoved", "Фоновое изображение убрано", "Background image removed");
        put("appearance.blur", "Размыть фон", "Blur background");
        put("appearance.blurRadius", "Радиус размытия", "Blur radius");
        put("appearance.dim", "Затемнение фона", "Background dimming");
        put("appearance.panelOpacity", "Плотность панелей", "Panel opacity");
        put("appearance.fileNotFound", "Файл не найден: {0}", "File not found: {0}");
        put("appearance.formatUnsupported", "Формат изображения не поддерживается",
                "Image format is not supported");
        put("appearance.gifReadFailed", "Не удалось прочитать GIF", "Failed to read the GIF");
        put("appearance.readError", "Ошибка чтения: {0}", "Read error: {0}");


        put("theme.custom", "Свой цвет", "Custom color");
        put("theme.emeraldDark.desc", "Глубокий графит и изумруд", "Deep graphite and emerald");
        put("theme.midnightEmber.desc", "Ночная синева и угли костра", "Midnight blue and glowing embers");
        put("theme.noirRose.desc", "Тёмный нуар и розовый неон", "Dark noir and pink neon");
        put("theme.vaultGold.desc", "Хранилище и старое золото", "Vault walls and old gold");
        put("theme.abyssFrost.desc", "Глубина океана и морозный лёд", "Ocean depth and frozen ice");
        put("theme.crimsonChalk.desc", "Чёрный графит и багровый мел", "Black graphite and crimson chalk");
        put("theme.electricPurple.desc", "Электрический пурпур и сливки", "Electric purple and cream");
        put("theme.royalPurple.desc", "Королевский пурпур и золото", "Royal purple and gold");
        put("theme.deepForest.desc", "Тёмный лес и пламя", "Deep forest and flame");
        put("theme.customDesc", "Выберите цвет на круге", "Pick a color on the wheel");

        put("error.startup", "Не удалось запустить лаунчер:", "Failed to start the launcher:");
        put("error.startup.title", "Ошибка запуска", "Startup error");
        put("progress.versionPrepare", "Подготовка версии {0}", "Preparing version {0}");
        put("progress.versionChain", "Цепочка версий: {0}", "Version chain: {0}");
        put("progress.clientJar", "Клиент игры", "Game client");
        put("progress.libraries", "Загрузка библиотек", "Downloading libraries");
        put("progress.librariesCount", "Библиотек к проверке: {0}", "Libraries to verify: {0}");
        put("progress.assets", "Загрузка ассетов", "Downloading assets");
        put("progress.assetsCount", "Ассетов к проверке: {0}", "Assets to verify: {0}");
        put("progress.assetIndex", "Индекс ассетов", "Asset index");
        put("progress.natives", "Распаковка нативных библиотек", "Extracting native libraries");
        put("progress.legacyAssets", "Подготовка legacy-ассетов", "Preparing legacy assets");
        put("progress.javaCheck", "Проверка Java", "Checking Java");
        put("progress.javaRequired", "требуется Java {0}", "Java {0} required");
        put("progress.javaUsing", "Java: {0}", "Java: {0}");
        put("progress.auth", "Авторизация", "Authentication");
        put("progress.tokenValid", "токен действителен", "token is valid");
        put("progress.done", "Готово", "Done");
        put("progress.commandReady", "команда собрана", "command assembled");
        put("progress.patchingClient", "Патчинг клиента", "Patching the client");
        put("progress.patchingOptifine", "Патчинг OptiFine", "Patching OptiFine");
        put("progress.classesProcessed", "классов обработано: {0}", "classes processed: {0}");
        put("progress.allDownloaded", "всё уже загружено", "everything already downloaded");
    }

    private Strings() {
    }

    private static void put(String key, String russian, String english) {
        TEXTS.put(key, new String[]{russian, english});
    }


    public static Language language() {
        return current;
    }

    public static void setLanguage(Language language) {
        if (language == null || language == current) {
            return;
        }
        current = language;
        for (Runnable listener : new ArrayList<>(listeners)) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                System.err.println("Слушатель языка завершился ошибкой: " + e.getMessage());
            }
        }
    }

    public static void addLanguageListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeLanguageListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static String get(String key) {
        String[] variants = TEXTS.get(key);
        if (variants == null) {
            return key;
        }
        return current == Language.ENGLISH ? variants[EN] : variants[RU];
    }


    public static String get(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return new MessageFormat(pattern, current.locale()).format(args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }
}
