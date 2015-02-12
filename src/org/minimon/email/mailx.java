package org.minimon.email;

import org.minimon.core.logger;

import java.util.LinkedHashMap;

/**
 * Рассылка через UNIX mailx
 */
public class mailx
        implements sendMail {

    protected mailx(LinkedHashMap<String, String> settings, logger log) {

    }

    protected mailx(mailx coreMailX) {

    }

    /**
     * Отправка сообщения
     *
     * @param mailHeader Тема сообщения
     * @param mailBody   Тело сообщения
     */
    @Override
    public void send(String mailHeader, String mailBody) {

    }

    /**
     * Смена/установка адресатов
     *
     * @param mailTo Адресаты
     */
    @Override
    public void setMailTo(String mailTo) {

    }

    /**
     * Получение экземпляра 1го уровня
     *
     * @param settings Настройки в виде LinkedHashMap (String)
     * @param log      Логгер
     * @return Экземпляр 1го уровня
     */
    @Override
    public sendMail getInstance(LinkedHashMap<String, String> settings, logger log) {
        return null;// сие убрать во время создания
    }

    public sendMail getInstance(logger log) {
        return null;
    }
}
