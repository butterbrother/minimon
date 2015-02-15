package org.minimon.core;

import com.sun.mail.smtp.SMTPTransport;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

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

        try {
            Properties sysProps = System.getProperties();
            sysProps.put("mail.smtp.host", "smtp.mail.ru");
            sysProps.put("mail.smtp.auth", "true");
            sysProps.put("mail.smtp.port", "465");
            sysProps.put("mail.smtp.ssl.enable", "true");
            Session mailSession = Session.getInstance(sysProps, null);
            SMTPTransport transport = (SMTPTransport) mailSession.getTransport("smtp");
            transport.connect("slf_test_mail@mail.ru", "DynamicIns1d3rs");
            Message msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress("slf_test_mail@mail.ru", false));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress("o.bobukh@yandex.ru", false));
            msg.setHeader("X-Mailer", "javax mail test");
            msg.setSentDate(new Date());
            msg.setSubject("Test mail");
            msg.setText("Test mail text");
            transport.sendMessage(msg, msg.getAllRecipients());
            System.out.println("Last code: " + transport.getLastReturnCode());
            System.out.println("Last message: " + transport.getLastServerResponse());
            transport.close();
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }
}
