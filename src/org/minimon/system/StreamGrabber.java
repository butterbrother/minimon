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
 * С ПРОГРАММНЫМ ОБЕСПЕЧЕНИЕМ.
 */

package org.minimon.system;

import org.minimon.core.logger;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Выполняет сбор выхлопа процесса в отдельном потоке
 */
public class StreamGrabber
		implements Runnable {

	BufferedReader execOutBuffer;    // Выходной поток приложения
	StringBuilder output = new StringBuilder();    // Буфер данных
	String prefix;    // Префикс перед каждой строкой
	logger grabLog;    // Логгер

	public StreamGrabber(BufferedReader execOutBuffer, String prefix, logger grabLog) {
		this.execOutBuffer = execOutBuffer;
		this.prefix = prefix;
		this.grabLog = grabLog;
		new Thread(this).start();
	}

	/**
	 * Запускаем и считываем
	 */
	public void run() {
		String buffer;
		try {
			while ((buffer = execOutBuffer.readLine()) != null)
				output.append(prefix).append(buffer).append(System.lineSeparator());
		} catch (IOException exc) {
			grabLog.appErrorWriter(exc);
		}
	}

	/**
	 * Получение выхлопа
	 *
	 * @return Выхлоп процесса
	 */
	public String getResults() {
		return output.toString();
	}

	/**
	 * Закрытие потока выхлопа
	 */
	public void closeOut() {
		try {
			execOutBuffer.close();
		} catch (IOException exc) {
			grabLog.appErrorWriter(exc);
		}
	}
}
