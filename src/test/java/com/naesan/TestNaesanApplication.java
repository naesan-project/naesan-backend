package com.naesan;

import org.springframework.boot.SpringApplication;

public class TestNaesanApplication {

    public static void main(String[] args) {
        SpringApplication.from(NaesanApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
