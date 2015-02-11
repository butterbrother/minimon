package org.minimon.probes;

import org.minimon.core.logger;
import org.minimon.core.staticValues;
import org.minimon.utils.collections;

import java.util.LinkedHashMap;

/**
 * Вспомогательный класс, помогает распарсить стандартные параметры
 */
public class helper
        implements staticValues {
    // Параметры
    private LinkedHashMap<String, LinkedHashMap<String, String>> parameters;
    // Логгер
    private logger log;

    // Параметры тестов
    // Пауза между проверками
    private long checkDelay = 0;
    // Необходимость использовать фильтр ложных срабатываний
    private boolean needFailFilter = false;
    // Пауза между итерациями фильтра ложных срабатываний
    private long failFilterDelay = 0;
    // Общее число итераций фильтра
    private long failFilterCount = 0;
    // Минимальное необходимое число положительных итераций фильтра
    private long failFilterSuccessCount = 0;
    // Дополнительный интервал между неудачными проверками
    private long failUpInterval = 0;
    // Удвоение дополнительного интервала при каждой неудачной проверке
    private boolean doubleFailIntervals = false;
    // Необходимость трассировки до удалённого сервера
    private boolean needTraceRoute = false;


    /**
     * Инициализация
     *
     * @param options Настройки
     */
    public helper(LinkedHashMap<String, LinkedHashMap<String, String>> options, logger log) {
        // Получаем настройки
        parameters = options;
        this.log = log;
    }

    // Отдача результатов

    /**
     * Получение интервала между проверками
     *
     * @return Значение интервала
     */
    public long getCheckDelay() {
        return checkDelay;
    }

    /**
     * Получение необходимости использовать фильтр
     * случайных ошибок
     *
     * @return Необходимость использования фильтра
     */
    public boolean isNeedFailFilter() {
        return needFailFilter;
    }

    /**
     * Получение интервала между итерациями фильтра
     * случайных ошибок
     *
     * @return Значение интервала
     */
    public long getFailFilterDelay() {
        return failFilterDelay;
    }

    /**
     * Получение числа итераций фильтра случайных ошибок
     *
     * @return Число итераций
     */
    public long getFailFilterCount() {
        return failFilterCount;
    }

    /**
     * Получение минимального числа успешных итераций фильтра
     * случайных ошибок
     *
     * @return Число успешных итераций
     */
    public long getFailFilterSuccessCount() {
        return failFilterSuccessCount;
    }

    /**
     * Получение дополнительного итервала между провалами
     *
     * @return Значение интервала
     */
    public long getFailUpInterval() {
        return failUpInterval;
    }

    /**
     * Получение необходимости удваивать дополнительный интервал
     * между провалами в случае идущих подряд провалов
     *
     * @return Необходимость удвоения
     */
    public boolean isDoubleFailIntervals() {
        return doubleFailIntervals;
    }

    /**
     * Получение необходимости проводить трассировку до удалённого
     * узла
     *
     * @return Необходимость трассировки
     */
    public boolean isNeedTraceRoute() {
        return needTraceRoute;
    }

    /**
     * Парсинг и корректировка основных параметров работы процесса-потока
     * Изначально все основные параметры нулевые и false
     *
     * @param minCheckDelay            Минимально допустимая пауза между проверками
     * @param maxCheckDelay            Максимально допустимая пауза между проверками
     * @param enableFailFilter         Необходимость работы фильтра случайных ошибок с точки зрения теста
     * @param minFailFilterDelay       Минимально допустимая пауза фильтра
     * @param maxFailFilterDelay       Максимально допустимая пауза фильтра
     * @param minFailFilterCount       Минимально допустимое число итераций фильтра
     * @param maxFailFilterCount       Максимально допустимое число итераций фильтра
     * @param minFailUpIntervals       Минимально допустимое увеличение паузы в случае провала
     * @param maxFailUpIntervals       Максимально допустимое увеличение паузы в случае провала
     * @param enableDoubleFailInterval Необходимость удвоения интервала увеличения в случае провала
     * @param enableTraceRoute         Необходимость ведения трассировки
     */
    public void parseBasical(
            long minCheckDelay,
            long maxCheckDelay,
            boolean enableFailFilter,
            long minFailFilterDelay,
            long maxFailFilterDelay,
            long minFailFilterCount,
            long maxFailFilterCount,
            long minFailUpIntervals,
            long maxFailUpIntervals,
            boolean enableDoubleFailInterval,
            boolean enableTraceRoute
    ) {
        // Пауза между проверками
        checkDelay = 1000 * getLongParamValue(
                MAIN_SECTION,
                PROBE_CHECK_DELAY_NAME,
                PROBE_CHECK_DELAY_DEFAULT,
                minCheckDelay,
                maxCheckDelay
        );
		log.debug("Check Delay: " + Long.toString(checkDelay));

        // Необходимость фильтра
        needFailFilter = parseBoolean(
                MAIN_SECTION,
                PROBE_NEED_FAIL_FILTER_NAME,
                PROBE_NEED_FAIL_FILTER_DEFAULT
        ) & enableFailFilter;
		log.debug("Need fail filter: " + Boolean.toString(needFailFilter));

        // Пауза фильтра
        failFilterDelay = 1000 * getLongParamValue(
                MAIN_SECTION,
                PROBE_FAIL_FILTER_DELAY_NAME,
                PROBE_FAIL_FILTER_DELAY_DEFAULT,
                minFailFilterDelay,
                maxFailFilterDelay
        );
		log.debug("Fail filter delay: " + Long.toString(failFilterDelay));

        // Число итераций фильтра
        failFilterCount = getLongParamValue(
                MAIN_SECTION,
                PROBE_FAIL_FILTER_COUNT_NAME,
                PROBE_FAIL_FILTER_COUNT_DEFAULT,
                minFailFilterCount,
                maxFailFilterCount
        );
		log.debug("Fail filter count: " + Long.toString(failFilterCount));

        // Минимальное число успешных итераций фильтра
        failFilterSuccessCount = getLongParamValue(
                MAIN_SECTION,
                PROBE_FAIL_FILTER_SUCCESS_COUNT_NAME,
                PROBE_FAIL_FILTER_SUCCESS_COUNT_DEFAULT,
                minFailFilterCount,
                failFilterCount // Должен быть <= общего числа итераций
        );
		log.debug("Fail filter success count: " + Long.toString(failFilterSuccessCount));

        // Увеличение паузы в случае провала
        failUpInterval = 1000 * getLongParamValue(
                MAIN_SECTION,
                PROBE_FAIL_UP_INTERVAL_NAME,
                PROBE_FAIL_UP_INTERVAL_DEFAULT,
                minFailUpIntervals,
                maxFailUpIntervals
        );
		log.debug("Fail up interval: " + Long.toString(failUpInterval));

        // Необходимость удвоения паузы в случае провала
        doubleFailIntervals = parseBoolean(
                MAIN_SECTION,
                PROBE_DOUBLE_FAIL_INTERVAL_NAME,
                PROBE_DOUBLE_FAIL_INTERVAL_DEFAULT
        ) & enableDoubleFailInterval;
		log.debug("Double fail intervals: " + Boolean.toString(doubleFailIntervals));

        // Необходимость трассировки
        needTraceRoute = parseBoolean(
                MAIN_SECTION,
                PROBE_NEED_TRACE_ROUTE_NAME,
                PROBE_NEED_TRACE_ROUTE_DEFAULT
        ) & enableTraceRoute;
		log.debug("Need trace route: " + Boolean.toString(needTraceRoute));
    }

    /**
     * Получение целочисленного параметра
     * с последующей коррекцией
     *
     * @param sectionName      Имя секции
     * @param longValueName    Имя ключа
     * @param longValueDefault Значение по-умолчанию (можно не указывать, но тогда умолчание - 0)
     * @param minValue         Минимально допустимое значение
     * @param maxValue         Максимально допустимое значение
     * @return Целочисленное откорректированное значение [секции]->ключа
     */
    public long getLongParamValue(
            String sectionName,
            String longValueName,
            String longValueDefault,
            long minValue,
            long maxValue
    ) {
        return longCorrect(
                parseLong(
                        sectionName,
                        longValueName,
                        longValueDefault
                ),
                minValue,
                maxValue
        );
    }

    /**
     * Коррекция целочисленных
     *
     * @param currentValue Текущее значение
     * @param minValue     Минимальное значение
     * @param maxValue     Максимальное значение
     * @return Коррекция
     */
    private long longCorrect(long currentValue, long minValue, long maxValue) {
        if (currentValue < minValue)
            return minValue;
        if (currentValue > maxValue)
            return maxValue;
        return currentValue;
    }

    /**
     * Парсинг String -> Long
     * из параметров
     * Для внутренних целей с коррекцией в 0 в случае ошибки
     *
     * @param sectionName      Имя секции
     * @param longKeyName      Параметр, содержащий Long
     * @param longDefaultValue Значение по-умолчанию (может быть null)
     * @return Значение типа long
     */
    private long parseLong(String sectionName, String longKeyName, String longDefaultValue) {
        long returnValue;
        try {
            if (longDefaultValue != null) {
                returnValue = Long.parseLong(collections.searchKeyInSubIgnoreCase(parameters, sectionName, longKeyName, longDefaultValue));
            } else {
                returnValue = Long.parseLong(collections.searchKeyInSubIgnoreCase(parameters, sectionName, longKeyName, "0"));
            }
        } catch (NumberFormatException exc) {
            returnValue = 0;
            log.error("Unable to parse number in parameter [" + sectionName + "]->" + longKeyName);
        }
        log.debug("Parsing: [" + sectionName + "]->" + longKeyName + ": " + Long.toString(returnValue));
        return returnValue;
    }

    /**
     * Парсинг String -> Boolean
     * из параметров
     * Для внутренних целей с коррекцией в false в случае отсутствия значения по-умолчанию
     *
     * @param sectionName         Имя секции
     * @param booleanKeyName      Параметр, содержащий boolean/yes-no/enable-disable значения
     * @param booleanDefaultValue Значение по-умолчанию
     * @return Значение типа boolean
     */
    private boolean parseBoolean(String sectionName, String booleanKeyName, String booleanDefaultValue) {
        String buffer;
        if (booleanDefaultValue != null) {
            buffer = collections.searchKeyInSubIgnoreCase(parameters, sectionName, booleanKeyName, booleanDefaultValue);
        } else {
            buffer = collections.searchKeyInSubIgnoreCase(parameters, sectionName, booleanKeyName, "FALSE");
        }
        return (Boolean.parseBoolean(buffer) || buffer.toLowerCase().contains("yes") || buffer.toLowerCase().contains("enable"));
    }
}
