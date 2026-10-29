package com.chua.starter.monitor.support.datasync.loader;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * PostgreSQL CSV 快速导入实现（{@code COPY ... FROM STDIN}）。
 *
 * <p>用 pgjdbc 的 {@link CopyManager#copyIn} 把规范 CSV 以 {@code CSV} 格式流式灌入目标表：
 * PG 的 COPY 是服务端原生批量装载，比逐行 insert 快一个数量级，且事务内一致。</p>
 *
 * <p>规范 CSV 是「首行表头 + 逗号分隔 + 双引号转义」，与 COPY 的
 * {@code FORMAT csv, HEADER true} 直接对应，无需额外转换。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Spi("postgresql")
public class PostgresqlCsvLoader implements JdbcCsvLoader {

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public boolean supports(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    @Override
    public long load(DatasourceJdbcInfo target, String targetTable, List<String> columns, Path csvFile, String encoding)
            throws Exception {
        Properties props = new Properties();
        props.setProperty("user", target.username() == null ? "" : target.username());
        props.setProperty("password", target.password() == null ? "" : target.password());
        String columnList = String.join(", ", columns);
        String copySql = "COPY " + targetTable + " (" + columnList + ") FROM STDIN WITH (FORMAT csv, HEADER true)";
        log.info("[bulk-import] PostgreSQL COPY 直灌表 {}，文件 {}，列 {} 个", targetTable, csvFile, columns.size());
        try (Connection connection = DriverManager.getConnection(target.url(), props)) {
            CopyManager copyManager = new CopyManager((BaseConnection) connection);
            try (Reader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
                return copyManager.copyIn(copySql, reader);
            }
        }
    }
}