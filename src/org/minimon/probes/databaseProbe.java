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

	// Известные типы сравнения
	private final int COMPARE_EQUALS = 0;    // Должны быть равны
	private final int COMPARE_LARGER = 1;    // Результат должен быть больше
	private final int COMPARE_SMALLER = 2;    // Результат должен быть меньше
	private final int COMPARE_NOT_EQUALS = 3;    // Результат не должен быть равен
	private final int COMPARE_EQUALS_OR_LARGER = 4;    // Результат должен быть больше или равен
	private final int COMPARE_EQUALS_OR_SMALLER = 5;    // Результат должен быть меньше или равен
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
	// Тип сравнения
	private int queryCompareType = COMPARE_EQUALS;    // По-умолчанию - равны
	// Настройки
	private LinkedHashMap<String, LinkedHashMap<String, String>> settings = null;
	// Парсер унифицированных настроек процесса-потока
	private helper basicParser = null;
	// Свойства соединения
	private Properties connectionProperties = null;
	// Текущее соединение
	private Connection connection = null;
	// Выражение
	private Statement stat = null;
	// Результат выражения
	private ResultSet result = null;

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
		// Метод сравнения
		String compareType = collections.getSectionParameter(settings, PROBE_DB_SECTION_NAME, "Compare type", "equals").toLowerCase();
		// Парсим метод сравнения
		// Здесь равно, больше или равно, меньше или равно, либо не равно. Сочетания
		if (compareType.contains("equals") || compareType.contains("eq")) {
			if (compareType.contains("larger") || compareType.contains("lg")) {
				queryCompareType = COMPARE_EQUALS_OR_LARGER;
			} else if (compareType.contains("smaller") || compareType.contains("sm")) {
				queryCompareType = COMPARE_EQUALS_OR_SMALLER;
			} else if (compareType.contains("not") || compareType.contains("!")) {
				queryCompareType = COMPARE_NOT_EQUALS;
			} else {
				queryCompareType = COMPARE_EQUALS;
			}
			// Больше
		} else if (compareType.equals("larger") || compareType.equals("lg")) {
			queryCompareType = COMPARE_LARGER;
			// Меньше
		} else if (compareType.equals("smaller") || compareType.equals("sm")) {
			queryCompareType = COMPARE_SMALLER;
			// По-умолчанию - сравнение
		} else {
			queryCompareType = COMPARE_EQUALS;
		}

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


		// Проверяем наличие свойство, если пустые - создаём
		try {
			if (connectionProperties == null) {             // По факту невозможно, но всё же
				connectionProperties = new Properties();
				throw new Exception("BUG: Connection properties is NULL!");
			}
		} catch (Exception exc) {
			log.appErrorWriter(exc);
		}

		// При отсутствии соединения пытаемся соединиться
		if (connection == null) {
			if (!connect())
				return false;
		}


		// Создаём выражение
		try {
			stat = connection.createStatement();
		} catch (SQLException exc) {
			lastError = "Unable create statement: " + exc;
			// Принудительно разрываем соединение, если не можем создать выражение
			forceCloseStatement();
			forceDisconnect();
			return false;
		}
		log.debug("DB " + URL + " statement created");

		// исполняем запрос и сравниваем с результатами (если он есть)
		if (debug) log.debug("DB " + URL + " try execute statement");
		try {
			result = stat.executeQuery(queryRequest);
		} catch (SQLException exc) {
			lastError = "Unable execute query \"" + queryResult + "\": " + exc;
			forceCloseStatement();
			return false;
		}

		// Когда имеется результат
		try {
			if (result != null) {
				if (result.next()) {
					// Если указан собственный ответ
					if (queryResult != null) {
						// То сравниваем с ним
						switch (queryCompareType) {
							// Не должно совпадать
							case COMPARE_NOT_EQUALS:
								if (result.getString(1).equals(queryResult)) {
									lastError += "Query result return: " + System.lineSeparator()
											+ result.getString(1) + System.lineSeparator()
											+ "But expected must not equals: " + System.lineSeparator()
											+ queryResult;
									closeStatement();
									return false;
								}
								break;
							// Значение должно быть больше
							case COMPARE_LARGER:
								try {
									// Получаем вещественные значения результата и сравниваемого значения
									double dResult = Double.parseDouble(result.getString(1));
									double dCompare = Double.parseDouble(queryResult);
									// Сравниваем
									if (dResult <= dCompare) {
										lastError += "Query result return: " + System.lineSeparator()
												+ dResult + System.lineSeparator()
												+ "But result must be larger, that: " + System.lineSeparator()
												+ dCompare;
									}
									return false;
									// Конвертация может вернуть два исключения - пустое значение, либо не конвертируемое в число
								} catch (NullPointerException e) {
									lastError += "Resuest return null";
									return false;
								} catch (NumberFormatException e) {
									lastError += "Data conversion error, result: " + result.getString(1) + System.lineSeparator()
											+ "or equals data: " + queryResult + " not a numbers.";
									return false;
								}
								// Значение должно быть меньше
							case COMPARE_SMALLER:
								try {
									// Получаем вещественные значения результата и сравниваемого значения
									double dResult = Double.parseDouble(result.getString(1));
									double dCompare = Double.parseDouble(queryResult);
									// Сравниваем
									if (dResult >= dCompare) {
										lastError += "Query result return: " + System.lineSeparator()
												+ dResult + System.lineSeparator()
												+ "But result must be smaller, that: " + System.lineSeparator()
												+ dCompare;
									}
									return false;
								} catch (NullPointerException e) {
									lastError += "Resuest return null";
									return false;
								} catch (NumberFormatException e) {
									lastError += "Data conversion error, result: " + result.getString(1) + System.lineSeparator()
											+ "or equals data: " + queryResult + " not a numbers.";
									return false;
								}
								// Значение должно быть больше или равно
							case COMPARE_EQUALS_OR_LARGER:
								try {
									// Получаем вещественные значения результата и сравниваемого значения
									double dResult = Double.parseDouble(result.getString(1));
									double dCompare = Double.parseDouble(queryResult);
									// Сравниваем
									if (dResult < dCompare) {
										lastError += "Query result return: " + System.lineSeparator()
												+ dResult + System.lineSeparator()
												+ "But result must be larger or equals: " + System.lineSeparator()
												+ dCompare;
									}
									return false;
									// Конвертация может вернуть два исключения - пустое значение, либо не конвертируемое в число
								} catch (NullPointerException e) {
									lastError += "Resuest return null";
									return false;
								} catch (NumberFormatException e) {
									lastError += "Data conversion error, result: " + result.getString(1) + System.lineSeparator()
											+ "or equals data: " + queryResult + " not a numbers.";
									return false;
								}
								// Значение должно быть меньше или равно
							case COMPARE_EQUALS_OR_SMALLER:
								try {
									// Получаем вещественные значения результата и сравниваемого значения
									double dResult = Double.parseDouble(result.getString(1));
									double dCompare = Double.parseDouble(queryResult);
									// Сравниваем
									if (dResult > dCompare) {
										lastError += "Query result return: " + System.lineSeparator()
												+ dResult + System.lineSeparator()
												+ "But result must be smaller or equals: " + System.lineSeparator()
												+ dCompare;
									}
									return false;
								} catch (NullPointerException e) {
									lastError += "Resuest return null";
									return false;
								} catch (NumberFormatException e) {
									lastError += "Data conversion error, result: " + result.getString(1) + System.lineSeparator()
											+ "or equals data: " + queryResult + " not a numbers.";
									return false;
								}
								// Должно совпадать
							case COMPARE_EQUALS:
							default:
								if (!result.getString(1).equals(queryResult)) {
									lastError += "Query result return: " + System.lineSeparator()
											+ result.getString(1) + System.lineSeparator()
											+ "But expected must equals: " + System.lineSeparator()
											+ queryResult;
									closeStatement();
									return false;
								}
								break;
						}
					}
					log.debug("Query result (first cell): " + result.getString(1));
				} else {
					// Если нет результата, но при этом указан ожидаемый ответ
					if (queryResult != null) {
						lastError += "Empty query result";
						closeStatement();
						return false;
					}
				}
			}
		} catch (SQLException exc) {
			lastError = "Error result request: " + exc;
			forceCloseStatement();
			return false;
		}

		// Закрываем выражение
		try {
			closeStatement();
			log.debug("DB " + URL + " statement closed");
		} catch (SQLException exc) {
			lastError = "Unable to close statement: " + exc;
			forceCloseStatement();
			return false;
		}

		return true;
	}

	/**
	 * Выполнения подключения
	 *
	 * @return Успех подключения
	 */
	private boolean connect() {
		try {
			connection = driver.connect(URL, connectionProperties);
			log.debug("DB " + URL + " connected");
		} catch (SQLException exc) {
			lastError = "Unable connect to database:" + System.lineSeparator()
					+ exc;
			return false;
		}
		return true;
	}

	/**
	 * Принужительное разъединение, очищаются
	 * указатели и игнорируются исключения.
	 * Вызывается при ошибке
	 */
	private void forceDisconnect() {
		log.debug("Force disconnection");
		forceCloseStatement();
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException ignore) {
			}
			connection = null;
		}
	}

	/**
	 * Закрытие и зануление выражений и результатов
	 *
	 * @throws SQLException
	 */
	private void closeStatement() throws SQLException {
		if (result != null) {
			result.close();
			result = null;
		}
		if (stat != null) {
			stat.close();
			stat = null;
		}
	}

	/**
	 * Приндительное закрытие выражения и результатов
	 * и очистка указателей. Вызывается при сбое.
	 * Игнорирует все исключения
	 */
	private void forceCloseStatement() {
		log.debug("Force close statement");
		if (result != null) {
			try {
				result.close();
			} catch (SQLException ignore) {
			}
			result = null;
		}
		if (stat != null) {
			try {
				stat.close();
			} catch (SQLException ignore) {
			}
			stat = null;
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

