package com.chua.starter.datasync.support;

import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.datasync.agent.support.source.JdbcDataSyncAgentSource;
import com.chua.datasync.agent.support.sink.CsvDataSyncAgentSink;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.configuration.DataSyncAutoConfiguration;
import com.chua.starter.datasync.configuration.DatasyncProperties;
import com.chua.starter.datasync.mapping.DataSyncMappingManager;
import com.chua.starter.datasync.scanner.DirectoryDataSyncMappingScanner;
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

/**
 * 数据同步模块启动类（真实启动测试入口）。
 *
 * <p>通过参数选择映射来源：</p>
 * <ul>
 *   <li><code>--datasync.mode=file</code>（默认）：从文件目录加载映射</li>
 *   <li><code>--datasync.mode=database</code>：从数据库表加载映射（默认嵌入 H2，
 *       加 <code>--datasync.db=mysql</code> 连接真实 MySQL）</li>
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
     * 数据库类型参数值（MySQL 显式指定；默认嵌入式 H2 便于本地真实启动验证）
     */
    private static final String DB_MYSQL = "mysql";

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 解析模式参数（优先系统属性，其次命令行 --datasync.mode=）
        String mode = System.getProperty("datasync.mode", "file");
        // 解析数据库类型参数（--datasync.db=mysql 时连真实 MySQL；默认 H2）
        String dbType = System.getProperty("datasync.db", "h2");
        for (String arg : args) {
            if (arg != null && arg.startsWith("--datasync.mode=")) {
                mode = arg.substring("--datasync.mode=".length());
            }
            if (arg != null && arg.startsWith("--datasync.db=")) {
                dbType = arg.substring("--datasync.db=".length());
            }
        }
        boolean databaseMode = MODE_DATABASE.equalsIgnoreCase(mode);
        boolean useMysql = DB_MYSQL.equalsIgnoreCase(dbType);

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
        // 启用 SSH 服务（端口 2222，admin/admin）
        defaults.put("server.port", "28978");
        defaults.put("plugin.ssh.server.enabled", "true");
        defaults.put("plugin.ssh.server.port", "2222");
        defaults.put("plugin.ssh.server.host", "127.0.0.1");
        defaults.put("plugin.ssh.server.authentication.username", "admin");
        defaults.put("plugin.ssh.server.authentication.password", "admin");
        defaults.put("plugin.ssh.server.authentication.user-password", "admin");
        defaults.put("plugin.ssh.server.authentication.password-enabled", "true");
        defaults.put("plugin.ssh.server.file-transfer.sftp-enabled", "true");
        defaults.put("plugin.ssh.server.file-transfer.root-directory", "./");
        // OSGi shell 远程管理（端口 6666）
        defaults.put("plugin.osgi.shell.enabled", "true");
        defaults.put("plugin.osgi.shell.ssh-port", "6666");
        defaults.put("plugin.osgi.shell.ssh-enabled", "true");
        defaults.put("plugin.osgi.shell.username", "admin");
        defaults.put("plugin.osgi.shell.password", "admin");
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

        // 注册测试 Source/Sink，使定时调度真正执行数据同步
        registerSampleSourcesAndSinks(server, databaseMode, useMysql);
        System.out.println("[DATA-SYNC] 已注册测试 Source/Sink，等待定时调度执行");
        System.out.println("[DATA-SYNC] DataSyncServer 启动成功");
    }

    /**
     * 注册示例 Source/Sink。
     *
     * <p>文件模式：Source 从 {@code datasync/samples} 目录的 CSV 文件读取数据，
     * Sink 将转换后的数据写入 {@code target/datasync-out} 目录。
     * 数据库模式：MySQL 时 Source 从 {@code test_user} 表读取数据；
     * 默认（嵌入式 H2）时 Source 同样从 CSV 读取，Sink 写入 {@code target/datasync-out-db}。</p>
     *
     * @param server       数据同步服务器
     * @param databaseMode 是否数据库模式
     * @param useMysql     是否使用真实 MySQL（false 时数据库模式走嵌入式 H2）
     */
    private static void registerSampleSourcesAndSinks(DefaultDataSyncServer server, boolean databaseMode, boolean useMysql) {
        // 确保 Sink 输出目录存在（DelimitedDataSyncAgentSink 不会自动创建父目录）
        java.io.File outDir = new java.io.File("target/datasync-out");
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.out.println("[DATA-SYNC] 创建输出目录失败: " + outDir.getAbsolutePath());
        }
        java.io.File outDirDb = new java.io.File("target/datasync-out-db");
        if (!outDirDb.exists() && !outDirDb.mkdirs()) {
            System.out.println("[DATA-SYNC] 创建输出目录失败: " + outDirDb.getAbsolutePath());
        }

        if (databaseMode && useMysql) {
            // user-sync: source=user-source -> sink=user-sink（从 MySQL 读取）
            server.registerSource(new JdbcDataSyncAgentSource(
                    "user-source", "user-input",
                    "jdbc:mysql://172.16.0.40:3306/datasync_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                    "root", "root@",
                    "SELECT name, age FROM test_user", "name", "age"));
        } else if (databaseMode) {
            // 数据库模式（嵌入式 H2）：db-user-sync 映射 source=user-source -> sink=user-sink
            // Source 从 CSV 读取，Sink 写入 target/datasync-out-db
            server.registerSource(new DelimitedDataSyncAgentSource(
                    "user-source", "user-input",
                    "src/main/resources/datasync/samples/user-source.csv", ","));
        } else {
            // user-sync: source=user-source -> sink=user-sink
            server.registerSource(new DelimitedDataSyncAgentSource(
                    "user-source", "user-input",
                    "src/main/resources/datasync/samples/user-source.csv", ","));

            // order-sync: source=order-source -> sink=order-sink
            server.registerSource(new DelimitedDataSyncAgentSource(
                    "order-source", "order-input",
                    "src/main/resources/datasync/samples/order-source.csv", ","));
            server.registerSink(new CsvDataSyncAgentSink(
                    "order-sink", "target/datasync-out/order-sink.csv"));
        }

        server.registerSink(new CsvDataSyncAgentSink(
                "user-sink", databaseMode ? "target/datasync-out-db/user-sink.csv" : "target/datasync-out/user-sink.csv"));
    }

    /**
     * 配置数据源。
     *
     * <p>启动类已排除 {@link DataSourceInspect}（datasource-starter 动态数据源注册器），
     * 本数据源即为上下文唯一 DataSource，{@code DatabaseMappingLoader} 直接注入本数据源
     * 查询映射表。</p>
     *
     * <p>数据库模式默认使用嵌入式 H2（schema.sql 建表 + 预置 db-user-sync），保证无外网
     * 也能真实启动验证数据库加载链路；仅当显式传入 {@code --datasync.db=mysql} 时才连接
     * 真实 MySQL（172.16.0.40，库 datasync_test）。文件模式同样使用嵌入式 H2。</p>
     *
     * @return 数据源
     */
    @Bean
    public DataSource dataSource() {
        String mode = System.getProperty("datasync.mode", "file");
        String dbType = System.getProperty("datasync.db", "h2");
        if (MODE_DATABASE.equalsIgnoreCase(mode) && DB_MYSQL.equalsIgnoreCase(dbType)) {
            com.zaxxer.hikari.HikariDataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
            dataSource.setJdbcUrl("jdbc:mysql://172.16.0.40:3306/datasync_test"
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
            dataSource.setUsername("root");
            dataSource.setPassword("root@");
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setMaximumPoolSize(5);
            dataSource.setMinimumIdle(1);
            return dataSource;
        }
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("datasync-testdb")
                .addScript("classpath:schema.sql")
                .build();
    }
}
