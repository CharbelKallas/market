package com.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MarketApplication {

    private static final Logger log = LoggerFactory.getLogger(MarketApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MarketApplication.class, args);
    }

}
