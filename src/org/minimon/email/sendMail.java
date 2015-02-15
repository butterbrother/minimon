package org.minimon.email;

import org.minimon.core.logger;

/**
 * Основной интерфейс рассылки сообщений
 */
public interface sendMail {

    /**
     * Отправка простого сообщения
     *
     * @param mailHeader Тема сообщения
     * @param mailBody   Тело сообщения
     */
    public abstract void send(String mailHeader, String mailBody);

    /**
     * Установка адресатов. Обычно выполняется после получения
     * экземпляра модулем либо тестом
     *
     * @param mailTo Адресаты
     */
    public abstract void setMailTo(String mailTo);

    /**
     * Получение экземпляра для модулей и тестов
     *
     * @param log Логгер
     * @return Экземпляр sendMail для модуля
     */
    public sendMail getInstance();
}
