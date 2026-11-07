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
        put("app.offlineOnly", "Оффлайн, Ely.by и Microsoft", "Offline, Ely.by and Microsoft");
        put("app.ready", "Готов к работе", "Ready");
        put("app.version", "версия {0}", "version {0}");

        put("app.currentInstance", "Текущий инстанс: {0} - {1}", "Current instance: {0} - {1}");
        put("app.currentInstanceNone", "Текущий инстанс: не выбран", "Current instance: none selected");
        put("app.loaderVanilla", "ванилла", "vanilla");

        put("horizon.standaloneVersion", "Horizon , отдельная Rust-версия", "Horizon , standalone Rust version");
        put("horizon.nativeEngine", "Нативный клиент Horizon", "Native Horizon client");
        put("horizon.offlineVersionList", "26.3 Rust доступна в списке; первая установка требует сети.",
                "26.3 Rust is listed; the first installation requires a connection.");
        put("horizon.platformUnsupported", "Horizon не поддерживает эту платформу: {0} / {1}",
                "Horizon does not support this platform: {0} / {1}");
        put("horizon.notInstalledOffline", "Horizon ещё не установлен. Для первой установки отключите офлайн-режим.",
                "Horizon is not installed. Go online for the first installation.");
        put("horizon.checking", "Проверка подписанного релиза Horizon…",
                "Checking the signed Horizon release…");
        put("horizon.cachedBuild", "Сеть недоступна, используем установленный Horizon: {0}",
                "Network unavailable; using installed Horizon: {0}");
        put("horizon.badSignature", "Неверная подпись релиза Horizon. Установка остановлена.",
                "Invalid Horizon release signature. Installation stopped.");
        put("horizon.missingArchive", "В подписанном релизе нет архива {0}",
                "Archive {0} is missing from the signed release");
        put("horizon.damagedBuild", "Установка Horizon повреждена: {0}. Переименуйте этот каталог и повторите запуск.",
                "Horizon installation is damaged: {0}. Rename that directory and try again.");
        put("horizon.downloading", "Скачивание Horizon {0}…",
                "Downloading Horizon {0}…");
        put("horizon.extracting", "Распаковка Horizon…",
                "Extracting Horizon…");
        put("horizon.invalidArchive", "Архив Horizon не содержит клиента и подготовщика ресурсов.",
                "Horizon archive has no client and resource preparer.");
        put("horizon.incompatibleBuild", "Эта сборка Horizon несовместима с интеграцией 26.3 Rust.",
                "This Horizon build is incompatible with the 26.3 Rust integration.");
        put("horizon.reusing", "Копирование совместимых ресурсов предыдущей сборки Horizon…",
                "Copying compatible resources from a previous Horizon build…");
        put("horizon.minecraftMismatch", "Файлы Minecraft не соответствуют закреплённой версии Horizon.",
                "Minecraft files do not match the version pinned by Horizon.");
        put("horizon.serverMissingOffline", "Для подготовки Horizon отсутствует проверенный серверный JAR. Нужна первая установка с сетью.",
                "The verified server JAR needed by Horizon is missing. Prepare it online first.");
        put("horizon.serverDownload", "Загрузка серверного JAR Minecraft для подготовки ресурсов…",
                "Downloading the Minecraft server JAR for resource preparation…");
        put("horizon.manifestDownload", "Загрузка оригинального описания Minecraft для Horizon…",
                "Downloading the original Minecraft version manifest for Horizon…");
        put("horizon.manifestMissingOffline", "Для подготовки Horizon нужен оригинальный манифест Minecraft. Повторите запуск с включённой сетью.",
                "Horizon needs the original Minecraft manifest. Retry with network access enabled.");
        put("horizon.manifestMismatch", "Контрольная сумма манифеста Minecraft для Horizon не совпадает.",
                "The Minecraft manifest checksum for Horizon does not match.");
        put("horizon.preparing", "Подготовка ресурсов Horizon (первый запуск может занять несколько минут)…",
                "Preparing Horizon resources (the first launch may take several minutes)…");
        put("horizon.resourcesIncomplete", "Подготовка Horizon не создала все необходимые ресурсы.",
                "Horizon preparation did not produce all required resources.");
        put("horizon.prepareFailed", "Подготовка Horizon завершилась с кодом {0}",
                "Horizon preparation exited with code {0}");
        put("horizon.interrupted", "Подготовка Horizon прервана.",
                "Horizon preparation was interrupted.");
        put("horizon.offlineNotice", "26.3 Rust: используется только ник, без сессии и токенов. Только offline-серверы; Java-моды и шейдеры несовместимы.",
                "26.3 Rust: nickname only, no session or tokens. Offline servers only; Java mods and shaders are incompatible.");
        put("horizon.onlyVersion", "Horizon поддерживается только для Minecraft 26.3.",
                "Horizon is supported only for Minecraft 26.3.");
        put("horizon.repairHint", "Repair для Java-инстансов здесь недоступен. Файлы Horizon проверяются при установке.",
                "Java instance repair is unavailable here. Horizon downloads are verified during installation.");
        put("horizon.instanceHint", "Отдельный инстанс 26.3 Rust (Horizon). Установка начнётся при запуске.",
                "Separate 26.3 Rust (Horizon) instance. Installation starts on launch.");
        put("horizon.noJavaMods", "Horizon , Rust-клиент: Java-моды, шейдеры и Java-модпаки не поддерживаются.",
                "Horizon is a Rust client: Java mods, shaders and Java modpacks are unsupported.");
        put("horizon.credits", "Horizon , Ludovic Marie и участники Horizon, © 2026, MIT. Minecraft , Mojang; файлы загружаются отдельно.",
                "Horizon , Ludovic Marie and the Horizon contributors, © 2026, MIT. Minecraft , Mojang; game files are downloaded separately.");
        put("horizon.license", "Лицензия Horizon (MIT)",
                "Horizon license (MIT)");

        put("hnmod.tab", "Horizon Native", "Horizon Native");
        put("hnmod.details", "Версия {0} · {1}", "Version {0} · {1}");
        put("hnmod.fileFilter", "Нативные моды Horizon (.hnmod)", "Horizon native mods (.hnmod)");
        put("hnmod.instanceMissing", "Выбранный инстанс Horizon не найден. Выберите обычный 26.3 Rust или существующую сборку.", "The selected Horizon instance was not found. Select plain 26.3 Rust or an existing build.");
        put("hnmod.instanceName", "Horizon · модов: {0} · {1}", "Horizon · mods: {0} · {1}");
        put("hnmod.instanceDone", "Создан инстанс «{0}». Он выбран для запуска; свои миры и настройки находятся в его папке.", "Instance “{0}” was created and selected. Its worlds and settings are in its own folder.");
        put("hnmod.newInstance", "Собрать отдельный инстанс", "Build separate instance");
        put("hnmod.builderDownload", "Загрузка официального сборочного компонента Horizon…", "Downloading the official Horizon build component…");
        put("hnmod.hint", "Выберите .hnmod для новой отдельной сборки. Обычный 26.3 Rust не изменяется.", "Select .hnmod files for a new separate build. Plain 26.3 Rust is unchanged.");
        put("hnmod.add", "Добавить .hnmod", "Add .hnmod");
        put("hnmod.toggle", "Включить / выключить", "Enable / disable");
        put("hnmod.open", "Открыть папку", "Open folder");
        put("hnmod.build", "Собрать клиент", "Build client");
        put("hnmod.path", "Папка: {0}", "Folder: {0}");
        put("hnmod.deleteConfirm", "Удалить нативный мод {0}?", "Delete native mod {0}?");
        put("hnmod.invalid", "Неверный или неподдерживаемый .hnmod: проверьте архив и манифесты для 26.3.", "Invalid or unsupported .hnmod: check the archive and its 26.3 manifests.");
        put("hnmod.duplicate", "Мод с ID {0} уже установлен. Сначала удалите старую версию.", "A mod with ID {0} is already installed. Remove the old version first.");
        put("hnmod.needsBuild", "Нативные моды изменились или клиент ещё не собран. Откройте «Моды → Horizon Native» и соберите клиент.", "Native mods changed or the client has not been built. Open Mods → Horizon Native and build the client.");
        put("hnmod.building", "Идёт сборка нативного клиента…", "Building the native client…");
        put("hnmod.noEnabled", "Нет включённых нативных модов.", "No native mods are enabled.");
        put("hnmod.launcherInvalid", "Выберите исполняемый файл официального Horizon Launcher.", "Select the official Horizon Launcher executable.");
        put("hnmod.baseInvalid", "Подготовленная база Horizon повреждена: не найдена корректная контрольная сумма архива.", "The prepared Horizon base is damaged: its archive checksum is missing or invalid.");
        put("hnmod.copyBase", "Копирование подготовленной базы в отдельный каталог сборки…", "Copying the prepared base to the separate build directory…");
        put("hnmod.buildFailed", "Horizon Launcher завершил сборку с кодом {0}. Подробности — в журнале ниже.", "Horizon Launcher exited with code {0}. See the log below.");
        put("hnmod.omitted", "Horizon исключил один или несколько включённых модов. Клиент не сохранён для запуска; подробности в журнале.", "Horizon omitted one or more enabled mods. The client was not saved for launch; see the log.");
        put("hnmod.buildMissing", "Horizon Launcher не создал ожидаемую нативную сборку.", "Horizon Launcher did not produce the expected native build.");
        put("hnmod.changedDuringBuild", "Моды изменились во время сборки. Повторите сборку.", "Mods changed during the build. Build again.");
        put("hnmod.usingBuild", "Запуск клиента Horizon с собранными нативными модами.", "Starting the Horizon client with compiled native mods.");
        put("hnmod.setup", "Требуется отдельно установленный официальный Horizon Launcher и подготовленная в нём версия Horizon для Minecraft 26.3. Далее выберите исполняемый файл Horizon Launcher, затем папку готовой версии внутри его versions. AgroLauncher скопирует базу в отдельный каталог; сборка может занять много времени и несколько гигабайт.", "An installed official Horizon Launcher and a Horizon version prepared for Minecraft 26.3 are required. Select the Horizon Launcher executable, then the prepared version folder in its versions directory. AgroLauncher copies the base to a separate directory; building may take substantial time and several gigabytes.");
        put("hnmod.chooseLauncher", "Выберите исполняемый файл Horizon Launcher", "Select the Horizon Launcher executable");
        put("hnmod.chooseBase", "Выберите подготовленную папку версии Horizon из versions", "Select the prepared Horizon version folder from versions");
        put("hnmod.trust", "Доверяю и собрать", "Trust and build");
        put("hnmod.trustWarning", "Официальный сборщик Horizon соберёт отдельный инстанс с модами: {0}. Нативный код и скрипты НЕ изолированы и имеют все права пользователя, включая доступ к файлам и сети. Продолжайте только если доверяете исходникам. Сборщик загружает Rust, исходники и зависимости; могут потребоваться системные инструменты C, много времени и места. Его сетевые соединения используют собственные настройки.", "The official Horizon builder will create a separate instance with mods: {0}. Native code and build scripts are NOT sandboxed and have full user permissions, including file and network access. Continue only if you trust the source. The builder downloads Rust, source and dependencies; system C tools may be required, and the build can take substantial time and space. Its network connections use their own settings.");
        put("hnmod.done", "Нативный клиент собран. Его можно запустить через «Играть».", "The native client is built. Start it with Play.");
        put("hnmod.advanced", "Этот мод использует trait API, зависимости, отдельные crates или post-build: прямая сборка пока их не поддерживает. Клиент с этим модом не был запущен.", "This mod uses trait APIs, dependencies, crates or post-build steps, which direct builds do not yet support. The game was not started.");
        put("hnmod.toolMissing", "Не найден {0}. Установите его и перезапустите AgroLauncher; для Rust нужны rustc и cargo.", "{0} was not found. Install it and restart AgroLauncher; Rust needs rustc and cargo.");
        put("hnmod.toolFailed", "Ошибка команды {0} (код {1}). Подробности в журнале сборки.", "Command {0} failed (exit code {1}). See the build log.");
        put("hnmod.sourceChanged", "Кэш исходников Horizon изменён. Удалите только папку source внутри .agro-native-build и повторите сборку.", "The cached Horizon source changed. Delete only the source folder inside .agro-native-build and retry.");
        put("hnmod.differentPins", "Выбранные моды закреплены за несовместимыми версиями Horizon или horizon-mixins.", "Selected mods pin incompatible Horizon or horizon-mixins revisions.");
        put("hnmod.hooksMismatch", "Закреплённые исходники Horizon не содержат нужную версию native hooks.", "The selected Horizon source does not provide the required native hooks version.");
        put("hnmod.resourceMismatch", "Ресурсы установленной версии Horizon несовместимы с исходниками мода. Сборка остановлена без изменения клиента.", "The installed Horizon resources are incompatible with the mod's pinned source. Build stopped without changing the client.");
        put("hnmod.offline", "Для сборки нативных модов отключите офлайн-режим.", "Turn off offline mode to build native mods.");

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
        put("common.networkError", "ошибка сети", "network error");
        put("downloads.title", "Загрузки", "Downloads");
        put("downloads.empty", "Нет активных или недавних загрузок", "No active or recent downloads");
        put("downloads.pause", "Пауза", "Pause");
        put("downloads.resume", "Продолжить", "Resume");
        put("downloads.cancel", "Отменить", "Cancel");
        put("downloads.status.queued", "в очереди", "queued");
        put("downloads.status.running", "загрузка", "running");
        put("downloads.status.paused", "пауза", "paused");
        put("downloads.status.completed", "готово", "completed");
        put("downloads.status.failed", "ошибка", "failed");
        put("downloads.status.cancelled", "отменено", "cancelled");
        put("unit.gb", "ГБ", "GB");
        put("unit.mb", "МБ", "MB");
        put("unit.kb", "КБ", "KB");
        put("unit.b", "Б", "B");


        put("nav.play", "Играть", "Play");
        put("nav.versions", "Версии", "Versions");
        put("nav.accounts", "Аккаунты", "Accounts");
        put("nav.mods", "Моды", "Mods");
        put("nav.settings", "Настройки", "Settings");
        put("nav.about", "О лаунчере", "About launcher");

        put("about.title", "О лаунчере", "About launcher");
        put("about.subtitle", "Информация о приложении и полезные ссылки", "Application information and useful links");
        put("about.description", "Быстрый и кастомизируемый лаунчер Minecraft", "Fast and customizable Minecraft launcher");
        put("about.version", "Версия", "Version");
        put("about.creator", "Создатель", "Creator");
        put("about.community", "Сообщество", "Community");
        put("about.sourceCode", "Исходный код", "Source code");
        put("about.openFailed", "Не удалось открыть ссылку: {0}", "Could not open link: {0}");
        put("about.browseUnsupported", "Открытие ссылок не поддерживается системой", "Opening links is not supported by the system");

        put("play.version", "Версия", "Version");
        put("play.loader", "Загрузчик", "Loader");
        put("play.account", "Аккаунт", "Account");
        put("play.memory", "Память", "Memory");
        put("play.button", "ИГРАТЬ", "PLAY");
        put("play.quickServer", "Быстрый вход на сервер", "Quick server join");
        put("play.quickServerAddressMissing", "Адрес сервера MargyCraft пока не задан",
                "The MargyCraft server address is not configured yet");
        put("play.stop", "Остановить игру", "Stop game");
        put("play.console", "Консоль", "Console");
        put("console.launcherPrefix", "[Лаунчер]", "[Launcher]");
        put("console.outputClosed", "Поток вывода закрыт: {0}", "Output stream closed: {0}");
        put("play.clear", "Очистить", "Clear");
        put("play.exportAnalysis", "Экспорт анализа", "Export analysis");
        put("play.exportAnalysisError", "Не удалось экспортировать анализ: {0}",
                "Could not export analysis: {0}");
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
        put("repair.button", "Исправление багов", "Bug fixing tool");
        put("repair.title", "Исправление багов", "Bug fixing tool");
        put("repair.starting", "Подготовка безопасного восстановления…", "Preparing safe repair…");
        put("repair.confirm",
                "Будут перепроверены и при необходимости переустановлены:\n"
                        + "• Minecraft {0} (только version JSON/JAR и проверяемые зависимости)\n"
                        + "• загрузчик {1}\n"
                        + "• только моды с явной записью об установке лаунчером\n\n"
                        + "Будут сохранены: миры, скриншоты, ресурспаки, шейдеры, config, options*.txt, "
                        + "servers.dat*, usercache.json, логи, crash-reports, пользовательские и неизвестные моды/файлы.\n"
                        + "Каталог игры целиком не удаляется. Продолжить?",
                "The following will be verified and reinstalled when needed:\n"
                        + "• Minecraft {0} (version JSON/JAR and verifiable dependencies only)\n"
                        + "• {1} loader\n"
                        + "• only mods explicitly recorded as launcher-installed\n\n"
                        + "Preserved: worlds, screenshots, resource packs, shaders, config, options*.txt, "
                        + "servers.dat*, usercache.json, logs, crash reports, user and unknown mods/files.\n"
                        + "The game directory is never deleted. Continue?");
        put("repair.success", "Восстановление завершено. Управляемых модов проверено: {0}",
                "Repair completed. Launcher-managed mods verified: {0}");
        put("repair.error", "Восстановление не выполнено: {0}", "Repair failed: {0}");
        put("repair.started", "Восстановление запущено: Minecraft {0}, загрузчик {1}{2}",
                "Repair started: Minecraft {0}, loader {1}{2}");
        put("repair.recommendedVersion", " (рекомендуемая версия)", " (recommended version)");
        put("repair.completedDetails", "Восстановление завершено: профиль {0}, управляемых модов обработано: {1}",
                "Repair completed: profile {0}, launcher-managed mods processed: {1}");
        put("repair.consoleError", "ОШИБКА восстановления: {0}", "REPAIR ERROR: {0}");
        put("repair.backupCreated", "Резервная копия создана до восстановления: {0}",
                "Backup created before repair: {0}");
        put("repair.stageMinecraft", "Исправление Minecraft", "Repairing Minecraft");
        put("repair.stageLoader", "Исправление загрузчика", "Repairing loader");
        put("repair.stageManagedMods", "Исправление управляемых модов", "Repairing launcher-managed mods");
        put("repair.stageCompleted", "Исправление завершено", "Repair completed");
        put("repair.modConflict", "Управляемый мод пропущен: одновременно существуют {0} и {1}",
                "Launcher-managed mod skipped: both {0} and {1} exist");


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
        put("versions.offlineList", "Показаны установленные версии ({0}) , список Mojang недоступен",
                "Showing installed versions ({0}) , Mojang list unavailable");
        put("versions.noVersions", "Список версий недоступен: нет соединения и нет установленных версий",
                "No versions available: no connection and nothing installed");
        put("versions.loaderUnavailable", "Загрузчик недоступен", "Loader unavailable");
        put("versions.typeRelease", "релиз", "release");
        put("versions.typeSnapshot", "снапшот", "snapshot");
        put("versions.typeBeta", "бета", "beta");
        put("versions.typeAlpha", "альфа", "alpha");
        put("loader.recommended", "рекомендуется", "recommended");
        put("theme.qtSystem", "Тема QT", "QT Theme");
        put("theme.qtSystem.desc",
                "Цвета системной палитры рабочего стола (Qt/KDE через доступные Swing defaults)",
                "Desktop system palette (Qt/KDE through available Swing defaults)");


        put("accounts.title", "Аккаунты", "Accounts");
        put("accounts.subtitle",
                "Выберите оффлайн-профиль, Ely.by или аккаунт Microsoft.",
                "Choose an offline profile, Ely.by or a Microsoft account.");
        put("accounts.saved", "Сохранённые аккаунты", "Saved accounts");
        put("accounts.makeActive", "Сделать активным", "Set as active");
        put("accounts.active", "активный", "active");
        put("accounts.offline", "Оффлайн", "Offline");
        put("accounts.microsoft", "Microsoft", "Microsoft");
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
        put("accounts.oauth", "Войти через OAuth Ely.by", "Sign in with Ely.by OAuth");
        put("accounts.oauthNotConfigured",
                "Настройте endpoints OAuth Ely.by и client ID в settings.json",
                "Configure the Ely.by OAuth endpoints and client ID in settings.json");
        put("accounts.oauthWaiting", "Ожидание авторизации в браузере…",
                "Waiting for authorization in the browser…");
        put("accounts.fillCredentials", "Заполните логин и пароль", "Enter your login and password");
        put("accounts.checking", "Проверяем данные…", "Checking credentials…");
        put("accounts.signedIn", "Вход выполнен: {0}", "Signed in: {0}");
        put("accounts.activeAccount", "Активный аккаунт: {0}", "Active account: {0}");
        put("accounts.deleteConfirm", "Удалить аккаунт {0}?", "Delete account {0}?");
        put("accounts.deleted", "Аккаунт удалён", "Account deleted");

        put("mods.title", "Моды", "Mods");
        put("mods.subtitle", "Каталог Modrinth: поиск, установка и автоматическая подтяжка зависимостей",
                "Modrinth catalog: search, install and automatic dependency resolution");
        put("mods.catalog", "Моды", "Mods");
        put("mods.resourcePacks", "Ресурспаки", "Resource packs");
        put("mods.shaders", "Шейдеры", "Shaders");
        put("mods.installed", "Установленные моды", "Installed mods" );
        put("mods.installedTitle", "Установленные моды", "Installed mods");
        put("mods.curseForge", "CurseForge в браузере", "CurseForge in browser");
        put("mods.curseForgeTooltip",
                "CurseForge не даёт доступа к каталогу без ключа разработчика, поэтому сайт "
                        + "открывается во внешнем браузере. Скачанные файлы добавьте кнопкой «Добавить файлы».",
                "CurseForge does not allow catalog access without a developer key, so the site "
                        + "opens in an external browser. Add downloaded files with the Add files button.");
        put("mods.curseForgeOpened",
                "CurseForge открыт в браузере , скачанные файлы добавьте кнопкой «Добавить файлы»",
                "CurseForge opened in browser , add downloaded files with the Add files button");
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
                "Выбран {0} , моды не будут загружены. Выберите Fabric, Forge, Quilt или NeoForge.",
                "{0} is selected , mods will not load. Choose Fabric, Forge, Quilt or NeoForge.");
        put("mods.folderTitle", "Папка модов", "Mods folder");
        put("mods.openFolderFailed", "Не удалось открыть папку: {0}", "Failed to open folder: {0}");
        put("mods.openBrowserFailed", "Не удалось открыть браузер: {0}", "Failed to open browser: {0}");

        put("update.title", "Обновление AgroLauncher", "AgroLauncher update");
        put("update.downloadPrompt", "Доступна новая версия AgroLauncher {0}. Скачать обновление?",
                "A new AgroLauncher version {0} is available. Download the update?");
        put("update.declined", "Загрузка обновления {0} отменена",
                "Update {0} download was declined");
        put("update.manual", "Автоустановка недоступна в этом режиме или не настроены доверенный ключ и подписанный релиз. Открыть страницу GitHub?",
                "Automatic installation is unavailable in this mode, or the trusted key and signed release are not configured. Open the GitHub release page?");
        put("update.downloading", "Скачивание и проверка обновления {0}…",
                "Downloading and verifying update {0}…");
        put("update.ready", "Обновление скачано и проверено. Перезапустить лаунчер для установки?",
                "The update has been downloaded and verified. Restart the launcher to install it?");
        put("update.failed", "Не удалось установить обновление: {0}",
                "Could not install the update: {0}");
        put("update.checkFailed", "Не удалось проверить обновления: {0}",
                "Could not check for updates: {0}");
        put("builds.independent", "Независимые сборки:", "Independent builds:");
        put("builds.createSnapshot", "Создать снимок", "Create snapshot");
        put("builds.delete", "Удалить сборку", "Delete build");
        put("builds.namePrompt", "Название сборки:", "Build name:");
        put("builds.newTitle", "Новая сборка", "New build");
        put("builds.defaultName", "Сборка {0}", "Build {0}");
        put("builds.created", "Создан независимый снимок: {0}", "Independent snapshot created: {0}");
        put("builds.selected", "Выбрана сборка: {0}", "Build selected: {0}");
        put("builds.deleteConfirm",
                "Удалить сборку «{0}» и все данные её изолированной игры? Отменить это действие нельзя. Перед удалением закройте игру.",
                "Delete build “{0}” and all data in its isolated game? This cannot be undone. Close the game before deleting.");
        put("builds.deleted", "Сборка «{0}» удалена", "Build “{0}” deleted");
        put("builds.deleteFailed", "Не удалось удалить сборку «{0}»: {1}",
                "Could not delete build “{0}”: {1}");
        put("builds.modCount", "{0} модов", "{0} mods");
        put("builds.selectVersion", "Выберите версию Minecraft", "Select a Minecraft version");
        put("builds.loaderUnsupported", "Выбранный загрузчик не поддерживает моды",
                "The selected loader does not support mods");


        put("browser.searchPlaceholder.mod", "Поиск модов: Sodium и т. д.",
                "Search mods: Sodium, etc.");
        put("browser.searchPlaceholder.resourcepack", "Поиск ресурспаков: Faithful и т. д.",
                "Search resource packs: Faithful, etc.");
        put("browser.searchPlaceholder.shader", "Поиск шейдеров: Complementary и т. д.",
                "Search shaders: Complementary, etc.");
        put("browser.allCategories", "Все категории", "All categories");
        put("browser.loadMore", "Показать ещё", "Load more");
        put("browser.loadingCatalog", "Загрузка каталога…", "Loading catalog…");
        put("browser.nothingFound", "Ничего не найдено , измените запрос или фильтры",
                "Nothing found , change your query or filters");
        put("browser.shown", "Показано {0} из {1}", "Showing {0} of {1}");
        put("browser.loadFailed", "Не удалось загрузить каталог: {0}", "Failed to load catalog: {0}");
        put("browser.unavailable", "Каталог модов недоступен: {0}", "Mod catalog unavailable: {0}");
        put("browser.filterByProfile", "Фильтр по профилю: ", "Profile filter: ");
        put("browser.filterNotSet", "не задан , выберите версию и загрузчик на вкладке «Версии»",
                "not set , choose a version and loader on the Versions tab");
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
        put("modDialog.install.mod", "Установить мод", "Install mod");
        put("modDialog.install.resourcepack", "Установить ресурспак", "Install resource pack");
        put("modDialog.install.shader", "Установить шейдер", "Install shader");
        put("modDialog.installed", "Установлен", "Installed");
        put("modDialog.installed.mod", "Мод установлен", "Mod installed");
        put("modDialog.installed.resourcepack", "Ресурспак установлен", "Resource pack installed");
        put("modDialog.installed.shader", "Шейдер установлен", "Shader installed");
        put("modDialog.alreadyInstalled", "Уже установлен", "Already installed");
        put("modDialog.openPage", "Открыть страницу", "Open page");
        put("modDialog.withDependencies", "Установить обязательные зависимости",
                "Install required dependencies");
        put("modDialog.loadingVersions", "Загрузка версий…", "Loading versions…");
        put("modDialog.noVersions", "Нет версий под выбранный загрузчик",
                "No versions for the selected loader");
        put("modDialog.versionsFailed", "Ошибка загрузки версий: {0}", "Failed to load versions: {0}");
        put("modDialog.installing", "Установка…", "Installing…");
        put("modDialog.installing.mod", "Установка мода…", "Installing mod…");
        put("modDialog.installing.resourcepack", "Установка ресурспака…", "Installing resource pack…");
        put("modDialog.installing.shader", "Установка шейдера…", "Installing shader…");
        put("modDialog.requiresDeps", "Требует зависимостей: {0}", "Requires dependencies: {0}");
        put("modDialog.depsWillInstall", " , будут установлены", " , will be installed");
        put("modDialog.depsDisabled", " , установка отключена", " , installation disabled");
        put("modDialog.browserFailed", "Не удалось открыть браузер: {0}", "Failed to open browser: {0}");

        put("install.alreadyInstalled", "Уже установлен: {0}", "Already installed: {0}");
        put("install.installed", "Установлен: {0} ({1})", "Installed: {0} ({1})");
        put("install.downloadFailed", "Ошибка загрузки {0}: {1}", "Failed to download {0}: {1}");
        put("install.fetchingDep", "Подтягиваем зависимость: {0}", "Fetching dependency: {0}");
        put("install.depNotFound", "Не найдена подходящая версия зависимости: {0}",
                "No suitable dependency version found: {0}");
        put("install.stage", "Установка мода", "Installing mod");
        put("install.stage.mod", "Установка мода", "Installing mod");
        put("install.stage.resourcepack", "Установка ресурспака", "Installing resource pack");
        put("install.stage.shader", "Установка шейдера", "Installing shader");
        put("install.summaryInstalled", "Установлено: {0}", "Installed: {0}");
        put("install.summarySkipped", ", уже было: {0}", ", already present: {0}");
        put("install.summaryFailed", ", с ошибкой: {0}", ", failed: {0}");

        put("settings.title", "Настройки", "Settings");
        put("settings.subtitle", "Память, Java, оформление и параметры запуска",
                "Memory, Java, appearance and launch options");
        put("settings.general", "Общие", "General");
        put("settings.appearance", "Интерфейс", "Appearance");

        put("settings.performance", "Производительность и очистка", "Performance and cleanup");
        put("settings.downloadPerHost", "Соединений на сервер", "Connections per host");
        put("settings.parallelDownloads", "Параллельных загрузок", "Parallel downloads");
        put("settings.parallelDownloadsRestart", "применится после перезапуска лаунчера", "takes effect after restarting the launcher");
        put("settings.parallelDownloadsSaved", "Число параллельных загрузок сохранено; оно применится после перезапуска", "Parallel download count saved; it will take effect after restart");
        put("settings.metadataCacheEnabled", "Использовать кэш метаданных", "Use metadata cache");
        put("settings.metadataCacheTtl", "Срок хранения метаданных", "Metadata cache lifetime");
        put("settings.minutes", "минут", "minutes");
        put("settings.cleanupCache", "Очистить общий кэш", "Clean generic cache");
        put("settings.cleanupUnused", "Удалить неиспользуемые файлы", "Remove unused files");
        put("settings.cleanupHint", "Общий кэш очищается отдельно от неиспользуемых библиотек, ресурсов и управляемых Java", "Generic cache is cleaned separately from unused libraries, assets, and managed Java runtimes");
        put("settings.cleanupTitle", "Очистка", "Cleanup");
        put("settings.cleanupScanning", "Поиск файлов для безопасной очистки…", "Scanning for files that can be safely cleaned…");
        put("settings.cleanupNothing", "Файлы для очистки не найдены.", "No files to clean were found.");
        put("settings.cleanupConfirmTitle", "Подтвердите очистку", "Confirm cleanup");
        put("settings.cleanupPreview", "Будет удалено объектов: {0}\nМожно освободить: {1}\n\nПо категориям:", "Items to remove: {0}\nReclaimable: {1}\n\nBy category:");
        put("settings.cleanupCategoryTotal", "{0} объектов, {1}", "{0} items, {1}");
        put("settings.cleanupConfirm", "Удалить именно этот план? Операцию нельзя отменить.", "Execute this exact plan? This action cannot be undone.");
        put("settings.cleanupRunning", "Очистка…", "Cleaning…");
        put("settings.cleanupComplete", "Удалено объектов: {0}\nОсвобождено: {1}", "Deleted items: {0}\nReclaimed: {1}");
        put("settings.cleanupPartial", "Не удалось удалить объектов: {0}", "Items that could not be deleted: {0}");
        put("settings.cleanupReclaimed", "Очистка завершена, освобождено {0}", "Cleanup completed, reclaimed {0}");
        put("settings.cleanupFailed", "Не удалось выполнить очистку: {0}", "Cleanup failed: {0}");
        put("settings.cleanupCategory.LIBRARY", "Библиотеки", "Libraries");
        put("settings.cleanupCategory.ASSET_OBJECT", "Объекты ресурсов", "Asset objects");
        put("settings.cleanupCategory.ASSET_INDEX", "Индексы ресурсов", "Asset indexes");
        put("settings.cleanupCategory.ASSET_VIRTUAL", "Виртуальные ресурсы", "Virtual assets");
        put("settings.cleanupCategory.VERSION_METADATA", "Метаданные версий", "Version metadata");
        put("settings.cleanupCategory.VERSION_JAR", "Файлы версий", "Version files");
        put("settings.cleanupCategory.MANAGED_RUNTIME", "Управляемые Java", "Managed Java runtimes");
        put("settings.cleanupCategory.CACHE", "Общий кэш", "Generic cache");

        put("settings.memory", "Оперативная память", "Memory");
        put("settings.maxRam", "Выделено игре (-Xmx)", "Allocated to the game (-Xmx)");
        put("settings.minRam", "Начальный размер (-Xms), МБ", "Initial size (-Xms), MB");
        put("settings.minRamHint", "0 , не задавать", "0 , do not set");
        put("settings.ramAllocated", "Выделено памяти: {0}", "Memory allocated: {0}");
        put("settings.ramTotal", "всего в системе: {0}", "system total: {0}");
        put("settings.ramUnknown", "объём памяти определить не удалось", "could not detect system memory");
        put("settings.ramTooMuch", "Слишком много , системе может не хватить памяти",
                "Too much , the system may run out of memory");
        put("settings.ramTooLittle", "Для версий 1.18+ с модами рекомендуется от 4 ГБ",
                "For 1.18+ with mods, 4 GB or more is recommended");

        put("settings.java", "Java", "Java");
        put("settings.javaMode", "Режим выбора Java", "Java selection mode");
        put("settings.useManagedJava", "Использовать встроенную Java (скачивается автоматически)",
                "Use bundled Java (downloaded automatically)");
        put("settings.offlineMode", "Ограниченный офлайн-режим", "Limited offline mode");
        put("settings.offlineModeTooltip",
                "Использовать только уже загруженные данные; сетевые функции будут недоступны",
                "Use only previously downloaded data; network features will be unavailable");
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
        put("settings.javaNone", "Java не найдена , скачайте нужную версию",
                "No Java found , download a version");
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
        put("appearance.interfaceStyle", "Стиль интерфейса", "Interface style");
        put("appearance.materialYou", "Material You", "Material You");
        put("appearance.materialYouHint", "Увеличенные скругления, pill-элементы, карточки и более свободные отступы",
                "Larger corners, pill controls, cards and roomier spacing");
        put("appearance.materialYouChanged", "Стиль Material You: {0}", "Material You style: {0}");
        put("appearance.enabled", "включён", "enabled");
        put("appearance.disabled", "выключен", "disabled");
        put("appearance.theme", "Цветовая тема", "Color theme");
        put("appearance.themeSelected", "Тема: {0}", "Theme: {0}");
        put("appearance.customColor", "Свой акцентный цвет", "Custom accent color");
        put("appearance.customColorHint",
                "Остальные оттенки темы рассчитываются автоматически на основе выбранного цвета",
                "The rest of the palette is derived automatically from the chosen color");
        put("appearance.gradient", "Градиент", "Gradient");
        put("appearance.gradientHint", "Выберите пресет или задайте до 10 цветов через запятую",
                "Choose a preset or enter up to 10 comma-separated colors");
        put("appearance.gradientColorsHint", "Например: #FF0000,#FF8800,#FFFF00",
                "Example: #FF0000,#FF8800,#FFFF00");
        put("appearance.gradientNone", "Без градиента", "No gradient");
        put("appearance.gradientBlazingFlame", "Пылающее пламя", "Blazing Flame");
        put("appearance.gradientDeepSea", "Глубокое море", "Deep Sea");
        put("appearance.gradientMaterialYou", "Material You", "Material You");
        put("appearance.gradientRainbow", "Радуга", "Rainbow");
        put("appearance.gradientMonochrome", "Монохром", "Monochrome");
        put("appearance.gradientAgedWine", "Состаренно винный", "Aged Wine");
        put("appearance.gradientMojito", "Мохито", "Mojito");
        put("appearance.gradientNeon", "Неон", "Neon");
        put("appearance.gradientStylishOrange", "Стильный оранжевый", "Stylish Orange");
        put("appearance.gradientAncient", "Древний", "Ancient");
        put("appearance.gradientCustom", "Свой градиент", "Custom gradient");
        put("appearance.gradientSelected", "Выбран градиент: {0}", "Gradient selected: {0}");
        put("gradient.none.desc", "Обычная тема без градиента", "Regular theme without a gradient");
        put("gradient.blazingFlame.desc", "Красный, оранжевый и золотой", "Red, orange and gold");
        put("gradient.deepSea.desc", "Глубокий синий и бирюзовый", "Deep blue and cyan");
        put("gradient.materialYou.desc", "Светло-фиолетовый → более светлый фиолетовый",
                "Light purple → lighter purple");
        put("gradient.rainbow.desc", "Яркий многоцветный спектр", "Bright multicolor spectrum");
        put("gradient.monochrome.desc", "Графитовый и серебристый", "Graphite and silver");
        put("gradient.agedWine.desc", "Винный, состаренное золото и глубокий чёрный",
                "Wine, aged gold and deep black");
        put("gradient.mojito.desc", "Мягкий лайм, лесная зелень и фиолетовый",
                "Soft lime, forest green and purple");
        put("gradient.neon.desc", "Неоновая бирюза, розовый и тёмный фон",
                "Neon cyan, pink and a dark background");
        put("gradient.stylishOrange.desc", "Тёплый кремовый, яркий оранжевый и графит",
                "Warm cream, vivid orange and graphite");
        put("gradient.ancient.desc", "Старое золото, глубокая бирюза и слоновая кость",
                "Old gold, deep teal and ivory");
        put("gradient.custom.desc", "Задайте собственные цвета", "Choose your own colors");
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
                " , при размытии показывается первый кадр без анимации",
                " , blur shows the first frame without animation");
        put("appearance.backgroundFailed", "Не удалось загрузить изображение", "Failed to load the image");
        put("appearance.backgroundRemoved", "Фоновое изображение убрано", "Background image removed");
        put("appearance.blur", "Размыть фон", "Blur background");
        put("appearance.blurRadius", "Радиус размытия", "Blur radius");
        put("appearance.dim", "Затемнение фона", "Background dimming");
        put("appearance.panelOpacity", "Плотность панелей", "Panel opacity");
        put("appearance.windowOpacity", "Прозрачность окна", "Window opacity");
        put("appearance.fileNotFound", "Файл не найден: {0}", "File not found: {0}");
        put("appearance.formatUnsupported", "Формат изображения не поддерживается",
                "Image format is not supported");
        put("appearance.gifReadFailed", "Не удалось прочитать GIF", "Failed to read the GIF");
        put("appearance.readError", "Ошибка чтения: {0}", "Read error: {0}");


        put("theme.custom", "Свой цвет/градиент", "Custom color/gradient");
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

        put("launch.command", "Запуск: {0}", "Launching: {0}");
        put("launch.envApplied", "Переменные окружения для игры: {0}",
                "Game environment variables: {0}");
        put("launch.javaSelected", "Требуется Java {0}, выбрана Java {1} ({2})",
                "Java {0} required, using Java {1} ({2})");
        put("launch.ramRecommended", "Рекомендуемая память: {0}–{1} МБ", "Recommended RAM: {0}–{1} MB");
        put("launch.customJavaWarning",
                "ПРЕДУПРЕЖДЕНИЕ: для выбранной версии Minecraft/Forge требуется Java {0}, но выбран пользовательский runtime Java {1}. Custom runtime может вызвать ошибку запуска; запуск будет продолжен.",
                "WARNING: this Minecraft/Forge version requires Java {0}, but a custom Java {1} runtime is selected. A custom runtime may fail to start the game; launching anyway.");

        put("auth.oauthExpired", "Сессия Ely.by OAuth истекла; войдите заново",
                "The Ely.by OAuth session has expired; sign in again");
        put("microsoft.checkingOwnership", "Проверка лицензии Minecraft Java…", "Checking Minecraft Java ownership…");
        put("microsoft.noOwnership", "У этого аккаунта нет подтверждённого доступа к Minecraft Java Edition.", "This account does not have confirmed access to Minecraft Java Edition.");
        put("microsoft.ownershipRequiresOnline", "Для проверки лицензии Microsoft отключите офлайн-режим и подключитесь к сети.", "Go online and disable offline mode to verify Minecraft ownership.");
        put("horizon.sessionUnsupported", "Сборка Horizon не поддерживает безопасную передачу сессии Microsoft. Обновите или пересоберите Horizon.", "This Horizon build does not support secure Microsoft session transfer. Update or rebuild Horizon.");
        put("horizon.onlineNotice", "26.3 Rust: используется проверенная сессия Microsoft. Java-моды и шейдеры несовместимы.", "26.3 Rust: using the verified Microsoft session. Java mods and shaders are unsupported.");
        put("microsoft.signIn", "Войти через браузер", "Sign in with browser");
        put("microsoft.hint", "Вход в Minecraft Java через аккаунт Microsoft.",
                "Sign in to Minecraft Java with a Microsoft account.");
        put("microsoft.offline", "Для входа отключите офлайн-режим.", "Go online to sign in.");
        put("microsoft.browserManualTitle", "Открыть ссылку вручную", "Open link manually");
        put("microsoft.browserManualHint", "Браузер не открылся. Скопируйте ссылку и откройте её на этом компьютере.",
                "The browser did not open. Copy the link and open it on this computer.");
        put("microsoft.browserManualWarning", "Не передавайте ссылку другим. После открытия нажмите OK.",
                "Do not share this link. Press OK after opening it.");
        put("microsoft.browserUnavailable", "Не удалось открыть системный браузер.", "The system browser is unavailable.");
        put("microsoft.timeout", "Время ожидания входа истекло. Попробуйте снова.", "Sign-in timed out. Try again.");
        put("microsoft.interrupted", "Вход прерван.", "Sign-in was interrupted.");
        put("microsoft.callbackFailed", "Не удалось получить ответ от браузера.", "Could not receive the browser callback.");
        put("microsoft.denied", "Авторизация Microsoft отменена или отклонена.", "Microsoft authorization was cancelled or denied.");
        put("microsoft.browserReceived", "Ответ получен. Вернитесь в AgroLauncher, чтобы завершить вход.",
                "Response received. Return to AgroLauncher to finish signing in.");
        put("microsoft.browserDenied", "Авторизация не выполнена. Вернитесь в AgroLauncher.",
                "Authorization was not completed. Return to AgroLauncher.");
        put("microsoft.signInAgain", "Сессия Microsoft истекла. Войдите заново.", "Microsoft session expired. Sign in again.");
        put("microsoft.accountChanged", "Профиль Minecraft изменился. Войдите в нужный аккаунт заново.",
                "The Minecraft profile changed. Sign in to the intended account again.");
        put("microsoft.invalidResponse", "Некорректный ответ службы авторизации.", "Invalid authentication service response.");
        put("microsoft.httpError", "Ошибка {0}: HTTP {1}.", "{0} failed: HTTP {1}.");
        put("microsoft.xstsFailed", "Ошибка Xbox XSTS: HTTP {0}, код {1}.", "Xbox XSTS failed: HTTP {0}, code {1}.");
        put("microsoft.appNotApproved",
                "Minecraft Services отклонил приложение (HTTP 403). Скорее всего лаунчер еще не был одобрен Mojang. ",
                "Minecraft Services rejected the application (HTTP 403). Most likely launcher isn't approved by Mojang yet. ");
        put("microsoft.noProfile", "Профиль Minecraft Java не найден. Проверьте доступ к игре и создание профиля.",
                "Minecraft Java profile not found. Check game access and profile setup.");

        put("jre.stageSearch", "поиск сборки", "looking up a build");
        put("jre.stageDownload", "загрузка {0}", "downloading {0}");
        put("jre.stageExtract", "распаковка", "extracting");
        put("jre.stageDone", "готово", "done");
        put("jre.installed", "Java {0} установлена: {1}", "Java {0} installed: {1}");
        put("jre.sourceSystem", "система", "system");
        put("jre.sourceManaged", "встроенная", "bundled");

        put("authlib.updateFailed", "Не удалось проверить обновление authlib-injector, используется локальная копия",
                "Could not check for authlib-injector updates; using the local copy");
        put("authlib.downloading", "загрузка {0}", "downloading {0}");
        put("authlib.ready", "authlib-injector {0} готов", "authlib-injector {0} is ready");

        put("loader.installSummary", "{0} {1} для Minecraft {2}", "{0} {1} for Minecraft {2}");
        put("loader.stageInstalling", "Установка {0}", "Installing {0}");
        put("loader.stageProfile", "получение профиля", "fetching the profile");
        put("loader.profileSaved", "Профиль сохранён: {0}", "Profile saved: {0}");
        put("loader.installed", "{0} установлен: {1}", "{0} installed: {1}");
        put("loader.stageDownload", "загрузка {0}", "downloading {0}");
        put("loader.filesProcessed", "файлов обработано: {0}", "files processed: {0}");

        put("forge.installNotRequired", "Установка не требуется: проверен профиль {0}",
                "Installation is not required: profile {0} verified");
        put("forge.installRequired", "Установка требуется: marker отсутствует или профиль неполон",
                "Installation is required: the marker is missing or the profile is incomplete");
        put("forge.forceReinstall", "Принудительная переустановка запрошена Repair",
                "Forced reinstallation requested by Repair");
        put("forge.stageTitle", "Установка {0}", "Installing {0}");
        put("forge.stageDownloadInstaller", "загрузка установщика", "downloading the installer");
        put("forge.stageRunInstaller", "запуск установщика", "running the installer");
        put("forge.stageFindProfile", "поиск профиля", "locating the profile");
        put("forge.processorRetry", "Промежуточные файлы не совпали с эталоном , очищаем и повторяем",
                "Intermediate files failed verification; cleaning up and retrying");
        put("forge.profileVerified", "Профиль установлен и проверен: {0}", "Profile installed and verified: {0}");
        put("forge.profileSelected", "Generated profile выбран: {0}", "Generated profile selected: {0}");
        put("forge.markerInvalid", "Marker установки недействителен: {0}", "Installation marker is invalid: {0}");
        put("forge.checkingVanilla", "Проверка ванильного клиента", "Verifying the vanilla client");
        put("forge.vanillaCheckSkipped", "Список версий недоступен , проверка ванильного клиента пропущена",
                "Version manifest unavailable; skipping the vanilla client check");
        put("forge.vanillaBroken", "Ванильный {0}.jar повреждён или изменён , скачиваем заново",
                "The vanilla {0}.jar is corrupted or modified; re-downloading");
        put("forge.vanillaRestored", "файл восстановлен", "file restored");
        put("forge.vanillaOk", "файл в порядке", "file is fine");
        put("forge.cleanupFailed", "Не удалось очистить промежуточные файлы: {0}",
                "Could not clean intermediate files: {0}");
        put("forge.cleanupRemoved", "Удалено промежуточных файлов: {0}", "Removed intermediate files: {0}");
        put("forge.patching", "Патчинг клиента", "Patching the client");

        put("optifine.stageTitle", "Установка OptiFine", "Installing OptiFine");
        put("optifine.stagePatch", "патчинг клиента", "patching the client");
        put("optifine.stageLaunchwrapper", "извлечение launchwrapper", "extracting launchwrapper");
        put("optifine.stageProfile", "создание профиля", "creating the profile");
        put("optifine.patching", "Патчинг OptiFine", "Patching OptiFine");
        put("optifine.launchwrapperExtracted", "launchwrapper извлечён: {0}", "launchwrapper extracted: {0}");
        put("optifine.launchwrapperFallback",
                "Собственный launchwrapper не найден, используется net.minecraft:launchwrapper:1.12",
                "No bundled launchwrapper found; using net.minecraft:launchwrapper:1.12");
        put("optifine.addedToMods", "OptiFine добавлен в mods: {0}", "OptiFine copied into mods: {0}");

        put("natives.missingSkip", "Нативная библиотека не найдена, пропуск: {0}",
                "Native library not found, skipping: {0}");
        put("resolver.downloadingVersion", "Загрузка описания версии {0}", "Downloading version metadata {0}");
        put("assets.noIndex", "У версии нет индекса ассетов , пропускаем",
                "The version has no asset index; skipping");

        put("install.noFile", "{0} (нет файла для загрузки)", "{0} (no file to download)");
        put("install.depNoVersion", "зависимость {0} (нет подходящей версии)",
                "dependency {0} (no matching version)");
        put("install.depError", "зависимость {0}: {1}", "dependency {0}: {1}");

        put("browser.untitled", "Без названия", "Untitled");
        put("unit.million", "млн", "M");
        put("unit.thousand", "тыс.", "K");

        put("diag.JAVA_MISMATCH.title", "Несовместимая версия Java", "Incompatible Java version");
        put("diag.JAVA_MISMATCH.advice", "Выберите Java, требуемую этой версией Minecraft/loader.",
                "Select the Java version required by this Minecraft/loader version.");
        put("diag.OUT_OF_MEMORY.title", "Недостаточно памяти Java", "Not enough Java memory");
        put("diag.OUT_OF_MEMORY.advice", "Увеличьте RAM или уменьшите число модов/ресурспаков.",
                "Increase RAM or reduce the number of mods/resource packs.");
        put("diag.MISSING_CLASS.title", "Отсутствует класс или модуль", "Missing class or module");
        put("diag.MISSING_CLASS.advice", "Проверьте библиотеки, loader и совместимость модов.",
                "Check libraries, the loader and mod compatibility.");
        put("diag.MOD_DEPENDENCY.title", "Не выполнена зависимость мода", "Mod dependency not satisfied");
        put("diag.MOD_DEPENDENCY.advice", "Установите требуемую версию зависимости или удалите несовместимый мод.",
                "Install the required dependency version or remove the incompatible mod.");
        put("diag.MIXIN_FAILURE.title", "Ошибка Mixin", "Mixin failure");
        put("diag.MIXIN_FAILURE.advice", "Проверьте совместимость модов и версий loader.",
                "Check mod and loader version compatibility.");
        put("diag.NATIVE_OPENGL.title", "Ошибка native/OpenGL", "Native/OpenGL failure");
        put("diag.NATIVE_OPENGL.advice", "Обновите видеодрайвер и проверьте архитектуру Java/native-библиотек. На NVIDIA+Wayland при ошибке OpenGL добавьте __GLX_VENDOR_LIBRARY_NAME=nvidia в настройках «Переменные окружения».",
                "Update the video driver and check the Java/native library architecture. On NVIDIA+Wayland with OpenGL failures add __GLX_VENDOR_LIBRARY_NAME=nvidia to Settings, Environment variables.");
        put("diag.CORRUPT_ARCHIVE.title", "Повреждён JAR/ZIP", "Corrupted JAR/ZIP");
        put("diag.CORRUPT_ARCHIVE.advice", "Повторно загрузите повреждённый файл или выполните восстановление.",
                "Re-download the corrupted file or run a repair.");
        put("diag.AUTH_NETWORK.title", "Ошибка авторизации или сети", "Authentication or network failure");
        put("diag.AUTH_NETWORK.advice", "Проверьте подключение, время системы и повторите вход.",
                "Check the connection and system clock, then sign in again.");
        put("diag.implicatedMods", "Моды", "Mods");

        put("update.checking", "Проверка обновлений…", "Checking for updates…");
        put("update.latest", "У вас установлена последняя версия", "You are running the latest version");
        put("about.updates", "Обновления", "Updates");
        put("about.checkUpdates", "Проверить обновления", "Check for updates");
        put("about.autoCheck", "проверять при запуске", "check on startup");

        put("nav.instances", "Инстансы", "Instances");
        put("instances.scanFailed", "Не удалось обновить список инстансов: {0}", "Could not refresh instances: {0}");
        put("instances.title", "Инстансы", "Instances");
        put("instances.subtitle", "Установленные копии игры и независимые мод-сборки",
                "Installed game copies and independent mod builds");
        put("instances.installations", "Установки", "Installations");
        put("instances.builds", "Мод-сборки", "Mod builds");
        put("instances.play", "Играть", "Play");
        put("instances.selected", "Выбрано: {0}", "Selected: {0}");
        put("instances.deleteConfirm", "Удалить игровую директорию «{0}»? Моды и миры этой установки будут удалены безвозвратно.",
                "Delete the game directory of \"{0}\"? Mods and worlds of this installation will be removed permanently.");
        put("instances.deleted", "Установка удалена: {0}", "Installation deleted: {0}");
        put("instances.empty", "Установок пока нет: запустите игру или импортируйте модпак , они появятся здесь",
                "No installations yet: launch the game or import a modpack and they will appear here");
        put("instances.summary", "Установок: {0}", "Installations: {0}");
        put("instances.playtime", "{0} ч {1} мин", "{0}h {1}m");
        put("instances.worlds", "Миры", "Worlds");
        put("instances.screenshots", "Скриншоты", "Screenshots");
        put("instances.openFile", "Открыть", "Open");
        put("instances.emptyFolder", "Папка пуста", "The folder is empty");
        put("instances.deleteEntryConfirm", "Удалить \"{0}\" безвозвратно?", "Delete \"{0}\" permanently?");
        put("mods.searchPlaceholder", "Поиск по установленным модам…", "Search installed mods…");
        put("mods.modpackFilter", "Модпаки Modrinth (.mrpack) и CurseForge (.zip)",
                "Modrinth (.mrpack) and CurseForge (.zip) modpacks");
        put("modpack.cfFileFailed", "CurseForge: не удалось разрешить файл {0} проекта {1}",
                "CurseForge: failed to resolve file {0} of project {1}");
        put("settings.exportSettings", "Экспорт настроек", "Export settings");
        put("settings.importSettings", "Импорт настроек", "Import settings");
        put("settings.settingsExported", "Настройки экспортированы: {0}", "Settings exported: {0}");
        put("settings.settingsImportedRestart", "Настройки импортированы. Перезапустите лаунчер, чтобы применить их полностью",
                "Settings imported. Restart the launcher to apply them fully");
        put("settings.importFailed", "Не удалось импортировать настройки: {0}",
                "Failed to import settings: {0}");
        put("settings.importConfirm", "Импортировать настройки? Текущие будут заменены.",
                "Import settings? Current ones will be replaced.");

        put("mods.modpacks", "Модпаки", "Modpacks");
        put("mods.exportMrpack", "Экспорт .mrpack", "Export .mrpack");
        put("mods.exportMrpackTooltip", "Собрать текущую установку в файл Modrinth-модпака",
                "Bundle the current installation into a Modrinth modpack file");
        put("browser.searchPlaceholder.modpack", "Поиск модпаков: Fabulously Optimized и т. д.",
                "Search modpacks: Fabulously Optimized, etc.");
        put("modpack.installed", "Модпак «{0}» установлен для Minecraft {1}",
                "Modpack \"{0}\" installed for Minecraft {1}");
        put("modpack.namePrompt", "Название модпака:", "Modpack name:");
        put("modpack.exporting", "Экспорт модпака…", "Exporting the modpack…");
        put("modpack.exported", "Модпак сохранён: {0}", "Modpack saved: {0}");

        put("settings.network", "Сеть", "Network");
        put("settings.proxyMode", "Прокси", "Proxy");
        put("settings.proxyPort", "Порт", "Port");
        put("settings.proxyRestartHint", "применяется сразу к новым загрузкам", "applies to new downloads immediately");

        put("settings.backups", "Бэкапы", "Backups");
        put("backups.hint", "Бэкапы создаются автоматически перед восстановлением установки; восстановление затрагивает сохранения, настройки и моды текущей выбранной установки.",
                "Backups are created automatically before repairing an installation; restoring affects saves, settings and mods of the currently selected installation.");
        put("backups.empty", "Бэкапов пока нет", "No backups yet");
        put("backups.summary", "Бэкапов: {0}", "Backups: {0}");
        put("backups.restore", "Восстановить", "Restore");
        put("backups.restoring", "Восстановление {0}…", "Restoring {0}…");
        put("backups.restored", "Восстановлено в {0}", "Restored into {0}");
        put("backups.restoreFailed", "Не удалось восстановить бэкап: {0}", "Failed to restore the backup: {0}");
        put("backups.restoreConfirm", "Восстановить бэкап {0} в {1}? Текущие файлы будут заменены.",
                "Restore backup {0} into {1}? Current files will be replaced.");
        put("backups.entryDetail", "{0} · файлов: {1}", "{0} · files: {1}");

        put("about.linksCaption", "Ссылки", "Links");
        put("about.margycraftSubtitle", "Сервер от Narezany", "Server by Narezany");
        put("about.creatorLabel", "Создатель", "Creator");
        put("about.techLine", "Собрано на FlatLaf · Gson · JNA · Modrinth API",
                "Built with FlatLaf · Gson · JNA · Modrinth API");

        put("appearance.style.standard", "Обычный", "Default");
        put("appearance.style.standard.hint", "Классические панели и скругления",
                "Classic panels and corners");
        put("appearance.style.materialYou", "Material You", "Material You");
        put("appearance.style.materialYou.hint",
                "Увеличенные скругления, pill-элементы, карточки и более свободные отступы",
                "Larger corners, pill controls, cards and roomier spacing");
        put("appearance.style.liquidGlass", "Liquid Glass", "Liquid Glass");
        put("appearance.style.liquidGlass.hint",
                "Полупрозрачное стекло с бликом и мягкими краями",
                "Translucent glass with a highlight and soft edges");
        put("appearance.style.fluent", "Fluent 2", "Fluent 2");
        put("appearance.style.fluent.hint", "Стиль Microsoft: мягкие края, тонкие контуры и слои",
                "Microsoft-inspired soft corners, fine outlines and layers");
        put("appearance.style.apple", "Apple", "Apple");
        put("appearance.style.apple.hint", "В духе macOS: сгруппированные панели и мягкие блики",
                "macOS-inspired grouped panels and subtle highlights");
        put("appearance.style.gnome", "GNOME", "GNOME");
        put("appearance.style.gnome.hint", "В духе Libadwaita: скруглённые карточки и спокойные элементы",
                "Libadwaita-inspired rounded cards and understated controls");
        put("appearance.style.breeze", "KDE Breeze", "KDE Breeze");
        put("appearance.style.breeze.hint", "В духе KDE: компактные формы и чёткие контуры",
                "KDE-inspired compact forms and crisp outlines");
        put("appearance.style.frostedGlass", "Frosted Glass", "Frosted Glass");
        put("appearance.style.frostedGlass.hint", "Матовые панели размывают статичный фон. Анимированный GIF не размывается отдельно под панелями.",
                "Frosted panels blur still backgrounds. Animated GIFs are not blurred separately under panels.");
        put("appearance.style.crystalGlass", "Crystal Glass", "Crystal Glass");
        put("appearance.style.crystalGlass.hint", "Прозрачные панели с яркими гранями и бликами",
                "Transparent panels with bright edges and highlights");
        put("appearance.style.glassmorphism", "Glassmorphism", "Glassmorphism");
        put("appearance.style.glassmorphism.hint", "Скруглённые полупрозрачные панели с мягкими контурами",
                "Rounded translucent panels with soft outlines");
        put("appearance.styleChanged", "Стиль интерфейса: {0}", "Interface style: {0}");

        put("mods.addFilesFilter", "Моды (.jar) и модпаки (.mrpack)", "Mods (.jar) and modpacks (.mrpack)");
        put("mods.importMrpack", "Импорт .mrpack", "Import .mrpack");
        put("mods.importMrpackTooltip", "Установить файл Modrinth-модпака отдельной установкой",
                "Install a Modrinth modpack file as a separate installation");
        put("modpack.importedWithDir", "Модпак «{0}» установлен и выбран. Каталог установки:{1}",
                "Modpack \"{0}\" installed and selected. Installation directory:{1}");
        put("mods.installedInto", "Установка в: {0}", "Installing into: {0}");
        put("mods.mrpackInMods", "В папке модов лежит модпак {0} , игра его не загрузит. Импортируйте его кнопкой «Импорт .mrpack»",
                "The mods folder contains the modpack {0} , the game will not load it. Import it with the \"Import .mrpack\" button");
        put("mods.unpackMrpack", "Распаковать .mrpack", "Unpack .mrpack");
        put("modpack.importFailed", "Не удалось импортировать модпак: {0}",
                "Failed to import the modpack: {0}");
        put("modpack.exportFailed", "Не удалось экспортировать модпак: {0}",
                "Failed to export the modpack: {0}");
        put("mods.modpackBadge", "модпак", "modpack");
        put("mods.modsElsewhere", "В текущей установке модов нет , они установлены в: {0}",
                "The current installation has no mods , they are installed in: {0}");
        put("mods.switchTo", "Переключиться", "Switch");
        put("settings.gameDataPath", "Каталог Minecraft", "Minecraft directory");
        put("settings.gameDataPathHint", "Пусто , стандартный каталог в данных лаунчера; изменение применяется к новым загрузкам",
                "Empty means the default directory inside launcher data; applies to new downloads");
        put("settings.gameDataCopy", "Копировать данные", "Copy data");
        put("settings.gameDataMove", "Перенести данные", "Move data");
        put("settings.gameDataConfirm", "Данные будут скопированы в {0}, существующие файлы там будут перезаписаны. Продолжить?",
                "Data will be copied into {0}; existing files there will be overwritten. Continue?");
        put("settings.gameDataApplied", "Каталог Minecraft: {0}", "Minecraft directory: {0}");
        put("settings.gameDataNested", "Новый каталог не должен находиться внутри текущего (и наоборот)",
                "The new directory must not be inside the current one (or vice versa)");
        put("settings.gameDataNotAbsolute", "Укажите абсолютный путь к каталогу",
                "Specify an absolute directory path");
        put("settings.envVars", "Переменные окружения", "Environment variables");
        put("settings.envVarsHint", "По одной KEY=VALUE на строку",
                "One KEY=VALUE per line");
        put("mods.switchToTooltip", "Сделать установку с модами текущей",
                "Make the installation with mods the current one");
        put("instances.buildsEmpty",
                "Мод-сборки создаются на экране «Моды» → «Установленные» кнопкой «Создать снимок»",
                "Mod builds are created on the Mods → Installed screen with the \"Create snapshot\" button");


        put("forge.filesProcessed", "обработано файлов: {0}", "files processed: {0}");
        put("modpack.importing", "Импорт модпака…", "Importing modpack…");
        put("downloads.failedFiles", "Не удалось скачать файлы: {0}", "Failed to download files: {0}");
        put("launch.preflightFailed", "Проверка перед запуском не пройдена: {0}", "Launch preflight failed: {0}");
        put("play.errorPrefix", "ОШИБКА: {0}", "ERROR: {0}");
        put("play.diagnosticsHeader", "Диагностика:", "Diagnostics:");


        put("cli.themeFailed", "Не удалось применить тему: {0}", "Could not apply the theme: {0}");
        put("cli.needVersion", "Укажите версию: --dry-run 1.20.1 [ник]", "Specify a version: --dry-run 1.20.1 [nickname]");
        put("cli.offlineProfile", "Офлайн-профиль: {0} (UUID {1})", "Offline profile: {0} (UUID {1})");
        put("cli.command", "Команда запуска ({0} аргументов):", "Launch command ({0} arguments):");
        put("cli.error", "Ошибка: {0}", "Error: {0}");
        put("cli.description", "Лаунчер Minecraft с поддержкой Forge, Fabric, Quilt, NeoForge, OptiFine и Ely.by",
                "Minecraft launcher with Forge, Fabric, Quilt, NeoForge, OptiFine and Ely.by support");
        put("cli.guiHint", "Запуск без аргументов открывает графический интерфейс.",
                "Run without arguments to open the graphical interface.");
        put("cli.arguments", "Аргументы:", "Arguments:");
        put("cli.argHelp", "показать эту справку", "show this help");
        put("cli.argVersion", "показать версию", "show the version");
        put("cli.argDataDir", "  --data-dir <путь>          свой каталог данных (портативный режим)",
                "  --data-dir <path>          custom data directory (portable mode)");
        put("cli.argDryRun", "  --dry-run <версия> [ник]   подготовить версию и показать команду запуска",
                "  --dry-run <version> [nick] prepare a version and print the launch command");
        put("cli.defaultDataDir", "Каталог данных по умолчанию: {0}", "Default data directory: {0}");


        put("units.bytes", "{0} Б", "{0} B");
        put("units.kb", "{0} КБ", "{0} KB");
        put("units.mb", "{0} МБ", "{0} MB");
        put("units.gb", "{0} ГБ", "{0} GB");
        put("units.tb", "{0} ТБ", "{0} TB");
        put("disk.unknown", "неизвестно", "unknown");
        put("disk.free", "свободно на диске: {0}", "free disk space: {0}");
        put("disk.freeLow", "мало места на диске: {0}", "low disk space: {0}");
        put("disk.insufficient", "Недостаточно места на диске: нужно {0}, свободно {1} (с запасом 256 МБ). Путь: {2}",
                "Not enough disk space: {0} needed, {1} free (256 MB is kept in reserve). Path: {2}");
        put("disk.downloadEstimate", "{0}: будет скачано около {1}, свободно {2}",
                "{0}: about {1} will be downloaded, {2} free");


        put("clone.button", "Клонировать", "Clone");
        put("clone.title", "Клонирование: {0}", "Clone: {0}");
        put("clone.name", "Название новой сборки", "New build name");
        put("clone.defaultName", "{0} (копия)", "{0} (copy)");
        put("clone.parts", "Что перенести", "What to copy");
        put("clone.part.worlds", "Миры (saves)", "Worlds (saves)");
        put("clone.part.mods", "Моды", "Mods");
        put("clone.part.settings", "Настройки игры, серверы и конфиги модов", "Game options, servers and mod configs");
        put("clone.part.resources", "Ресурспаки и шейдеры", "Resource packs and shaders");
        put("clone.estimating", "Подсчёт размера…", "Calculating size…");
        put("clone.estimate", "Размер копии: {0} · свободно: {1}", "Copy size: {0} · free: {1}");
        put("clone.estimateLow", "Недостаточно места: копия {0}, свободно {1}", "Not enough space: copy {0}, free {1}");
        put("clone.hint", "Копия появится во вкладке «Мод-сборки» и будет запускаться из своей папки.",
                "The copy appears on the Mod builds tab and runs from its own folder.");
        put("clone.start", "Клонировать", "Clone");
        put("clone.running", "Клонирование «{0}»…", "Cloning \"{0}\"…");
        put("clone.done", "Создана сборка «{0}»", "Build \"{0}\" created");
        put("clone.failed", "Не удалось клонировать: {0}", "Cloning failed: {0}");
        put("clone.sourceMissing", "Папка инстанса не найдена: {0}", "Instance folder not found: {0}");
        put("clone.horizonUnsupported", "Клонирование Horizon (26.3 Rust) не поддерживается.",
                "Cloning Horizon (26.3 Rust) is not supported.");


        put("compat.button", "Совместимость", "Compatibility");
        put("compat.title", "Совместимость модов: {0}", "Mod compatibility: {0}");
        put("compat.running", "Проверка модов «{0}»…", "Checking mods of \"{0}\"…");
        put("compat.preflightRunning", "Проверка совместимости модов…", "Checking mod compatibility…");
        put("compat.preflightTitle", "Найдены проблемы с модами", "Mod problems found");
        put("compat.launchAnyway", "Запустить всё равно", "Launch anyway");
        put("compat.launchCancelled", "Запуск отменён: исправьте моды или отключите проверку в настройках",
                "Launch cancelled: fix the mods or turn off the check in Settings");
        put("compat.noIssues", "Проверено модов: {0}. Проблем не найдено.", "Mods checked: {0}. No problems found.");
        put("compat.summary", "Проверено модов: {0}. Ошибок: {1}, предупреждений: {2}.",
                "Mods checked: {0}. Errors: {1}, warnings: {2}.");
        put("compat.severity.error", "[Ошибка]", "[Error]");
        put("compat.severity.warning", "[Предупреждение]", "[Warning]");
        put("compat.readFailed", "не удалось прочитать папку модов: {0}", "could not read the mods folder: {0}");
        put("compat.noLoader", "В папке mods {0} файлов, но у инстанса {1} нет загрузчика модов; они не будут загружены.",
                "The mods folder has {0} files, but the {1} instance has no mod loader; they will not be loaded.");
        put("compat.wrongLoader", "мод для {0}, а инстанс использует {1}", "mod is for {0}, but the instance uses {1}");
        put("compat.forgeOnNeoForge", "мод сделан для Forge; на NeoForge для этой версии Minecraft он может не работать",
                "this is a Forge mod; it may not work on NeoForge for this Minecraft version");
        put("compat.notAMod", "не найдено описание мода (возможно, это библиотека или мод старого формата)",
                "no mod descriptor found (it may be a library or a legacy mod)");
        put("compat.duplicate", "мод {0} установлен несколько раз; оставьте один файл",
                "mod {0} is installed more than once; keep a single file");
        put("compat.missingDependency", "нет обязательной зависимости {0} (нужна версия: {1})",
                "required dependency {0} is missing (version needed: {1})");
        put("compat.dependencyVersion", "зависимость {0} нужна версии {1}, установлена {2}",
                "dependency {0} must be {1}, installed {2}");
        put("compat.minecraftMismatch", "нужен Minecraft {0}, а инстанс на {1}",
                "requires Minecraft {0}, but the instance is {1}");
        put("compat.minecraftUnknown", "версия Minecraft инстанса неизвестна", "the instance Minecraft version is unknown");
        put("compat.breaks", "несовместим с установленным модом {0}", "is incompatible with the installed mod {0}");
        put("compat.conflicts", "может конфликтовать с модом {0}", "may conflict with the mod {0}");
        put("settings.checkModsBeforeLaunch", "Проверять совместимость модов перед запуском",
                "Check mod compatibility before launching");
        put("settings.checkModsBeforeLaunchHint", "Загрузчик, версия Minecraft, зависимости и дубликаты проверяются офлайн по файлам модов",
                "Loader, Minecraft version, dependencies and duplicates are checked offline from the mod files");


        put("worlds.title", "Миры: {0}", "Worlds: {0}");
        put("worlds.empty", "Миров пока нет. Импортируйте ZIP или папку с level.dat.",
                "No worlds yet. Import a ZIP or a folder that contains level.dat.");
        put("worlds.summary", "Миров: {0} · занято {1} · свободно {2}", "Worlds: {0} · {1} used · {2} free");
        put("worlds.loadFailed", "Не удалось прочитать миры: {0}", "Could not read worlds: {0}");
        put("worlds.details", "{0} · {1} · {2}", "{0} · {1} · {2}");
        put("worlds.openWorld", "Папка мира", "World folder");
        put("worlds.duplicate", "Копия", "Duplicate");
        put("worlds.import", "Импорт…", "Import…");
        put("worlds.export", "Экспорт…", "Export…");
        put("worlds.importTitle", "Выберите ZIP-архив или папку мира", "Choose a ZIP archive or a world folder");
        put("worlds.exportTitle", "Сохранить мир как ZIP", "Save world as ZIP");
        put("worlds.zipFilter", "ZIP-архивы миров", "World ZIP archives");
        put("worlds.copyName", "{0} - копия", "{0} - copy");
        put("worlds.copying", "Копирование «{0}»…", "Copying \"{0}\"…");
        put("worlds.copied", "Создана копия {0}", "Copy {0} created");
        put("worlds.importing", "Импорт {0}…", "Importing {0}…");
        put("worlds.imported", "Импортирован мир {0}", "World {0} imported");
        put("worlds.exporting", "Экспорт «{0}»…", "Exporting \"{0}\"…");
        put("worlds.exported", "Сохранено: {0}", "Saved: {0}");
        put("worlds.deleting", "Удаление «{0}»…", "Deleting \"{0}\"…");
        put("worlds.deleted", "Удалён мир {0}", "World {0} deleted");
        put("worlds.overwriteConfirm", "Файл {0} уже существует. Заменить?", "{0} already exists. Replace it?");
        put("worlds.notAWorld", "{0} не является миром этого инстанса", "{0} is not a world of this instance");
        put("worlds.noLevelDat", "В выбранном источнике нет level.dat", "The selected source has no level.dat");
        put("worlds.alreadyInSaves", "Этот мир уже находится в папке saves", "This world is already in the saves folder");
        put("worlds.exportInside", "Нельзя сохранить архив внутрь папки мира", "The archive cannot be saved inside the world folder");
        put("worlds.archiveTooLarge", "Архив слишком большой или содержит слишком много файлов",
                "The archive is too large or contains too many files");
        put("worlds.unsafeArchive", "Архив содержит небезопасные пути и не будет распакован",
                "The archive contains unsafe paths and will not be extracted");
        put("worlds.badArchive", "Повреждённый архив: {0}", "Damaged archive: {0}");


        put("stats.tab", "Статистика", "Statistics");
        put("stats.period", "Период", "Period");
        put("stats.period.week", "7 дней", "7 days");
        put("stats.period.month", "30 дней", "30 days");
        put("stats.period.quarter", "90 дней", "90 days");
        put("stats.period.all", "Всё время", "All time");
        put("stats.byDay", "По дням", "By day");
        put("stats.byInstance", "По установкам и сборкам", "By installation and build");
        put("stats.total", "Всего: {0} · запусков: {1} · дней с игрой: {2}", "Total: {0} · sessions: {1} · days played: {2}");
        put("stats.empty", "За этот период игр не было", "No play time in this period");
        put("stats.minutes", "{0} мин", "{0}m");
        put("stats.instanceDetail", "{0} · {1}% · запусков: {2} · последний раз {3}",
                "{0} · {1}% · sessions: {2} · last played {3}");
        put("stats.dayTooltip", "{0}: {1}, запусков: {2}", "{0}: {1}, sessions: {2}");
        put("stats.buildLabel", "Сборка «{0}»", "Build \"{0}\"");
        put("stats.sharedDir", "Общая папка игры", "Shared game folder");
        put("stats.removed", "Удалённый инстанс ({0})", "Removed instance ({0})");
        put("stats.hint", "Учитывается время с момента запуска до закрытия игры. Сессии после полуночи делятся по дням.",
                "Time from launch until the game closes is counted. Sessions past midnight are split by day.");
    }

    private Strings() {
    }

    private static void put(String key, String russian, String english) {
        if (TEXTS.put(key, new String[]{russian, english}) != null) {
            throw new IllegalStateException("Duplicate string key: " + key);
        }
    }


    public static boolean has(String key) {
        return TEXTS.containsKey(key);
    }


    public static Map<String, String[]> all() {
        Map<String, String[]> copy = new LinkedHashMap<>();
        TEXTS.forEach((key, value) -> copy.put(key, value.clone()));
        return copy;
    }


    public static Language language() {
        return current;
    }

    public static void setLanguage(Language language) {
        if (language == null || language == current) {
            return;
        }
        current = language;
        FORMAT_CACHE.clear();
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
            MessageFormat template = FORMAT_CACHE.get(key);
            if (template == null) {
                template = new MessageFormat(pattern, current.locale());
                MessageFormat existing = FORMAT_CACHE.putIfAbsent(key, template);
                if (existing != null) {
                    template = existing;
                }
            }
            return ((MessageFormat) template.clone()).format(args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    private static final java.util.Map<String, MessageFormat> FORMAT_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
}
