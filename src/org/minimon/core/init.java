/*
* Copyright (c) 2014, Oleg Bobukh
* MIT License, http://opensource.org/licenses/mit-license.php
* with classpath restrictions.
*
* Данная лицензия разрешает лицам, получившим копию данного
* программного обеспечения и сопутствующей документации
* (в дальнейшем именуемыми «Программное Обеспечение»),
* безвозмездно использовать Программное Обеспечение
* без ограничений, включая неограниченное право на
* использование, копирование, изменение, добавление,
* публикацию, распространение, сублицензирование и/или
* продажу копий Программного Обеспечения, а также лицам,
* которым предоставляется данное Программное Обеспечение,
* при соблюдении следующих условий:
*
* Указанное выше уведомление об авторском праве и данные
* условия должны быть включены во все копии или значимые
* части данного Программного Обеспечения.
*
* ДАННОЕ ПРОГРАММНОЕ ОБЕСПЕЧЕНИЕ ПРЕДОСТАВЛЯЕТСЯ «КАК ЕСТЬ»,
* БЕЗ КАКИХ-ЛИБО ГАРАНТИЙ, ЯВНО ВЫРАЖЕННЫХ ИЛИ ПОДРАЗУМЕВАЕМЫХ,
* ВКЛЮЧАЯ ГАРАНТИИ ТОВАРНОЙ ПРИГОДНОСТИ, СООТВЕТСТВИЯ ПО ЕГО
* КОНКРЕТНОМУ НАЗНАЧЕНИЮ И ОТСУТСТВИЯ НАРУШЕНИЙ, НО НЕ
* ОГРАНИЧИВАЯСЬ ИМИ. НИ В КАКОМ СЛУЧАЕ АВТОРЫ ИЛИ
* ПРАВООБЛАДАТЕЛИ НЕ НЕСУТ ОТВЕТСТВЕННОСТИ ПО КАКИМ-ЛИБО
* ИСКАМ, ЗА УЩЕРБ ИЛИ ПО ИНЫМ ТРЕБОВАНИЯМ, В ТОМ ЧИСЛЕ, ПРИ
* ДЕЙСТВИИ КОНТРАКТА, ДЕЛИКТЕ ИЛИ ИНОЙ СИТУАЦИИ, ВОЗНИКШИМ ИЗ-ЗА
* ИСПОЛЬЗОВАНИЯ ПРОГРАММНОГО ОБЕСПЕЧЕНИЯ ИЛИ ИНЫХ ДЕЙСТВИЙ
* С ПРОГРАММНЫМ ОБЕСПЕЧЕНИЕМ..
*/

package org.minimon.core;

import org.minimon.system.procExec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Инициирующий класс
 * Точка отсчёта начинается здесь.
 * Так же здесь расположены методы определения местоположения
 * и системные методы
 * Константы, занимающие много места, и-или необходимые в нескольких классах сразу,
 * расположены в интерфейсе staticValues
 * <p/>
 * В stdout должен писать только классы, активные до запуска логгера.
 * Остальные - только в лог.
 */
public class init
        implements staticValues {

    // Режимы работы
    private static final int DO_NOTHING = 0;
    private static final int START_ACTION = 1;
    private static final int STOP_ACTION = 2;
    private static final int RESTART_ACTION = 3;
    private static final int RUNTIME_ACTION = 4;

    // Ключи запуска и останова
    private static final String KEY_ARGS[] = {
            "-s", "--start",
            "-t", "--stop",
            "-r", "--restart",
            "-h", "--help",
            "--runtime",
            "-c", "--config",
            "-d", "--debug"
    };

    // Ключи запуска и останова, которые могут содержать параметры
    private static final String HAS_PARAMS[] = {
            "-s", "--start",
            "-t", "--stop",
            "-r", "--restart",
    };

    /**
     * Отображение справки
     */
    private static void getHelp() {
        for (String item : SHOW_HELP) System.out.println(item);
        System.exit(EXIT_NORMAL);
    }

    /*
     */

    /**
     * Выполняет добавление в classpath всех jar-файлов
     * из директории lib, расположенной в той же родительской директории,
     * что и исполнимый Jar-файл
     */
    private static void libPreloader() {
        // Вначале получаем родительский каталог Jar-файла
        File gtf = getJarFileLocation();
        // По пути превозмогаем отсутвие прав
        fileFixPerm.tryFixFilePerm(gtf.getAbsolutePath());
        // Выполняем поиск поддиректории lib
        File[] sub = gtf.listFiles();
        // При отсутствии доступа sub может быть null
        if (sub == null) {
            for (String item : MESSAGE_GENERAL_ACCESS)
                System.out.println(item);
            System.exit(EXIT_RUNTIME_ERROR);
        }
        gtf = null;
        for (File item : sub)
            if (item.getName().toLowerCase().equals("lib"))
                gtf = item;
        if (gtf == null || (!gtf.isDirectory())) {
            for (String item : MESSAGE_GENERAL_LIB_NOT_FOUND)
                System.out.println(item);
            System.exit(EXIT_RUNTIME_ERROR);
        }
        // Перечисляем lib и подгружаем jar-файлы
        fileFixPerm.tryFixFilePerm(gtf.getAbsolutePath());
        sub = gtf.listFiles();
        if (sub == null) {
            for (String item : MESSAGE_GENERAL_ACCESS)
                System.out.println(item);
            System.exit(EXIT_RUNTIME_ERROR);
        }
        // Подгружаем
        for (File item : sub) {
            fileFixPerm.tryFixFilePerm(item.getAbsolutePath());
            if (item.isFile() && item.canRead() && item.getName().toLowerCase().endsWith(".jar")) {
                try {
                    // http://stackoverflow.com/questions/1010919/adding-files-to-java-classpath-at-runtime
                    Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
                    method.setAccessible(true);
                    method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{item.toURI().toURL()});
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * Проверяет наличие библиотеки ini4j
     */
    public static void ini4jCheck() {
        try {
            Class.forName("org.ini4j.Ini").getName();
        } catch (Exception exc) {
            for (String item : SHOW_INI4J_NOT_FOUND) System.out.println(item);
            System.exit(EXIT_RUNTIME_ERROR);
        }
    }

    /**
     * Поиск в строковом массиве без учёта регистра
     *
     * @param array         Массив
     * @param searchPhrase  Искомый элемент
     * @return              Наличие либо отсутствие
     */
    private static boolean inArray(String[] array, String searchPhrase) {
        for (String item : array)
            if (searchPhrase.equalsIgnoreCase(item))
                return true;

        return false;
    }

    /**
     * Проверка версии JRE
     * Поддерживается >= 1.7 (применяется ProcessBuilder и т.п.)
     */
    public static void checkJavaVersion() {
        String ver = System.getProperty("java.version");
        if (ver.compareToIgnoreCase("1.7") < 0) {
            System.out.println("Incompatible Java version: " + ver + ", must be >= 1.7");
            System.exit(EXIT_RUNTIME_ERROR);
        }
    }

    /**
     * Start here
     *
     * @param args аргументы командной строки
     */
    public static void main(String args[]) {
        // Проверяем версию Java
        checkJavaVersion();
        // Подгружаем все библиотеки
        libPreloader();
        // Проверяем наличие библиотеки ini4j
        ini4jCheck();
        // Режим отладки, по-умолчанию отключен
        boolean debug = false;
        // Файл конфигурации
        String configFileName = new File(getJarFileLocation(), DEFAULT_CONFIG_FILE_NAME).getAbsolutePath();
        // Режим запуска
        int workMode = DO_NOTHING;
        // Имя модуля
        String moduleName = null;

        // Парсинг входных параметров
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                String loCase = args[i].toLowerCase();
                if (debug) {
                    System.err.println("DEBUG: case: " + loCase + ", module name: " + moduleName);
                }

                // Режим отладки
                if ((loCase.endsWith("-d")) || (loCase.endsWith("--debug"))) {
                    debug = true;
                }

                // Использование внешнего файла конфигурации
                if ((loCase.endsWith("-c")) || (loCase.endsWith("--config"))) {
                    try {
                        // Считываем следующий параметр и проверяем, не является ли он ключом
                        String nextArg = args[i + 1];
                        if (! inArray(KEY_ARGS, nextArg.toLowerCase()))
                            configFileName = nextArg;
                    } catch (ArrayIndexOutOfBoundsException ignore) {
                        System.out.println("Another config file name not set");
                        configFileName = DEFAULT_CONFIG_FILE_NAME;
                    }
                }

                // Отображение справки
                if ((loCase.endsWith("-h")) || (loCase.endsWith("--help"))) {
                    getHelp();
                }

                // Запуск либо системы либо теста
                if ((loCase.endsWith("-s")) || (loCase.endsWith("--start"))) {
                    workMode = START_ACTION;
                }

                // Останов либо системы либо теста
                if ((loCase.endsWith("-t")) || (loCase.endsWith("--stop"))) {
                    workMode = STOP_ACTION;
                }

                // Перезапуск
                if ((loCase.endsWith("-r")) || (loCase.endsWith("--restart"))) {
                    workMode = RESTART_ACTION;
                }

                // Фоновый режим
                if (loCase.endsWith("--runtime"))
                    workMode = RUNTIME_ACTION;

                // Имя модуля для запуска/останова/перезапуска
                if (inArray(HAS_PARAMS, loCase)) {
                    if (debug) System.err.println("DEBUG: " + loCase + " has parameters");
                    try {
                        String nextArgs = args[i + 1].toLowerCase();
                        if (! inArray(KEY_ARGS, nextArgs)) {
                            moduleName = nextArgs;
                            if (debug) {
                                System.err.println("DEBUG: " + nextArgs + " is " + loCase + " parameter");
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException ignore) {
                    }
                }
            }
        }

        // Считываем настройки
        settingsReader mainSettings = new settingsReader(configFileName, debug);
        if (debug) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("Incoming arguments: ");
            for (String item : args) {
                buffer.append('[').append(item).append(']');
            }
            buffer.append(", debug is: TRUE, config file name is: ")
                    .append(configFileName)
                    .append(", module name is: ")
                    .append(moduleName == null ? "NULL" : moduleName)
                    .append(", runtime mode is: ");
            String[] workModes = {"DO_NOTHING", "START_ACTION", "STOP_ACTION", "RESTART_ACTION", "RUNTIME_ACTION"};
            buffer.append(workModes[workMode]);
            mainSettings.getCoreSubLogger("Init").debug(buffer);
        }


        // Далее считается, что запущен рабочий режим
        switch (workMode) {
            case START_ACTION:
                if (moduleName != null) {
                    startModuleAction(mainSettings, moduleName);
                } else {
                    startAction(mainSettings, debug, configFileName);
                }
                break;
            case STOP_ACTION:
                if (moduleName != null) {
                    stopModuleAction(mainSettings, moduleName);
                } else {
                    stopAction(mainSettings);
                }
                break;
            case RESTART_ACTION:
                if (moduleName != null) {
                    restartModuleAction(mainSettings, moduleName);
                } else {
                    restartAllModules(mainSettings);
                }
                break;
            case RUNTIME_ACTION:
                runtimeAction(mainSettings, debug);
                break;
        }

        getHelp();
    }

    /**
     * Основной режим работы при получении аргументы --runtime от init либо системы с опцией --start
     *
     * @param mainSettings Основные настройки
     * @param debugState   Статус отладки
     */
    private static void runtimeAction(settingsReader mainSettings, boolean debugState) {
        logger initLogger = mainSettings.getCoreSubLogger("Init system");
        try {
            controller workMode = new controller(mainSettings, debugState);
            workMode.workmode();
        } catch (Exception exc) {
            initLogger.fatal("Runtime error");
            initLogger.appErrorWriter(exc);
        }
    }

    /**
     * Определяет расположение исполнимого файла java
     * Если файл не будет найден (либо будет ошибка доступа) - приложение
     * незамедлительно завершится (ведётся запись в переданный логгер)
     *
     * @param findLogger Логгер
     * @return Исполнимый файл
     */
    private static File getJavaBinary(logger findLogger) {
        // Определяем Java home
        File javaHomePath = new File(System.getProperty("java.home"));
        if (!javaHomePath.exists() || !javaHomePath.isDirectory()) {
            findLogger.fatal("Unable to find JAVA_HOME directory");
            System.exit(EXIT_RUNTIME_ERROR);
        }
        // Определяем вложенную директорию bin
        File[] dirIncluded = javaHomePath.listFiles();
        if (dirIncluded == null) {
            findLogger.fatal("JAVA_HOME is empty or access denied");
            System.exit(EXIT_RUNTIME_ERROR);
        }
        File binDir = null;
        for (File item : dirIncluded) {
            if (item.isDirectory() && item.getName().toLowerCase().equals("bin"))
                binDir = item;
        }
        if (binDir == null) {
            findLogger.fatal("Unable to find JAVA_HOME->bin directory");
            System.exit(EXIT_RUNTIME_ERROR);
        }
        // Поиск исполнимого файла
        dirIncluded = binDir.listFiles();
        if (dirIncluded == null) {
            findLogger.fatal("JAVA_HOME->bin dir is empty or access denied");
            System.exit(EXIT_RUNTIME_ERROR);
        }
        // Вначале выполняем поиск вложенной amd64. При наличии переходим туда
        for (File item : dirIncluded) {
            if (item.isDirectory() && item.getName().toLowerCase().equals("amd64")) {
                binDir = item;
                dirIncluded = binDir.listFiles();
                if (dirIncluded == null) {
                    findLogger.fatal("JAVA_HOME->bin->amd64 dir is empty or access denied");
                    System.exit(EXIT_RUNTIME_ERROR);
                }
            }
        }
        File javaBinary = null;
        for (File item : dirIncluded) {
            if (item.isFile() && (item.getName().toLowerCase().equals("java") || item.getName().toLowerCase().equals("java.exe"))) {
                javaBinary = item;
            }
        }
        if (javaBinary == null) {
            findLogger.fatal("java binary file not found");
            System.exit(EXIT_RUNTIME_ERROR);
        }
        return javaBinary;
    }

    /**
     * Выполняет поиск Jar-файла minimon.
     *
     * @return Файл логгера
     */
    private static File getJARFile() {
        File jarFile = new File(init.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        fileFixPerm.tryFixFilePerm(jarFile.getPath());
        return jarFile;
    }

    /**
     * Возвращает родительскую директорию исполнимого Jar-файла
     *
     * @return Исполнимая директория
     */
    public static File getJarFileLocation() {
        return getJARFile().getParentFile();
    }

    /**
     * Выполняет запись в файловый сокет
     * Файловый сокет - обычный периодически опрашиваемый на изменения файл
     * Новые записи вносятся путём перезаписи текущего файла
     * Срабатывание производится по дате изменения файла
     *
     * @param socketFileName  Имя файлового сокета
     * @param socketWriterLog Логгер
     * @param message         Сообщение для записи
     */
    private static void socketWriter(String socketFileName, logger socketWriterLog, String message) {
        // Поиск запущенного файлового сокета
        File socketFile = new File(socketFileName);
        if ((!socketFile.exists()) || (!socketFile.isFile())) {
            socketWriterLog.fatal(NOT_RUN);
            System.exit(EXIT_NOT_RUN);
        }
        // Запись
        try {
            fileFixPerm.tryFixFilePerm(socketFile.getAbsolutePath());
            OutputStreamWriter sockWriter = new OutputStreamWriter(new FileOutputStream(socketFile, false), "UTF-8");
            sockWriter.append(message);
            sockWriter.flush();
            sockWriter.close();
        } catch (Exception exc) {
            socketWriterLog.appErrorWriter(exc);
            System.exit(EXIT_RUNTIME_ERROR);
        }
    }

    /**
     * Запуск системы
     * Предполагается, что имя тестового модуля не указано и это общий старт
     * Метод завершает работу приложения
     *
     * @param mainSettings   Настройки
     * @param debugMode      Режим отладки
     * @param configFileName Имя файла конфигурации, полученного из входных параметров
     */
    private static void startAction(settingsReader mainSettings, boolean debugMode, String configFileName) {
        logger startupLogger = mainSettings.getCoreSubLogger("Startup mode");
        // Поиск запущенного файлового сокета
        File socketFile = new File(mainSettings.getSocketFileName());
        if (socketFile.exists() && socketFile.isFile()) {
            startupLogger.fatal(ALREADY_RUN);
            System.exit(EXIT_ALREADY_RUN);
        }
        // Определение бинарного файла java
        File javaBinary = getJavaBinary(startupLogger);
        // Определение расположения jar-файла
        File jarFile = getJARFile();
        // Определяем вышестоящую директорию - директория запуска
        File runtimeDirectory = jarFile.getParentFile();

        // Формирование и запуск
        procExec startup = new procExec(
                debugMode,
                startupLogger,
                javaBinary.getAbsolutePath(),
                "-jar",
                jarFile.getAbsolutePath(),
                debugMode ? "--debug" : "",
                "--runtime",
                "--config", configFileName
        );
        try {
            startup.execute(runtimeDirectory);
        } catch (IOException exc) {
            startupLogger.appErrorWriter(exc);
        }
        System.exit(EXIT_NORMAL);
    }

    /**
     * Запуск модуля
     * Предполагается, что указали имя тестового модуля, процесс запущен
     * и мы через файловый сокет указываем команду запуска модуля
     * Метод завершает работу приложения
     *
     * @param mainSettings Настройка
     */
    private static void startModuleAction(settingsReader mainSettings, String moduleName) {
        socketWriter(
                mainSettings.getSocketFileName(),
                mainSettings.getCoreSubLogger("Probe activation"),
                "START:" + moduleName
        );
        System.exit(EXIT_NORMAL);
    }

    /**
     * Осуществляет останов системы
     * Предполагается, что не указан тестовый модуль, процесс запущен.
     * Отсылается команда через файловый сокет
     * Метод завершает работу приложения в любом случае
     *
     * @param mainSettings Настройки
     */
    private static void stopAction(settingsReader mainSettings) {
        socketWriter(
                mainSettings.getSocketFileName(),
                mainSettings.getCoreSubLogger("Shutdown mode"),
                "STOP"
        );
        System.exit(EXIT_NORMAL);
    }

    /**
     * Останов модуля
     * Предполагается, что указали имя тестового модуля, процесс запущен
     * и мы через файловый сокет указываем команду останова модуля
     * Метод завершает работу приложения
     *
     * @param mainSettings основные настройки
     * @param moduleName   имя модуля
     */
    private static void stopModuleAction(settingsReader mainSettings, String moduleName) {
        socketWriter(
                mainSettings.getSocketFileName(),
                mainSettings.getCoreSubLogger("Probe deactivation"),
                "STOP:" + moduleName
        );
        System.exit(EXIT_NORMAL);
    }

    /**
     * Перезапуск всех подулей
     * Предполагается, что указали имя тестового модуля, процесс запущен
     * и мы через файловый сокет указываем команду общего перезапуска
     * Метод завершает работу приложения
     *
     * @param mainSettings Настройки
     */
    private static void restartAllModules(settingsReader mainSettings) {
        socketWriter(
                mainSettings.getSocketFileName(),
                mainSettings.getCoreSubLogger("All probes restart"),
                "RESTART"
        );
    }

    /**
     * Осуществляет перезапуск модуля
     * Предполагается, что указали имя тестового модуля, процесс запущен
     * и мы через файловый сокет указываем команду перезапуска
     *
     * @param mainSettings Настройки
     * @param moduleName   Имя модуля
     */
    private static void restartModuleAction(settingsReader mainSettings, String moduleName) {
        socketWriter(
                mainSettings.getSocketFileName(),
                mainSettings.getCoreSubLogger("Probe restart"),
                "RESTART:" + moduleName
        );
    }
}
