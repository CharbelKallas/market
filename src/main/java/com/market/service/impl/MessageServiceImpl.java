package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.service.MessageService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MessageServiceImpl implements MessageService {

    private final JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${mail.enabled}")
    private boolean mailEnabled;

    @Value("${sms.enabled}")
    private boolean smsEnabled;

    @Value("${twilio.sms.account.sid}")
    private String accountSid;

    @Value("${twilio.sms.auth.token}")
    private String authToken;

    @Value("${twilio.sms.twilio.number}")
    private String twilioNumber;

    @Autowired
    public MessageServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void sendEmailMessage(String to, String subject, String text) {
        if (mailEnabled) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            try {
                emailSender.send(message);
            } catch (Exception e) {
                throw MarketException.throwException("Email - Exception", e);
            }
        }
    }

    @Override
    public void sendSmsMessage(String to, String body) {
        if (smsEnabled) {
            Twilio.init(accountSid, authToken);
            try {
                Message.creator(
                                new PhoneNumber(to),
                                new PhoneNumber(twilioNumber),
                                body)
                        .create();
            } catch (Exception e) {
                throw MarketException.throwException("SMS - Exception", e);
            }
        }
    }
}