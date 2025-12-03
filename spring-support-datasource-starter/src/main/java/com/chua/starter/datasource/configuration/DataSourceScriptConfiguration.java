package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.properties.DataSourceScriptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.DigestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据源脚本配置类
 *
 * @author CH
 * @since 2025/9/3 9:07
 */
@Slf4j
public class DataSourceScriptConfiguration {

    /**
     * 数据源初始化器配置
     *
     * @param ds                         数据源对象，用于执行初始化脚本
     *                                   示例：可通过 {@code @Autowired} 注入一个 HikariDataSource 实例
     * @param dataSourceScriptProperties 数据源脚本配置属性，包含脚本执行相关配置
     *                                   示例：{@code plugin.datasource.script.enable=true}
     * @return 数据源初始化器，用于在应用启动时执行数据库初始化脚本
     */
    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource ds, DataSourceScriptProperties dataSourceScriptProperties) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setEnabled(dataSourceScriptProperties.isEnable());
        initializer.setDataSource(ds);
        initializer.setDatabasePopulator(new FlywayLikePopulator(ds, dataSourceScriptProperties));
        return initializer;
    }

    /**
     * Flyway风格数据库脚本迁移器
     *
     * 支持以下功能：
     * 1. 自动创建版本记录表
     * 2. 支持多路径脚本扫描（db/init, db/migration等）
     * 3. 支持数据库特定脚本
     * 4. 支持校验和验证
     * 5. 支持基线版本
     *
     * @author CH
     * @version 1.1.0
     * @since 2025/9/3 9:07
     */
    static class FlywayLikePopulator implements DatabasePopulator {

        private final DataSource ds;
        private final JdbcTemplate jdbc;
        private final DataSourceScriptProperties dataSourceScriptProperties;

        /**
         * 默认初始化脚本路径
         */
        private static final String DEFAULT_INIT_PATH = "classpath*:db/init/*.sql";

        /**
         * 构造函数
         *
         * @param ds                         数据源对象
         * @param dataSourceScriptProperties 数据源脚本配置属性
         */
        FlywayLikePopulator(DataSource ds, DataSourceScriptProperties dataSourceScriptProperties) {
            this.ds = ds;
            this.jdbc = new JdbcTemplate(ds);
            this.dataSourceScriptProperties = dataSourceScriptProperties;
        }

        @Override
        public void populate(Connection connection) throws SQLException {
            try {
                // 1. 创建版本记录表（兼容多种数据库）
                createVersionTable();

                // 2. 如果启用基线且表为空，插入基线记录
                insertBaselineIfNeeded();

                // 3. 扫描脚本（包括db/init和配置的脚本路径）
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                List<Resource> list = new ArrayList<>(Arrays.asList(resolver.getResources(dataSourceScriptProperties.getScriptPath())));

                // 如果配置了数据库类型，则额外加载数据库特定的脚本目录
                if (dataSourceScriptProperties.getDatabaseType() != null && !dataSourceScriptProperties.getDatabaseType().trim().isEmpty()) {
                    String dbSpecificPath = buildDatabaseSpecificPath(dataSourceScriptProperties.getScriptPath(), dataSourceScriptProperties.getDatabaseType());
                    Resource[] dbSpecificResources = resolver.getResources(dbSpecificPath);
                    list.addAll(Arrays.asList(dbSpecificResources));

                    if (dataSourceScriptProperties.isVerbose()) {
                        log.info("加载数据库特定脚本路径: {}", dbSpecificPath);
                    }
                }

                // 过滤符合命名规范的脚本文件
                list = list.stream()
                        .filter(res -> res.getFilename() != null &&
                                res.getFilename().startsWith(dataSourceScriptProperties.getScriptPrefix()) &&
                                res.getFilename().endsWith(dataSourceScriptProperties.getScriptSuffix()))
                        .sorted(Comparator.comparing(Resource::getFilename))
                        .collect(Collectors.toList());

                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("找到 {} 个迁移脚本", list.size());
                }

                for (Resource res : list) {
                    String fileName = Objects.requireNonNull(res.getFilename());
                    String version = versionOf(fileName);
                    String description = descriptionOf(fileName);
                    String currentChecksum = checksum(res);

                    // 4. 检查是否已执行过
                    String tableName = dataSourceScriptProperties.getVersionTable();
                    String tablePrefix = extractTablePrefix(tableName);

                    Integer count = jdbc.queryForObject(
                            String.format("SELECT COUNT(*) FROM %s WHERE %s_version = ?",
                                    tableName, tablePrefix),
                            Integer.class, version);

                    if (count != null && count > 0) {
                        // 如果启用校验和验证，检查脚本是否被修改
                        if (dataSourceScriptProperties.isValidateChecksum()) {
                            String storedChecksum = jdbc.queryForObject(
                                    String.format("SELECT %s_checksum FROM %s WHERE %s_version = ?",
                                            tablePrefix, tableName, tablePrefix),
                                    String.class, version);
                            if (!currentChecksum.equals(storedChecksum)) {
                                String errorMsg = String.format("版本 %s 的校验和不匹配。期望值: %s, 实际值: %s",
                                        version, storedChecksum, currentChecksum);
                                log.error(errorMsg);
                                if (!dataSourceScriptProperties.isContinueOnError()) {
                                    throw new SQLException(errorMsg);
                                }
                            }
                        }

                        if (dataSourceScriptProperties.isVerbose()) {
                            log.info("跳过已执行的脚本: {}", fileName);
                        }
                        continue;
                    }

                    // 5. 执行脚本
                    String success = "false";
                    try {
                        if (dataSourceScriptProperties.isVerbose()) {
                            log.info("执行迁移脚本: {} (版本: {})", fileName, version);
                        }

                        Connection scriptConnection = ds.getConnection();
                        try {
                            // 创建编码资源
                            EncodedResource encodedResource = new EncodedResource(res, dataSourceScriptProperties.getSqlScriptEncoding());

                            // 执行脚本
                            ScriptUtils.executeSqlScript(
                                    scriptConnection,
                                    encodedResource,
                                    dataSourceScriptProperties.isContinueOnError(),
                                    dataSourceScriptProperties.isIgnoreFailedDrops(),
                                    ScriptUtils.DEFAULT_COMMENT_PREFIX,
                                    dataSourceScriptProperties.getSeparator(),
                                    ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                                    ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);

                            success = "true";

                        } finally {
                            scriptConnection.close();
                        }

                        // 6. 记录执行结果
                         tableName = dataSourceScriptProperties.getVersionTable();
                         tablePrefix = extractTablePrefix(tableName);
                        jdbc.update(
                                String.format("INSERT INTO %s(%s_version,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?)",
                                        tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                                version, description, currentChecksum, success);

                        log.info("成功执行迁移脚本: {} (版本: {})", fileName, version);

                    } catch (Exception e) {
                        log.error("执行迁移脚本失败: {} (版本: {})", fileName, version, e);

                        // 记录失败的执行
                        try {
                             tableName = dataSourceScriptProperties.getVersionTable();
                             tablePrefix = extractTablePrefix(tableName);
                            jdbc.update(
                                    String.format("INSERT INTO %s(%s_version,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?)",
                                            tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                                    version, description, currentChecksum, false);
                        } catch (Exception recordException) {
                            log.error("记录迁移失败信息失败", recordException);
                        }

                        if (!dataSourceScriptProperties.isContinueOnError()) {
                            throw new SQLException("脚本迁移失败: " + fileName, e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("读取脚本资源失败", e);
            }
        }

        /* ---------- 工具方法 ---------- */

        /**
         * 创建版本记录表
         * 兼容多种数据库类型，字段名使用表名前缀
         */
        private void createVersionTable() {
            String tableName = dataSourceScriptProperties.getVersionTable();
            // 提取表名作为字段前缀（去掉可能的schema前缀）
            String tablePrefix = extractTablePrefix(tableName);

            String createTableSql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    %s_version VARCHAR(32) PRIMARY KEY COMMENT '脚本版本号',
                    %s_description VARCHAR(100) COMMENT '脚本描述信息',
                    %s_checksum VARCHAR(64) COMMENT '脚本文件MD5校验和',
                    %s_executed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '脚本执行时间',
                    %s_success VARCHAR(8) COMMENT '执行状态：true-成功，false-失败'
                )
            """, tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix);

            try {
                jdbc.execute(createTableSql);
                if (dataSourceScriptProperties.isVerbose()) {
                    log.debug("版本记录表已创建或已存在: {}", tableName);
                }
            } catch (DataAccessException e) {
                log.warn("创建版本记录表时发生异常（表可能已存在）: {}", e.getMessage());
            }
        }

        /**
         * 插入基线版本记录（如果需要）
         */
        private void insertBaselineIfNeeded() {
            if (!dataSourceScriptProperties.isBaselineOnMigrate()) {
                return;
            }

            String tableName = dataSourceScriptProperties.getVersionTable();
            String tablePrefix = extractTablePrefix(tableName);

            try {
                Integer count = jdbc.queryForObject(
                        String.format("SELECT COUNT(*) FROM %s", tableName),
                        Integer.class);

                if (count != null && count == 0) {
                    jdbc.update(
                            String.format("INSERT INTO %s(%s_version,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?)",
                                    tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                            dataSourceScriptProperties.getBaselineVersion(),
                            dataSourceScriptProperties.getBaselineDescription(),
                            "baseline",
                            "true");
                    if (dataSourceScriptProperties.isVerbose()) {
                        log.info("创建基线版本: {}", dataSourceScriptProperties.getBaselineVersion());
                    }
                }
            } catch (DataAccessException e) {
                log.warn("插入基线版本记录失败: {}", e.getMessage());
            }
        }

        /**
         * 扫描多个路径下的脚本资源
         *
         * @param resolver 资源解析器
         * @param paths    脚本路径列表
         * @return 脚本资源列表
         * @throws IOException IO异常
         */
        private List<Resource> scanScripts(PathMatchingResourcePatternResolver resolver, String... paths)
                throws IOException {
            List<Resource> allResources = new ArrayList<>();
            for (String path : paths) {
                try {
                    Resource[] resources = resolver.getResources(path);
                    allResources.addAll(Arrays.asList(resources));
                    if (dataSourceScriptProperties.isVerbose()) {
                        log.debug("从路径 {} 扫描到 {} 个脚本", path, resources.length);
                    }
                } catch (IOException e) {
                    log.debug("路径不存在或无法访问: {}", path);
                }
            }
            return allResources;
        }

        /**
         * 从文件名中提取版本号
         * 例如: V1.0.0__create_user.sql -> 1.0.0
         *
         * @param fileName 文件名
         * @return 版本号
         */
        private String versionOf(String fileName) {
            String prefix = dataSourceScriptProperties.getScriptPrefix();
            String suffix = dataSourceScriptProperties.getScriptSuffix();
            String separator = dataSourceScriptProperties.getVersionSeparator();

            // 构建正则表达式: ^V(.*?)__.*\.sql$
            String regex = String.format("^%s(.*?)%s.*\\%s$",
                    escapeRegex(prefix),
                    escapeRegex(separator),
                    escapeRegex(suffix));

            return fileName.replaceFirst(regex, "$1");
        }

        /**
         * 从文件名中提取描述信息
         * 例如: V1.0.0__create_user.sql -> create_user
         *
         * @param fileName 文件名
         * @return 描述信息
         */
        private String descriptionOf(String fileName) {
            String suffix = dataSourceScriptProperties.getScriptSuffix();
            String separator = dataSourceScriptProperties.getVersionSeparator();

            // 构建正则表达式: ^.*?__(.*)\.sql$
            String regex = String.format("^.*?%s(.*)\\%s$",
                    escapeRegex(separator),
                    escapeRegex(suffix));

            String description = fileName.replaceFirst(regex, "$1");
            // 将下划线替换为空格，使描述更可读
            return description.replace("_", " ");
        }

        /**
         * 计算资源文件的MD5校验和
         *
         * @param res 资源文件
         * @return MD5校验和
         * @throws IOException 读取资源文件时发生IO异常
         */
        private String checksum(Resource res) throws IOException {
            return DigestUtils.md5DigestAsHex(res.getInputStream());
        }

        /**
         * 转义正则表达式特殊字符
         *
         * @param str 需要转义的字符串
         * @return 转义后的字符串
         */
        private String escapeRegex(String str) {
            return str.replaceAll("([\\[\\]\\(\\)\\{\\}\\*\\+\\?\\^\\$\\|\\\\])", "\\\\$1");
        }

        /**
         * 提取表名前缀
         * 例如：db_version_history -> db_version_history
         *      schema.db_version -> db_version
         *
         * @param tableName 表名（可能包含schema）
         * @return 表名前缀
         */
        private String extractTablePrefix(String tableName) {
            // 如果包含schema前缀（如 schema.table），只取表名部分
            if (tableName.contains(".")) {
                tableName = tableName.substring(tableName.lastIndexOf('.') + 1);
            }
            // 移除可能的反引号或双引号
            tableName = tableName.replace("`", "").replace("\"", "");
            return tableName;
        }

        /**
         * 构建数据库特定的脚本路径
         *
         * @param originalPath 原始路径
         * @param databaseType 数据库类型
         * @return 数据库特定的路径
         */
        private String buildDatabaseSpecificPath(String originalPath, String databaseType) {
            // 查找文件扩展名的位置
            int lastDotIndex = originalPath.lastIndexOf('.');
            int lastSlashIndex = originalPath.lastIndexOf('/');

            // 如果有扩展名且扩展名在最后一个斜杠之后
            if (lastDotIndex > lastSlashIndex && lastDotIndex != -1) {
                // 在扩展名前插入数据库类型
                String pathWithoutExtension = originalPath.substring(0, lastDotIndex);
                String extension = originalPath.substring(lastDotIndex);
                return pathWithoutExtension + "/" + databaseType + "/*" + extension;
            } else {
                // 没有扩展名，直接在路径末尾添加数据库类型
                return originalPath.endsWith("/") ?
                        originalPath + databaseType + "/*" :
                        originalPath + "/" + databaseType + "/*";
            }
        }
    }
}
