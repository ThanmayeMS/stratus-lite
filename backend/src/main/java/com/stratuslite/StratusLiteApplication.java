package com.stratuslite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StratusLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(StratusLiteApplication.class, args);
    }
}
