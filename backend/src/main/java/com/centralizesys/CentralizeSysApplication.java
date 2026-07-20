package com.centralizesys;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class CentralizeSysApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    public static void main(String[] args) {
        // --- SYSTEM RESTORE LOGIC ---
        // Runs before Spring Context to avoid file locking
        // Utility handles paths robustly via DataPathConfig
        // DatabaseRestorer functionality removed to use pg_dump/psql instead

        SpringApplication.run(CentralizeSysApplication.class, args);
    }
}