package org.minimon.core;

import com.sun.istack.internal.Nullable;
import org.ini4j.Ini;
import org.minimon.utils.collections;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * Чтение настроек из ini-файлов
 * Выполнение первоначальных проверок
 */
public class settingsReader
        implements staticValues {
    // Имя конфигурационного файла
    private final String configFileName;
    // Настройки
    LinkedHashMap<String, LinkedHashMap<String, String>> mainSettings;
    // Логгер. Получаем после инициализации базовых настроек
    // Изначально отсутствует, пишем в stdout
    private logger coreLogger = null;
    // Логгер считывателя настроек
    private logger settingsReaderLogger = null;
    // Имена доступных модулей
    private LinkedList<String> enabledModules = null;
    // Файловый сокет
    private String socketFileName;
    // Статус отладки
    private boolean debugState = false;

    /**
     * Выполнение чтения настроек
     *
     * @param configFileName Имя файла конфигурации
     */
    public settingsReader(String configFileName, boolean debugState) {
        this.debugState = debugState;
        this.configFileName = configFileName;
        /*
        Ищем файл конфигурации. Если отсутствует - завершаем работу с
        сообщением об ошибке. Иначе сначала пытаемся исправить права на файл
         */
        File configFile = new File(configFileName);
        if (!configFile.exists()) {
            System.out.println(MESSAGE_CONFIG_FILE_NOT_FOUND + configFileName);
            System.exit(EXIT_SETTINGS_READ_ERROR);
        } else {
            fileFixPerm.tryFixFilePerm(configFileName);
        }

        // Считываем основной ini-файл
        Ini mainConfig = null;
        try {
            mainConfig = new Ini(new InputStreamReader(new FileInputStream(configFile), "UTF-8"));
        } catch (Exception exc) {
            System.out.println(MESSAGE_CONFIG_FILE_READ_ERROR + exc.toString());
            System.exit(EXIT_SETTINGS_READ_ERROR);
        }

        // Пытаемся найти базовые настройки
        mainSettings = collections.IniToLinkedHashMap(mainConfig);

        // 1. Логгер
        String coreLoggerFileName = collections.getSectionParameter(mainSettings, LOG_SECTION, LOG_PATH_NAME);
        if (coreLoggerFileName == null) {
            // Если не указан - берём по-умолчанию относительно исполнимого файла
            File logFile = new File(init.getJarFileLocation(), LOG_PATH_DEFAULT);
            coreLoggerFileName = logFile.getAbsolutePath();
        }
        // Сразу создаём логгер
        coreLogger = new logger(coreLoggerFileName, debugState);
        // И пишем первое сообщение о старте
        coreLogger.info(STARTUP_INFO);
        // Создаём сублоггер для считывателя настроек
        settingsReaderLogger = coreLogger.getModuleSubLogger(CORE_CONFIG_READER);
        settingsReaderLogger.debug(
                "Readed ini: " + mainConfig.toString() + System.lineSeparator()
                        + "Converted settings: " + mainSettings.toString()
        );

        // 3. Имена модулей тестов
        enabledModules = parseRAWProbeTypes(collections.getSectionParameter(mainSettings, MAIN_SECTION, ENABLED_MODULES_NAME, ENABLED_MODULES_DEFAULT));

        // 4. Файловый сокет
        socketFileName = collections.getSectionParameter(mainSettings, MAIN_SECTION, SOCKET_PATH_NAME);
        if (socketFileName == null) {
            File sockFile = new File(init.getJarFileLocation(), SOCKET_PATH_DEFAULT);
            socketFileName = sockFile.getAbsolutePath();
        }
    }

    /**
     * Считывает настройку индивидуального теста
     * Если файл настроек существует - созвращает вложенный HashMap, иначе null
     * (ошибка чтения, формата и т.п.)
     *
     * @param probeType Тип теста
     * @param probeName Имя теста либо имя файла, если не указано имя в настройках
     * @return Настройки для указанного теста
     */
    public LinkedHashMap<String, LinkedHashMap<String, String>> readProbeSettings(String probeType, String probeName) {
        // Получаем список доступных файлов указанной категории
        File[] filesList = getCategorisedProbeList(probeType);
        if (filesList == null) return null;

        // Создаём ссылку объекта. Такая категория тестов существует
        // Осталось найти настройки
        LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;

        // Обрабатываем каждый файл последовательно до тех пор, пока не совпадёт либо имя файла, либо имя из настроек
        // В таком случае считываем
        for (File probeInfo : filesList) {
            try {
                Ini probeConfig = new Ini(new InputStreamReader(new FileInputStream(probeInfo), "UTF-8"));
                LinkedHashMap<String, LinkedHashMap<String, String>> convertedConfig = collections.IniToLinkedHashMap(probeConfig);

                // Выполняем поиск имени теста, если нет - возвращаем
                String nameFromIni = collections.getSectionParameter(convertedConfig, MAIN_SECTION, PROBE_NAME_KEY);
                if ((nameFromIni != null && nameFromIni.equalsIgnoreCase(probeName)) || probeName.equalsIgnoreCase(probeInfo.getName())) {
                    settings = convertedConfig;

                    // Проверяем, есть ли в параметре имя теста, если нет - подставляем имя файла
                    if (nameFromIni == null) {
                        // Нет секции Main - создаём
                        if (settings.get(MAIN_SECTION) == null) {
                            settings.put(MAIN_SECTION, new LinkedHashMap<String, String>());
                        }
                        // Добавляем имя из файла в качестве имени теста
                        settings.get(MAIN_SECTION).put(PROBE_NAME_KEY, probeInfo.getName());
                    }
                }
            } catch (Exception ignore) {
                settingsReaderLogger.debug(ignore);
            }
        }

        // Для отладки, перечисляем имена
        if (debugState) {
            String buffer = "Probe type " + probeType + " elements: " + (settings != null ? settings.toString() : "[NULL]");
            settingsReaderLogger.debug(buffer);
        }

        // Возвращаем список
        return settings;
    }

    /**
     * Выполняет разбор типов тестов из строки с разделителями
     * в коллекцию-список (LinkedList)
     *
     * @param rawProbeTypes Строка из Ini-файла с разделителями, типы тестов
     * @return Разобранный список либо null, если ничего нет
     */
    private LinkedList<String> parseRAWProbeTypes(String rawProbeTypes) {
        // Выполняем разбор списка
        StringTokenizer rawString = new StringTokenizer(rawProbeTypes, " ,;/");
        LinkedList<String> values = new LinkedList<>();
        while (rawString.hasMoreElements()) {
            values.add(rawString.nextToken());
        }

        // Отображаем список в дебаг-режиме
        if (debugState) {
            String buffer = "Registered probe types: ";
            for (String item : values)
                buffer += "[" + item + "]";
            settingsReaderLogger.debug(buffer);
        }

        return values;
    }

    /**
     * Возвращает доступные типы тестов
     *
     * @return Список доступных типов
     */
    public LinkedList<String> getProbeTypes() {
        return enabledModules;
    }

    /**
     * Возвращает доступные файлы параметров тестов определённого
     * типа теста, исходя из настроек расположения в основном файле
     *
     * @param probeType Тип тестов
     * @return Список файлов либо null при отсутствии или ошибке ввода-вывода
     */
    @Nullable
    private File[] getCategorisedProbeList(String probeType) {
        // Прежде всего проверяем, существует ли такой тип тестов
        String findingProbeType = collections.searchInLinkedList(getProbeTypes(), probeType);
        if (findingProbeType == null) {
            settingsReaderLogger.debug("Probe type " + probeType + " not found in probe types list");
            return null;
        }

        // Ищем основной файл конфигурации
        File configFile = new File(configFileName);
        if (!configFile.exists()) {
            settingsReaderLogger.error(MESSAGE_CONFIG_FILE_NOT_FOUND + configFileName);
            return null;
        } else {
            fileFixPerm.tryFixFilePerm(configFileName);
        }

        // Считываем основной ini-файл
        Ini mainConfig;
        try {
            mainConfig = new Ini(new InputStreamReader(new FileInputStream(configFile), "UTF-8"));
        } catch (Exception exc) {
            settingsReaderLogger.error(MESSAGE_CONFIG_FILE_READ_ERROR + exc.toString());
            return null;
        }

        // Конвертируем настройки
        LinkedHashMap<String, LinkedHashMap<String, String>> mainSettings = collections.IniToLinkedHashMap(mainConfig);
        settingsReaderLogger.debug("Settings in main config file: " + mainSettings.toString());

        // Вначале проверяем, имеется ли описывающая секция с настройкой расположения описаний тестов
        // Если имеется - считываем расположение
        // иначе - расположение по-умолчанию - относительная директория+директория с именем теста
        String locationPath = findingProbeType;
        if (collections.searchKeyInComboIgnoreCase(mainSettings, PROBES_SETTINGS_PATH_SECTION) != null) {
            locationPath = collections.getSectionParameter(mainSettings, PROBES_SETTINGS_PATH_SECTION, findingProbeType, locationPath);
        }
        settingsReaderLogger.debug("Location path is " + locationPath);

        // Проверяем, имеется ли такая директория, директория ли это и содержит ли она файлы
        File locationDir = new File(locationPath);
        if (!locationDir.isDirectory()) {
            settingsReaderLogger.debug("Location path is not a directory");
            return null;
        }
        File[] filesList = locationDir.listFiles(); // Список файлов в поддиректории
        if (filesList == null || filesList.length == 0) {
            settingsReaderLogger.debug("Files list in " + locationPath + " empty or cannot read");
            return null;
        }

        // Для отладки, перечисляем список файлов
        if (debugState) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("Files list: ");
            for (File item : filesList) {
                buffer.append("[").append(item.getName()).append("]");
            }
            settingsReaderLogger.debug(buffer);
        }

        return filesList;
    }

    /**
     * Получение списка доступных тестов определённой категории
     *
     * @param probeType Имя категории
     * @return Список
     */
    public LinkedList<String> getProbesList(String probeType) {

        // Список доступных файлов, изначально пуст
        LinkedList<String> values = new LinkedList<>();

        // Получаем список доступных файлов
        File[] filesList = getCategorisedProbeList(probeType);
        if (filesList == null) return values;


        // Обрабатываем каждый файл последовательно, заносим информацию в объект
        for (File probeInfo : filesList) {
            try {
                Ini probeConfig = new Ini(new InputStreamReader(new FileInputStream(probeInfo), "UTF-8"));
                LinkedHashMap<String, LinkedHashMap<String, String>> convertedConfig = collections.IniToLinkedHashMap(probeConfig);
                // Выполняем поиск имени теста, если нет - возвращаем
                values.add(collections.getSectionParameter(convertedConfig, MAIN_SECTION, PROBE_NAME_KEY, probeInfo.getName()));
            } catch (Exception ignore) {
                settingsReaderLogger.debug(ignore);
            }
        }

        // Для отладки, перечисляем имена
        if (debugState) {
            StringBuilder buffer = new StringBuilder();
            if (values.size() != 0) {
                buffer.append("Probe type ").append(probeType).append(" has elements: ");
                for (String item : values)
                    buffer.append("[").append(item).append("]");
            } else {
                buffer.append("Probe type ").append(probeType).append(" no has elements.");
            }
            settingsReaderLogger.debug(buffer);
        }

        // Возвращаем список
        return values;
    }

    /**
     * Возвращает именованный сублоггер логгера первого уровня
     *
     * @param moduleName Имя модуля
     * @return Суб-логгер
     */
    public logger getCoreSubLogger(String moduleName) {
        return coreLogger.getModuleSubLogger(moduleName);
    }

    /**
     * Возвращает имя файлового сокета
     *
     * @return Строковое имя файлового сокета из настроек
     */
    public String getSocketFileName() {
        return socketFileName;
    }

    /**
     * Возвращает основной файл настроек
     *
     * @return Файл настроек (скорвертированный ini)
     */
    public LinkedHashMap<String, LinkedHashMap<String, String>> getMainSettings() {
        return mainSettings;
    }
}
