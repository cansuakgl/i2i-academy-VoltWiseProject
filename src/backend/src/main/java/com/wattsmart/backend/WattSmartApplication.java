package com.wattsmart.backend;

import com.wattsmart.backend.common.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WattSmartApplication {

    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(WattSmartApplication.class, args);
    }
}
