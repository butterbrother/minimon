package org.minimon.email;

import org.minimon.core.logger;
import org.minimon.core.staticValues;
import org.minimon.system.StreamGrabber;
import org.minimon.system.procExec;
import org.minimon.utils.collections;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;

/**
 * Рассылка через UNIX mailx
 */
public class mailx
        implements sendMail, staticValues {

    // Адресаты
    private String mailTo = "";
    // Логгер
    private logger log;
    // Ссылка на мейлер 1го уровня
    private mailx rootMailer = null;
    // Статус отладки
    private boolean debug = false;

    /**
     * Инициализация мейлера 1го уровня
     * Отличается от остальных лишь тем, что выполняет рассылку сообщений,
     * делая это в один поток
     *
     * @param settings Настройки из секции сообщений
     * @param log      Логгер
     */
    public mailx(LinkedHashMap<String, LinkedHashMap<String, String>> settings, logger log, boolean debugState) {
        // Получаем логгер
        this.log = log;
        // Разбираем параметры
        // Интересен только mail to
        StringBuilder mailTo = new StringBuilder();
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
            mailTo.append(RAWmailTo.nextToken()).append(" ");
        this.mailTo = mailTo.toString().trim();
        // Статус отладки
        this.debug = debugState;
    }

    /**
     * Инициализация мейлера для модулей
     *
     * @param coreMailX Вышестоящий мейлер
     */
    private mailx(mailx coreMailX) {
        // Получаем ссылку на вышестоящий мейлер
        this.rootMailer = coreMailX;
    }

    /**
     * Отправка сообщения
     *
     * @param mailHeader Тема сообщения
     * @param mailBody   Тело сообщения
     */
    @Override
    public void send(String mailHeader, String mailBody) {
        // Если это мейлер модуля, передаём сообщение выше
        if (rootMailer != null) {
            rootMailer.send(mailTo, mailHeader, mailBody);
        } else {
            // Иначе просто вызываем свою же функцию
            send(mailTo, mailHeader, mailBody);
        }
    }

    /**
     * Непосредственная отправка сообщения
     *
     * @param mailTo     Получатели
     * @param mailHeader Тема сообщения
     * @param mailBody   Тело сообщения
     */
    protected void send(String mailTo, String mailHeader, String mailBody) {
        // Если отправители присутствуют - отправляем сообщение
        if (!mailTo.trim().equalsIgnoreCase("")) {
            // Просто вызываем mailx
            procExec mailx = new procExec(debug, log.getModuleSubLogger("Process executor"), "mailx", "-s", mailHeader, mailTo);
            try {
                mailx.execute();
                StreamGrabber input = null;
                if (debug) input = new StreamGrabber(mailx.getStdout(), "", log.getModuleSubLogger("MailX stdout"));
                BufferedWriter output = mailx.getStdin();
                output.append(mailBody);
                output.close();
                mailx.waitFor();

                if (input != null) {
                    log.debug(
                            new StringBuilder().append("Mailx stdout: ").append(System.lineSeparator())
                                    .append(input.getResults()).append(System.lineSeparator())
                                    .append("Process return: ").append(mailx.exitValue())
                    );
                }
            } catch (IOException exc) {
                log.appErrorWriter(exc);
            } catch (InterruptedException exc) {
                mailx.terminate();
                log.debug("sendMail terminated");
            }
        } else {
            log.debug("Send mail disabled");
        }
    }

    /**
     * Смена/установка адресатов
     *
     * @param mailTo Адресаты
     */
    @Override
    public void setMailTo(String mailTo) {
        StringBuilder mailToBuilder = new StringBuilder();
        StringTokenizer RAWmailTo = new StringTokenizer(
                mailTo,
                " ,;"
        );
        while (RAWmailTo.hasMoreElements())
            mailToBuilder.append(RAWmailTo.nextToken());
        this.mailTo = mailToBuilder.toString().trim();
    }

    /**
     * Получение экземпляра мейлера для модулей
     *
     * @return Мейлер для модулей
     */
    @Override
    public sendMail getInstance() {
        return new mailx(this);
    }
}
