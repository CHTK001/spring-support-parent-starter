package com.chua.starter.hotsport.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hotsport（APM 热点观测）独立启动类。
 *
 * <p>提供 REST API 暴露运行时 APM 数据（传输记录 / 依赖图 / 资源泄漏 / 日志），
 * 并在启动时自注入 Java Agent（{@code RuntimeBoot.attachSelf}）对当前 JVM 插桩。</p>
 *
 * <p>不依赖数据库：pom 已排除 {@code spring-boot-starter-jdbc}，
 * APM 数据走 {@code StorageManager} SPI（内存 / SQLite）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication
@EnableScheduling
public class HotsportApplication {

    /**
    * 主入口。
    *
    * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HotsportApplication.class, args);
    }
}
