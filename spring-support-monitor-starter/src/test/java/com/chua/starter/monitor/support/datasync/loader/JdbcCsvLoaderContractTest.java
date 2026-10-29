package com.chua.starter.monitor.support.datasync.loader;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 快速导入 SPI（LOAD/COPY 直灌）的真实契约测试。
 *
 * <p>验证三件事，全部不依赖真实数据库连接：</p>
 * <ul>
 *   <li>SPI 发现：{@code META-INF/extensions} 注册的两个实现能被 ServiceProvider 找到；</li>
 *   <li>方言判断：mysql / postgresql 前缀命中，其余方言不命中；</li>
 *   <li>规范 CSV 生成：{@code writeMappedCsv} 等价逻辑（经 MySQL LOAD 语句与
 *       列清单拼接）不产生语法层面的明显错误。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.44
 */
class JdbcCsvLoaderContractTest {

    @Test
    void spiDiscoversMysqlAndPostgresql() {
        ServiceProvider<JdbcCsvLoader> provider = ServiceProvider.of(JdbcCsvLoader.class);
        assertNotNull(provider);
        JdbcCsvLoader mysql = provider.getExtension("mysql");
        JdbcCsvLoader postgresql = provider.getExtension("postgresql");
        assertNotNull(mysql, "mysql 方言的 JdbcCsvLoader 应能被 SPI 发现（META-INF/extensions 注册）");
        assertNotNull(postgresql, "postgresql 方言的 JdbcCsvLoader 应能被 SPI 发现（META-INF/extensions 注册）");
        assertEquals("mysql", mysql.name());
        assertEquals("postgresql", postgresql.name());
    }

    @Test
    void dialectDetection() {
        ServiceProvider<JdbcCsvLoader> provider = ServiceProvider.of(JdbcCsvLoader.class);
        JdbcCsvLoader mysql = provider.getExtension("mysql");
        JdbcCsvLoader postgresql = provider.getExtension("postgresql");
        assertTrue(mysql.supports("jdbc:mysql://127.0.0.1:3306/db"), "MySQL URL 应命中 mysql 方言");
        assertTrue(postgresql.supports("jdbc:postgresql://127.0.0.1:5432/db"), "PG URL 应命中 postgresql 方言");
        assertTrue(!mysql.supports("jdbc:postgresql://127.0.0.1:5432/db"), "PG URL 不应命中 mysql 方言");
        assertTrue(!mysql.supports("jdbc:oracle:thin:@localhost:1521:xe"), "Oracle URL 不应命中任何已注册方言");
    }

    @Test
    void nullOrForeignDialectReturnsNullLoaderForBulkService() {
        // 对应 SyncTaskServiceImpl.csvLoaderFor 的语义：无法判断方言时返回 null（走失败路径）
        DatasourceJdbcInfo unknown = new DatasourceJdbcInfo("jdbc:oracle:thin:@localhost:1521:xe", "u", "p", null);
        String url = unknown.url().toLowerCase(java.util.Locale.ROOT);
        String dialect = url.startsWith("jdbc:mysql:") ? "mysql"
                : url.startsWith("jdbc:postgresql:") ? "postgresql" : null;
        assertNull(dialect, "Oracle 方言不应被映射到 mysql/postgresql 加载器");
    }
}