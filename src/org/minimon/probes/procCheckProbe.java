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
 * Проверка наличия запущенного процесса
 */
public class procCheckProbe
        implements probe, staticValues {

    // Логгер для отладки и ошибок выполнения
    private logger log;
    // Последняя ошибка теста
    private String lastError = "";
    // Переданные параметры
    private helper basicParser = null;
    private LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;
    // procExec для запуска отчёта запущенных процессов
    private procExec ps = null;
    // Строка с именем процесса, поисковая фраза
    private String procCheck;

    /**
     * Импорт настроек из ini-файла
     *
     * @param settings Настройки в виде вложенной LinkedHashMap
     */
    @Override
    public void importSettings(LinkedHashMap<String, LinkedHashMap<String, String>> settings) {
        this.settings = settings;
    }

    /**
     * Выполнение подготовки к проверкам
     * Парсинг параметров и т.п.
     *
     * @param log        Ссылка на текущий логгер
     * @param debugState Состояние отладки
     * @return Успешность подготовки
     */
    @Override
    public boolean prepare(logger log, boolean debugState) {
        this.log = log;

        // Определяем, передавали ли настройки вообще
        if (settings == null) {
            lastError = "BUG: Settings not imported";
            return false;
        }

        // Базовые параметры для процесса-потока
        basicParser = new helper(settings, log.getModuleSubLogger("Common settings parser"));
        // Парсим и корректируем
        basicParser.parseBasical(
                // Ограничения базовых параметров
                1,      // Минимально допустимая пауза между проверками
                86400,  // Максимально допустимая пауза между проверками
                false,  // Необходимость работы фильтра случайных ошибок с точки зрения теста
                1,      // Минимально допустимая пауза фильтра
                1,      // Максимально допустимая пауза фильтра
                1,      // Минимально допустимое число итераций фильтра
                1,      // Максимально допустимое число итераций фильтра
                0,      // Минимально допустимое увеличение паузы в случае провала
                3600,   // Максимально допустимое увеличение паузы в случае провала
                true,   // Необходимость удвоения интервала увеличения в случае провала
                false   // Необходимость ведения трассировки
        );

        // Секция [Main]
        // Process = Имя процесса
        String PROBE_PROC_CHECK = "Process";
        procCheck = collections.getSectionParameter(settings, MAIN_SECTION, PROBE_PROC_CHECK);

        // Только один параметр, имя процесса. Обязателен
        if (procCheck == null) {
            lastError = "Process checking name not set. It can be set in [" + MAIN_SECTION + "] -> \"" + PROBE_PROC_CHECK + "\".";
            return false;
        }

        // Инициализируем ps исходя из типа ОС
        switch (cross.getSystemType()) {
            case (OS_TYPE_LINUX):
            case (OS_TYPE_SUN):
                ps = new procExec(debugState, log.getModuleSubLogger("ps executor"), "ps", "-ef");
                break;
            case (OS_TYPE_WINDOWS):
                ps = new procExec(debugState, log.getModuleSubLogger("tasklist executor"), "tasklist", "/V");
                break;
            default:
                ps = new procExec(debugState, log.getModuleSubLogger("unknown executor"), "echo", "Unsupported os");
        }

        // Очистка указателя настроек
        settings = null;
        return true;
    }

    /**
     * Непосредственное выполнение проверки
     *
     * @return Результат проверки
     */
    @Override
    public boolean iteration() {
        if (ps == null) return false;

        lastError = "";

        try {
            // Исполняем
            ps.execute();
            StreamGrabber errors = new StreamGrabber(ps.getStderr(), "<stderr>", log.getModuleSubLogger("StdErr grabber"));
            StreamGrabber results = new StreamGrabber(ps.getStdout(), "", log.getModuleSubLogger("StdOut grabber"));
            ps.waitFor();

            // Считываем в ошибки всё из stderr
            lastError += errors;

            // Ищем в выхлопе ps имя процесса
            if (results.getResults().toLowerCase().contains(procCheck)) {
                // Если нашли
                log.debug("Process name - " + procCheck + ", found");
                return true;
            } else {
                // Если не нашли
                lastError += System.lineSeparator() + "Check phrase not found";
                return false;
            }
        } catch (IOException exc) {
            log.appErrorWriter(exc);
        } catch (InterruptedException exc) {
            lastError = "Interrupted";
            ps.terminate();
            Thread.currentThread().interrupt();
            return true;
        }

        return false;
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
     *
     * @return Последняя полученная ошибка
     */
    @Override
    public String getLastError() {
        return lastError;
    }

    /**
     * Отдаёт цель для traceroute
     *
     * @return В данном случае трассировка не нужна, всегда возвращается null
     */
    @Override
    public String getTracerouteTarget() {
        return null;
    }

    /**
     * Возвращает тип проверки
     *
     * @return Тип проверки
     */
    @Override
    public String getCheckType() {
        return "Process check";
    }
}
