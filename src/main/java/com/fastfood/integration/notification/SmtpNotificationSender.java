package com.fastfood.integration.notification;

import com.fastfood.config.AppConfig;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SmtpNotificationSender implements NotificationSender {

    private static final Logger LOG = Logger.getLogger(SmtpNotificationSender.class.getName());

    private static final String TIMEOUT_MS = "10000";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;

    public SmtpNotificationSender() {
        this.host = AppConfig.get("notification.mail.host", "smtp.gmail.com").trim();
        this.port = AppConfig.getInt("notification.mail.port", 587);
        this.username = AppConfig.get("notification.mail.username", "").trim();
        this.password = AppConfig.get("notification.mail.password", "").trim();
        this.fromAddress = AppConfig.get("notification.mail.from", username).trim();
        this.fromName = AppConfig.get("notification.mail.fromName", "Fast Food Pre-order").trim();
    }

    @Override
    public String getChannel() {
        return "EMAIL";
    }

    public boolean isConfigured() {
        return !host.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }

    @Override
    public boolean send(String recipient, String subject, String content) {
        if (recipient == null || recipient.isBlank()) {
            LOG.warning("SMTP: khong co dia chi nguoi nhan, bo qua thu: " + subject);
            return false;
        }
        if (!isConfigured()) {
            LOG.severe("SMTP: thieu notification.mail.host/username/password, khong gui duoc thu");
            return false;
        }
        try {
            Transport.send(build(recipient, subject, content));
            LOG.info(() -> "SMTP: da gui thu den " + recipient + " | " + subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            LOG.log(Level.SEVERE, "SMTP: gui thu den " + recipient + " that bai", e);
            return false;
        }
    }

    private MimeMessage build(String recipient, String subject, String content)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = new MimeMessage(session());
        message.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
        message.setSubject(subject, "UTF-8");
        message.setText(content, "UTF-8");
        return message;
    }

    private Session session() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
