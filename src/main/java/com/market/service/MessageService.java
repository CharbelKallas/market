package com.market.service;

public interface MessageService {
    /**
     * @return true if the email was sent, false if email sending is disabled
     * @throws RuntimeException if sending failed
     */
    boolean sendEmailMessage(String to, String subject, String text);

    /**
     * @return true if the SMS was sent, false if SMS sending is disabled
     * @throws RuntimeException if sending failed
     */
    boolean sendSmsMessage(String to, String body);
}
