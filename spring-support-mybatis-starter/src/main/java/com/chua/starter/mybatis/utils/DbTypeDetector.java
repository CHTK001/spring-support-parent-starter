package com.chua.starter.mybatis.utils;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 数据库方言探测工具类。
 *
 * <p>国产数据库（达梦、人大金仓、南大通用、华为高斯、OceanBase、openGauss、神通等）
 * 的驱动产品名或 JDBC URL 前缀各不相同，MyBatis Plus 的 {@link DbType#getDbType(String)}
 * 对部分国产数据库支持不完整。本工具类提供两阶段探测：</p>
 * <ul>
 *   <li>优先读取数据源配置的 JDBC URL（自动剥离 p6spy 等代理包装前缀）</li>
 *   <li>回退到 {@link DatabaseMetaData#getDatabaseProductName()} 的产品名匹配</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DbTypeDetector {

    /**
     * 数据库产品名关键字到 MyBatis Plus 数据库类型的映射
     */
    private static final Map<String, DbType> PRODUCT_NAME_MAPPINGS = new LinkedHashMap<>();

    /**
     * JDBC URL 关键字到 MyBatis Plus 数据库类型的映射
     */
    private static final Map<String, DbType> URL_MAPPINGS = new LinkedHashMap<>();

    static {
        registerProductNameMapping("mysql", DbType.MYSQL);
        registerProductNameMapping("mariadb", DbType.MARIADB);
        registerProductNameMapping("oracle", DbType.ORACLE);
        registerProductNameMapping("db2", DbType.DB2);
        registerProductNameMapping("h2", DbType.H2);
        registerProductNameMapping("hsql", DbType.HSQL);
        registerProductNameMapping("sqlite", DbType.SQLITE);
        registerProductNameMapping("postgresql", DbType.POSTGRE_SQL);
        registerProductNameMapping("sql server", DbType.SQL_SERVER2005);
        registerProductNameMapping("dm", DbType.DM);
        registerProductNameMapping("dameng", DbType.DM);
        registerProductNameMapping("kingbase", DbType.KINGBASE_ES);
        registerProductNameMapping("kingbasees", DbType.KINGBASE_ES);
        registerProductNameMapping("gbase", DbType.GBASE);
        registerProductNameMapping("openGauss", DbType.OPENGAUSS);
        registerProductNameMapping("gaussdb", DbType.GAUSS);
        registerProductNameMapping("gauss", DbType.GAUSS_DB);
        registerProductNameMapping("oceanbase", DbType.OCEAN_BASE);
        registerProductNameMapping("oscar", DbType.OSCAR);
        registerProductNameMapping("shengtong", DbType.OSCAR);
        registerProductNameMapping("highgo", DbType.HIGH_GO);
        registerProductNameMapping("hanamajor", DbType.HIGH_GO);
        registerProductNameMapping("xugu", DbType.XU_GU);
        registerProductNameMapping("sinodb", DbType.SINODB);
        registerProductNameMapping("sundb", DbType.SUNDB);
        registerProductNameMapping("uxdb", DbType.UXDB);
        registerProductNameMapping("vastbase", DbType.VASTBASE);
        registerProductNameMapping("goldendb", DbType.GOLDENDB);
        registerProductNameMapping("yasdb", DbType.YASDB);
        registerProductNameMapping("tdengine", DbType.TDENGINE);
        registerProductNameMapping("clickhouse", DbType.CLICK_HOUSE);

        registerUrlMapping("jdbc:mysql", DbType.MYSQL);
        registerUrlMapping("jdbc:mariadb", DbType.MARIADB);
        registerUrlMapping("jdbc:oracle", DbType.ORACLE);
        registerUrlMapping("jdbc:db2", DbType.DB2);
        registerUrlMapping("jdbc:h2", DbType.H2);
        registerUrlMapping("jdbc:hsqldb", DbType.HSQL);
        registerUrlMapping("jdbc:sqlite", DbType.SQLITE);
        registerUrlMapping("jdbc:postgresql", DbType.POSTGRE_SQL);
        registerUrlMapping("jdbc:sqlserver", DbType.SQL_SERVER2005);
        registerUrlMapping("jdbc:dm", DbType.DM);
        registerUrlMapping("jdbc:kingbase8", DbType.KINGBASE_ES);
        registerUrlMapping("jdbc:kingbase", DbType.KINGBASE_ES);
        registerUrlMapping("jdbc:gbase", DbType.GBASE);
        registerUrlMapping("jdbc:gaussdb", DbType.GAUSS);
        registerUrlMapping("jdbc:oceanbase", DbType.OCEAN_BASE);
        registerUrlMapping("jdbc:opengauss", DbType.OPENGAUSS);
        registerUrlMapping("jdbc:oscar", DbType.OSCAR);
        registerUrlMapping("jdbc:highgo", DbType.HIGH_GO);
        registerUrlMapping("jdbc:xugu", DbType.XU_GU);
        registerUrlMapping("jdbc:sinodb", DbType.SINODB);
        registerUrlMapping("jdbc:sundb", DbType.SUNDB);
        registerUrlMapping("jdbc:uxdb", DbType.UXDB);
        registerUrlMapping("jdbc:vastbase", DbType.VASTBASE);
        registerUrlMapping("jdbc:goldendb", DbType.GOLDENDB);
        registerUrlMapping("jdbc:yasdb", DbType.YASDB);
        registerUrlMapping("jdbc:tdengine", DbType.TDENGINE);
        registerUrlMapping("jdbc:clickhouse", DbType.CLICK_HOUSE);
    }

    /**
     * 私有构造，禁止实例化
     */
    private DbTypeDetector() {
    }

    /**
     * 注册产品名映射
     *
     * @param keyword 数据库产品名关键字
     * @param dbType  数据库类型
     */
    private static void registerProductNameMapping(String keyword, DbType dbType) {
        PRODUCT_NAME_MAPPINGS.put(keyword.toLowerCase(Locale.ROOT), dbType);
    }

    /**
     * 注册 JDBC URL 映射
     *
     * @param urlPrefix JDBC URL 前缀关键字
     * @param dbType    数据库类型
     */
    private static void registerUrlMapping(String urlPrefix, DbType dbType) {
        URL_MAPPINGS.put(urlPrefix.toLowerCase(Locale.ROOT), dbType);
    }

    /**
     * 探测数据源对应的数据库类型。
     *
     * <p>优先尝试 JDBC URL 探测；失败或无法识别时回退到数据库产品名探测；
     * 均无法识别时返回 {@link DbType#OTHER}。</p>
     *
     * @param dataSource 数据源
     * @return 数据库类型，探测失败返回 OTHER
     */
    public static DbType detect(DataSource dataSource) {
        if (dataSource == null) {
            return DbType.OTHER;
        }
        String url = resolveJdbcUrl(dataSource);
        if (url != null) {
            DbType urlType = detectFromUrl(url);
            if (urlType != null && urlType != DbType.OTHER) {
                return urlType;
            }
        }
        String productName = resolveProductName(dataSource);
        if (productName != null) {
            DbType productType = detectFromProductName(productName);
            if (productType != null && productType != DbType.OTHER) {
                return productType;
            }
        }
        return DbType.OTHER;
    }

    /**
     * 从 JDBC URL 探测数据库类型。
     *
     * <p>自动剥离 p6spy（jdbc:p6spy:）等代理包装前缀后匹配。</p>
     *
     * @param jdbcUrl JDBC URL
     * @return 数据库类型，无法识别返回 OTHER
     */
    public static DbType detectFromUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return DbType.OTHER;
        }
        String normalized = normalizeUrl(jdbcUrl).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, DbType> entry : URL_MAPPINGS.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DbType.OTHER;
    }

    /**
     * 从数据库产品名探测数据库类型。
     *
     * @param productName 数据库产品名
     * @return 数据库类型，无法识别返回 OTHER
     */
    public static DbType detectFromProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            return DbType.OTHER;
        }
        String normalized = productName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, DbType> entry : PRODUCT_NAME_MAPPINGS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DbType.OTHER;
    }

    /**
     * 解析数据源的 JDBC URL。
     *
     * @param dataSource 数据源
     * @return JDBC URL，获取失败返回 null
     */
    private static String resolveJdbcUrl(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getURL();
        } catch (SQLException e) {
            log.warn("[MyBatis] 获取数据源 JDBC URL 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析数据库产品名。
     *
     * @param dataSource 数据源
     * @return 数据库产品名，获取失败返回 null
     */
    private static String resolveProductName(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            log.warn("[MyBatis] 获取数据库产品名失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 规范化 JDBC URL，剥离代理包装前缀。
     *
     * @param jdbcUrl JDBC URL
     * @return 规范化后的 JDBC URL
     */
    private static String normalizeUrl(String jdbcUrl) {
        String result = jdbcUrl;
        while (result.startsWith("jdbc:p6spy:") || result.startsWith("jdbc:p6spy")) {
            result = result.replaceFirst("^jdbc:p6spy:?", "jdbc:");
        }
        return result;
    }
}
