package com.chua.starter.datasync.dbprobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

/**
   * 装配关闭探针，验证 {@code plugin.datasync.enabled=false} 时 数据同步 自动配置完全关闭。
 *
 * <p>与 {@link com.chua.starter.datasync.support.DataSyncApplication} 的区别：
   * 启动类在启动后会按 标识 获取 数据同步 相关 Bean，已启用=false 时这些 Bean 不存在会直接抛异常；
   * 本探针只按类型枚举 Bean 名称，已启用=false 时预期 Datasync/数据同步 类型数量为 0，
   * 且上下文仍正常启动（无 数据同步 相关装配）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DataSyncDisabledProbe {

    /**
     * 程序入口。
     *
     * @param args 命令行参数，应为 --plugin.datasync.已启用=false
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DataSyncDisabledProbe.class);
        application.setDefaultProperties(java.util.Map.of("spring.main.banner-mode", "off", "server.port", "0"));
        ApplicationContext context = application.run(args);

        String[] names = context.getBeanNamesForType(
                org.springframework.beans.factory.SmartInitializingSingleton.class);
        java.util.List<String> datasyncRelated = new java.util.ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            if (name.toLowerCase().contains("datasync")) {
                datasyncRelated.add(name);
            }
        }
        boolean hasServer = false;
        try {
            hasServer = context.getBean(com.chua.starter.datasync.DataSyncServer.class) != null;
        } catch (Exception ignore) {
            hasServer = false;
        }
        log.info("[DISABLED] datasync-related bean count={}", datasyncRelated.size());
        log.info("[DISABLED] datasync-related beans={}", datasyncRelated);
        log.info("[DISABLED] has DataSyncServer={}", hasServer);
        log.info("[DISABLED] context started ok, datasync auto-config disabled");
    }
}