package com.wattsmart.backend;

import com.wattsmart.backend.common.config.DotenvLoader;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class WattSmartSimulatorApplication {

    public static void main(String[] args) {
        DotenvLoader.load();
        new SpringApplicationBuilder(WattSmartApplication.class)
                .profiles("simulator")
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
