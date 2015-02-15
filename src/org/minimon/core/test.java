package org.minimon.core;

/**
 * Класс-пустышка для отработки некоторых моментов
 */
public class test {

    public static void main(String args[]) {

        new org.minimon.probes.databaseProbe();
        new org.minimon.probes.pingProbe();
        new org.minimon.probes.procCheckProbe();
        new org.minimon.probes.httpProbe();
        new org.minimon.probes.freeSpaceProbe();

    }
}
