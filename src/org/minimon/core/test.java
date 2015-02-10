package org.minimon.core;

import java.util.LinkedList;

/**
 * Класс-пустышка для отработки некоторых моментов
 */
public class test {

    public static void main(String args[]) {

        String external = "ping \"google some com\" another string case \"\" and \"";
        System.out.println(external);

        // Динамический массив облегчит задачу - не придётся предварительно
        // считать число элементов
        LinkedList<String> parsed = new LinkedList<String>();
        // Позиция
        int pos = 0;
        // В кавычках?
        boolean inQuote = false;
        for (int i = 0; i < external.length(); i++) {
            switch (external.charAt(i)) {
                case ('\"'):
                    inQuote = !inQuote;
                    break;
                case (' '):
                    if (!inQuote) {
                        String buffer = external.substring(pos, i);
                        if (buffer.startsWith("\"") && buffer.endsWith("\"") && !buffer.equals("\""))
                            buffer = buffer.substring(1, buffer.length() - 1);
                        parsed.add(buffer);
                        pos = i + 1;
                    }
            }
            // Если последний элемент был в кавычке, то сохраняем её
            if (i == (external.length() - 1) && inQuote)
                parsed.add(external.substring(pos, external.length()));
        }
        String[] retValue = parsed.toArray(new String[parsed.size()]);
        for (String item : retValue)
            System.out.print("[" + item + "]");
        System.out.println();
        new org.minimon.probes.databaseProbe();
        new org.minimon.probes.pingProbe();
        new org.minimon.probes.httpProbe();
        /*try {
            File socketFile = new File("skt");
            if (! socketFile.exists()) socketFile.createNewFile();
            long socketModified = socketFile.lastModified();
            BufferedReader socketReader;
            String buffer;
            while (true) {
                if (socketFile.exists() && socketFile.lastModified() != socketModified) {
                    socketModified = socketFile.lastModified();
                    socketReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File("skt")), "UTF-8"));
                    String message = "";
                    while ((buffer = socketReader.readLine()) != null) {
                        message += buffer;
                    }
                    System.out.println("Socket: " + message);
                    if (message.equalsIgnoreCase("exit")) {
                        socketReader.close();
                        System.exit(0);
                    }
                }
                Thread.sleep(100);
            }
        } catch (Exception exc) {
            exc.printStackTrace();
        }*/
    }
}
