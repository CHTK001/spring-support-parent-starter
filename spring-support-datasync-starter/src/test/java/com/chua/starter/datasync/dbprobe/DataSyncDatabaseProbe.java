// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.File;

/**
 * 数据库模式探针 (H2 代替外网 MySQL, 验证 databasemapping加载 加载链路)。
 *
 * <p>与 {@link com.chua.starter.datasync.support.DataSyncApplication} 的区别:
 * 数据同步application 的 database 模式
 * 硬编码外网 MySQL (172.16.0.40), 探针改用嵌入式 H2 + {@code schema.sql}
 * (预置 db-用户-同步 映射 + 用户-sink 字段映射), 使数据库加载链路可在无外网环境
 * 真实验证: H2 建表 → databasemapping加载 查 datasync_mapping → 加载到 管理器 →
 * 定时调度真实执行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
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
        log.info("[DB-PROBE] 已加载映射数={}", mappingManager.getMappings().size());
        mappingManager.getMappings().forEach(m -> log.info("[DB-PROBE] 映射: id={}, source={}, sink={}, batch={}, cron={}, params={}, mappings={}",
                m.mappingId(), m.sourceId(), m.sinkId(), m.batch(), m.cron(), m.params(), m.mappings()));

 // 启动 数据同步服务端 + 注册 CSV 源/sink, 使 db-用户-同步 定时真实执行
        DefaultDataSyncServer server = context.getBean(DefaultDataSyncServer.class);
        File outDir = new File("target/datasync-out-db");
        if (!outDir.exists() && !outDir.mkdirs()) {
            log.warn("[DB-PROBE] 创建输出目录失败");
        }
        server.registerSource(new DelimitedDataSyncAgentSource(
                "user-source", "user-input",
                "src/main/resources/datasync/samples/user-source.csv", ","));
        server.registerSink(new CsvDataSyncAgentSink(
                "user-sink", "target/datasync-out-db/user-sink.csv"));
        server.start();
        log.info("[DB-PROBE] DataSyncServer 启动成功, 等待定时调度执行");

        // 等待调度执行 6 秒后退出 (cron=0/5 理论 1~2 轮)
        try {
            Thread.sleep(6000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        server.stop();
    }

    /**
     * 配置嵌入式 H2 数据源 (模式.SQL 建 datasync_mapping / datasync_字段_mapping 两表并预置数据)。
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
    }}