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

import org.minimon.probes.probe;
import org.minimon.probes.probeThread;
import org.minimon.system.sendMail;
import org.minimon.utils.collections;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Основной класс, управляющий мониторингом
 */
public class controller
        implements staticValues {
    // Флаг отладки
    private final boolean debug;
    // Настройки (считыватель)
    private settingsReader settings;
    // Суб-логгер непосредственно для контроллера
    private logger controllerLogger;
    // Все тесты
    private LinkedBlockingDeque<probeThread<? extends probe>> currentProbes = new LinkedBlockingDeque<>();
    // Mail-er для администратора системы
    private sendMail systemMessages;
    // Флаг активности
    private boolean activeState = true;
    private boolean socketActiveState = false;

    /**
     * Инициализация
     *
     * @param settings   Считанные параметры
     * @param debugState Состояние отладки
     */
    public controller(settingsReader settings, boolean debugState) {
        debug = debugState;
        this.settings = settings;
        controllerLogger = settings.getCoreSubLogger("Controller");
        controllerLogger.debug("controller initialised");
        systemMessages = settings.getMailer();
    }

    /**
     * Точка отсчёта контроллера
     */
    public void workmode() {
        new socketReader(settings.getSocketFileName(), this, settings.getCoreSubLogger("Socket reader"));
        startAll();
        controllerLogger.info("All avaliable probes started, in work mode");
        int sleepSkip = 0;
        while (activeState) {
            try {
                Thread.sleep(100);
                sleepSkip += 1;
            } catch (InterruptedException exc) {
                stopAll();
                System.exit(EXIT_NORMAL);
            }
            if (sleepSkip >= 600) {
                sleepSkip = 0;
                if (currentProbes.size() > 0) {
                    StringBuilder buffer = new StringBuilder();
                    buffer.append("Active probes: ").append(System.lineSeparator());
                    long num = 1;
                    for (probeThread<? extends probe> item : currentProbes) {
                        buffer.append(num)
                                .append(": ")
                                .append(item.getName())
                                .append(" (")
                                .append(item.getType())
                                .append(")")
                                .append(System.lineSeparator());
                        num++;
                    }
                    controllerLogger.info(buffer);
                } else
                    controllerLogger.info("No active probes started");
            }
        }

        // Останавливаем все проверки
        stopAll();

        // Ожидаем завершения работы файлового сокета
        while (socketActiveState) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exc) {
                controllerLogger.fatal("Force shut down");
            }
        }
        controllerLogger.info("Shutdown");
        System.exit(EXIT_NORMAL);
    }

    /**
     * Оповещение о текущем статусе монитора сокета
     * Оповещает сам монитор
     *
     * @param activeState   Текущий статус
     */
    public void reportSocketState(boolean activeState) {
        this.socketActiveState = activeState;
    }

    /**
     * Останов при вызове из сокета
     */
    public void switchOff() {
        activeState = false;
    }

    /**
     * Запуск всез процессов
     */
    public void startAll() {
        // Перечисляем и запускаем все тесты из доступных
        controllerLogger.debug("Start all");
		if (settings.getProbeTypes().size() != 0) {
			for (String item : settings.getProbeTypes()) {
				LinkedList<String> probesList = settings.getProbesList(item);
				if (probesList.size() != 0) {
					controllerLogger.debug("Start probes type: " + item);
					for (String subItem : settings.getProbesList(item)) {
						LinkedHashMap<String, LinkedHashMap<String, String>> probeSettings = settings.readProbeSettings(item, subItem);
						if (probeSettings != null)
							startProbe(item, probeSettings);
					}
				} else
					controllerLogger.info("Probes type " + item + " not found");
			}
		} else {
			// Случай, когда параметр каким-то образом оказался пустым
			controllerLogger.info("Probes types not defined");
		}
    }

    /**
     * Выполняет запуск определённого теста (по имени)
     *
     * @param moduleName имя теста
     */
    public void startModule(String moduleName) {
        // Выполняем поиск по имени (выполняя полное перечисление аналогично старту)
        boolean found = false;
        for (String item : settings.getProbeTypes()) {
            for (String subItem : settings.getProbesList(item)) {
                if (subItem.equalsIgnoreCase(moduleName)) {
                    found = true;
                    LinkedHashMap<String, LinkedHashMap<String, String>> probeSettings = settings.readProbeSettings(item, subItem);
					if (probeSettings != null)
						startProbe(item, probeSettings);
					else
						controllerLogger.warning("Unable to start: " + moduleName);
				}
            }
        }
        if (!found) {
            controllerLogger.error("Probe " + moduleName + " not found");
        }
    }

    /**
     * Запуск определённого теста с собственным типом и настройками
     * из ini-файла
     *
     * @param probeType     Тип теста
     * @param probeSettings Настройки
     */
    synchronized private void startProbe(String probeType, LinkedHashMap<String, LinkedHashMap<String, String>> probeSettings) {
        // Смотрим, имеется ли аналогичный запущенный процесс
        String probeName = collections.searchKeyInSubIgnoreCase(probeSettings, MAIN_SECTION, PROBE_NAME_KEY, "[Unknown]");
        for (probeThread<? extends probe> item : currentProbes) {
            if (item.getName().equals(probeName)) {
                controllerLogger.error("Probe " + probeName + " already run");
                return;
            }
        }
        // Считается, что данные тип передан методами start/startModule
        // Пытаемся создать объект
        probe toExec;
        try {
            // Выполняем загрузку
            if (probeType.contains(".")) {
                // Если имя теста содержит точку - описан полный путь пакета
                toExec = (probe) Class.forName(probeType).newInstance();
            } else {
                // иначе считаем, что путь совпадает со стандартным - org.minimon.probes
                toExec = (probe) Class.forName("org.minimon.probes." + probeType).newInstance();
            }
            // Экземпляр теста получен. Формируем тест-поток и запускаем
            controllerLogger.info("Initialising " + probeName + " (" + probeType + ")");
            probeThread<? extends probe> initiatedThread = new probeThread<>(
                    toExec,
                    probeSettings,
                    probeType,
                    settings.getCoreSubLogger("New probe type " + probeType),
                    debug
            );
            // Запускаем по готовности
            if (initiatedThread.isReady()) {
                currentProbes.add(initiatedThread);
                initiatedThread.start();
                controllerLogger.info("Probe " + initiatedThread.getName() + " initiated");
            }
        } catch (ClassNotFoundException exc) {
            controllerLogger.error(
                    "Unable to load probe "
                            + collections.searchKeyInSubIgnoreCase(probeSettings, MAIN_SECTION, PROBE_NAME_KEY, "[Unknown]")
                            + " as probe type "
                            + probeType
                            + ". May be this type can't set in configuration file and will not loaded. See section [main], parameter \"Probes\" in main config"
            );
        } catch (InstantiationException | IllegalAccessException exc) {
            controllerLogger.error(
                    "Incompatible probes type: "
                            + probeType
                            + "."
            );
        }
    }

    /**
     * Выполняет остановку подпроцесса
     *
     * @param moduleName имя запущенного теста
     */
    synchronized public void stopModule(String moduleName) {
        Iterator<probeThread<? extends probe>> item = currentProbes.iterator();
        while (item.hasNext()) {
            probeThread<? extends probe> itIs = item.next();
            if (itIs.getName().equalsIgnoreCase(moduleName)) {
                itIs.offPobeThread();
                item.remove();
                controllerLogger.info("Probe " + moduleName + " switch off");
                return;
            }
        }
        controllerLogger.error("Unable to find " + moduleName + ", can't be stopped");
    }

    /**
     * Останаилвает все текущие тесты
     */
    synchronized public void stopAll() {
        Iterator<probeThread<? extends probe>> item = currentProbes.iterator();
        while (item.hasNext()) {
            probeThread<? extends probe> itIs = item.next();
            controllerLogger.info("Probe " + itIs.getName() + " switch off");
            itIs.offPobeThread();
            item.remove();
        }
        systemMessages.send("Minimon system message", "All probes stopped");
    }

    /**
     * Одиночный перезапуск
     *
     * @param moduleName имя запущенного теста
     */
    public void restart(String moduleName) {
        stopModule(moduleName);
        startModule(moduleName);
        systemMessages.send("Minimon system message", "Probe " + moduleName + " started (restart)");
    }

    /**
     * Полный перезапуск всех тестов
     */
    public void restartAll() {
        stopAll();
        startAll();
        systemMessages.send("Minimon system message", "All probes started (restart)");
    }
}

