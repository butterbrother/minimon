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

/**
 * Основные статичные значения
 * Здесь указываем названия базовых параметров, и их значения по-умолчанию
 * в соответствующих константах. Только базовые параметры. Цели мониторинга
 * будут располагаться в отдельных, читабельных файлах
 * <p/>
 * Крайне важно, что бы параметры по-умолчанию были корректны
 */
public interface staticValues {

    //----------------------------------------------------------------------------------------
    // Флаги завершения работы
    // Нормальное завершение
    int EXIT_NORMAL = 0;
    // Ошибка в работе приложения
    int EXIT_RUNTIME_ERROR = 2;
    // Ошибка чтения настроек
    int EXIT_SETTINGS_READ_ERROR = 1;
    // Уже запущен
    int EXIT_ALREADY_RUN = 3;
    // Ещё не запущен
    int EXIT_NOT_RUN = 4;

    //----------------------------------------------------------------------------------------
    // Типы ос
    // Не определено
    int OS_TYPE_UNKNOWN = 0;
    // Linux
    int OS_TYPE_LINUX = 1;
    // Windows
    int OS_TYPE_WINDOWS = 2;
    // Solaris
    int OS_TYPE_SUN = 3;


    //----------------------------------------------------------------------------------------
    // Имена файлов и путей
    // Все должны быть относительны
    //----
    // Стандартный путь к файлу конфигурации
    String DEFAULT_CONFIG_FILE_NAME = "minimon.ini";

    //----------------------------------------------------------------------------------------
    // Сообщения
    //----
    // Невозможность получить доступ к lib
    String[] MESSAGE_GENERAL_ACCESS = {
            "Unable to access to enumerate parent directory of executable jar-file",
            "or unable to enumerate files in \"lib\" subdirectory.",
            "Try to fix permissions."
    };
    // Отсутствие директории lib
    String[] MESSAGE_GENERAL_LIB_NOT_FOUND = {
            "Directory \"lib\" not found. It directory contains:",
            "-- lib4j library",
            "-- probes libraries",
			"-- another executable files (for example, wget.exe for Windows)",
			"Please create this directory in same jar-file directory"
    };
    // Сообщение об отсутствии файла конфигурации
    String MESSAGE_CONFIG_FILE_NOT_FOUND = "Configuration file not found: ";
    // Сообщение об ошибках чтения
    String MESSAGE_CONFIG_FILE_READ_ERROR = "Error reading config file: ";

    // Отображение информации об отсутствии ini4j
    String[] SHOW_INI4J_NOT_FOUND = {
            "Unable to find ini4j library",
            "You can download in from site:",
            "http://sourceforge.net/projects/ini4j/files/ini4j-bin/",
            "Version >= 0.5.2",
			"And place into lib directory"
    };
    // Сообщение при каждом запуске, инициализации считывателя настроек
    String STARTUP_INFO = "Minimon core initialisation with command-line params.";
    // Сообщение о том, что имеется запущенный экземпляр
    String ALREADY_RUN = "Minimon process already run. See above in log file. Optionally use --stop to stop system.";
    // Сообщение о том, что процесс не запущен
    String NOT_RUN = "Minimon process not run. See above in log file. Optionally use --start to start system.";

    //----------------------------------------------------------------------------------------
    // Основной файл конфигурации
    //----
    // [main]
    String MAIN_SECTION = "Main";
    // e-mail адреса для уведомлений администраторов системы
    String MAIL_TO_NAME = "Mail to";
    String MAIL_TO_DEFAULT = "";
    // Названия активных/доступных модулей теста
    String ENABLED_MODULES_NAME = "Probes";
	String ENABLED_MODULES_DEFAULT = "databaseProbe freeSpaceProbe httpProbe pingProbe procCheckProbe";
    // Файловый сокет
    String SOCKET_PATH_NAME = "Socket";
    String SOCKET_PATH_DEFAULT = "minimon.soc";
    // [log]
    String LOG_SECTION = "Log";
    // Путь к логу
    String LOG_PATH_NAME = "Path";
    String LOG_PATH_DEFAULT = "minimon.log";
    // Активировать сжатие
    String LOG_ENABLE_COMPRESSION_NAME = "Compression";
    String LOG_ENABLE_COMPRESSION_DEFAULT = "true";
    // [location]
    String PROBES_SETTINGS_PATH_SECTION = "Location";

    //-----------------------------------------------------------------------------------------
    // Индивидуальный файл конфигурации теста, общая обязательная структура и общие обязательные
    // параметры (и умолчания)
    //----
    // [main]
    // Аналогично MAIN_SECTION
    // Имя
    String PROBE_NAME_KEY = "Name";
    // e-mail адреса отправки уведомлений
    // аналогично MAIL_TO_NAME/MAIN_TO_DEFAULT
    // Пауза между проверками
    String PROBE_CHECK_DELAY_NAME = "Check Delay";
    String PROBE_CHECK_DELAY_DEFAULT = "60";
    String PROBE_CHECK_DELAY_ERROR = "Unable to parse [Main]->Check Delay parameter (invalid number format)";
    // Необходимость фильтрации случайных ошибок
    String PROBE_NEED_FAIL_FILTER_NAME = "Fail Filter";
    String PROBE_NEED_FAIL_FILTER_DEFAULT = "Yes";
    // Пауза между инетациями фильтра
    String PROBE_FAIL_FILTER_DELAY_NAME = "Fail Filter Delay";
    String PROBE_FAIL_FILTER_DELAY_DEFAULT = "10";
    // Общее число итераций фильтра ошибок
    String PROBE_FAIL_FILTER_COUNT_NAME = "Fail Filter Count";
    String PROBE_FAIL_FILTER_COUNT_DEFAULT = "10";
    // Минимальное число успешных итераций
    String PROBE_FAIL_FILTER_SUCCESS_COUNT_NAME = "Fail Filter Success";
    String PROBE_FAIL_FILTER_SUCCESS_COUNT_DEFAULT = "5";
    // Дополнительный интервал между неудачными проверками
    String PROBE_FAIL_UP_INTERVAL_NAME = "Fail Up Delay";
    String PROBE_FAIL_UP_INTERVAL_DEFAULT = "20";
    // Удвоение дополнительного интервала при каждой неудачной проверке (подряд)
    String PROBE_DOUBLE_FAIL_INTERVAL_NAME = "Multiple Fail Delay";
    String PROBE_DOUBLE_FAIL_INTERVAL_DEFAULT = "Yes";
    // Необходимость проведения трассировка
    String PROBE_NEED_TRACE_ROUTE_NAME = "Traceroute";
    String PROBE_NEED_TRACE_ROUTE_DEFAULT = "Yes";
    // [external]
    // исполнение внешних приложений
    // Имя
    String EXTERNAL_EXEC_NAME = "External";
    // при предупреждении
    String EXTERNAL_EXEC_WARNING = "Warning";
    // при аварии
    String EXTERNAL_EXEC_ALERT = "Alert";
    // Необходимость дожидаться завершения работы внешнего приложения
    String EXTERNAL_DO_WAIT = "Waiting";

    //-----------------------------------------------------------------------------------------
    // Имена сублоггеров системы
    //----
    // Считыватель настроек
    String CORE_CONFIG_READER = "Settings reader";
    // e-mail-er отправки сообщений системы
    String CORE_MAILER = "System mail";

	// Отображение справки
    String[] SHOW_HELP = {
            "Minimon (experimental)",
            "Run only under GNU/Linux, SUNOS and MS Windows",
            "Flags:",
            "   --help | -h          		Show this help",
            "  --start | -s [module]    	Start daemon (or module)",
            "   --stop | -t [module]    	Stop daemon (or module)",
			"--restart | -r [module]		Restart (all modules or one)",
			" --config | -c config file		Use another config file",
			"								(default - " + DEFAULT_CONFIG_FILE_NAME + " in same of jar directory)",
            "--runtime               		Run under daemon mode (no detach), for init",
			"  --debug | -d					Run in debug mode",
			"",
			"Configuration file structure:",
			"Minimon use ini files with structure:",
			"[Section]",
			"Param 1 = option",
			"; Comment (optional, will be ignored)",
			"Param 2 = foo option",
			"",
			"Options can start and end with \"\" - this quotes automaticaly deleted (removed only pair)",
			"",
			"Main configuration file structure (default name is " + DEFAULT_CONFIG_FILE_NAME + "):",
			"[" + MAIN_SECTION + "]",
			MAIL_TO_NAME + " = minimon administrators e-mails (default - " + MAIL_TO_DEFAULT + ")",
			ENABLED_MODULES_NAME + " = active probes types (default - " + ENABLED_MODULES_DEFAULT + ").",
			"	Build-in probes (default value) can be set with simple name. External - with full (include \"java packages\") path",
			SOCKET_PATH_NAME + " = controller socket path name (default - " + SOCKET_PATH_DEFAULT + ")",
			"   This socket need to control daemon process.",
			"[" + LOG_SECTION + "]",
			LOG_PATH_NAME + " = application log path (default - " + LOG_PATH_DEFAULT + ")",
			LOG_ENABLE_COMPRESSION_NAME + " = enable old log compression (gzip), default - " + LOG_ENABLE_COMPRESSION_DEFAULT,
			"[" + PROBES_SETTINGS_PATH_SECTION + "]",
			"   This section contains custom path to own probes. Parameter name = probe type",
			"   Parameter option = ini-files for this probe type location"
    };
}

