package com.market.util;

import com.market.config.PropertiesConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OtpUtil {

    private static PropertiesConfig propertiesConfig;

    @Autowired
    public OtpUtil(PropertiesConfig propertiesConfig) {
        OtpUtil.propertiesConfig = propertiesConfig;
    }

    public static String generateOTP() {
        int size = Integer.parseInt(propertiesConfig.getConfigValue("otp.size"));
        int otp = (int) ((Math.random() * 9 * Math.pow(10, size - 1)) + (Math.pow(10, size - 1)));
        return String.valueOf(otp);
    }

}

