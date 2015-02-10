package org.minimon.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * Выполняет мониторинг файлового сокета и исполняет команды
 */
public class socketReader
        implements staticValues, Runnable {

    // Активность
    boolean alive = true;
    // Файловый сокет
    private File socketFile;
    // Ссылка на контроллер
    private controller worker;
    // Логгер
    private logger log;

    /**
     * Инициализация
     *
     * @param socketFileName Путь к сокету
     * @param worker         Активный контроллер
     * @param log            Логгер
     */
    public socketReader(String socketFileName, controller worker, logger log) {
        socketFile = new File(socketFileName);
        this.worker = worker;
        this.log = log;
        new Thread(this, "Socket reader").start();
    }

    @Override
    public void run() {
        try {
            //Thread.currentThread().setDaemon(true);
            log.info("Command controller initialised");
            worker.reportSocketState(true);
            // Статус активности
            while (alive) {
                try {
                    // Пересоздаём сокет-файл
                    fileFixPerm.tryFixFilePerm(socketFile.getAbsolutePath());
                    if (socketFile.exists())
						if (! socketFile.delete())
							log.error("Unable to delete socket file");
                    if (!socketFile.exists())
						if (! socketFile.createNewFile())
							log.error("Unable to create socket file");
                    long socketModified = socketFile.lastModified();
                    BufferedReader socketReader;
                    String buffer;
                    while (alive) {
                        if (socketFile.exists() && socketFile.lastModified() != socketModified) {
                            socketModified = socketFile.lastModified();
                            socketReader = new BufferedReader(new InputStreamReader(new FileInputStream(socketFile), "UTF-8"));
                            String message = "";
                            while ((buffer = socketReader.readLine()) != null) {
                                message += buffer;
                            }
                            log.debug("Socket: " + message);
                            parseCommand(message);
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException exc) {
                    log.debug("Socket reader interrupted");
                    switchOff();
                } catch (Exception exc) {
                    // При любой ошибке просто начинаем всё с начала
                    log.appErrorWriter(exc);
                }
            }
            log.info("Command controller switch off");

        } catch (Exception exc) {
            log.appErrorWriter(exc);
        }

        // Удаляем файловый сокет
        if (!socketFile.delete())
            log.error("Unable to delete socket file");

        worker.reportSocketState(false);
    }

    /**
     * Выключает исполнителя
     */
    private void switchOff() {
        alive = false;
    }

    /**
     * Парсинг входного сообщения
     *
     * @param command Сообщение
     */
    private void parseCommand(String command) {
        if (alive) {
            // Выполняем разбор
            StringTokenizer parser = new StringTokenizer(command, ":");
            String action = null, param = null;
            if (parser.hasMoreElements())
                action = parser.nextToken().toLowerCase();
            if (parser.hasMoreElements())
                param = parser.nextToken();

            // Далее в зависимости от параметров
            if (action != null && alive) {
                if (action.startsWith("start") && param != null && alive) {
                    worker.startModule(param);
                }
                if (action.startsWith("stop") && param != null && alive) {
                    worker.stopModule(param);
                } else if (action.startsWith("stop") && param == null && alive) {
                    // Тут полный останов
                    switchOff();
                    worker.switchOff();
                }
                if (action.startsWith("restart") && param != null && alive) {
                    worker.restart(param);
                } else if (action.startsWith("restart") && param == null && alive) {
                    worker.restartAll();
                }
            }
        }
    }
}
