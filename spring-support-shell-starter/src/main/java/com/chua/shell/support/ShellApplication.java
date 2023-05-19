package com.chua.shell.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author CH
 */
@RestController
@SpringBootApplication
public class ShellApplication {

    @Value("${server.name:23}")
    private String port;

    @GetMapping("/name")
    public String get() {
        return port;
    }

    public static void main(String[] args) {
        SpringApplication.run(ShellApplication.class);
    }
}
