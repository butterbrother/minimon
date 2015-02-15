package org.minimon.email;

import java.util.LinkedList;

/**
 * Полное сообщение, со всеми данными.
 * Ими оперируют сложные мейлеры (smtp и т.п.) как внутри себя,
 * так и между уровнями
 */
public class fullMail {
    private LinkedList<String> recipients;
    private String mailTopic;
    private String mailBody;

    /**
     * Инициализация с полным заполнением сообщения
     *
     * @param recipients Получатели
     * @param mailTopic  Тема сообщения
     * @param mailBody   Сообщение
     */
    public fullMail(LinkedList<String> recipients, String mailTopic, String mailBody) {
        this.recipients = recipients;
        this.mailTopic = mailTopic;
        this.mailBody = mailBody;
    }

    /**
     * Получение получателей
     *
     * @return список получателей
     */
    public LinkedList<String> getRecipients() {
        return recipients;
    }

    /**
     * Получение темы сообщения
     *
     * @return тема сообщения
     */
    public String getMailTopic() {
        return mailTopic;
    }

    /**
     * Получение сообщения
     *
     * @return сообщение
     */
    public String getMailBody() {
        return mailBody;
    }

    /**
     * Признак наличия получателей
     *
     * @return Отсутствие либо наличие получателей
     */
    public boolean recipientsExists() {
        return (recipients.size() != 0);
    }
}
