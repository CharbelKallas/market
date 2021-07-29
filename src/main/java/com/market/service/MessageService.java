package com.market.service;

public interface MessageService {
    void sendEmailMessage(String to, String subject, String text);

    void sendSmsMessage(String to, String body);
}
