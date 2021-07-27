package com.market.util;

public class OtpUtil {

    public static String generateOTP(int size) {

        int randomPin = (int) ((Math.random() * 9 * Math.pow(10, size - 1)) + (Math.pow(10, size - 1)));
        return String.valueOf(randomPin);
    }

}

