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
 * @since 4.0.0.42
 * @author CH
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
        /**
         * 注册product名称mapping。
         * @param "mysql" "MySQL"
         * @param DbType.MYSQL db类型.MySQL
         */
        registerProductNameMapping("mysql", DbType.MYSQL);
        /**
         * 注册product名称mapping。
         * @param "mariadb" "mariadb"
         * @param DbType.MARIADB db类型.MARIADB
         */
        registerProductNameMapping("mariadb", DbType.MARIADB);
        /**
         * 注册product名称mapping。
         * @param "oracle" "oracle"
         * @param DbType.ORACLE db类型.ORACLE
         */
        registerProductNameMapping("oracle", DbType.ORACLE);
        /**
         * 注册product名称mapping。
         * @param "db2" "db2"
         * @param DbType.DB2 db类型.DB2
         */
        registerProductNameMapping("db2", DbType.DB2);
        /**
         * 注册product名称mapping。
         * @param "h2" "h2"
         * @param DbType.H2 db类型.H2
         */
        registerProductNameMapping("h2", DbType.H2);
        /**
         * 注册product名称mapping。
         * @param "hsql" "hsql"
         * @param DbType.HSQL db类型.HSQL
         */
        registerProductNameMapping("hsql", DbType.HSQL);
        /**
         * 注册product名称mapping。
         * @param "sqlite" "sqlite"
         * @param DbType.SQLITE db类型.SQLITE
         */
        registerProductNameMapping("sqlite", DbType.SQLITE);
        /**
         * 注册product名称mapping。
         * @param "postgresql" "PostgreSQL"
         * @param DbType.POSTGRE_SQL db类型.POSTGRE_SQL
         */
        registerProductNameMapping("postgresql", DbType.POSTGRE_SQL);
        /**
         * 注册product名称mapping。
         * @param server" 服务端"
         * @param DbType.SQL_SERVER2005 db类型.SQL_服务端2005
         */
        registerProductNameMapping("sql server", DbType.SQL_SERVER2005);
        /**
         * 注册product名称mapping。
         * @param "dm" "dm"
         * @param DbType.DM db类型.DM
         */
        registerProductNameMapping("dm", DbType.DM);
        /**
         * 注册product名称mapping。
         * @param "dameng" "dameng"
         * @param DbType.DM db类型.DM
         */
        registerProductNameMapping("dameng", DbType.DM);
        /**
         * 注册product名称mapping。
         * @param "kingbase" "kingbase"
         * @param DbType.KINGBASE_ES db类型.KINGBASE_ES
         */
        registerProductNameMapping("kingbase", DbType.KINGBASE_ES);
        /**
         * 注册product名称mapping。
         * @param "kingbasees" "kingbasees"
         * @param DbType.KINGBASE_ES db类型.KINGBASE_ES
         */
        registerProductNameMapping("kingbasees", DbType.KINGBASE_ES);
        /**
         * 注册product名称mapping。
         * @param "gbase" "gbase"
         * @param DbType.GBASE db类型.GBASE
         */
        registerProductNameMapping("gbase", DbType.GBASE);
        /**
         * 注册product名称mapping。
         * @param "openGauss" "openGauss"
         * @param DbType.OPENGAUSS db类型.openGauss
         */
        registerProductNameMapping("openGauss", DbType.OPENGAUSS);
        /**
         * 注册product名称mapping。
         * @param "gaussdb" "gaussdb"
         * @param DbType.GAUSS db类型.GAUSS
         */
        registerProductNameMapping("gaussdb", DbType.GAUSS);
        /**
         * 注册product名称mapping。
         * @param "gauss" "gauss"
         * @param DbType.GAUSS_DB db类型.GAUSS_DB
         */
        registerProductNameMapping("gauss", DbType.GAUSS_DB);
        /**
         * 注册product名称mapping。
         * @param "oceanbase" "oceanbase"
         * @param DbType.OCEAN_BASE db类型.OCEAN_基础
         */
        registerProductNameMapping("oceanbase", DbType.OCEAN_BASE);
        /**
         * 注册product名称mapping。
         * @param "oscar" "oscar"
         * @param DbType.OSCAR db类型.OSCAR
         */
        registerProductNameMapping("oscar", DbType.OSCAR);
        /**
         * 注册product名称mapping。
         * @param "shengtong" "shengtong"
         * @param DbType.OSCAR db类型.OSCAR
         */
        registerProductNameMapping("shengtong", DbType.OSCAR);
        /**
         * 注册product名称mapping。
         * @param "highgo" "highgo"
         * @param DbType.HIGH_GO db类型.HIGH_Go
         */
        registerProductNameMapping("highgo", DbType.HIGH_GO);
        /**
         * 注册product名称mapping。
         * @param "hanamajor" "hanamajor"
         * @param DbType.HIGH_GO db类型.HIGH_Go
         */
        registerProductNameMapping("hanamajor", DbType.HIGH_GO);
        /**
         * 注册product名称mapping。
         * @param "xugu" "xugu"
         * @param DbType.XU_GU db类型.XU_GU
         */
        registerProductNameMapping("xugu", DbType.XU_GU);
        /**
         * 注册product名称mapping。
         * @param "sinodb" "sinodb"
         * @param DbType.SINODB db类型.SINODB
         */
        registerProductNameMapping("sinodb", DbType.SINODB);
        /**
         * 注册product名称mapping。
         * @param "sundb" "sundb"
         * @param DbType.SUNDB db类型.SUNDB
         */
        registerProductNameMapping("sundb", DbType.SUNDB);
        /**
         * 注册product名称mapping。
         * @param "uxdb" "uxdb"
         * @param DbType.UXDB db类型.UXDB
         */
        registerProductNameMapping("uxdb", DbType.UXDB);
        /**
         * 注册product名称mapping。
         * @param "vastbase" "vastbase"
         * @param DbType.VASTBASE db类型.VASTBASE
         */
        registerProductNameMapping("vastbase", DbType.VASTBASE);
        /**
         * 注册product名称mapping。
         * @param "goldendb" "goldendb"
         * @param DbType.GOLDENDB db类型.GOLDENDB
         */
        registerProductNameMapping("goldendb", DbType.GOLDENDB);
        /**
         * 注册product名称mapping。
         * @param "yasdb" "yasdb"
         * @param DbType.YASDB db类型.YASDB
         */
        registerProductNameMapping("yasdb", DbType.YASDB);
        /**
         * 注册product名称mapping。
         * @param "tdengine" "tdengine"
         * @param DbType.TDENGINE db类型.TDENGINE
         */
        registerProductNameMapping("tdengine", DbType.TDENGINE);
        /**
         * 注册product名称mapping。
         * @param "clickhouse" "clickhouse"
         * @param DbType.CLICK_HOUSE dbtype.click房子
         */
        registerProductNameMapping("clickhouse", DbType.CLICK_HOUSE);

        /**
         * 注册urlmapping。
         * @param "jdbc:mysql" "JDBC:MySQL"
         * @param DbType.MYSQL db类型.MySQL
         */
        registerUrlMapping("jdbc:mysql", DbType.MYSQL);
        /**
         * 注册urlmapping。
         * @param "jdbc:mariadb" "JDBC:mariadb"
         * @param DbType.MARIADB db类型.MARIADB
         */
        registerUrlMapping("jdbc:mariadb", DbType.MARIADB);
        /**
         * 注册urlmapping。
         * @param "jdbc:oracle" "JDBC:oracle"
         * @param DbType.ORACLE db类型.ORACLE
         */
        registerUrlMapping("jdbc:oracle", DbType.ORACLE);
        /**
         * 注册urlmapping。
         * @param "jdbc:db2" "JDBC:db2"
         * @param DbType.DB2 db类型.DB2
         */
        registerUrlMapping("jdbc:db2", DbType.DB2);
        /**
         * 注册urlmapping。
         * @param "jdbc:h2" "JDBC:h2"
         * @param DbType.H2 db类型.H2
         */
        registerUrlMapping("jdbc:h2", DbType.H2);
        /**
         * 注册urlmapping。
         * @param "jdbc:hsqldb" "JDBC:hsqldb"
         * @param DbType.HSQL db类型.HSQL
         */
        registerUrlMapping("jdbc:hsqldb", DbType.HSQL);
        /**
         * 注册urlmapping。
         * @param "jdbc:sqlite" "JDBC:sqlite"
         * @param DbType.SQLITE db类型.SQLITE
         */
        registerUrlMapping("jdbc:sqlite", DbType.SQLITE);
        /**
         * 注册urlmapping。
         * @param "jdbc:postgresql" "JDBC:PostgreSQL"
         * @param DbType.POSTGRE_SQL db类型.POSTGRE_SQL
         */
        registerUrlMapping("jdbc:postgresql", DbType.POSTGRE_SQL);
        /**
         * 注册urlmapping。
         * @param "jdbc:sqlserver" "JDBC:sqlserver"
         * @param DbType.SQL_SERVER2005 db类型.SQL_服务端2005
         */
        registerUrlMapping("jdbc:sqlserver", DbType.SQL_SERVER2005);
        /**
         * 注册urlmapping。
         * @param "jdbc:dm" "JDBC:dm"
         * @param DbType.DM db类型.DM
         */
        registerUrlMapping("jdbc:dm", DbType.DM);
        /**
         * 注册urlmapping。
         * @param "jdbc:kingbase8" "JDBC:kingbase8"
         * @param DbType.KINGBASE_ES db类型.KINGBASE_ES
         */
        registerUrlMapping("jdbc:kingbase8", DbType.KINGBASE_ES);
        /**
         * 注册urlmapping。
         * @param "jdbc:kingbase" "JDBC:kingbase"
         * @param DbType.KINGBASE_ES db类型.KINGBASE_ES
         */
        registerUrlMapping("jdbc:kingbase", DbType.KINGBASE_ES);
        /**
         * 注册urlmapping。
         * @param "jdbc:gbase" "JDBC:gbase"
         * @param DbType.GBASE db类型.GBASE
         */
        registerUrlMapping("jdbc:gbase", DbType.GBASE);
        /**
         * 注册urlmapping。
         * @param "jdbc:gaussdb" "JDBC:gaussdb"
         * @param DbType.GAUSS db类型.GAUSS
         */
        registerUrlMapping("jdbc:gaussdb", DbType.GAUSS);
        /**
         * 注册urlmapping。
         * @param "jdbc:oceanbase" "JDBC:oceanbase"
         * @param DbType.OCEAN_BASE db类型.OCEAN_基础
         */
        registerUrlMapping("jdbc:oceanbase", DbType.OCEAN_BASE);
        /**
         * 注册urlmapping。
         * @param "jdbc:opengauss" "JDBC:openGauss"
         * @param DbType.OPENGAUSS db类型.openGauss
         */
        registerUrlMapping("jdbc:opengauss", DbType.OPENGAUSS);
        /**
         * 注册urlmapping。
         * @param "jdbc:oscar" "JDBC:oscar"
         * @param DbType.OSCAR db类型.OSCAR
         */
        registerUrlMapping("jdbc:oscar", DbType.OSCAR);
        /**
         * 注册urlmapping。
         * @param "jdbc:highgo" "JDBC:highgo"
         * @param DbType.HIGH_GO db类型.HIGH_Go
         */
        registerUrlMapping("jdbc:highgo", DbType.HIGH_GO);
        /**
         * 注册urlmapping。
         * @param "jdbc:xugu" "JDBC:xugu"
         * @param DbType.XU_GU db类型.XU_GU
         */
        registerUrlMapping("jdbc:xugu", DbType.XU_GU);
        /**
         * 注册urlmapping。
         * @param "jdbc:sinodb" "JDBC:sinodb"
         * @param DbType.SINODB db类型.SINODB
         */
        registerUrlMapping("jdbc:sinodb", DbType.SINODB);
        /**
         * 注册urlmapping。
         * @param "jdbc:sundb" "JDBC:sundb"
         * @param DbType.SUNDB db类型.SUNDB
         */
        registerUrlMapping("jdbc:sundb", DbType.SUNDB);
        /**
         * 注册urlmapping。
         * @param "jdbc:uxdb" "JDBC:uxdb"
         * @param DbType.UXDB db类型.UXDB
         */
        registerUrlMapping("jdbc:uxdb", DbType.UXDB);
        /**
         * 注册urlmapping。
         * @param "jdbc:vastbase" "JDBC:vastbase"
         * @param DbType.VASTBASE db类型.VASTBASE
         */
        registerUrlMapping("jdbc:vastbase", DbType.VASTBASE);
        /**
         * 注册urlmapping。
         * @param "jdbc:goldendb" "JDBC:goldendb"
         * @param DbType.GOLDENDB db类型.GOLDENDB
         */
        registerUrlMapping("jdbc:goldendb", DbType.GOLDENDB);
        /**
         * 注册urlmapping。
         * @param "jdbc:yasdb" "JDBC:yasdb"
         * @param DbType.YASDB db类型.YASDB
         */
        registerUrlMapping("jdbc:yasdb", DbType.YASDB);
        /**
         * 注册urlmapping。
         * @param "jdbc:tdengine" "JDBC:tdengine"
         * @param DbType.TDENGINE db类型.TDENGINE
         */
        registerUrlMapping("jdbc:tdengine", DbType.TDENGINE);
        /**
         * 注册urlmapping。
         * @param "jdbc:clickhouse" "JDBC:clickhouse"
         * @param DbType.CLICK_HOUSE dbtype.click房子
         */
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
     * @return JDBC URL，获取失败返回 空
     */
    private static String resolveJdbcUrl(DataSource dataSource) {
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
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
     * @return 数据库产品名，获取失败返回 空
     */
    private static String resolveProductName(DataSource dataSource) {
        /**
         * 尝试。
         * @param dataSource.getConnection( 数据源.getconnection(
         */
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
