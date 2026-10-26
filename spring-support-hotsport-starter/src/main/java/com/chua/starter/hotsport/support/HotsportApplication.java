package com.chua.starter.hotsport.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hotsport（APM 热点观测）独立启动类。
 *
 * <p>提供 REST API 暴露运行时 APM 数据（传输记录 / 依赖图 / 资源泄漏 / 日志），
 * 并在启动时自注入 Java Agent（{@code RuntimeBoot.attachSelf}）对当前 JVM 插桩。</p>
 *
 * <p><b>不依赖数据库</b>：本模块业务代码零 {@code DataSource} / {@code JdbcTemplate}
 * 使用，APM 数据全部走 {@code StorageManager} SPI（内存 / SQLite）。{@code spring-jdbc}
 * 仅因 common-starter 的异常处理器引用 {@code DataAccessException} 才留在 classpath，
 * 但绝不能让它自动配置 DataSource —— 否则会要求一个可连的 MySQL，启动即失败。
 * 故这里显式排除 {@link DataSourceAutoConfiguration}：观测后台必须能脱离任何外部
 * 数据库独立启动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
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
