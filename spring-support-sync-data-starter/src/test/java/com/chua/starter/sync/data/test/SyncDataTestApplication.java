package com.chua.starter.sync.data.test;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 同步数据测试应用
 *
 * @author CH
 * @since 2026/03/20
 */
@SpringBootApplication
@MapperScan("com.chua.starter.sync.data.support.mapper")
@ComponentScan(basePackages = {
    "com.chua.starter.sync.data.support",
    "com.chua.starter.sync.data.test"
})
public class SyncDataTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncDataTestApplication.class, args);
    }
}
