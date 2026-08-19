package com.chua.starter.monitor.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 监控模块独立测试启动类
 *
 * @since 2026/8/5
 * @author CH
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.chua.starter.monitor.support.mapper")
public class MonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitorApplication.class, args);
    }
}
