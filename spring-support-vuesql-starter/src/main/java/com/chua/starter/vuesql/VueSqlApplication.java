package com.chua.starter.vuesql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

/**
 * @author CH
 */
@EnableCaching
@SpringBootApplication
public class VueSqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(VueSqlApplication.class);
    }
}
