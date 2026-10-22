package com.spendsmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpendSmartApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendSmartApplication.class, args);
    }
}
