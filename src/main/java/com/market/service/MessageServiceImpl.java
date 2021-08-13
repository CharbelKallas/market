package com.market.service;

import com.market.exception.MarketException;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import static com.market.exception.EntityType.EMAIL;
import static com.market.exception.EntityType.SMS;
import static com.market.exception.ExceptionType.ENTITY_EXCEPTION;

@Component
public class MessageServiceImpl implements MessageService {

    @Autowired
    private JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String MAIL_USERNAME;

    @Value("${mail.enabled}")
    private boolean MAIL_ENABLED;

    @Value("${sms.enabled}")
    private boolean SMS_ENABLED;

    @Value("${twilio.sms.account.sid}")
    private String ACCOUNT_SID;

    @Value("${twilio.sms.auth.token}")
    private String AUTH_TOKEN;

    @Value("${twilio.sms.twilio.number}")
    private String TWILIO_NUMBER;

    @Override
    public void sendEmailMessage(String to, String subject, String text) {
        if (MAIL_ENABLED) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(MAIL_USERNAME);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            try {
                emailSender.send(message);
            } catch (Exception e) {
                throw MarketException.throwException(EMAIL, ENTITY_EXCEPTION, e.getMessage());
            }
        }
    }

    @Override
    public void sendSmsMessage(String to, String body) {
        if (SMS_ENABLED) {
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
            try {
                Message.creator(
                                new PhoneNumber(to),
                                new PhoneNumber(TWILIO_NUMBER),
                                body)
                        .create();
            } catch (Exception e) {
                throw MarketException.throwException(SMS, ENTITY_EXCEPTION, e.getMessage());
            }
        }
    }
}