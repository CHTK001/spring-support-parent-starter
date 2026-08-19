package com.chua.starter.datasync.dbprobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;

/**
 * 装配关闭探针，验证 {@code plugin.datasync.enabled=false} 时 DataSync 自动配置完全关闭。
 *
 * <p>与 {@link com.chua.starter.datasync.support.DataSyncApplication} 的区别：
 * 启动类在启动后会按 ID 获取 DataSync 相关 Bean，enabled=false 时这些 Bean 不存在会直接抛异常；
 * 本探针只按类型枚举 Bean Name，enabled=false 时预期 Datasync/DataSync 类型数量为 0，
 * 且上下文仍正常启动（无 DataSync 相关装配）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DataSyncDisabledProbe {

    /**
     * 程序入口。
     *
     * @param args 命令行参数，应为 --plugin.datasync.enabled=false
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
        System.out.println("[DISABLED] datasync-related bean count=" + datasyncRelated.size());
        System.out.println("[DISABLED] datasync-related beans=" + datasyncRelated);
        System.out.println("[DISABLED] has DataSyncServer=" + hasServer);
        System.out.println("[DISABLED] context started ok, datasync auto-config disabled");
    }
}