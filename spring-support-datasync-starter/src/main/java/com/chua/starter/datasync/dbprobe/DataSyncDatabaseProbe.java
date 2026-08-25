package com.chua.starter.datasync.dbprobe;

import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.datasync.agent.support.sink.CsvDataSyncAgentSink;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.configuration.DataSyncAutoConfiguration;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasource.support.DataSourceInspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.io.File;

/**
 * 数据库模式探针 (H2 代替外网 MySQL, 验证 DatabaseMappingLoader 加载链路)。
 *
 * <p>与 {@link com.chua.starter.datasync.support.DataSyncApplication} 的区别:
 * DataSyncApplication 的 database 模式
 * 硬编码外网 MySQL (172.16.0.40), 探针改用嵌入式 H2 + {@code schema.sql}
 * (预置 db-user-sync 映射 + user-sink 字段映射), 使数据库加载链路可在无外网环境
 * 真实验证: H2 建表 → DatabaseMappingLoader 查 datasync_mapping → 加载到 manager →
 * 定时调度真实执行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, DataSourceInspect.class})
@Import(DataSyncAutoConfiguration.class)
public class DataSyncDatabaseProbe {

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 强制 database 模式 + 启用数据库映射源
        java.util.Map<String, Object> defaults = new java.util.HashMap<>();
        defaults.put("plugin.datasync.mapping-source", "database");
        defaults.put("plugin.datasync.database-mapping.enabled", "true");
        defaults.put("plugin.datasync.database-mapping.table-prefix", "datasync_");
        defaults.put("spring.main.banner-mode", "off");
        defaults.put("server.port", "0");

        SpringApplication application = new SpringApplication(DataSyncDatabaseProbe.class);
        application.setDefaultProperties(defaults);
        ApplicationContext context = application.run(args);

        // 打印加载结果
        DataSyncMappingManager mappingManager = context.getBean(DataSyncMappingManager.class);
        System.out.println("[DB-PROBE] 已加载映射数=" + mappingManager.getMappings().size());
        mappingManager.getMappings().forEach(m -> System.out.println("[DB-PROBE] 映射: id=" + m.mappingId()
                + ", source=" + m.sourceId()
                + ", sink=" + m.sinkId()
                + ", batch=" + m.batch()
                + ", cron=" + m.cron()
                + ", params=" + m.params()
                + ", mappings=" + m.mappings()));

        // 启动 DataSyncServer + 注册 CSV source/sink, 使 db-user-sync 定时真实执行
        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        File outDir = new File("target/datasync-out-db");
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.out.println("[DB-PROBE] 创建输出目录失败");
        }
        server.registerSource(new DelimitedDataSyncAgentSource(
                "user-source", "user-input",
                "src/main/resources/datasync/samples/user-source.csv", ","));
        server.registerSink(new CsvDataSyncAgentSink(
                "user-sink", "target/datasync-out-db/user-sink.csv"));
        server.start();
        System.out.println("[DB-PROBE] DataSyncServer 启动成功, 等待定时调度执行");

        // 等待调度执行 6 秒后退出 (cron=0/5 理论 1~2 轮)
        try { Thread.sleep(6000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        server.stop();
        System.exit(0);
    }

    /**
     * 配置嵌入式 H2 数据源 (schema.sql 建 datasync_mapping / datasync_field_mapping 两表并预置数据)。
     *
     * @return 数据源
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("datasync-db-probe")
                .addScript("classpath:schema.sql")
                .build();
    }
}