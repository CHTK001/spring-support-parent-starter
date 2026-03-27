package com.chua.starter.sync.data.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 可直接运行的 Sync Data Demo 应用。
 */
@SpringBootApplication(scanBasePackages = {
        "com.chua.starter.sync.data.support",
        "com.chua.starter.sync.data.demo"
})
@MapperScan("com.chua.starter.sync.data.support.mapper")
public class SyncDataDemoApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SyncDataDemoApplication.class);
        application.setAdditionalProfiles("demo");
        application.run(args);
    }
}
