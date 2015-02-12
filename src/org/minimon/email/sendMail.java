package org.minimon.email;

import org.minimon.core.logger;

import java.util.LinkedHashMap;

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
    public void send(String mailHeader, String mailBody);

    /**
     * Установка адресатов. Обычно выполняется после получения
     * экземпляра модулем либо тестом
     *
     * @param mailTo Адресаты
     */
    public void setMailTo(String mailTo);

    /**
     * Получение экземпляра рассылки с указанными настройками
     * Экземпляр считается индивидуальным либо 1м по уровню
     * </p>
     * Рекомендуется конструктор сделать protected
     *
     * @param settings Настройки в виде LinkedHashMap (String)
     * @param log      Логгер
     * @return Экземпляр sendMail
     */
    public sendMail getInstance(LinkedHashMap<String, String> settings, logger log);

    /**
     * Получение экземпляра для модулей и тестов
     *
     * @param log Логгер
     * @return Экземпляр sendMail для модуля
     */
    public sendMail getInstance(logger log);
}
