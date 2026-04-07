package com.lww;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Saa01HelloWordApplication {
    public static void main(String[] args) {
        SpringApplication.run(Saa01HelloWordApplication.class,args);
    }
}
