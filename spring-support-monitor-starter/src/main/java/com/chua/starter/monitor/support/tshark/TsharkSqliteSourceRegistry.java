package com.chua.starter.monitor.support.tshark;

import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.support.DataSourceGroupRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抓包报文 SQLite 数据源注册表。
 *
 * <p>每个抓包会话一个 {@code .db} 文件，文件与 pcap 同目录同名（去 .pcap 后缀）。
 * 会话主记录仍留在 MySQL（master），报文不再写进 MySQL 的 {@code ms_tshark_packet}
 * 单表；报文 mapper 在调用点临时切到 {@code tshark-<sessionId>} 数据源。</p>
 *
 * <p>数据源键名刻意用连字符：{@link DataSourceGroupRegistry#extractGroupName} 只认
 * {@code _+数字} 后缀，{@code tshark-123} 不会被误分组。关闭时必须用本类自留的原生
 * Hikari 实例，{@link DataSourceContextSupport#DATA_SOURCE_MAP} 里存的是 SQL 监控
 * 启用时包装过的实例，直接 close 包装对象并不能释放连接池。</p>
 */
@Slf4j
@Component
public class TsharkSqliteSourceRegistry {

    /**
     * 数据源键前缀。
     */
    private static final String DS_PREFIX = "tshark-";

    private static final String DDL = "CREATE TABLE IF NOT EXISTS ms_tshark_packet (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "ms_tshark_packet_session_id INTEGER NOT NULL," +
            "ms_tshark_packet_time TEXT," +
            "ms_tshark_packet_frame_no INTEGER," +
            "ms_tshark_packet_source_ip TEXT," +
            "ms_tshark_packet_dest_ip TEXT," +
            "ms_tshark_packet_source_port INTEGER," +
            "ms_tshark_packet_dest_port INTEGER," +
            "ms_tshark_packet_protocol TEXT," +
            "ms_tshark_packet_length INTEGER," +
            "ms_tshark_packet_info TEXT," +
            "ms_tshark_packet_raw TEXT," +
            "ms_tshark_packet_restored TEXT," +
            "create_name TEXT," +
            "create_by INTEGER," +
            "create_time TEXT," +
            "update_name TEXT," +
            "update_by INTEGER," +
            "update_time TEXT)";

    private static final String INDEX_SESSION =
            "CREATE INDEX IF NOT EXISTS idx_tshark_packet_session ON ms_tshark_packet" +
                    "(ms_tshark_packet_session_id, id)";
    private static final String INDEX_PROTOCOL =
            "CREATE INDEX IF NOT EXISTS idx_tshark_packet_protocol ON ms_tshark_packet" +
                    "(ms_tshark_packet_protocol)";

    /**
     * 会话 id → 原生 Hikari 池。
     */
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    /**
     * 会话 id → sqlite 文件路径。
     */
    private final Map<Long, Path> dbPaths = new ConcurrentHashMap<>();

    /**
     * 数据源键名。
     *
     * @param sessionId 会话标识
     * @return 注册表内的数据源名称
     */
    public String dbName(Long sessionId) {
        return DS_PREFIX + sessionId;
    }

    /**
     * 由 pcap 路径推导同目录同名 sqlite 文件。
     *
     * @param pcapPath pcap 路径
     * @return 对应 .db 路径
     */
    public static Path dbPathForPcap(Path pcapPath) {
        String name = pcapPath.getFileName().toString();
        String dbName = name.replaceAll("\\.[^.]*$", "") + ".db";
        return pcapPath.resolveSibling(dbName);
    }

    /**
     * 幂等确保：建立连接池、注册动态数据源并建表。
     *
     * @param sessionId 会话标识
     * @param pcapPath  pcap 文件路径
     */
    public void ensure(Long sessionId, Path pcapPath) {
        if (pools.containsKey(sessionId)) {
            return;
        }
        synchronized (this) {
            if (pools.containsKey(sessionId)) {
                return;
            }
            Path dbPath = dbPathForPcap(pcapPath);
            try {
                if (dbPath.getParent() != null) {
                    Files.createDirectories(dbPath.getParent());
                }
                HikariDataSource ds = new HikariDataSource(buildConfig(sessionId, dbPath));
                try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                    st.execute(DDL);
                    st.execute(INDEX_SESSION);
                    st.execute(INDEX_PROTOCOL);
                }
                DataSourceContextSupport.addDatasource(dbName(sessionId), ds);
                pools.put(sessionId, ds);
                dbPaths.put(sessionId, dbPath);
                log.info("[tshark] SQLite 报文库就绪 sessionId={} db={}", sessionId, dbPath);
            } catch (Exception e) {
                log.error("[tshark] SQLite 报文库初始化失败 sessionId={} db={}", sessionId, dbPath, e);
                throw new IllegalStateException("初始化会话报文库失败: " + sessionId, e);
            }
        }
    }

    private static HikariConfig buildConfig(Long sessionId, Path dbPath) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(DS_PREFIX + sessionId);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath().toString().replace('\\', '/'));
        config.setMaximumPoolSize(5);
        config.setConnectionTestQuery("SELECT 1");
        return config;
    }

    /**
     * 是否已为该会话创建过 sqlite 数据源。
     *
     * @param sessionId 会话标识
     * @return 是否已注册
     */
    public boolean has(Long sessionId) {
        return pools.containsKey(sessionId);
    }

    /**
     * 关闭并移除数据源，返回库文件路径。
     *
     * @param sessionId 会话标识
     * @return sqlite 文件路径；本次运行未创建过时返回 null
     */
    public Path close(Long sessionId) {
        HikariDataSource ds = pools.remove(sessionId);
        Path dbPath = dbPaths.remove(sessionId);
        if (ds != null) {
            DataSourceContextSupport.DATA_SOURCE_MAP.remove(dbName(sessionId));
            try {
                ds.close();
            } catch (Exception e) {
                log.warn("[tshark] 关闭 SQLite 连接池失败 sessionId={}", sessionId, e);
            }
        }
        return dbPath;
    }
}