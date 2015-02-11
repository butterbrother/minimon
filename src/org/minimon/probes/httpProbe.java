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
import org.minimon.system.cross;
import org.minimon.system.procExec;
import org.minimon.utils.collections;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;

/**
 * Тест работоспособности веб-сервиса
 * <p/>
 * По-умолчанию проверяется только код возврата
 * Если указана поисковая фраза - то ищется фраза на сайте
 */
public class httpProbe
        implements probe, staticValues {

    // Логгер для отладки и ошибок выполнения
    private logger log;

    // Последняя ошибка теста
    private String lastError = "";

    // procExec для wget теста
    private procExec wget = null;

    // Настройки
    private LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;
    // Парсер базовых параметров
    private helper basicParser = null;

    // Отдельно - цель traceroute.
    private String traceRouteTarget;
    // Искомая фраза
    private String phrase = null;

    /**
     * Импорт настроек из ini-файла
     *
     * @param settings Настройки в виде вложенной LinkedHashMap
     */
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
    public boolean prepare(logger log, boolean debugState) {
        this.log = log;

        // Проверяем само наличие настроек
        if (settings == null) {
            lastError = "BUG: settings not imported";
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

        // Получаем параметры
        String PROBE_URL = "URL";
        String Url = collections.searchKeyInSubIgnoreCase(settings, MAIN_SECTION, PROBE_URL);
        String PROBE_LOGIN = "Login";
        String Login = collections.searchKeyInSubIgnoreCase(settings, MAIN_SECTION, PROBE_LOGIN);
        String PROBE_PASSWORD = "Password";
        String Password = collections.searchKeyInSubIgnoreCase(settings, MAIN_SECTION, PROBE_PASSWORD);
        String PROBE_SEARCH_PHRASE = "Search";
        phrase = collections.searchKeyInSubIgnoreCase(settings, MAIN_SECTION, PROBE_SEARCH_PHRASE);

        // Проверяем наличие необходимых
        if (Url == null) {
            lastError += "URL parameter not set. It can be set into [" + MAIN_SECTION + "] -> \"" + PROBE_URL + "\" property";
            return false;
        }
        // Опциональные
        if ((Login != null && Password == null) || (Password != null && Login == null)) {
            log.warning("Set only login without password (or contrary)");
        }

        // Далее разбираем, конвертируем и создаём параметры запуска в зависимости от типа ОС
        // и указанных параметров
        // wget считает все полученные пустые параметры именами сайтов
        LinkedList<String> params = new LinkedList<>();

        switch (cross.getSystemType()) {
            case (OS_TYPE_LINUX):
                params.add("wget");
                break;
            case (OS_TYPE_SUN):
                params.add("/usr/sfw/bin/wget");
                break;
            case (OS_TYPE_WINDOWS):
                params.add("lib\\wget.exe");
                break;
            default:
                params.add("echo");
                log.error("Operating system unknown and not supported");
        }

        params.add("-O");
        params.add("-");
        params.add("--no-check-certificate");
        if (Login != null) params.add("--user=" + Login);
        if (Password != null) params.add("--password=" + Password);
        params.add(Url);

        String[] wgetArrayParameters = params.toArray(new String[params.size()]);

        // Создаём объект исполнения wget и зануляем параметры
        wget = new procExec(debugState, log.getModuleSubLogger("wget executor"), wgetArrayParameters);

        // Цель trace route = url
        traceRouteTarget = Url;

        // Обнуляем указатель настроек (больше не нужен)
        settings = null;

        return true;
    }


    /**
     * Непосредственное выполнение проверки
     *
     * @return Результат проверки
     */
    public boolean iteration() {
        if (wget == null) {
            lastError = "Not initialised";
            return false;
        }
        lastError = "";

        try {
            wget.execute();
            BufferedReader result = wget.getStdout();
            BufferedReader errors = wget.getStderr();
            wget.waitFor();

            String buffer;
            while ((buffer = errors.readLine()) != null) {
                lastError += buffer + System.lineSeparator();
            }

            // Если имеется фраза - ищем её
            if (phrase != null) {
                while ((buffer = result.readLine()) != null) {
                    // Если находим
                    if (buffer.toLowerCase().contains(phrase.toLowerCase())) {
                        result.close();
                        errors.close();
                        log.debug("String - " + buffer + ", phrase - " + phrase + ", found");
                        return true;
                    }
                }
                // Если не находим
                lastError += System.lineSeparator() + "Check phrase not found";
                result.close();
                errors.close();
                return false;
            }
            result.close();
            errors.close();
        } catch (IOException exc) {
            log.appErrorWriter(exc);
        } catch (InterruptedException exc) {
            lastError = "Interrupted";
            wget.terminate();
            Thread.currentThread().interrupt();
            return true;
        }

        // проверяем и возвращаем коды возврата приложения
        log.debug("Ping probe returned value: "
                        + Integer.toString(wget.exitValue())
                        + ", URL: "
                        + traceRouteTarget
                        + System.lineSeparator()
                        + lastError
        );

        return (wget.exitValue() == 0);
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
     * Отдаёт цель для traceroute
     *
     * @return Цель traceroute
     */
    public String getTracerouteTarget() {
        return traceRouteTarget;
    }

    /**
     * Возвращает тип проверки
     *
     * @return Тип проверки
     */
    public String getCheckType() {
        return "HTTP Test";
    }

    /**
     * Отдаём последнюю полученную ошибку
     *
     * @return Последняя ошибка
     */
    public String getLastError() {
        return lastError;
    }
}
