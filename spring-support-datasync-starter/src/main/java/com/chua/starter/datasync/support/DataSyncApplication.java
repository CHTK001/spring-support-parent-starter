package com.chua.starter.datasync.support;

import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * 数据同步模块启动类。
 *
 * <p>用于 DataSync 集成测试：启动嵌入式 H2 并加载表结构，
 * 分别验证文件模式与数据库模式下的映射加载与 DataSyncServer 启动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(
        scanBasePackages = "com.chua.starter.datasync.support",
        exclude = {DataSourceAutoConfiguration.class})
public class DataSyncApplication {
    /**
     * 映射配置文件目录（main resources 下）
     */
    private static final String MAPPING_DIR = "./src/main/resources/datasync/mappings";

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) throws Exception {
        SpringApplication application = new SpringApplication(DataSyncApplication.class);
        // 强制使用 main 资源下的映射目录，避免被 test 配置覆盖
        application.setDefaultProperties(java.util.Map.of(
                "plugin.datasync.file-mapping.directory-path", MAPPING_DIR));
        ApplicationContext context = application.run(args);

        // 打印配置
        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[DATA-SYNC] enabled=" + properties.isEnabled());
        System.out.println("[DATA-SYNC] mapping-source=" + properties.getMappingSource());
        System.out.println("[DATA-SYNC] file-mapping.directory-path=" + properties.getFileMapping().getDirectoryPath());
        System.out.println("[DATA-SYNC] database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());

        // 显式触发文件映射扫描
        DirectoryDataSyncMappingScanner scanner = context.getBean(DirectoryDataSyncMappingScanner.class);
        scanner.start();

        // 打印已加载映射
        DataSyncMappingManager mappingManager = context.getBean(DataSyncMappingManager.class);
        System.out.println("[DATA-SYNC] 已加载映射数=" + mappingManager.getMappings().size());
        mappingManager.getMappings().forEach(m -> System.out.println("[DATA-SYNC] 映射: id=" + m.mappingId()
                + ", source=" + m.sourceId()
                + ", sink=" + m.sinkId()
                + ", mappings=" + m.mappings()));

        // 启动 DataSyncServer
        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        server.start();
        System.out.println("[DATA-SYNC] DataSyncServer 启动成功");
    }

    /**
     * 配置嵌入式 H2 数据源并加载 schema 脚本。
     *
     * @return H2 嵌入式数据源
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("datasync-testdb")
                .addScript("classpath:schema.sql")
                .build();
    }
}
