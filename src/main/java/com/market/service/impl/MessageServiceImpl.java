package com.market.service.impl;

import com.market.exception.MarketException;
import com.market.service.MessageService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

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

    public MessageServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @PostConstruct
    void initTwilio() {
        if (smsEnabled)
            Twilio.init(accountSid, authToken);
    }

    @Override
    public boolean sendEmailMessage(String to, String subject, String text) {
        if (!mailEnabled)
            return false;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            emailSender.send(message);
            return true;
        } catch (Exception e) {
            throw MarketException.internal("Could not send the email.", e);
        }
    }

    @Override
    public boolean sendSmsMessage(String to, String body) {
        if (!smsEnabled)
            return false;

        try {
            Message.creator(new PhoneNumber(to), new PhoneNumber(twilioNumber), body).create();
            return true;
        } catch (Exception e) {
            throw MarketException.internal("Could not send the SMS.", e);
        }
    }
}
