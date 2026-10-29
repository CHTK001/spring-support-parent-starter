package com.chua.starter.monitor.support.datasync.loader;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * MySQL CSV 快速导入实现（{@code LOAD DATA LOCAL INFILE}）。
 *
 * <p>把规范 CSV 通过 MySQL 的原生 LOAD 语句直灌目标表：文件在服务器本地，
 * 用 {@code LOCAL} 关键字让驱动把文件内容经连接流送进服务端，绕开
 * {@code secure_file_priv} 对「服务端读文件路径」的限制，同时避免逐行 insert
 * 的开销——百万行场景下 LOAD 比逐行快一个数量级。</p>
 *
 * <p>目标 JDBC URL 若未带 {@code allowLoadLocalInfile=true}，这里在连接前
 * 把它补上（MySQL 8 驱动默认禁 LOCAL 读取）；口令等其余参数原样保留。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Spi("mysql")
public class MysqlCsvLoader implements JdbcCsvLoader {

    /**
     * LOAD 语句里的转义规则：FIELDS TERMINATED BY ',' ENCLOSED BY '"' ESCAPED BY '\\'
     * 与 {@code CsvWriterSupport} 产出的规范 CSV 完全一致（首行表头用 IGNORE 1 LINES 跳过）。
     */
    private static final String FIELDS_CLAUSE =
            "FIELDS TERMINATED BY ',' ENCLOSED BY '\"' ESCAPED BY '\\\\' LINES TERMINATED BY '\\n'";

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:");
    }

    @Override
    public long load(DatasourceJdbcInfo target, String targetTable, List<String> columns, Path csvFile, String encoding)
            throws Exception {
        String url = ensureLocalInfile(target.url());
        Properties props = new Properties();
        props.setProperty("user", target.username() == null ? "" : target.username());
        props.setProperty("password", target.password() == null ? "" : target.password());
        // 统一按 UTF-8 读取（规范 CSV 是 UTF-8 无 BOM）
        props.setProperty("characterEncoding", "UTF-8");
        props.setProperty("allowLoadLocalInfile", "true");
        String table = quote(targetTable);
        String columnList = String.join(", ", columns.stream().map(MysqlCsvLoader::quote).toList());
        String sql = "LOAD DATA LOCAL INFILE '" + csvFile.toString().replace("'", "\\'") + "' "
                + "INTO TABLE " + table + " " + FIELDS_CLAUSE + " IGNORE 1 LINES (" + columnList + ")";
        log.info("[bulk-import] MySQL LOAD 直灌 {} 表 {}，文件 {}，列 {} 个", table, targetTable, csvFile, columns.size());
        try (Connection connection = DriverManager.getConnection(url, props);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return statement.getUpdateCount();
        }
    }

    /**
     * 在 JDBC URL 上补 allowLoadLocalInfile=true（若未显式设置）。
     *
     * @param url 原始 JDBC URL
     * @return 确保 LOCAL INFILE 可用的 URL
     */
    private static String ensureLocalInfile(String url) {
        if (url == null || url.contains("allowLoadLocalInfile")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "allowLoadLocalInfile=true";
    }

    /**
     * 表名/列名加反引号（MySQL 方言）。
     *
     * @param name 名称
     * @return 反引号包裹
     */
    private static String quote(String name) {
        return "`" + name.replace("`", "``") + "`";
    }
}
