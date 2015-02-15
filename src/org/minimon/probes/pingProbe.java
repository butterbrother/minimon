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
import org.minimon.system.StreamGrabber;
import org.minimon.system.cross;
import org.minimon.system.procExec;
import org.minimon.utils.collections;

import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * Тест пингом
 */
public class pingProbe
        implements probe, staticValues {

    // Логгер, только для отладки
    private logger log;
    // Последняя ошибка теста
    private String lastError = "";
    // URL/IP для выполнения пинга
    private String URL = null;
    // procExec для пинга
    private procExec ping = null;
    // Параметры из ini
    private LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;
    // Helper
    private helper basicParser = null;

    /**
     * Получение настроек
     *
     * @param settings Настройки в виде вложенной LinkedHashMap
     */
    @Override
    public void importSettings(LinkedHashMap<String, LinkedHashMap<String, String>> settings) {
        this.settings = settings;
    }

    /**
     * Подготовка и проверка параметров
     *
     * @param log        Ссылка на текущий логгер
     * @param debugState состояние отладки
     * @return Готовность
     */
    @Override
    public boolean prepare(logger log, boolean debugState) {
        this.log = log;

        // Вначале проверяем, были ли переданы параметры вообще
        if (settings == null) {
            lastError = "Settings not imported";
            return false;
        }

        // Базовые параметры для процесса-потока
        basicParser = new helper(settings, log.getModuleSubLogger("Common settings parser"));
        // Парсим и корректируем
        basicParser.parseBasical(
                // Ограничения базовых параметров
                1,      // Минимально допустимая пауза между проверками
                86400,  // Максимально допустимая пауза между проверками
                true,   // Необходимость работы фильтра случайных ошибок с точки зрения теста
                1,      // Минимально допустимая пауза фильтра
                60,     // Максимально допустимая пауза фильтра
                2,      // Минимально допустимое число итераций фильтра
                100,    // Максимально допустимое число итераций фильтра
                0,      // Минимально допустимое увеличение паузы в случае провала
                3600,   // Максимально допустимое увеличение паузы в случае провала
                true,   // Необходимость удвоения интервала увеличения в случае провала
                true    // Необходимость ведения трассировки
        );
        // Параметр URL
        URL = collections.getSectionParameter(settings, MAIN_SECTION, "Host");
        if (URL == null) {
            lastError = "Empty URL or IP address";
            return false;
        }

        // Даём имя логгеру (для отладки)
        log.renameModule("Ping probe - " + URL);

        // Создаём объект для запуска
        switch (cross.getSystemType()) {
            case (OS_TYPE_LINUX):
                ping = new procExec(debugState, log.getModuleSubLogger("Ping exec"), "ping", "-c", "1", "-w", "10", URL);
                break;
            case (OS_TYPE_SUN):
                ping = new procExec(debugState, log.getModuleSubLogger("Ping exec"), "/usr/sbin/ping", "-s", URL, "64", "1");
                break;
            case (OS_TYPE_WINDOWS):
                ping = new procExec(debugState, log.getModuleSubLogger("Ping exec"), "ping", "-n", "1", URL);
                break;
            default:
                ping = new procExec(debugState, log.getModuleSubLogger("Ping exec"), "echo", "Unknown OS");
                log.error("Unknown operation system, ping probe not supported");
        }

        // Параметры определены, очистка
        settings = null;
        return true;
    }

    /**
     * Выполнение теста
     *
     * @return результат теста
     */
    @Override
    public boolean iteration() {
        // Получаем выхлоп либо ошибки, если получаем
        // Все ошибки на этот раз пишем не в лог, а в lastError
        if (ping == null) return false;
        lastError = "";
        try {
            ping.execute();
            StreamGrabber results = new StreamGrabber(ping.getStdout(), "", log.getModuleSubLogger("StdOut grabber"));
            StreamGrabber errors = new StreamGrabber(ping.getStderr(), "<stderr>", log.getModuleSubLogger("StdErr grabber"));
            ping.waitFor();
            lastError += errors.getResults() + System.lineSeparator() + results.getResults();
            lastError += results.getResults();
        } catch (IOException exc) {
            lastError = log.appErrorWriter(exc);
            return false;
        } catch (InterruptedException exc) {
            lastError = "Interrupted";
            ping.terminate();
            Thread.currentThread().interrupt();
            return true;
        }

        // проверяем и возвращаем коды возврата приложения
        log.debug("Returned value: "
                        + Integer.toString(ping.exitValue())
                        + ", URL: "
                        + URL
                        + System.lineSeparator()
                        + lastError
        );

        return (ping.exitValue() == 0);
    }

    //--------------------------------------------------------------
    // Отдача значений в процесс-поток

    /**
     * Отдаёт цель для traceroute
     *
     * @return Цель traceroute
     */
    @Override
    public String getTracerouteTarget() {
        return URL;
    }

    /**
     * Возвращает тип проверки
     *
     * @return Тип проверки
     */
    @Override
    public String getCheckType() {
        return "Ping";
    }

    /**
     * Отдаём последнюю полученную ошибку
     *
     * @return Последняя ошибка
     */
    @Override
    public String getLastError() {
        return lastError;
    }

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
}
