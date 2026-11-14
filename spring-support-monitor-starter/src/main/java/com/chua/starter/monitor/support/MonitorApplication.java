package com.chua.starter.monitor.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 监控模块独立测试启动类
 *
 * @since 2026/8/5
 * @author CH
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan({"com.chua.starter.monitor.support.mapper", "com.chua.starter.server.support.mapper",
        "com.chua.starter.job.support.store"})
public class MonitorApplication {

    /**
     * 启动阶段步骤的缓冲条数。
     *
     * <p>{@code /actuator/startup} 只在 {@code ApplicationStartup} 是
     * {@link BufferingApplicationStartup} 时才注册（见 {@code StartupEndpointAutoConfiguration}），
     * 且必须是 {@link SpringApplication} 自己用的那个实例，靠 {@code @Bean} 补不出来。
     * 监控自身作为被观测项目时，发布管理的「其它端点 → startup」靠它出启动步骤时间线。</p>
     */
    private static final int STARTUP_STEPS = 4096;

    /**
     * Main
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MonitorApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(STARTUP_STEPS));
        application.run(args);
    }
}
