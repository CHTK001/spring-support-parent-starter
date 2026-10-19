package com.chua.starter.monitor.support.tshark;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Locale;

/**
 * tshark 表结构兜底升级。
 *
 * <p>项目用 {@code spring.sql.init} 跑 {@code CREATE TABLE IF NOT EXISTS}，对已经存在的
 * 表不会补列。抓包异常状态与磁盘保护新增了 {@code last_error / file_size / max_size}，
 * 显示过滤的 {@code frame.number} 新增了 {@code frame_no}。这里在启动时按列是否存在
 * 补一次 DDL，避免要求部署方手工改表。</p>
 *
 * <p>只在缺列时执行，且失败只记日志：表结构已经正确时是零成本；DDL 权限受限等场景
 * 也不能影响主流程启动。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
public class TsharkSchemaGuard {

    private final JdbcTemplate jdbcTemplate;

    public TsharkSchemaGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureColumns() {
        addIfMissing("ms_tshark_session", "ms_tshark_session_last_error", "VARCHAR(2000) NULL");
        addIfMissing("ms_tshark_session", "ms_tshark_session_file_size", "BIGINT DEFAULT 0");
        addIfMissing("ms_tshark_session", "ms_tshark_session_max_size", "INT DEFAULT 0");
        addIfMissing("ms_tshark_packet", "ms_tshark_packet_frame_no", "BIGINT NULL");
    }

    private void addIfMissing(String table, String column, String ddl) {
        if (hasColumn(table, column)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
            log.info("tshark schema upgraded: {}.{}", table, column);
        } catch (Exception e) {
            // 并行启动 / 无 DDL 权限时，列可能已经由别处补上；只记录，不影响启动
            log.debug("add tshark column skipped, {}.{} reason={}", table, column, e.getMessage());
        }
    }

    private boolean hasColumn(String table, String column) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (column.equalsIgnoreCase(meta.getColumnName(i))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // 表还不存在（首次启动时初始化脚本尚未跑完）时按缺失处理，下次启动会补
            log.debug("check tshark column failed, {}.{} reason={}", table, column, e.getMessage());
            return false;
        }
    }

    /** 供单元测试断言用：不区分大小写的列名比较。 */
    static boolean same(String a, String b) {
        return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}
