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

import java.io.*;

/**
 * Запуск приложений и получение их потоков (pipe)
 */
public class procExec {
    // Флаг отладки
    boolean debug = false;
    // Инициализатор
    ProcessBuilder procBuild = null;
    // Управляемый подпроцесс
    Process runtime = null;
    // Логгер (при наличии)
    logger log = null;

    /**
     * Начальная инициализация с флагом отладки
     *
     * @param debugState статус отладки
     * @param parameters команда запуска с параметрами
     */
    public procExec(boolean debugState, String... parameters) {
        debug = debugState;
        procBuild = new ProcessBuilder(parameters);
        if (debug) {
            String logmessage = "DEBUG: params: ";
            for (String item : parameters) logmessage += item + " ";
            System.out.println(logmessage);
        }
    }

    /**
     * Начальная инициализация с логгером и флагом отладки
     * Для выполнения в качестве сервиса
     */
    public procExec(boolean debugState, logger log, String... parameters) {
        debug = debugState;
        this.log = log;
        procBuild = new ProcessBuilder(parameters);
        if (debug) {
            String logMessage = "Runtime params: ";
            for (String item : parameters) logMessage += item + " ";
            log.debug(logMessage);
        }
    }

    /**
     * Изменение вывода выходного потока с pipe в указанный файл
     * Например, можно переводить в NULL/dev/null либо в лог-файл
     * Изменение доступно до запуска процесса
     *
     * @param fileName Выходной файл
     */
    public void redirectOutput(String fileName) {
        if (procBuild != null)
            procBuild.redirectOutput(new File(fileName));
        if (debug) {
            if (log == null)
                System.out.println("DEBUG: redirected to: " + fileName);
            else
                log.debug("redirected output to: " + fileName);
        }
    }

    /**
     * Выполняет запуск с указанными параметрами
     *
     * @throws IOException
     */
    public void execute() throws IOException {
        terminate();
        if (procBuild != null) {
            runtime = procBuild.start();
        }
    }

    /**
     * Выполняет запуск с указанными параметрами
     * и указанием рабочей директории
     *
     * @param position Рабочая директория
     * @throws IOException
     */
    public void execute(String position) throws IOException {
        terminate();
        if (procBuild != null) {
            File pos = new File(position);
            if (!pos.exists()) throw new IOException("Working directory not found");
            procBuild.directory(new File(position));
            runtime = procBuild.start();
        }
    }

    /**
     * Выполняет запуск с указанными параметрами
     * и указанием рабочей директории
     *
     * @param location Рабочая директория
     * @throws IOException
     */
    public void execute(File location) throws IOException {
        terminate();
        if (procBuild != null) {
            if (location == null || !location.exists()) throw new IOException("Working directory not found");
            procBuild.directory(location);
            runtime = procBuild.start();
        }
    }

    /**
     * Возвращает stdin процесса, оформленный в BufferedWriter либо null, если не запущен
     *
     * @return Входной поток процесса (pipe)
     */
    public BufferedWriter getStdin() {
        if (runtime != null)
            return new BufferedWriter(new OutputStreamWriter(runtime.getOutputStream()));
        else
            return null;
    }

    /**
     * Возвращает stdout процесса, оформленный в BufferedReader либо null, если не запущен
     *
     * @return Выходной поток процесса
     */
    public BufferedReader getStdout() {
        if (runtime != null)
            return new BufferedReader(new InputStreamReader(runtime.getInputStream()));
        else
            return null;
    }

    /**
     * Возвращает stderr процесса, оформленный в BufferedReader либо null, если не запущен
     *
     * @return Выходной поток ошибок процесса
     */
    public BufferedReader getStderr() {
        if (runtime != null)
            return new BufferedReader(new InputStreamReader(runtime.getErrorStream()));
        else
            return null;
    }

    /**
     * Ожидание завершения процесса
     *
     * @throws InterruptedException внезапное прерывание
     */
    public void waitFor() throws InterruptedException {
        if (runtime != null)
            runtime.waitFor();
    }

    /**
     * Возвращает код завершения процесса
     *
     * @return Код завершения
     */
    public int exitValue() {
        if (runtime != null)
            return runtime.exitValue();
        return 0;
    }

    /**
     * Убивает вызванный процесс
     */
    public void terminate() {
        if (runtime != null)
            runtime.destroy();
    }
}
