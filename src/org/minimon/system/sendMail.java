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

package org.minimon.system;

import org.minimon.core.logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Отправка e-mail средствами ОС
 */
public class sendMail {
    // e-mail адресаты
    private final String mailTo;
    // Флаг отладки
    private boolean debug;
    // Логгер
    private logger log;

    // Включена ли отправка e-mail, либо в параметрах null
    private boolean enabled;

    /**
     * Инициализация
     *
     * @param mailTo     Список адресатов
     * @param log        Логгер
     * @param debugState Статус отладки
     */
    public sendMail(String mailTo, logger log, boolean debugState) {
        if (mailTo != null) {
            this.mailTo = mailTo.trim();
            enabled = (!this.mailTo.isEmpty());
        } else {
            this.mailTo = "";
            enabled = false;
        }
        this.log = log;
        debug = debugState;
    }

    /**
     * Отправка e-mail
     *
     * @param topic   Тема письма
     * @param message Сообщение
     */
    public void send(String topic, String message) {
        if (enabled) {
            procExec mailx = new procExec(debug, log.getModuleSubLogger("Process executor"), "mailx", "-s", topic, mailTo);
            try {
                mailx.execute();
                BufferedReader input = null;
                if (debug) input = mailx.getStdout();
                BufferedWriter output = mailx.getStdin();
                output.append(message);
                output.close();
                mailx.waitFor();

                if (input != null) {
                    String buffer;
                    String logMessage = "";
                    while ((buffer = input.readLine()) != null) {
                        logMessage += buffer;
                    }
                    input.close();
                    logMessage += System.lineSeparator() + "Process return " + Integer.toString(mailx.exitValue());
                    log.debug(logMessage);
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
