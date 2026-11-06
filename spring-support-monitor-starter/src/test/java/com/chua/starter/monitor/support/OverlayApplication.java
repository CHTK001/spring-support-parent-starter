package com.chua.starter.monitor.support;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 覆盖层启动入口（仅供本地 8082 验证用，不参与打包）。
 *
 * 为什么不直接跑 MonitorApplication：它被当作「孤立 bean」时会把 Controller 重新注册一份，
 * 丢掉 at RequestMapping，接口就会报 "Name for argument ... not specified"。
 * 这里复用它的 @SpringBootApplication 元数据，并显式补上 MapperScan。
 */
public class OverlayApplication {

    /**
     * Main
     *
     * at param args 参数
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(MonitorApplication.class)
                .sources(AlarmScanConfig.class)
                .run(args);
        System.out.println("OVERLAY_STARTED " + ctx.getId());
    }

    /**
     * 扫描配置：等价于 MonitorApplication 上的 @EnableTransactionManagement + @MapperScan
     */
    @EnableTransactionManagement
    @MapperScan({"com.chua.starter.monitor.support.mapper", "com.chua.starter.server.support.mapper",
            "com.chua.starter.job.support.store"})
    @SpringBootApplication
    static class AlarmScanConfig {
    }
}
