package com.market.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OtpUtil {

    @Value("${otp.size}")
    private static int OTP_SIZE;

    public static String generateOTP() {
        int randomPin = (int) ((Math.random() * 9 * Math.pow(10, OTP_SIZE - 1)) + (Math.pow(10, OTP_SIZE - 1)));
        return String.valueOf(randomPin);
    }

}

