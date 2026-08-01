package com.chua.starter.datasync.support;

import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.configuration.DataSyncAutoConfiguration;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner;
import com.chua.starter.datasource.support.DataSourceInspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * 数据同步模块启动类（真实启动测试入口）。
 *
 * <p>通过参数选择映射来源：</p>
 * <ul>
 *   <li><code>--datasync.mode=file</code>（默认）：从文件目录加载映射</li>
 *   <li><code>--datasync.mode=database</code>：从数据库表加载映射</li>
 * </ul>
 *
 * <p>启动嵌入式 H2 并加载表结构，验证映射加载与 DataSyncServer 启动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, DataSourceInspect.class})
@Import(DataSyncAutoConfiguration.class)
public class DataSyncApplication {

    /**
     * 映射配置文件目录（main resources 下）
     */
    private static final String MAPPING_DIR = "./src/main/resources/datasync/mappings";

    /**
     * 数据库模式参数值
     */
    private static final String MODE_DATABASE = "database";

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 解析模式参数（优先系统属性，其次命令行 --datasync.mode=）
        String mode = System.getProperty("datasync.mode", "file");
        for (String arg : args) {
            if (arg != null && arg.startsWith("--datasync.mode=")) {
                mode = arg.substring("--datasync.mode=".length());
            }
        }
        boolean databaseMode = MODE_DATABASE.equalsIgnoreCase(mode);

        SpringApplication application = new SpringApplication(DataSyncApplication.class);
        // 按模式设置默认属性
        java.util.Map<String, Object> defaults = new java.util.HashMap<>();
        if (databaseMode) {
            defaults.put("plugin.datasync.mapping-source", "database");
            defaults.put("plugin.datasync.database-mapping.enabled", "true");
            defaults.put("plugin.datasync.database-mapping.table-prefix", "datasync_");
        } else {
            defaults.put("plugin.datasync.mapping-source", "file");
            defaults.put("plugin.datasync.file-mapping.directory-path", MAPPING_DIR);
        }
        application.setDefaultProperties(defaults);
        ApplicationContext context = application.run(args);

        // 打印配置
        DatasyncProperties properties = context.getBean(DatasyncProperties.class);
        System.out.println("[DATA-SYNC] enabled=" + properties.isEnabled());
        System.out.println("[DATA-SYNC] mapping-source=" + properties.getMappingSource());
        System.out.println("[DATA-SYNC] file-mapping.directory-path=" + properties.getFileMapping().getDirectoryPath());
        System.out.println("[DATA-SYNC] database-mapping.enabled=" + properties.getDatabaseMapping().isEnabled());
        System.out.println("[DATA-SYNC] database-mapping.table-prefix=" + properties.getDatabaseMapping().getTablePrefix());

        // 文件模式显式触发映射扫描
        if (!databaseMode) {
            DirectoryDataSyncMappingScanner scanner = context.getBean(DirectoryDataSyncMappingScanner.class);
            scanner.start();
        }

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
     * <p>启动类已排除 {@link DataSourceInspect}（datasource-starter 动态数据源注册器），
     * 本数据源即为上下文唯一 DataSource，{@code DatabaseMappingLoader} 直接注入本数据源
     * 查询 schema.sql 建好的映射表。</p>
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
