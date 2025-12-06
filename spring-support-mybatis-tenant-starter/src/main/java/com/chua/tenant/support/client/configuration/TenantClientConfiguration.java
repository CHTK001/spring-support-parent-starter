package com.chua.tenant.support.client.configuration;

import com.chua.tenant.support.common.properties.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * 租户客户端配置
 * <p>
 * 客户端模式下，自动检测并添加租户字段到数据库表
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = TenantProperties.PRE + ".client", name = "enable", havingValue = "true")
public class TenantClientConfiguration {

    private final TenantProperties tenantProperties;
    private final DataSource dataSource;

    /**
     * 初始化时自动添加租户字段
     */
    @PostConstruct
    public void init() {
        if (!tenantProperties.getClient().isAutoAddColumn()) {
            log.info("[租户客户端] 自动添加租户字段功能已禁用");
            return;
        }

        log.info("[租户客户端] 开始检测数据库表，自动添加租户字段...");
        autoAddTenantColumn();
    }

    /**
     * 自动为数据库表添加租户字段
     */
    private void autoAddTenantColumn() {
        String tenantIdColumn = tenantProperties.getClient().getTenantIdColumn();
        Set<String> ignoreTable = tenantProperties.getClient().getIgnoreTable();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();

            // 获取所有表
            Set<String> tables = getAllTables(metaData, catalog);
            log.info("[租户客户端] 检测到 {} 张表", tables.size());

            int addedCount = 0;
            for (String tableName : tables) {
                // 跳过忽略的表
                if (ignoreTable.contains(tableName)) {
                    log.debug("[租户客户端] 跳过忽略表: {}", tableName);
                    continue;
                }

                // 检查是否已有租户字段
                if (hasColumn(metaData, catalog, tableName, tenantIdColumn)) {
                    log.debug("[租户客户端] 表 {} 已有租户字段", tableName);
                    continue;
                }

                // 添加租户字段
                addTenantColumn(connection, tableName, tenantIdColumn);
                addedCount++;
            }

            log.info("[租户客户端] 租户字段添加完成，共处理 {} 张表", addedCount);

        } catch (Exception e) {
            log.error("[租户客户端] 自动添加租户字段失败", e);
        }
    }

    /**
     * 获取所有表名
     */
    private Set<String> getAllTables(DatabaseMetaData metaData, String catalog) throws Exception {
        Set<String> tables = new HashSet<>();
        try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    /**
     * 检查表是否有指定列
     */
    private boolean hasColumn(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getColumns(catalog, null, tableName, columnName)) {
            return rs.next();
        }
    }

    /**
     * 添加租户字段到表
     */
    private void addTenantColumn(Connection connection, String tableName, String columnName) {
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s INT DEFAULT 0 COMMENT '租户ID'", tableName, columnName);
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            log.info("[租户客户端] 表 {} 添加租户字段成功", tableName);
        } catch (Exception e) {
            log.warn("[租户客户端] 表 {} 添加租户字段失败: {}", tableName, e.getMessage());
        }
    }
}
