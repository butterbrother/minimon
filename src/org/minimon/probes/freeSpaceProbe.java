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

import java.io.File;
import java.util.LinkedHashMap;

/**
 * Проверка свободного дискового пространства
 */
public class freeSpaceProbe
        implements probe, staticValues {

    // Все настройки
    LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;
    // Логгер для отладки и ошибок выполнения
    private logger log;
    // Последняя ошибка теста
    private String lastError = "";
    // Объект-файл для получения свободного дискового пространства
    private File partitionFile;
    private long minimalFreeSpace;
    // Парсер унифицированных настроек процесса-потока
    private helper basicParser = null;

    /**
     * Получение настроек из ini-файла
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

        // Проверяем само наличие настроек
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
        // Partition = проверяемый раздел
        String PROBE_FREE_SPACE_PARTITION = "Partition";
        // Minimum = минимальное пространство, указывается в настройках в килобайтах
        String PROBE_FREE_SPACE_MINIMUM = "Minimum";
        String partitionName = collections.getSectionParameter(settings, MAIN_SECTION, PROBE_FREE_SPACE_PARTITION);
        minimalFreeSpace = 1024 * basicParser.getLongParamValue(
                MAIN_SECTION,
                PROBE_FREE_SPACE_MINIMUM,
                "512",
                100L,
                1000000000L
        );

        // Проверяемый раздел - обязательный параметр
        if (partitionName == null) {
            lastError = "Partition path or name not set. It can be set in section [" + MAIN_SECTION + "], parameter \"" + PROBE_FREE_SPACE_PARTITION + "\"";
            return false;
        }

        // Минимальное свободное пространство - желательный, отсутствие не критично
        if (collections.getSectionParameter(settings, MAIN_SECTION, PROBE_FREE_SPACE_MINIMUM) == null) {
            log.warning("Minimal free space not set, use default - 512 Kb. It can be set in section [" + MAIN_SECTION + "], parameter \"" + PROBE_FREE_SPACE_MINIMUM + "\"");
        }

        // Проверяем наличие самого файла
        partitionFile = new File(partitionName);
        if (!partitionFile.exists()) {
            lastError += "Check partition mount point or file on partition not found - " + partitionName;
            return false;
        }

        // Очистка указателя настроек
        settings = null;

        return true;
    }

    /**
     * Выполнение проверки
     *
     * @return Результат. Если false - ошибка, при этом само значение ошибки
     * хранится в lastError
     */
    @Override
    public boolean iteration() {
        lastError = "";

        // Сначала проверяем наличие раздела
        if (!partitionFile.exists()) {
            lastError += "Check partition mount point or file on partition not found - " + partitionFile.getAbsolutePath();
            return false;
        }

        // Теперь проверяем свободное пространство
        if (partitionFile.getFreeSpace() < minimalFreeSpace) {
            lastError =
                    "Overflowed free disk space"
                            + System.lineSeparator()
                            + "Avaliable: " + Long.toString(partitionFile.getFreeSpace() / 1024) + " kb" + System.lineSeparator()
                            + "Total space: " + Long.toString(partitionFile.getTotalSpace() / 1024) + " kb" + System.lineSeparator()
                            + "Minimal free space set: " + Long.toString(minimalFreeSpace / 1024) + " kb";

            return false;
        } else {
            log.debug("Avaliable: " + Long.toString(partitionFile.getFreeSpace() / 1024) + " kb" + System.lineSeparator()
                    + "Total space: " + Long.toString(partitionFile.getTotalSpace() / 1024) + " kb" + System.lineSeparator()
                    + "Minimal free space set: " + Long.toString(minimalFreeSpace / 1024) + " kb");
            return true;
        }

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
        return "Free space check";
    }

    /**
     * Отдаём последнюю ошибку теста
     *
     * @return Последняя ошибка
     */
    @Override
    public String getLastError() {
        return lastError;
    }
}
