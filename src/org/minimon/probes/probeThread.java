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
import org.minimon.system.sendMail;
import org.minimon.utils.collections;

import java.io.IOException;
import java.util.*;

/**
 * Самостоятельный поток-процесс проверки.
 */
public class probeThread<T extends probe>
        implements Runnable, staticValues {

    // Проверка
    private T probeItem;
    // Логгер процесса-потока
    private logger log;
    // Собственный экземпляр системы рассылки
    private sendMail mail;
    // Объект исполнения traceroure
    private procExec traceRoute = null;

    // Параметры
    // Имя, берётся из ini либо имени ini
    // Определяем имя проверки и потока
    // Должно быть уникально
    private String probeName;
    // Тип
    private String probeType;
    // Пауза между проверками
    private long checkDelay;
    // Необходимость использовать фильтр ложных срабатываний
    private boolean needFailFilter;
    // Пауза между итерациями фильтра ложных срабатываний
    private long failFilterDelay;
    // Общее число итераций фильтра
    private long failFilterCount;
    // Минимальное необходимое число положительных итераций фильтра
    private long failFilterSuccessCount;
    // Дополнительный интервал между неудачными проверками
    private long failUpInterval;
    // Удвоение дополнительного интервала при каждой неудачной проверке
    private boolean doubleFailIntervals;
    // Необходимость трассировки до удалённого сервера
    private boolean needTraceRoute;
	// Наростающая пауза между одинаковыми сообщениями
	private long messagesUpInterval;
	// Активность
    private boolean readyFlag = false;
    // Отладка
    private boolean debugState;
    // Текущий поток
    private Thread thisThread = null;

    // Внешнее приложение
    private procExec externalWarningApplication = null;
    private procExec externalAlertApplication = null;
    private boolean needWaitExternalApplication = false;

	// Статистические данные
	// Дата-время начала работы
	private Calendar startDate = Calendar.getInstance();
	// Общее число ошибок и успешных проверок
	private long totalSuccessCount = 0;
	private long totalWarningsCount = 0;
	private long totalFailsCount = 0;
	private long lastFailFilterResult = 0;
	// Ошибки и успехи за последний час (должны подчищаться при каждом подсчёте)
	private LinkedHashMap<Calendar, Integer> lastStates = new LinkedHashMap<>();

    /**
     * Инициализация с передачей проверки
     *
     * @param probeItem  Объект проверки
     * @param settings   Параметры из индивидуального ini-файла
     * @param log        Логгер
     * @param debugState Текущее состояние отладки
     */
    public probeThread(
            T probeItem,
            LinkedHashMap<String, LinkedHashMap<String, String>> settings,
            String probeType,
            logger log,
            boolean debugState
    ) {
        // Определяем имя самостоятельно
        probeName = collections.searchKeyInSubIgnoreCase(settings, MAIN_SECTION, PROBE_NAME_KEY, "[Unknown]");


        this.probeType = probeType;
        this.probeItem = probeItem;
        // Вначале передаём параметры
        this.probeItem.importSettings(settings);
        // Далее выполняем подготовку и обрабатываем параметры
        this.log = log;
        log.renameModule("Probe " + probeName + " (" + probeType + ")");
        this.debugState = debugState;
        if (probeItem.prepare(log.getModuleSubLogger(probeName + " probe"), debugState)) {
            // Успешная проверка
            log.debug(probeName + " initialised with last error message: " + probeItem.getLastError());

            // Получаем настройки, необходимые для работы потока
            // Переопределение типа, если поддерживается
            if (probeItem.getCheckType() != null) {
                if (!probeItem.getCheckType().isEmpty()) {
                    probeType = probeItem.getCheckType();
                }
            }

            // Создаём e-mail-ер
            String mailRecipients = collections.searchKeyInSubIgnoreCase(settings, MAIN_SECTION, MAIL_TO_NAME, MAIL_TO_DEFAULT);
            mail = new sendMail(mailRecipients, log.getModuleSubLogger("Mail system"), debugState);

            // Получаем настройки
            helper probeHelper = probeItem.getBasicalParseHelper();
            if (probeHelper != null) {
                checkDelay = probeHelper.getCheckDelay();
                needFailFilter = probeHelper.isNeedFailFilter();
                failFilterDelay = probeHelper.getFailFilterDelay();
                failFilterCount = probeHelper.getFailFilterCount();
                failFilterSuccessCount = probeHelper.getFailFilterSuccessCount();
                failUpInterval = probeHelper.getFailUpInterval();
                doubleFailIntervals = probeHelper.isDoubleFailIntervals();
                needTraceRoute = probeHelper.isNeedTraceRoute();
            } else {
                checkDelay = failFilterDelay = failFilterCount = failFilterSuccessCount = failUpInterval = 0;
                needFailFilter = doubleFailIntervals = needTraceRoute = false;
            }

            // Получаем объект исполнения traceroute (если необходим)
            if (needTraceRoute) {
                traceRoute = buildTraceRouteCaller(probeItem.getTracerouteTarget());
            }

            // Проверяем, необходимо ли исполнять внешние команды при поступлении событий
            String externalWarningCommand = collections.searchKeyInSubIgnoreCase(settings, EXTERNAL_EXEC_NAME, EXTERNAL_EXEC_WARNING);
            String externalAlertCommand = collections.searchKeyInSubIgnoreCase(settings, EXTERNAL_EXEC_NAME, EXTERNAL_EXEC_ALERT);
            String doWait = collections.searchKeyInSubIgnoreCase(settings, EXTERNAL_EXEC_NAME, EXTERNAL_DO_WAIT, "Yes");

			// Получаем паузу между сообщениями о провалах
			messagesUpInterval = (
					probeHelper != null ?
							probeHelper.getLongParamValue(
									MAIN_SECTION,
									PROBE_MESSAGES_DELAY_UP_NAME,
									PROBE_MESSAGES_DELAY_UP_DEFAULT,
									60,
									3600
							) * 1000
							: 300 * 1000
			);

            if (externalWarningCommand != null)
                externalWarningApplication = buildExternalExecutor(externalWarningCommand);
            if (externalAlertCommand != null)
                externalAlertApplication = buildExternalExecutor(externalAlertCommand);
            needWaitExternalApplication = (Boolean.parseBoolean(doWait) || doWait.toLowerCase().contains("yes") || doWait.toLowerCase().contains("enable"));

            // Записываем в лог информацию о тесте
            log.info(
                    "Probe: \"" + probeName + "\", type: \"" + probeType + "\"" + System.lineSeparator()
                            + "Parameters:" + System.lineSeparator()
                            + "-- Mail recipients: " + (mailRecipients != null ? mailRecipients : "none") + System.lineSeparator()
                            + "-- Checking pause (delay): " + (checkDelay / 1000) + " s." + System.lineSeparator()
                            + "-- Fail filter: " + (needFailFilter ? "enabled" : "disabled") + System.lineSeparator()
                            + "-- Fail filter count: " + (failFilterCount) + System.lineSeparator()
                            + "-- Fail filter minimal success count: " + (failFilterSuccessCount) + System.lineSeparator()
                            + "-- Fail filter iterations delay: " + (failFilterDelay / 1000) + " s." + System.lineSeparator()
                            + "-- Fail filter interval up: " + (failUpInterval / 1000) + " s." + System.lineSeparator()
                            + "-- Fail filter need double interval: " + (doubleFailIntervals ? "enabled" : "disabled") + System.lineSeparator()
                            + "-- Trace to network host: " + (needTraceRoute ? "enabled" : "disabled") + System.lineSeparator()
                            + "-- External command on warning: " + (externalWarningCommand != null ? externalWarningCommand : "not use") + System.lineSeparator()
                            + "-- External command on alert: " + (externalAlertCommand != null ? externalAlertCommand : "not use")
							+ "-- Message up delay: " + (messagesUpInterval / 1000) + " s."
			);


            // Выставляем флаг готовности
            readyFlag = true;
        } else {
            // Неудачная проверка
            log.error(
                    "Unable initialise probe "
                            + probeName
                            + ": "
                            + probeItem.getLastError()
            );
            readyFlag = false;
        }
    }

    /**
     * Флаг готовности к запуску
     *
     * @return Статус
     */
    public boolean isReady() {
        return readyFlag;
    }

    /**
     * Запуск процесса-потока проверки
     */
    public void start() {
        if (readyFlag && thisThread == null) {
            thisThread = new Thread(this, probeName);
            thisThread.start();
            //thisThread.setDaemon(true);
        }
    }

    /**
     * Возвращает имя
     *
     * @return Имя
     */
    public String getName() {
        return probeName;
    }

    /**
     * Возвращает тип
     *
     * @return Тип
     */
    public String getType() {
        return probeType;
    }

    /**
     * Создание исполнителя traceroute
     *
     * @param address Сетевой адрес либо IP
     * @return Исполнитель внешнего приложения
     */
    private procExec buildTraceRouteCaller(String address) {
        // Усекаем протокол и расположения
        if (address.contains("://"))
            address = address.substring(address.indexOf("://") + 3);
        if (address.contains("/"))
            address = address.substring(0, address.indexOf("/"));

        // Используется уже текущий исполнимый файл traceroute,
        // т.к. для icmp требуются права RAW_SOCKET
        switch (cross.getSystemType()) {
            case (OS_TYPE_LINUX):
                return new procExec(debugState, log.getModuleSubLogger("Application exec"), "traceroute", address);
            case (OS_TYPE_SUN):
                return new procExec(debugState, log.getModuleSubLogger("Application exec"), "/usr/sbin/traceroute", address);
            case (OS_TYPE_WINDOWS):
                return new procExec(debugState, log.getModuleSubLogger("Application exec"), "tracert", address);
            default:
                return new procExec(debugState, log.getModuleSubLogger("Application exec"), "echo", "unknown os");
        }
    }

    /**
     * Создаёт исполнителя внешней команды при достижении
     * событий (warning/alert)
     *
     * @param externalRAWCommand Команда из ini-файла
     * @return Исполнитель
     */
    private procExec buildExternalExecutor(String externalRAWCommand) {
        // Создаём массив из строки
        // для этого разбиваем оригинал, разделители - пробелы
        // Динамический массив облегчит задачу - не придётся предварительно
        // считать число элементов
        LinkedList<String> parsed = new LinkedList<>();
        // Позиция
        int pos = 0;
        // В кавычках?
        boolean inQuote = false;
        for (int i = 0; i < externalRAWCommand.length(); i++) {
            switch (externalRAWCommand.charAt(i)) {
                case ('\"'):
                    inQuote = !inQuote;
                    break;
                case (' '):
                    if (!inQuote) {
                        String buffer = externalRAWCommand.substring(pos, i);
                        if (buffer.startsWith("\"") && buffer.endsWith("\"") && !buffer.equals("\""))
                            buffer = buffer.substring(1, buffer.length() - 1);
                        parsed.add(buffer);
                        pos = i + 1;
                    }
            }
            // Если последний элемент был в кавычке, то сохраняем её
            if (i == (externalRAWCommand.length() - 1) && inQuote)
                parsed.add(externalRAWCommand.substring(pos, externalRAWCommand.length()));
        }
        String[] executeParam = parsed.toArray(new String[parsed.size()]);
        return new procExec(debugState, log.getModuleSubLogger("External execute"), executeParam);
    }

    /**
     * Фильтрация ложных срабатываний
     *
     * @return Статус фильтра
     */
    private boolean failFilter() throws InterruptedException {
		lastFailFilterResult = 0;
		long success = 0;
        if (failFilterSuccessCount > failFilterCount) {
            failFilterSuccessCount = failFilterCount / 2;
        }
        for (long i = 1; i <= failFilterCount; i++) {
            if (!readyFlag) return true;    // Досрочное завершение фильтра в случае отключения теста

            log.debug("Fail filter: iteration " + Long.toString(i) + " of " + Long.toString(failFilterCount));
            if (probeItem.iteration()) {
                success++;
				lastFailFilterResult++;
				log.debug("Success, current success count: " + Long.toString(success));
            } else {
                log.debug("Unsuccessful, current success count: " + Long.toString(success));
            }
            log.debug("Do sleep " + Long.toString(failFilterDelay) + " ms.");

            if (readyFlag) Thread.sleep(failFilterDelay);
        }

        if (debugState) {
            log.debug("Success: "
                    + Long.toString(success)
                    + ", Minimal success count: "
                    + Long.toString(failFilterSuccessCount)
                    + ", filter state: "
                    + Boolean.toString(success >= failFilterSuccessCount));
        }
        return (success >= failFilterSuccessCount);
    }

    /**
     * Выполняет трассировку и возвращает результат.
     * Если необходимости трассировки нет - вернёт
     * пустую строку
     *
     * @return Результат трассировки
     */
    private String runTraceRoute() {
        if (needTraceRoute && traceRoute != null) {
            return runExternalCommand("Trace to remote host", traceRoute, true);
        } else {
            return "";
        }
    }

    /**
     * Выполняет внешнюю команду, дожидается завершения и возвращает результат исполнения
     * (если это необходимо)
     *
     * @param name     Имя команды
     * @param executor Исполнитель (должен быть создан заранее)
     * @param needWait Необходимость ожидания завершения и результатов
     * @return Результат завершения либо пустая строка, если без ожидания
     */
    private String runExternalCommand(String name, procExec executor, boolean needWait) {
        if (executor != null) {
			StringBuilder returnValue = new StringBuilder();
			try {
                executor.execute();
                // Собираем выхлоп только если это необходимо
                if (needWait) {
					StreamGrabber executeErrors = new StreamGrabber(executor.getStderr(), "<stderr>", log.getModuleSubLogger("StdErr grabber"));
					StreamGrabber executeOutput = new StreamGrabber(executor.getStdout(), "", log.getModuleSubLogger("StdOut grabber"));
					executor.waitFor();

					returnValue.append(name).append(" result:").append(System.lineSeparator())
							.append(executeErrors.getResults()).append(System.lineSeparator())
							.append(executeOutput.getResults()).append(System.lineSeparator());
				}
            } catch (InterruptedException exc) {
                // Прерываем исполнителя и закрываем ридеры
                executor.terminate();
                log.debug(name + " interrupted");
                offPobeThread();
            } catch (IOException exc) {
                log.appErrorWriter(exc);
            }
			return returnValue.toString();
		} else {
            return "";
        }
    }

    /**
     * Действия по событию тревоги
     *
     * @param lastErrorMessage Последнее сообщение теста
     */
    private void alertAction(String lastErrorMessage) {
		if (needSendMessage()) {
			// Выполняем внешнюю команду
			String externalResult = runExternalCommand(
					"Alert action external application execute",
					externalAlertApplication,
					needWaitExternalApplication
			);

			// Создаём тему письма для mail-ера
			String topic = "Alert in probe " + probeName + " (" + probeType + ")";

			// Создаём тело письма
			String message = mailBody("alert", lastErrorMessage, externalResult);

			// Записываем в лог и отправляем сообщение
			log.alert(message);
			mail.send(topic, message);
		}
	}

    /**
     * Действия по событию предупреждения
     *
     * @param lastErrorMessage Последнее сообщение теста
     */
    private void warningAction(String lastErrorMessage) {
		if (needSendMessage()) {
			// Выполняем внешнюю команду
			String externalResult = runExternalCommand(
					"Warning action external application execute",
					externalWarningApplication,
					needWaitExternalApplication
			);

			// Создаём тему письма для mail-ера
			String topic = "Warning in probe " + probeName + " (" + probeType + ")";

			// Создаём тело письма
			String message = mailBody("warning", lastErrorMessage, externalResult);

			// Записываем в лог и отправляем сообщение
			log.warning(message);
			mail.send(topic, message);
		}
	}

	/**
	 * Формирование тела письма для различных уровней тревог:
	 * - Выполнение внешних команд (при необходимости)
	 * - Сбор данных для передачи в сообщении
	 *
	 * @param actionLevel      Уровень тревоги
	 * @param lastErrorMessage Последнее сообщение об ошибке
	 * @return Сообщение для отправки
	 */
	private String mailBody(String actionLevel, String lastErrorMessage, String externalResult) {
		// Создаём сообщение для лога и mail-ера
		StringBuilder message = new StringBuilder();

		// Начало сообщения, имя и уровень
		message.append("Probe ").append(actionLevel).append(": ").append(probeName).append(System.lineSeparator());
		// Категория
		message.append("Category: ").append(probeType).append(System.lineSeparator());
		// Сообщение
		message.append("Message: ").append(lastErrorMessage).append(System.lineSeparator());
		if (needFailFilter) {
			// Дописываем результат фильтра случайных ошибок, если он есть
			message.append("Fail filter result: ").append(lastFailFilterResult).append("/").append(failFilterCount).append(System.lineSeparator());
		}
		// Результат трассировки
		message.append(runTraceRoute()).append(System.lineSeparator());
		// Результат выполнения внешней команды
		message.append(externalResult).append(System.lineSeparator());
		// Таблица событий за последний час
		message.append(getLastHourStates());

		return message.toString();
	}

	/**
	 * Необходимость отправки сообщения. Рассчитывается из:
	 *
	 * @return Необходимость в отправке сообщения
	 */
	private boolean needSendMessage() {
		return true;
		// TODO: Допелить
		// Тут должна быть хитрая формула рассчёта
	}

    /**
     * Плавное и безопасное отключение потока-проверки
     */
    public void offPobeThread() {
        if (readyFlag) {
            log.info("Probe switch off");
			// Преключаем флаг готовности-активности
			readyFlag = false;
		}
    }

	/**
	 * Генерирует таблицу - статистику за последний час
	 * Кроме этого в вывод попадает общее число с момента запуска
	 *
	 * @return Статистика за последний час
	 */
	private String getLastHourStates() {
		// Подчищаем таблицу
		cleanupLastState();

		// Составляем таблицу
		Formatter stateTable = new Formatter();
		stateTable.format("%23s - %20s%n", "Date-time", "State");    // Заголовок
		stateTable.format("%s%n", "---------------------------------------------------");
		for (Map.Entry<Calendar, Integer> item : lastStates.entrySet()) {
			stateTable.format("%tY-%<tm-%<td %<tH:%<tM:%<tS.%<tL - %s%n", item.getKey(), checkStates[item.getValue()]);    // Полное дата-время до мс - тип события
		}
		stateTable.format("%s%n", "---------------------------------------------------");

		// Итоговые результаты (всего с момента старта)
		stateTable.format("Total (from %tY-%<tm-%<td %<tH:%<tM):%n", startDate);
		stateTable.format("Fails: %d, Warnings: %d, Success: %d%n", totalFailsCount, totalWarningsCount, totalSuccessCount);

		return stateTable.toString();
	}

	/**
	 * Подчищает таблицу событий за последний час
	 * Удаляются все события дальше одного часа
	 */
	private void cleanupLastState() {
		// Получаем текущее время-дату для сравнения
		long currentDateTime = Calendar.getInstance().getTimeInMillis();

		// Отчищаем таблицу событий за последний час
		Iterator<Map.Entry<Calendar, Integer>> iter = lastStates.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Calendar, Integer> item = iter.next();
			if ((currentDateTime - item.getKey().getTimeInMillis()) > 3600000) // 3 600 000 = 1 час в миллисекундах
				iter.remove();
		}
	}

	@Override
	/**
	 * Процесс-поток проверок
	 */
	public void run() {
        log.info("Probe " + probeName + " in running mode");
        try {
            // Суммарная плавающая пауза
            long bufferWait = checkDelay;
            // Выполняем до "смерти" потока либо процесса целиком
            // Либо до момента переключения флага готовности
            while (thisThread.isAlive() && readyFlag) {
                try {
                    log.debug("Iteration started");
                    //Итерация
                    if (!probeItem.iteration()) {
                        // Получаем ошибку-предупреждение
                        String errorMessage = probeItem.getLastError();
                        log.debug("Iteration fail: " + errorMessage);

                        // Выполняем повторные итерации, если это необходимо
                        if (needFailFilter) {
							if (!failFilter()) {
								// Пишем статистику
								lastStates.put(Calendar.getInstance(), STATE_ALERT); // Добавляем статус тревоги
								totalFailsCount++;    // Наращиваем число тревог
								// Отсылаем оповещение
								log.debug("Alert detected");
                                alertAction(errorMessage);
							} else {
								lastStates.put(Calendar.getInstance(), STATE_WARNING);    // Добавляем статус предупреждения
								totalWarningsCount++;    // Наращиваем число предупреждений

                                log.debug("Warning detected");
                                warningAction(errorMessage);
                            }
						} else {
							lastStates.put(Calendar.getInstance(), STATE_ALERT); // Добавляем статус тревоги
							totalFailsCount++;	// Наращиваем число тревог

                            log.debug("Alert detected");
                            alertAction(errorMessage);
                        }
                        // Увеличиваем паузу
                        if (doubleFailIntervals || bufferWait == checkDelay) {
                            bufferWait += failUpInterval;
                            log.debug("Probe check delay up to " + Long.toString(bufferWait));
                        }
                    } else {
                        // Всё прошло нормально
                        log.debug("All ok");
                        bufferWait = checkDelay; // Сбрасываем суммарную паузу

						// Увеличиваем счетчик успехов
						totalSuccessCount++;

						// Добавляем дату успеха
						lastStates.put(Calendar.getInstance(), STATE_SUCCESS);
					}

					log.debug("Iteration end");
					// Подчищаем таблицу состояний, что бы не переполнялась
					cleanupLastState();
					if (readyFlag) Thread.sleep(bufferWait);
                } catch (InterruptedException ignore) {
                    log.debug("Probe interrupted");
                    offPobeThread();
                }
            }
        } catch (Exception gerr) {
            log.appErrorWriter(gerr);
            try {
                if (readyFlag) Thread.sleep(checkDelay);
            } catch (InterruptedException ignore) {
                log.debug("Probe interrupted");
                offPobeThread();
            }
        }
        log.info("Probe " + probeName + " shutdown");
    }
}
