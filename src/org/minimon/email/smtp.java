package org.minimon.email;

import com.sun.mail.smtp.SMTPTransport;
import org.minimon.core.logger;
import org.minimon.core.staticValues;
import org.minimon.utils.collections;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Рассылка с использованием SMTP
 */
public class smtp
        implements sendMail, staticValues {

    boolean ssl = false;        // Наличие ssl
    int port = 0;               // Порт
    int timeout = 0;            // Тайм-аут соединения
    boolean tlsEnable = false;  // Наличие tls
    // Получатели
    private LinkedList<String> mailTo = new LinkedList<>();
    // Логгер
    private logger log;
    // Ссылка на мейлер 1го уровня
    private smtp rootMailer = null;
    // Настройки соединения
    private String server = null;       // Сервер
    private String login = null;        // Логин
    private String password = null;     // Пароль
    private String from = null;     // Отправитель
    // Поток-отправитель
    private senderThread sender = null;

    /**
     * Инициализация мейлера 1го уровня
     *
     * @param settings Настройки из основного файла настроек
     * @param log      Логгер
     */
    public smtp(LinkedHashMap<String, LinkedHashMap<String, String>> settings, logger log) {
        // Здесь инициализируем все настройки сервера и отправителя
        // Получаем получателей
        StringTokenizer RAWmailTo = new StringTokenizer(
                collections.getSectionParameter(
                        settings,
                        MAIL_SECTION,
                        MAIL_TO_NAME,
                        MAIL_TO_DEFAULT
                ),
                " ,;"
        );
        while (RAWmailTo.hasMoreElements())
            mailTo.add(RAWmailTo.nextToken());
        // Сохраняем логгер
        this.log = log;

        // Заполняем настройки подключения
        server = collections.getSectionParameter(settings, MAIL_SECTION, MAIL_SERVER, "");  // Сервер
        login = collections.getSectionParameter(settings, MAIL_SECTION, MAIL_LOGIN, "");    // Логин
        password = collections.getSectionParameter(settings, MAIL_SECTION, MAIL_PASSWORD, "");  // Пароль
        from = collections.getSectionParameter(settings, MAIL_SECTION, MAIL_FROM, login);   // Отправитель
        ssl = collections.getSectionBooleanParameter(settings, MAIL_SECTION, MAIL_SSL_ENABLE, "false"); // SSL
        tlsEnable = collections.getSectionBooleanParameter(settings, MAIL_SECTION, MAIL_TLS_ENABLE, "false");   // TLS
        // Порт
        try {
            port = collections.getSectionIntegerParameter(
                    settings,
                    MAIL_SECTION,
                    MAIL_PORT,
                    (ssl ? "465" : "25")
            );
        } catch (NumberFormatException exc) {
            log.error(exc);
            port = (ssl ? 465 : 25);
        }
        // Тайм-аут на соединение, ожидание и запись
        try {
            timeout = collections.getSectionIntegerParameter(
                    settings,
                    MAIL_SECTION,
                    MAIL_TIMEOUT,
                    MAIL_TIMEOUT_DEFAULT
            ) * 1000;
        } catch (NumberFormatException exc) {
            log.error(exc);
            timeout = 60000;
        }
        // Активируем поток-получатель
        sender = new senderThread();
    }

    /**
     * Инициализация мейлера для модулей
     *
     * @param coreMailer мейлер 1го уровня
     */
    protected smtp(smtp coreMailer) {
        // Инициализируемся только ссылкой вышестоящего мейлера
        this.rootMailer = coreMailer;
    }

    /**
     * Отправка простого сообщения
     *
     * @param mailHeader Тема сообщения
     * @param mailBody   Тело сообщения
     */
    @Override
    public void send(String mailHeader, String mailBody) {
        fullMail eMail = new fullMail(mailTo, mailHeader, mailBody);
        if (this.rootMailer == null) {
            // Если это мейлер 1го уровня - добавляем сообщение в кучу
            if (mailTo.size() != 0) send(eMail);
        } else {
            // Иначе передаём выше по уровню
            if (mailTo.size() != 0) rootMailer.send(eMail);
        }
    }

    /**
     * Непосредственная передача сообщения в
     * управляющий поток
     *
     * @param eMail Сообщение
     */
    protected void send(fullMail eMail) {
        if (sender != null) {
            if (eMail.recipientsExists()) sender.addMail(eMail);
        } else if (rootMailer != null) {
            if (eMail.recipientsExists()) rootMailer.send(eMail);
        } else {
            // А это уже баг, отправляет только мейлер 1го уровня,
            // с инициализированным потоком
            log.error("BUG: SMTP mailer sender not initialised");
        }
    }

    /**
     * Установка адресатов. Обычно выполняется после получения
     * экземпляра модулем либо тестом
     *
     * @param mailTo Адресаты
     */
    @Override
    public void setMailTo(String mailTo) {
        // Сбрасываем текущих адресатов
        this.mailTo = new LinkedList<>();
        // Заполняем из параметра
        StringTokenizer RAWMailTo = new StringTokenizer(mailTo, " ,;");
        while (RAWMailTo.hasMoreElements())
            this.mailTo.add(RAWMailTo.nextToken());
    }

    /**
     * Получение экземпляра для модулей и тестов
     *
     * @return Экземпляр sendMail для модуля
     */
    @Override
    public sendMail getInstance() {
        return new smtp(this);
    }

    /**
     * Класс-поток, управляет соединением с smtp-сервером и отправляет сообщения
     */
    private class senderThread
            implements Runnable {

        // Текущая сессия
        Session mailSession = null;
        // Текущее соединение
        SMTPTransport smtpTransport = null;
        // Настройки подключения
        Properties systemProperties;
        // Очередь сообщений (синхронизированно)
        private LinkedBlockingDeque<fullMail> mailQueue = new LinkedBlockingDeque<>();
        // Активность
        private boolean activity = true;
        // Спящий режим
        private boolean standby = false;

        /**
         * Инициализация с запуском
         */
        public senderThread() {
            // Получаем текущие настройки системы
            systemProperties = System.getProperties();
            // Дополняем настройками соединения
            // Сервер
            systemProperties.put(
                    (ssl ? "mail.smtps.host" : "mail.smtp.host"),
                    (server != null ? server : "")
            );
            // Авторизация
            if (login == null) login = "";
            if (password == null) password = "";
            // Если указан либо логин, либо пароль - используем авторизацию
            if (!login.equals("") || !password.equals("")) {
                systemProperties.put((ssl ? "mail.smtps.auth" : "mail.smtp.auth"), "true");
            } else {
                systemProperties.put((ssl ? "mail.smtps.auth" : "mail.smtp.auth"), "false");
            }
            // Порт
            if (port != 0)
                systemProperties.put((ssl ? "mail.smtps.port" : "mail.smtp.port"), Integer.toString(port));
            // TLS
            systemProperties.put(
                    (ssl ? "mail.smtps.starttls.enable" : "mail.smtp.starttls.enable"),
                    (tlsEnable ? "true" : "false")
            );
            // Тайм-ауты
            if (timeout != 0) {
                systemProperties.put(
                        (ssl ? "mail.smtps.connectiontimeout" : "mail.smtp.connectiontimeout"),
                        Integer.toString(timeout)
                );
                systemProperties.put(
                        (ssl ? "mail.smtps.timeout" : "mail.smtp.timeout"),
                        Integer.toString(timeout)
                );
                systemProperties.put(
                        (ssl ? "mail.smtps.writetimeout" : "mail.smtp.writetimeout"),
                        Integer.toString(timeout)
                );
            }
            new Thread(this, "SMTP mail sender thread").start();
        }

        /**
         * Выполнение подключения и переподключения
         */
        private void connect() {
            // Для обхода
            if (login == null) login = "";
            if (password == null) password = "";
            // Успешность соединения
            boolean success = false;
            int tries = 0; // Число попыток
            // "Долбимся" до тех пор, пока не подключимся
            while (!success) {
                tries++;
                if (smtpTransport != null) {
                    try {
                        smtpTransport.close();
                    } catch (MessagingException exc) {
                        log.error("Unable to close SMTP connection: " + exc);
                    }
                }
                // Создаём сессию
                mailSession = Session.getInstance(systemProperties, null);
                // Подключаемся к SMTP-серверу
                try {
                    log.debug("Establishing connection to SMTP");
                    smtpTransport = (SMTPTransport) mailSession.getTransport(ssl ? "smtps" : "smtp");
                    if (!login.equals("") || !password.equals("")) {
                        smtpTransport.connect(login, password);
                    } else {
                        smtpTransport.connect();
                    }
                    success = true;
                } catch (NoSuchProviderException exc) {
                    log.debug("BUG: in smtp.senderThread.connect: " + exc);
                } catch (MessagingException exc) {
                    // Ошибка соединения, записываем в лог
                    StringBuilder logMessage = new StringBuilder();
                    logMessage.append("Unable connect to SMTP server: ").append(exc);
                    if (smtpTransport != null)
                        logMessage.append(System.lineSeparator())
                                .append("Last server response: ").append(smtpTransport.getLastServerResponse())
                                .append(System.lineSeparator())
                                .append("Last return code: ").append(smtpTransport.getLastReturnCode());
                    log.error(logMessage);

                    // Ожидаем
                    try {
                        if (tries <= 5) {
                            Thread.sleep(2000);
                        } else {
                            tries = 0;
                            Thread.sleep(60000);
                        }
                    } catch (InterruptedException intExc) {
                        // Прервали - отключаемся
                        log.debug("Interrupted");
                        activity = false;
                        success = true;
                    }
                }
            }
        }

        /**
         * Добавление сообщения в очередь на отправку
         *
         * @param eMail Полное сообщение
         */
        public void addMail(fullMail eMail) {
            mailQueue.add(eMail);
            // Если в ожидании - пробуждаем
            if (standby) {
                synchronized (this) {
                    standby = false;
                    try {
                        this.notify();
                        log.debug("Wake up");
                    } catch (IllegalMonitorStateException exc) {
                        log.appErrorWriter(exc);
                    }
                }
            }
        }

        /**
         * Поток отправки сообщений.
         * Подключение и отправка занимают некоторое время,
         * поэтому вынесено потоком
         */
        @Override
        public void run() {
            log.debug("Send mail thread activated");
            while (activity) {
                // Висим в ожидании до прихода сообщения
                synchronized (this) {
                    while (standby) {
                        try {
                            log.debug("Standby");
                            wait();
                        } catch (InterruptedException exc) {
                            // При прерывании выключаем активность
                            activity = false;
                        }
                    }
                }
                // При отсутствии подключения - подключаемся
                if (smtpTransport == null || mailSession == null) {
                    connect();
                } else {
                    // При наличии - отправляем сообщения
                    Iterator<fullMail> currentMail = mailQueue.iterator();
                    while (currentMail.hasNext()) {
                        log.debug("Has mail in queue");
                        // Формируем сообщение
                        fullMail mail = currentMail.next();
                        Message mailToSend = new MimeMessage(mailSession);
                        // Отправитель
                        if (from != null) {
                            try {
                                log.debug("Set mail from " + from);
                                mailToSend.setFrom(new InternetAddress(from, false));
                            } catch (MessagingException exc) {
                                log.error("Unable to set \"From\" " + from + ": " + exc);
                            }
                        }
                        // Получатели
                        for (String TO : mail.getRecipients()) {
                            try {
                                log.debug("Add recipient " + TO);
                                mailToSend.addRecipient(Message.RecipientType.TO, new InternetAddress(TO, false));
                            } catch (MessagingException exc) {
                                log.error("Unable to set \"To\": " + TO + ": " + exc);
                            }
                        }
                        // X-Mailer
                        try {
                            log.debug("Set X-Mailer");
                            mailToSend.setHeader("X-Mailer", "Minimon monitoring system, with javax.mail");
                        } catch (MessagingException exc) {
                            log.error("BUG: unable to set X-Mailer header: " + exc);
                        }
                        // Дата отправки - текущая
                        try {
                            log.debug("Set sent date");
                            mailToSend.setSentDate(new Date());
                        } catch (MessagingException exc) {
                            log.error("Unable to set current sent date: " + exc);
                        }
                        // Тема письма
                        try {
                            mailToSend.setSubject(mail.getMailTopic());
                        } catch (MessagingException exc) {
                            log.error("Unable to set mail topic: " + exc);
                        }
                        // Сообщение
                        try {
                            mailToSend.setText(mail.getMailBody());
                        } catch (MessagingException exc) {
                            log.error("Unable to set mail body: " + exc);
                        }

                        // Отправляем
                        try {
                            log.debug("Trying to send");
                            smtpTransport.sendMessage(mailToSend, mailToSend.getAllRecipients());
                        } catch (SendFailedException exc) {
                            log.error(
                                    new StringBuilder()
                                            .append("Unable to sent to mail recipients: ").append(exc)
                                            .append(System.lineSeparator())
                                            .append("Last server response: ").append(smtpTransport.getLastServerResponse())
                                            .append(System.lineSeparator())
                                            .append("Last return code: ").append(smtpTransport.getLastReturnCode())
                            );
                        } catch (MessagingException exc) {
                            log.error(
                                    new StringBuilder()
                                            .append("Unable to send message: ").append(exc).append(", try to reconnect")
                                            .append(System.lineSeparator())
                                            .append("Last server response: ").append(smtpTransport.getLastServerResponse())
                                            .append(System.lineSeparator())
                                            .append("Last return code: ").append(smtpTransport.getLastReturnCode())
                            );
                            connect();
                            continue;
                        }
                        // Удаляем сообщение из очереди, если была успешная передача
                        // Либо только неверные команды/получатели
                        log.debug(
                                new StringBuilder()
                                        .append("Last server response: ").append(smtpTransport.getLastServerResponse())
                                        .append(System.lineSeparator())
                                        .append("Last return code: ").append(smtpTransport.getLastReturnCode())
                        );
                        log.debug("Remove mail from queue");
                        currentMail.remove();
                    }
                    // Переводим в ожидание
                    synchronized (this) {
                        log.debug("Switch to standby");
                        standby = true;
                    }
                }
            }
            // Разрываем соединение, если оно было
            log.debug("Mail shutdown");
            if (smtpTransport != null) {
                try {
                    smtpTransport.close();
                } catch (MessagingException exc) {
                    log.error("Unable to close SMTP connection: " + exc);
                }
            }
        }
    }
}
