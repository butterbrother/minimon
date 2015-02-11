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

import java.io.*;
import java.util.Calendar;
import java.util.Formatter;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

/**
 * Класс, управляющий логом
 * Обычно должен запускаться только один экземпляр
 */
public class logger {
    // Имя файла
    private final String logName;
    // Сообщения уровней
    private final String ALERT = "ALERT";
    private final String DEBUG = "DEBUG";
    private final String ERROR = "ERROR";
    private final String FATAL = "FATAL";
    private final String INFO = "INFO";
    private final String WARNING = "WARNING";
    private final String NULL = "[NULL]";
    // Дата лога. Для сравнения. При несовпадении создаётся новый лог, текущий перемещается
    private Calendar logCreateDate;
    // Фоматирующий записыватель в файл
    private Formatter logOuter = null;
    // Статус отладки
    private boolean debugState;
    // Вышестоящий логгер
    private logger upLevelLogger = null;
    // Имя модуля (для режима суб-логгера)
    private String moduleName = null;
    // Сообщения логгера, собственные ошибки переименования, удаления лога и т.д.
    // Только для логгера 1-го уровня
    private TreeSet<String> internalErrors = null;

    /**
     * Инициализация в режиме основного логгера
     *
     * @param logName    Имя лог-файла
     * @param debugState Состояние отладки
     */
    public logger(String logName, boolean debugState) {
        // Устанавливаем режим отладки
        this.debugState = debugState;
        // Получаем имя лога
        this.logName = logName;
        // Запоминаем дату создания лога
        logCreateDate = Calendar.getInstance();
        // Инициализируем список собственных проблем
        internalErrors = new TreeSet<>();

        // Проверяем, какая дата изменения текущего лога
        File test = new File(logName);
        Calendar currentLogDate = Calendar.getInstance();
        if (test.exists()) {
            currentLogDate.setTimeInMillis(test.lastModified());
        }

        // Если различаются - текущий лог сначала перименовываем, затем сжимаем
        Formatter oldLogName = null;
        if (currentLogDate.get(Calendar.YEAR) != logCreateDate.get(Calendar.YEAR) ||
                currentLogDate.get(Calendar.MONTH) != logCreateDate.get(Calendar.MONTH) ||
                currentLogDate.get(Calendar.DATE) != logCreateDate.get(Calendar.DATE)) {
            oldLogName = new Formatter();
            oldLogName.format("%s-%tY-%<tm-%<td", logName, currentLogDate);
            boolean success = (new File(logName)).renameTo(new File(oldLogName.toString()));
            if (!success)
                if (internalErrors.size() <= 10)
                    internalErrors.add("Rename log error: from " + logName + " to " + oldLogName.toString());
        }

        // И создаём первый лог
        try {
            logOuter = new Formatter(new OutputStreamWriter(new FileOutputStream(this.logName, true), "UTF-8"));
        } catch (FileNotFoundException | UnsupportedEncodingException ignore) {
        }

        // Сжатие лога после переименования. Сжимать до создания не можем - компрессор требует наличия логгера
        if (oldLogName != null)
            new loggzip(oldLogName.toString(), this);
    }

    /**
     * Инициализация суб-логгера передаёт запись вышестоящему логгеру,
     * дописывая имя модуля приложения
     *
     * @param moduleName Имя модуля, который пишет в суб-логгер
     * @param coreLogger Вышестоящие логгер
     */
    private logger(String moduleName, logger coreLogger, boolean debugState) {
        // У логга нет имени
        this.logName = "";
        // Вышестоящий логгер
        this.upLevelLogger = coreLogger;
        // Запоминаем имя модуля
        this.moduleName = moduleName;
        // Запоминаем состояние отладки
        this.debugState = debugState;
    }

    /**
     * Получение суб-логгера для конкретного модуля
     * Все записи в лог данным модулем будут с префиксом-именем модуля
     * дата-время уроверь_лога имя_модуля_сообщение
     * Имя модуля в суб-логгере может быть изменено позднее
     *
     * @param moduleName Имя модуля
     * @return Суб-логгер
     */
    public logger getModuleSubLogger(String moduleName) {
        return new logger(moduleName + "> <", this, debugState);
    }

    /**
     * Переименование модуля, записанного в суб-логгере
     *
     * @param newModuleName Новое имя модуля
     */
    public void renameModule(String newModuleName) {
        if (moduleName != null) {
            moduleName = newModuleName + "> <";
        }
    }

    /**
     * Непосредственная запись в лог без обработчиков
     *
     * @param logLevel  Уровень лога
     * @param writeLine Сообщение
     */
    synchronized private void writeln(String logLevel, String writeLine) {
        // Если логгер -- суб-логгер, то передаём сообщение вышестоящему логгеру,
        // дописывая имя модуля
        if (upLevelLogger != null) {
            upLevelLogger.writeln(logLevel, moduleName + writeLine);
        } else {
            if (logOuter != null) {
                try {
                    // Сначала получаем текущую дату
                    Calendar cl = Calendar.getInstance();

                    // Если отличается дата лога и текущая дата - создаём новый,
                    // а старый - переименовываем и сжимаем, затем пересоздаём
                    if (cl.get(Calendar.DATE) != logCreateDate.get(Calendar.DATE)) {
                        logOuter.close();

                        Formatter newFileName = new Formatter();
                        newFileName.format("%s-%tY-%<tm-%<td", logName, logCreateDate);
                        boolean success = (new File(logName)).renameTo(new File(newFileName.toString()));
                        if (!success)
                            if (internalErrors.size() <= 10) internalErrors.add("Unable to rename old file log");
                        new loggzip(newFileName.toString(), this);

                        logOuter = new Formatter(new OutputStreamWriter(new FileOutputStream(this.logName, true), "UTF-8"));

                        // обновляем дату лога
                        logCreateDate = Calendar.getInstance();
                    }

                    // Пишем в лог, только если это не отладочное сообщение вне режима отладки
                    if (!(!debugState && logLevel.equals("DEBUG"))) {
                        // Формат: <ГГГГ-МС-ДД ЧЧ24:ММ:СС.МСК> <Уровень> <Сообщение> перенос строки
                        // tY - ГГГГ // tm - ММ // td - ДД
                        // tH - ЧЧ24 // tM - МН // tS - СС // tL - МЛС
                        // s - строка
                        // %{индекс$}{мин_знаков}формат
                        logOuter.format("<%tY-%<tm-%<td %<tH:%<tM:%<tS.%<tL> <[%s]> <%s>%s", cl, logLevel, writeLine, System.lineSeparator());
                    }
                    logOuter.flush();

                    // Пишем обо всех ошибках логгера 1го уровря в лог
                    if (internalErrors.size() > 0) {
                        String errorMessages = "";
                        for (String item : internalErrors) {
                            errorMessages += item + System.lineSeparator();
                        }
                        // Сразу обнуляем
                        internalErrors = new TreeSet<>();
                        // Получаем дату и время
                        cl = Calendar.getInstance();
                        // Пишем в лог
                        logOuter.format("<%tY-%<tm-%<td %<tH:%<tM:%<tS.%<tL> <[%s]> <%s>%s", cl, ERROR, "Core logger> <Detected errors" + errorMessages, System.lineSeparator());
                        logOuter.flush();
                    }
                } catch (IOException exc) {
                    // При отказе пытемся править права и пересоздать лог
                    fileFixPerm.tryFixFilePerm(logName);
                    try {
                        logOuter.close();
                        logOuter = new Formatter(new OutputStreamWriter(new FileOutputStream(this.logName, true), "UTF-8"));
                    } catch (IOException ignore) {
                    }
                    // Записываем это событие
                    if (internalErrors.size() <= 10) internalErrors.add("Logger core error: " + exc.toString());
                }
            }
        }
    }

    /**
     * Запись в лог сообщение об ошибке
     * Так же возвращает полный текст при необходимости
     *
     * @param exc Исключение
     * @return Полный текст ошибки
     */
    public String appErrorWriter(Exception exc) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Detected application error: ").append(System.lineSeparator())
                .append("-------------------- Cut here --------------------").append(System.lineSeparator())
                .append("Type and message: ").append(exc.toString()).append(System.lineSeparator());

        // Даём расшифровки вероятных ошибок (особенно тех, с которыми столкнулись на этапе проектирования)
        if (exc instanceof IOException)
            logMessage.append("Signals that an I/O exception of some sort has occurred.").append(System.lineSeparator())
                    .append("This error produced by failed or interrupted I/O operations.").append(System.lineSeparator());
        if (exc instanceof ArrayIndexOutOfBoundsException)
            logMessage.append("Error indicate that an array has been accessed with an illegal index.").append(System.lineSeparator())
                    .append("The index is either negative or greater than or equal to the size of the array.").append(System.lineSeparator())
                    .append("This error strongly indicates an incorrect design of this application").append(System.lineSeparator());
        if (exc instanceof StringIndexOutOfBoundsException)
            logMessage.append("This error indicate that an index is either negative or greater than the size of the string").append(System.lineSeparator())
                    .append("Usually, this error indicates an incorrect design of this application").append(System.lineSeparator());

		// Трейс
        logMessage.append("Stack trace:").append(System.lineSeparator());
        for (StackTraceElement item : exc.getStackTrace()) {
			//logMessage.append(border).append("at ").append(item.toString()).append(System.lineSeparator());
			// Выше предыдущий, похожий на оригинальный java trace route
			// Ниже новый, отображает класс, метод, строку и файл
			logMessage.append("  ")
					.append("at File: ").append(item.getFileName())
					.append(", Line: ").append(item.getLineNumber())
					.append(", Class: ").append(item.getClassName())
					.append(", Method: ").append(item.getMethodName())
					.append(System.lineSeparator());
		}

        logMessage.append("-------------------- End cut --------------------");
        fatal(logMessage);
        return logMessage.toString();
    }

    /**
     * Запись аварийного сообщения
     */
    public void alert(String message) {
        if (message == null) message = NULL;
        writeln(ALERT, message);
    }

    public void alert(Object obj) {
        if (obj != null) {
            writeln(ALERT, obj.toString());
        } else {
            writeln(ALERT, NULL);
        }
    }

    /**
     * Запись отладочного сообщения
     * Только если первичный логгер создан с режимом отладки
     *
     * @param message Сообщение
     */
    public void debug(String message) {
        if (debugState) {
            if (message == null) message = NULL;
            writeln(DEBUG, message);
        }
    }

    public void debug(Object obj) {
        if (debugState) {
            if (obj != null) {
                writeln(DEBUG, obj.toString());
            } else {
                writeln(DEBUG, NULL);
            }
        }
    }

    /**
     * Запись сообщения об ошибке
     *
     * @param message Сообщение
     */
    public void error(String message) {
        if (message == null) message = NULL;
        writeln(ERROR, message);
    }

    public void error(Object obj) {
        if (obj != null) {
            writeln(ERROR, obj.toString());
        } else {
            writeln(ERROR, NULL);
        }
    }

    /**
     * Запись сообщения о критичной ошибке
     *
     * @param message Сообщение
     */
    public void fatal(String message) {
        if (message == null) message = NULL;
        writeln(FATAL, message);
    }

    public void fatal(Object obj) {
        if (obj != null) {
            writeln(FATAL, obj.toString());
        } else {
            writeln(FATAL, NULL);
        }
    }

    /**
     * Запись информационного сообщения
     *
     * @param message Сообщение
     */
    public void info(String message) {
        if (message == null) message = NULL;
        writeln(INFO, message);
    }

    public void info(Object obj) {
        if (obj != null) {
            writeln(INFO, obj.toString());
        } else {
            writeln(INFO, NULL);
        }
    }

    /**
     * Запись предупреждающего сообщения
     *
     * @param message Сообщение
     */
    public void warning(String message) {
        if (message == null) message = NULL;
        writeln(WARNING, message);
    }

    public void warning(Object obj) {
        if (obj != null) {
            writeln(WARNING, obj.toString());
        } else {
            writeln(WARNING, NULL);
        }
    }

    /**
     * Вложенный класс, обрабатывающий сжатие лога в отдельном потоке
     * Что-бы не тормозить логгирование
     */
    private class loggzip implements Runnable {
        // Имя архивируемого лога
        private final String logName;
        // Имя архива, генерируется из имени лога
        private final String GZName;
        // Имя логгера
        private logger log;

        /**
         * Инициализация архиватора
         *
         * @param logName Имя лога
         */
        loggzip(String logName, logger log) {
            this.logName = logName;
            this.GZName = logName + ".gz";
            this.log = log;
            Thread doCompress = new Thread(this, "log compress");
            doCompress.start();
        }

        /**
         * Непосредственное сжатие
         */
        public void run() {
            try {
                FileInputStream input = new FileInputStream(logName);
                GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(GZName));
                byte buffer[] = new byte[4096];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    gzip.write(buffer, 0, length);
                }
                input.close();
                gzip.close();
                boolean success = new File(logName).delete();
                if (!success)
                    if (internalErrors.size() <= 10)
                        internalErrors.add("Compression old log: delete old log file error");
            } catch (FileNotFoundException ignore) {
            } catch (IOException exc) {
                if (internalErrors.size() <= 10)
                    internalErrors.add("Compression old log: I/O error: " + exc.toString());
            }
        }
    }

}
