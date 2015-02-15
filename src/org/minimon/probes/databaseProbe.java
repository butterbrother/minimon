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

package org.minimon.probes;

import org.minimon.core.logger;
import org.minimon.core.staticValues;
import org.minimon.utils.collections;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Properties;

/**
 * Тестирование соединений к БД
 */
public class databaseProbe
        implements probe, staticValues {

    // Флаг отладки
    private boolean debug;
    // Логгер для отладки и ошибок выполнения
    private logger log;
    // Последняя ошибка теста
    private String lastError = "";
    // JDBC URL базы данных
    private String URL = null;
    // URL/IP для трассировки
    private String traceURL = null;
    // Экземпляр драйвера доступа к БД
    private Driver driver;
    // Строка произвольного запроса к базе
    private String queryRequest = null;
    // Необходимый результат при выполнении этого запроса
    private String queryResult = null;
    // Настройки
    private LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;
    // Парсер унифицированных настроек процесса-потока
    private helper basicParser = null;
    // Свойства соединения
    private Properties connectionProperties = null;

    /**
     * Импорт настроек
     *
     * @param settings Настройки в виде вложенной LinkedHashMap
     */
    @Override
    public void importSettings(LinkedHashMap<String, LinkedHashMap<String, String>> settings) {
        this.settings = settings;
    }

    /**
     * Выполняет подготовку и инициализацию данных
     * Выполняет проверку входных данных и первоначальные
     * настройки
     * Возвращаемый статус готовности уведомляет процесс-поток о
     * необходимости продолжать работу либо завершить
     *
     * @param log        Ссылка на текущий логгер
     * @param debugState Статус отладки
     * @return Статус готовности
     */
    @Override
    public boolean prepare(logger log, boolean debugState) {
        // Получаем лог и статус отладки
        this.log = log;
        this.debug = debugState;

        // Проверяем, были ли переданы настройки
        if (settings == null) {
            lastError = "Parameters not set";
            return false;
        }

        // Секция [Main]
        // Trace to = хост трассировки
        String PROBE_DB_TRACE = "Trace to";
        traceURL = collections.getSectionParameter(settings, MAIN_SECTION, PROBE_DB_TRACE);
        // Секция [Database]
        String PROBE_DB_SECTION_NAME = "Database";
        // Driver = строка драйвера
        String PROBE_DB_DRIVER = "Driver";
        String JDBCDriver = collections.getSectionParameter(settings, PROBE_DB_SECTION_NAME, PROBE_DB_DRIVER);
        // URL = JDBC Url
        String PROBE_DB_URL = "Url";
        URL = collections.getSectionParameter(settings, PROBE_DB_SECTION_NAME, PROBE_DB_URL);
        // Query = Проверочный запрос
        String PROBE_DB_QUERY = "Query";
        queryRequest = collections.getSectionParameter(settings, PROBE_DB_SECTION_NAME, PROBE_DB_QUERY);
        // Сравниваемый ответ
        queryResult = collections.getSectionParameter(settings, PROBE_DB_SECTION_NAME, "Result");
        // Секция [Properties]
        String PROBE_DB_PROPERTIES_NAME = "Properties";
        connectionProperties = collections.sectionToProperties(settings, PROBE_DB_PROPERTIES_NAME);

        // Проверяем
        if (JDBCDriver == null) {
            lastError = "JDBC driver not set. Use \"" + PROBE_DB_DRIVER + "\" property in [" + PROBE_DB_SECTION_NAME + "] section to set it.";
            return false;
        }
        if (URL == null) {
            lastError = "JDBC URL not set. Use \"" + PROBE_DB_URL + "\" property in [" + PROBE_DB_SECTION_NAME + "] section to set it.";
            return false;
        }
        if (queryRequest == null) {
            lastError = "SQL check query not set. Use \"" + PROBE_DB_QUERY + "\" property in [" + PROBE_DB_SECTION_NAME + "] section to set it." + System.lineSeparator()
                    + "Can use:" + System.lineSeparator()
                    + "\"SELECT 1\"" + System.lineSeparator()
                    + "For H2, MySQL, MS-SQL, PostgreSQL database;" + System.lineSeparator()
                    + "\"SELECT 1 FROM DUAL\"" + System.lineSeparator()
                    + "For Oracle database;" + System.lineSeparator()
                    + "\"SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS\"" + System.lineSeparator()
                    + "For HSQLDB database;" + System.lineSeparator()
                    + "\"SELECT 1 FROM SYSIBM.SYSDUMMY1\"" + System.lineSeparator()
                    + "For Apache Derby database;" + System.lineSeparator()
                    + "\"SELECT current date FROM sysibm.sysdummy1\"" + System.lineSeparator()
                    + "For IBM DB2 database;" + System.lineSeparator()
                    + "\"select count(*) from systables\"" + System.lineSeparator()
                    + "For IBM Informix database;";
            return false;
        }

        // Предупреждение о пустых свойствах соединения
        if (connectionProperties.size() == 0) {
            log.warning("Empty JDBC properties. It can set in [" + PROBE_DB_PROPERTIES_NAME + "] section.");
        }
        // Предупреждение о невозможности выполнить трассировку (не указан узел)
        if (traceURL == null) {
            log.warning("\"" + PROBE_DB_TRACE + "\" property in [" + MAIN_SECTION + "] not set. Trace to remote host will be off.");
        }

        // Базовые параметры для процесса-потока
        basicParser = new helper(settings, log.getModuleSubLogger("Common settings parser"));
        // Парсим и корректируем
        basicParser.parseBasical(
                // Ограничения базовых параметров
                60,      // Минимально допустимая пауза между проверками
                86400,  // Максимально допустимая пауза между проверками
                false,   // Необходимость работы фильтра случайных ошибок с точки зрения теста
                1,      // Минимально допустимая пауза фильтра
                60,     // Максимально допустимая пауза фильтра
                2,      // Минимально допустимое число итераций фильтра
                100,    // Максимально допустимое число итераций фильтра
                30,      // Минимально допустимое увеличение паузы в случае провала
                3600,   // Максимально допустимое увеличение паузы в случае провала
                true,   // Необходимость удвоения интервала увеличения в случае провала
                (traceURL != null)    // Необходимость ведения трассировки
        );

        // Получаем экземпляр драйвера
        try {
            driver = (Driver) Class.forName(JDBCDriver.trim()).newInstance();
        } catch (ClassNotFoundException exc) {
            // Либо не получаем
            lastError = "JDBC Driver not found for name " + JDBCDriver + System.lineSeparator()
                    + "add database JDBC driver into \"lib\" directory";
            if (debug) log.appErrorWriter(exc);
            return false;
        } catch (InstantiationException exc) {
            lastError = "JDBC driver " + JDBCDriver + " not support empty properties initialise.";
            return false;
        } catch (IllegalAccessException exc) {
            lastError = "JDBC driver " + JDBCDriver + " not support external initialise.";
            return false;
        }

        // Очищаем указатель настроек
        settings = null;

        return true;
    }


    /**
     * Непосредственное проведение теста
     * Полное сообщение результата хранится в lastError
     *
     * @return Результат
     */
    public boolean iteration() {
        log.debug("Iteration with " + URL + " started.");
        Connection connection;
        Statement stat = null;
        ResultSet result = null;

        // Соединяемся
        try {
            if (connectionProperties == null) {             // По факту невозможно, но всё же
                connectionProperties = new Properties();
                throw new Exception("BUG: Connection properties is NULL!");
            }
        } catch (Exception exc) {
            log.appErrorWriter(exc);
        }
        try {
            connection = driver.connect(URL, connectionProperties);
            log.debug("DB " + URL + " connected");
        } catch (SQLException exc) {
            lastError = "Unable connect to database:" + System.lineSeparator()
                    + exc;
            return false;
        }

        try {
            if (connection != null) {
                // Получаем выражение
                stat = connection.createStatement();
                log.debug("DB " + URL + " statement created");
                // Иначе исполняем кастомный запрос и сравниваем с результатами
                if (debug) log.debug("DB " + URL + " try execute statement");
                result = stat.executeQuery(queryRequest);
                // Когда имеется результат
                if (result.next()) {
                    // Если указан собственный ответ
                    if (queryResult != null) {
                        if (!result.getString(1).equals(queryResult)) {
                            lastError += "Query result return: " + System.lineSeparator()
                                    + result.getString(1) + System.lineSeparator()
                                    + "But expected: " + System.lineSeparator()
                                    + queryResult;
                            result.close();
                            stat.close();
                            connection.close();
                            return false;
                        }
                    }
                    log.debug("Query result (first cell): " + result.getString(1));
                } else {
                    // Если нет результата, но при этом указан ожидаемый ответ
                    if (queryResult != null) {
                        lastError += "Empty query result";
                        result.close();
                        stat.close();
                        connection.close();
                        return false;
                    }
                }
                result.close();
                stat.close();
                connection.close();
                log.debug("DB " + URL + " connection closed");
            }
        } catch (SQLException exc) {
            // Аварийная обработка ошибки - тест возвращает отрицательный результат
            // и пытаемся отсоединиться
            lastError += "Sql check error: " + exc;
            try {
                if (result != null) result.close();
                if (stat != null) stat.close();
                connection.close();
            } catch (SQLException ignore) {
            }
            return false;
        }

        return true;
    }

    //--------------------------------------------------------------
    // Отдача значений в процесс-поток

    /**
     * Возвращает helper
     * После возврата ссылка будет очищена
     *
     * @return helper
     */
    @Override
    public helper getBasicalParseHelper() {
        helper retValue = basicParser;
        basicParser = null;
        return retValue;
    }

    /**
     * Возвращает последнюю ошибку
     * Чаще всего возвращает последнее сообщение статуса
     * проверки. (в зависимости от успеха)
     *
     * @return Последняя ошибка
     */
    @Override
    public String getLastError() {
        return lastError;
    }

    /**
     * Возвращает тип проверки
     *
     * @return Тип проверки
     */
    @Override
    public String getCheckType() {
        return "DB test";
    }

    /**
     * Возвращает цель трассировки
     *
     * @return Цель трассировки
     */
    public String getTracerouteTarget() {
        return (traceURL != null ? traceURL : "");
    }

}

