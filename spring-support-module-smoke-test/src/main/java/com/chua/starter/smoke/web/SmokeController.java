package com.chua.starter.smoke.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SmokeController {

    private final Environment environment;
    private final String targetModule;

    public SmokeController(Environment environment,
                           @Value("${smoke.target.module:base}") String targetModule) {
        this.environment = environment;
        this.targetModule = targetModule;
    }

    @GetMapping("/smoke/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("module", targetModule);
        response.put("application", environment.getProperty("spring.application.name", "spring-support-module-smoke-test"));
        response.put("activeProfiles", environment.getActiveProfiles());
        return response;
    }
}
