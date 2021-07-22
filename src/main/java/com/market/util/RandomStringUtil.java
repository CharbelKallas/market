package com.market.util;

public class RandomStringUtil {
    public static String getAlphaNumericString(int n, String inputString) {

        String inputStringUcase = inputString.trim().toUpperCase().replaceAll(" ", "").concat("123456789");

        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            int index = (int) (inputStringUcase.length() * Math.random());
            sb.append(inputStringUcase.charAt(index));
        }
        return sb.toString();
    }
}

