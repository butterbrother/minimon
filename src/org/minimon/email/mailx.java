package org.minimon.email;

import org.minimon.core.logger;
import org.minimon.core.staticValues;
import org.minimon.system.StreamGrabber;
import org.minimon.system.procExec;
import org.minimon.utils.collections;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * Рассылка через UNIX mailx
 */
public class mailx
        implements sendMail, staticValues {

    // Адресаты
	private LinkedList<String> mailTo = new LinkedList<>();
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
     * Непосредственная отправка сообщения
     *
	 * @param eMail        Полное сообщение
	 */
	protected void send(fullMail eMail) {
		if (rootMailer != null) {
			rootMailer.send(eMail);
		} else {
			// Если отправители присутствуют - отправляем сообщение
			if (mailTo.size() != 0) {
				// Просто вызываем mailx
				LinkedList<String> cmdLineBuilder = new LinkedList<>();    // Билдер команды
				cmdLineBuilder.add("mailx");    // Команда
				// Если тема сообщения есть - устанавливаем
				if (!eMail.getMailTopic().trim().equals("")) {
					cmdLineBuilder.add("-s");
					cmdLineBuilder.add(eMail.getMailTopic());
				}
				// Вставляем адресатов
				for (String TO : eMail.getRecipients()) {
					cmdLineBuilder.add(TO);
				}
				// Преобразуем кучу в массив для procExec
				String[] cmdLine = cmdLineBuilder.toArray(new String[cmdLineBuilder.size()]);
				// Создаём исполнитель и посылаем сообщение
				procExec mailx = new procExec(debug, log.getModuleSubLogger("Process executor"), cmdLine);
				try {
					mailx.execute();
					StreamGrabber input = null;
					if (debug) input = new StreamGrabber(mailx.getStdout(), "", log.getModuleSubLogger("MailX stdout"));
					BufferedWriter output = mailx.getStdin();
					output.append(eMail.getMailBody());
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
	}

    /**
     * Смена/установка адресатов
     *
     * @param mailTo Адресаты
     */
    @Override
    public void setMailTo(String mailTo) {
		this.mailTo.clear();
		StringTokenizer RAWmailTo = new StringTokenizer(
                mailTo,
                " ,;"
        );
        while (RAWmailTo.hasMoreElements())
			this.mailTo.add(RAWmailTo.nextToken());
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
