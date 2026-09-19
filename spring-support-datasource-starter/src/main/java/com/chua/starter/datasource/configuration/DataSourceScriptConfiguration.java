package com.chua.starter.datasource.configuration;

import com.chua.common.support.lang.process.ProgressBar;
import com.chua.common.support.lang.process.ProgressBarBuilder;
import com.chua.common.support.lang.process.ProgressBarStyle;
import com.chua.common.support.lang.version.Version;
import com.chua.common.support.lang.datasource.flyway.Flyway;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.datasource.properties.DataSourceScriptProperties;
import com.chua.starter.datasource.support.DatabaseFileProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 数据源脚本配置类
 * <p>
 * 实现 Beanpost处理器 接口，在 数据源 初始化完成后立即执行数据库脚本，
 * 确保脚本在其他依赖数据库的 Bean（如 实体管理器工厂、SQL会话工厂）之前执行。
 * </p>
 *
 * @since 2025/9/3 9:07
 * @author CH
 */
@Slf4j
public class DataSourceScriptConfiguration implements BeanPostProcessor {

    /**
     * 数据来源script属性
    */
    private final DataSourceScriptProperties dataSourceScriptProperties;

    /**
     * 已处理的 数据源 Bean 名称集合，避免重复执行
     */
    private final Set<String> processedDataSources = Collections.synchronizedSet(new HashSet<>());
    /**
     * 已提示过禁用状态的 数据源 Bean 名称集合，避免重复打印日志
     */
    private final Set<String> skippedDataSources = Collections.synchronizedSet(new HashSet<>());

    /**
     * 创建 数据源script配置 实例
     * @param dataSourceScriptProperties 数据源script属性
     */
    public DataSourceScriptConfiguration(DataSourceScriptProperties dataSourceScriptProperties) {
        this.dataSourceScriptProperties = dataSourceScriptProperties;
    }

    @Override
    /**
     * Post处理之后initialization
    */
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            if (!dataSourceScriptProperties.isEnable()) {
                if (skippedDataSources.add(beanName)) {
                    log.info("检测到 DataSource [{}] 初始化完成，但 [{}]=false，已跳过数据库脚本执行",
                            beanName, DataSourceScriptProperties.PRE + ".enable");
                }
                return bean;
            }

            DataSource ds = (DataSource) bean;
 // 避免重复处理同一个 数据源
            if (processedDataSources.add(beanName)) {
                try {
                    log.info("检测到 DataSource [{}] 初始化完成，开始执行数据库脚本...", beanName);
 // 优先通过 SPI 获取 Flyway 高优先级实现（utils-support-flyway-starter 的 数据源Flyway）；
 // 获取成功则委托其迁移，否则回退到内置 flywaylikepopulator。
                    if (tryMigrateWithSpiFlyway(ds, beanName)) {
                        log.info("DataSource [{}] 数据库脚本执行完成（SPI Flyway）", beanName);
                    } else {
                        FlywayLikePopulator populator = new FlywayLikePopulator(ds, dataSourceScriptProperties);
                        populator.populate();
                        log.info("DataSource [{}] 数据库脚本执行完成", beanName);
                    }
                } catch (Exception e) {
                    log.warn("执行数据库脚本失败: {}", beanName, e);
                    if (!dataSourceScriptProperties.isContinueOnError()) {
                        /**
                         * illegal状态异常。
                         * @param beanName Bean名称
                         * @param e e
                         * @return illegal状态异常的结果
                         */
                        throw new IllegalStateException("执行数据库脚本失败: " + beanName, e);
                    }
                }
            }
        }
        return bean;
    }

    /**
     * 尝试通过 SPI 获取 Flyway 高优先级实现并执行迁移。
     * <p>仅在存在可用 SPI 实现（如 utils-support-flyway-starter 的 DataSourceFlyway）
     * 且能成功实例化时委托；否则返回 false 由内置 flywaylikepopulator 兜底。</p>
     *
     * @param ds       数据源
     * @param beanName Bean 名称
     * @return true 表示已由 SPI Flyway 完成迁移
     */
    private boolean tryMigrateWithSpiFlyway(DataSource ds, String beanName) {
        try {
            List<Flyway> extensions = ServiceProvider.of(Flyway.class)
                    .getNewExtensions(Flyway.SPI_NAME, ds);
            for (Flyway flyway : extensions) {
                if (flyway == null) {
                    continue;
                }
                for (String location : splitLocations(dataSourceScriptProperties.getScriptPath())) {
                    flyway.location(location);
                }
                flyway.protocol(resolveProtocol(ds));
                int count = flyway.migrate();
                log.info("DataSource [{}] SPI Flyway 迁移完成，执行 {} 个脚本", beanName, count);
                return true;
            }
        } catch (Exception e) {
            log.warn("DataSource [{}] SPI Flyway 不可用，回退内置迁移器: {}", beanName, e.getMessage());
        }
        return false;
    }

    /**
     * 依据目标库产品名解析 {@link com.chua.common.support.lang.datasource.flyway.ScriptConverter} 协议名。
     * <p>仅为需要方言转换的引擎返回非空协议：MySQL 系（mysql/mariadb/tidb/oceanbase）与 H2 返回
     * {@code null}，保持原样执行既有行为（MySQL 原生兼容、H2 以 {@code MODE=MySQL} 兼容）；
     * 其余引擎（sqlite/postgresql/oracle/sqlserver）返回对应协议名以启用 ScriptConverter 转换。</p>
     *
     * @param ds 数据源
     * @return 协议名；无需转换或无法判定时为 {@code null}
     */
    private String resolveProtocol(DataSource ds) {
        String product;
        try (java.sql.Connection connection = ds.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            log.debug("解析目标库协议失败，按原样执行: {}", e.getMessage());
            return null;
        }
        if (product == null) {
            return null;
        }
        String p = product.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("mysql") || p.contains("mariadb") || p.contains("tidb") || p.contains("oceanbase")
                || p.contains("h2")) {
            return null;
        }
        if (p.contains("sqlite")) {
            return "sqlite";
        }
        if (p.contains("postgres")) {
            return "postgresql";
        }
        if (p.contains("oracle")) {
            return "oracle";
        }
        if (p.contains("microsoft") || p.contains("sql server")) {
            return "sqlserver";
        }
        return null;
    }

    /**
     * 将逗号分隔的脚本路径拆分为 位置 列表。
     *
     * @param scriptPath 脚本路径配置（可逗号分隔多个 类路径 目录）
     * @return location 列表
     */
    private List<String> splitLocations(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scriptPath.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::stripWildcard)
                .distinct()
                .toList();
    }

    /**
     * 去除 类路径 通配符文件匹配（如 类路径*:db/初始化/*.SQL → 类路径:db/初始化），
     * 使 SPI Flyway 能按目录扫描 V*__*.SQL。
     *
     * @param location 原始 位置
     * @return 目录形式 位置
     */
    private String stripWildcard(String location) {
        String result = location.trim();
        if (result.startsWith("classpath*:")) {
            result = "classpath:" + result.substring("classpath*:".length());
        }
        int star = result.indexOf('*');
        if (star >= 0) {
            result = result.substring(0, star);
            result = result.replaceAll("/+$", "");
        }
        return result;
    }

    /**
     * Flyway风格数据库脚本迁移器
     *
     * <h3>支持以下功能：</h3>
     * <ol>
     *   <li>自动创建版本记录表</li>
     *   <li>支持多路径脚本扫描（db/init, db/migration等）</li>
     *   <li>支持数据库特定脚本</li>
     *   <li>支持校验和验证</li>
     *   <li>支持基线版本</li>
     * </ol>
     *
     * <h3>脚本命名规范：</h3>
     * <pre>
     * 1. V版本号__init_任意.sql  - 初始化脚本（同名只执行最高版本）
     *    示例：V1.0.0__init_user.sql, V2.0.0__init_user.sql → 只执行V2.0.0版本
     *
     * 2. V版本号__initdata_任意.sql - 初始化数据脚本（异步执行，使用虚拟线程）
     *    示例：V1.0.0__initdata_dict.sql → 表结构创建后异步执行
     *
     * 3. V版本号__add_任意.sql   - 增量脚本（全部执行，按脚本名+版本判断）
     *    示例：V1.0.0__add_index.sql, V1.0.1__add_index.sql → 两个都执行
     *
     * 4. V版本号__任意.sql       - 普通脚本（全部执行，按脚本名+版本判断）
     *    示例：V1.0.0__update_data.sql → 执行一次
     * </pre>
     *
     * <h3>执行顺序：</h3>
     * <ol>
     *   <li>同步执行 INIT 脚本（表结构，按版本号排序，同名只执行最高版本）</li>
     *   <li>同步执行 ADD 和普通脚本（按文件名排序）</li>
     *   <li>异步执行 INITDATA 脚本（初始化数据，使用虚拟线程）</li>
     * </ol>
     *
     * @since 2025/9/3 9:07
     * @author CH
     */
    @Slf4j
    static class FlywayLikePopulator {

        /**
         * DS
        */
        private final DataSource ds;
        /**
         * JDBC
        */
        private final JdbcTemplate jdbc;
        /**
         * 数据来源script属性
        */
        private final DataSourceScriptProperties dataSourceScriptProperties;

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

        /**
         * 执行数据库脚本迁移
         *
         * @throws SQLException SQL异常
         */
        public void populate() throws SQLException {
            try {
                DataSourceScriptProperties.ScanMode schemaScanMode = dataSourceScriptProperties.getEffectiveSchemaScanMode();
                DataSourceScriptProperties.RepeatableScanMode migrationScanMode =
                        dataSourceScriptProperties.getEffectiveMigrationScanMode();
                DataSourceScriptProperties.ScanMode dataScanMode = dataSourceScriptProperties.getEffectiveDataScanMode();

                if (schemaScanMode == DataSourceScriptProperties.ScanMode.NONE
                        && migrationScanMode == DataSourceScriptProperties.RepeatableScanMode.NONE
                        && dataScanMode == DataSourceScriptProperties.ScanMode.NONE) {
                    if (dataSourceScriptProperties.isVerbose()) {
                        log.info("数据库脚本扫描模式均为 NONE，跳过脚本扫描");
                    }
                    return;
                }

                Boolean emptyDatabase = null;
                if (schemaScanMode == DataSourceScriptProperties.ScanMode.ONCE) {
                    emptyDatabase = isDatabaseEmpty();
                    if (emptyDatabase == null) {
                        log.warn("表结构脚本扫描模式为 ONCE，但无法判断当前库是否为空，按 ALWAYS 继续执行");
                    }
                }

                Boolean hasBusinessData = null;
                if (dataScanMode == DataSourceScriptProperties.ScanMode.ONCE) {
                    hasBusinessData = hasBusinessData();
                    if (hasBusinessData == null) {
                        log.warn("初始化数据脚本扫描模式为 ONCE，但无法判断当前库是否已有业务数据，按 ALWAYS 继续执行");
                    }
                }

                // 1. 创建版本记录表（兼容多种数据库）
                /**
                 * 创建版本table。
                 */
                createVersionTable();

                // 2. 如果启用基线且表为空，插入基线记录
                /**
                 * 插入baselineifneeded。
                 */
                insertBaselineIfNeeded();

 // 3. 扫描脚本（包括db/初始化、db/migration、db/同步和配置的脚本路径）
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                List<String> scriptPaths = splitScriptPaths(dataSourceScriptProperties.getScriptPath());
                List<Resource> list = scanScripts(resolver, scriptPaths.toArray(String[]::new));

                // 如果配置了数据库类型，则额外加载数据库特定的脚本目录
                if (StringUtils.hasText(dataSourceScriptProperties.getDatabaseType())) {
                    List<String> dbSpecificPaths = new ArrayList<>();
                    for (String scriptPath : scriptPaths) {
                        String dbSpecificPath = buildDatabaseSpecificPath(scriptPath, dataSourceScriptProperties.getDatabaseType());
                        dbSpecificPaths.add(dbSpecificPath);
                        list.addAll(scanScripts(resolver, dbSpecificPath));
                    }

                    if (dataSourceScriptProperties.isVerbose() && !dbSpecificPaths.isEmpty()) {
                        log.info("加载数据库特定脚本路径: {}", dbSpecificPaths);
                    }
                }

 // 通过 服务加载 加载各模块提供的数据库脚本
                list.addAll(loadResourcesFromProviders());

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

                // 4. 分离脚本类型
 // 初始化 脚本：同名脚本只执行最高版本（同步执行）
 // 添加/NORMAL 脚本：全部执行，按脚本名+版本判断是否已执行（同步执行）
                // INITDATA 脚本：初始化数据，异步执行（使用虚拟线程）
                // 不符合格式的脚本：跳过不执行
                Map<String, Resource> initScriptsToExecute = new LinkedHashMap<>();
                List<Resource> otherScriptsToExecute = new ArrayList<>();
                List<Resource> initDataScriptsToExecute = new ArrayList<>();
                int skippedCount = 0;

                for (Resource res : list) {
                    String fileName = Objects.requireNonNull(res.getFilename());
                    String scriptType = getScriptType(fileName);
                    
                    // 跳过不符合格式的脚本
                    if (scriptType == null) {
                        if (dataSourceScriptProperties.isVerbose()) {
                            log.warn("跳过不符合格式的脚本: {} (格式应为: V版本号__init_xxx.sql 或 V版本号__add_xxx.sql 或 V版本号__xxx.sql)", fileName);
                        }
                        skippedCount++;
                        continue;
                    }
                    
                    // 检查版本类型是否允许执行
                    if (!isReleaseTypeAllowed(fileName)) {
                        if (dataSourceScriptProperties.isVerbose()) {
                            String versionStr = versionOf(fileName);
                            Version ver = Version.of(versionStr);
                            log.debug("跳过不允许的版本类型脚本: {} (版本: {}, 后缀: {})", fileName, versionStr, ver.getSuffix());
                        }
                        skippedCount++;
                        continue;
                    }
                    
                    String description = descriptionOf(fileName);

                    if ("INIT".equals(scriptType)) {
 // 初始化 脚本按描述分组，使用 版本 比较保留最高版本
                        Resource existingRes = initScriptsToExecute.get(description);
                        if (existingRes == null) {
                            initScriptsToExecute.put(description, res);
                        } else {
                            // 比较版本号，保留更高版本
                            String existingVersion = versionOf(existingRes.getFilename());
                            String currentVersion = versionOf(fileName);
                            Version existing = Version.of(existingVersion);
                            Version current = Version.of(currentVersion);
                            if (current.isHigherThan(existing)) {
                                initScriptsToExecute.put(description, res);
                                if (dataSourceScriptProperties.isVerbose()) {
                                    log.debug("INIT脚本 [{}]: 版本 {} 替换 {}", description, currentVersion, existingVersion);
                                }
                            }
                        }
                    } else if ("INITDATA".equals(scriptType)) {
                        // INITDATA 脚本加入异步执行列表
                        initDataScriptsToExecute.add(res);
                    } else {
 // 添加 和 NORMAL 脚本全部加入同步执行列表
                        otherScriptsToExecute.add(res);
                    }
                }
                
                if (skippedCount > 0) {
                    log.info("跳过 {} 个不符合格式的脚本", skippedCount);
                }

                List<Resource> schemaScriptsToExecute = filterSchemaScripts(
                        initScriptsToExecute.values(),
                        schemaScanMode,
                        emptyDatabase);
                List<Resource> migrationScriptsToExecute = filterMigrationScripts(
                        otherScriptsToExecute,
                        migrationScanMode);
                initDataScriptsToExecute = filterDataScripts(
                        initDataScriptsToExecute,
                        dataScanMode,
                        hasBusinessData);

 // 合并同步执行脚本列表：先执行 初始化（表结构），再执行 添加/NORMAL
                List<Resource> scriptsToExecute = new ArrayList<>();
                scriptsToExecute.addAll(schemaScriptsToExecute);
                scriptsToExecute.addAll(migrationScriptsToExecute);
                
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("表结构脚本: {} 个, 补丁脚本: {} 个, 异步数据脚本: {} 个",
                            schemaScriptsToExecute.size(),
                            migrationScriptsToExecute.size(),
                            initDataScriptsToExecute.size());
                }

                String tableName = dataSourceScriptProperties.getVersionTable();
                String tablePrefix = extractTablePrefix(tableName);

                // 第一遍遍历：统计需要实际执行的脚本数量（排除已执行的）
                int actualScriptsToExecute = 0;
                for (Resource res : scriptsToExecute) {
                    String fileName = Objects.requireNonNull(res.getFilename());
                    String version = versionOf(fileName);

                    ScriptExecutionRecord executionRecord =
                            /**
                             * findscript执行record。
                             * @param tableName table名称
                             * @param tablePrefix table前缀
                             * @param fileName 文件名称
                             * @param version 版本
                             */
                            findScriptExecutionRecord(tableName, tablePrefix, fileName, version);
                    if (needsExecution(executionRecord)) {
                        actualScriptsToExecute++;
                    }
                }

                // 创建总体进度条（只针对实际需要执行的脚本）
                ProgressBar overallProgressBar = null;
                if (dataSourceScriptProperties.isVerbose() && actualScriptsToExecute > 0) {
                    overallProgressBar = new ProgressBarBuilder()
                            .setTaskName("执行数据库脚本")
                            .setInitialMax(actualScriptsToExecute)
                            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
                            .build();
                    log.info("共找到 {} 个脚本，其中 {} 个需要执行", scriptsToExecute.size(), actualScriptsToExecute);
                }

                try {
                    int executedCount = 0;
                    for (Resource res : scriptsToExecute) {
                        String fileName = Objects.requireNonNull(res.getFilename());
                        String version = versionOf(fileName);
                        String description = descriptionOf(fileName);
                        String scriptType = getScriptType(fileName);
                        String currentChecksum = checksum(res);
                        ScriptExecutionRecord executionRecord =
                                /**
                                 * findscript执行record。
                                 * @param tableName table名称
                                 * @param tablePrefix table前缀
                                 * @param fileName 文件名称
                                 * @param version 版本
                                 */
                                findScriptExecutionRecord(tableName, tablePrefix, fileName, version);

                        if (hasSuccessfulExecution(executionRecord)) {
                            /**
                             * 校验校验和ifnecessary。
                             * @param executionRecord 执行record
                             * @param currentChecksum 当前校验和
                             * @param fileName 文件名称
                             * @param version 版本
                             */
                            validateChecksumIfNecessary(executionRecord, currentChecksum, fileName, version);

                            if (dataSourceScriptProperties.isVerbose()) {
                                log.info("跳过已执行的脚本: {} (版本: {})", fileName, version);
                            }
                            continue;
                        }

                        if (executionRecord != null && dataSourceScriptProperties.isVerbose()) {
                            log.info("检测到失败记录，重新执行脚本: {} (版本: {})", fileName, version);
                        }

                        executedCount++;

                        // 5. 执行脚本
                        String success = "false";
                        long startTime = System.currentTimeMillis();
                        try {
                            // 更新总体进度条信息
                            if (overallProgressBar != null) {
                                overallProgressBar.setExtraMessage(String.format("[%d/%d] %s (版本: %s)",
                                        executedCount, actualScriptsToExecute, fileName, version));
                            }

                            if (!dataSourceScriptProperties.isVerbose()) {
                                log.info("[{}/{}] 执行脚本: {}",
                                        executedCount,
                                        actualScriptsToExecute,
                                        fileName);
                            }

                            /**
                             * 尝试。
                             * @param ds.getConnection( ds.获取connection(
                             */
                            try (Connection scriptConnection = ds.getConnection()) {
                                // 禁用自动提交以提高性能
                                boolean originalAutoCommit = scriptConnection.getAutoCommit();
                                scriptConnection.setAutoCommit(false);

                                try {
                                    // 创建编码资源
                                    EncodedResource encodedResource = new EncodedResource(res, dataSourceScriptProperties.getSqlScriptEncoding());

                                    // 执行脚本（使用优化的批量执行）
                                    executeScriptWithProgress(
                                            scriptConnection,
                                            encodedResource,
                                            fileName);

                                    // 提交事务
                                    scriptConnection.commit();
                                    success = "true";

                                } catch (Exception e) {
                                    // 回滚事务
                                    try {
                                        scriptConnection.rollback();
                                    } catch (SQLException rollbackEx) {
                                        log.error("回滚事务失败", rollbackEx);
                                    }
                                    throw e;
                                } finally {
                                    // 恢复自动提交设置
                                    scriptConnection.setAutoCommit(originalAutoCommit);
                                }
                            }

                            // 6. 记录执行结果
                            saveScriptExecutionRecord(
                                    tableName,
                                    tablePrefix,
                                    version,
                                    fileName,
                                    scriptType,
                                    description,
                                    currentChecksum,
                                    success);

                            long endTime = System.currentTimeMillis();
                            long duration = endTime - startTime;

                            // 更新总体进度条
                            if (overallProgressBar != null) {
                                overallProgressBar.step();
                            }

                            if (!dataSourceScriptProperties.isVerbose()) {
                                log.info("[{}/{}] 成功执行迁移脚本: {} (版本: {}) - 耗时: {}ms",
                                        executedCount,
                                        actualScriptsToExecute,
                                        fileName,
                                        version,
                                        duration);
                            }

                        } catch (Exception e) {
                            log.error("执行迁移脚本失败: {} (版本: {})", fileName, version, e);

                            // 记录失败的执行
                            try {
                                saveScriptExecutionRecord(
                                        tableName,
                                        tablePrefix,
                                        version,
                                        fileName,
                                        scriptType,
                                        description,
                                        currentChecksum,
                                        "false");
                            } catch (Exception recordException) {
                                log.error("记录迁移失败信息失败", recordException);
                            }

                            // 更新总体进度条（即使失败也要更新）
                            if (overallProgressBar != null) {
                                overallProgressBar.step();
                            }

                            if (!dataSourceScriptProperties.isContinueOnError()) {
                                /**
                                 * SQL异常。
                                 * @param fileName 文件名称
                                 * @param e e
                                 * @return sql异常的结果
                                 */
                                throw new SQLException("脚本迁移失败: " + fileName, e);
                            }
                        }
                    }
                } finally {
                    // 关闭总体进度条
                    if (overallProgressBar != null) {
                        overallProgressBar.close();
                    }
                }
                // 同步脚本执行完成后，异步执行 INITDATA 脚本
                if (!initDataScriptsToExecute.isEmpty()) {
                    /**
                     * 执行初始化数据script异步。
                     * @param initDataScriptsToExecute 初始化数据script转为执行
                     * @param tableName table名称
                     * @param tablePrefix table前缀
                     */
                    executeInitDataScriptsAsync(initDataScriptsToExecute, tableName, tablePrefix);
                }
            } catch (IOException e) {
                /**
                 * runtime异常。
                 * @param "读取脚本资源失败" "读取脚本资源失败"
                 * @param e e
                 * @return runtime异常的结果
                 */
                throw new RuntimeException("读取脚本资源失败", e);
            }
        }

        /**
         * 使用虚拟线程异步执行 INITDATA 脚本
         * <p>
         * 这些脚本主要是初始化数据（插入语句），不影响系统启动
         * </p>
         *
         * @param scripts     待执行的 INITDATA 脚本列表
         * @param tableName   版本记录表名
         * @param tablePrefix 表字段前缀
         */
        private void executeInitDataScriptsAsync(List<Resource> scripts, String tableName, String tablePrefix) {
            int parallelism = dataSourceScriptProperties.getInitdataParallelism();
            int timeoutMinutes = dataSourceScriptProperties.getInitdataTimeoutMinutes();
            
            log.info("开始异步执行 {} 个初始化数据脚本（虚拟线程，并发数: {}）", 
                    scripts.size(), parallelism > 0 ? parallelism : "无限制");
            
            // 记录异步执行结果
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<String> failedScripts = new CopyOnWriteArrayList<>();
            
 // 使用 数量down插销 等待所有脚本执行完成
            CountDownLatch latch = new CountDownLatch(scripts.size());
            
            // 使用 Semaphore 控制并发数量（如果配置了parallelism）
            Semaphore semaphore = parallelism > 0 ? new Semaphore(parallelism) : null;
            
            for (Resource res : scripts) {
                // 使用虚拟线程执行每个脚本
                Thread.startVirtualThread(() -> {
                    // 如果配置了并发限制，先获取信号量
                    if (semaphore != null) {
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            latch.countDown();
                            return;
                        }
                    }
                    
                    String fileName = res.getFilename();
                    String version = versionOf(fileName);
                    String description = descriptionOf(fileName);
                    String scriptType = "INITDATA";
                    
                    try {
                        String currentChecksum = checksum(res);

                        ScriptExecutionRecord executionRecord =
                                /**
                                 * findscript执行record。
                                 * @param tableName table名称
                                 * @param tablePrefix table前缀
                                 * @param fileName 文件名称
                                 * @param version 版本
                                 */
                                findScriptExecutionRecord(tableName, tablePrefix, fileName, version);
                        if (hasSuccessfulExecution(executionRecord)) {
                            if (dataSourceScriptProperties.isVerbose()) {
                                log.debug("[异步] 跳过已执行的数据脚本: {} (v{})", fileName, version);
                            }
                            return;
                        }

                        if (executionRecord != null && dataSourceScriptProperties.isVerbose()) {
                            log.info("[异步] 检测到失败记录，重新执行数据脚本: {} (v{})", fileName, version);
                        }

                        long startTime = System.currentTimeMillis();
                        String success = "false";
                        
                        /**
                         * 尝试。
                         * @param ds.getConnection( ds.获取connection(
                         */
                        try (Connection scriptConnection = ds.getConnection()) {
                            boolean originalAutoCommit = scriptConnection.getAutoCommit();
                            scriptConnection.setAutoCommit(false);
                            
                            try {
                                EncodedResource encodedResource = new EncodedResource(res, dataSourceScriptProperties.getSqlScriptEncoding());
                                /**
                                 * 执行scriptwith进步。
                                 * @param scriptConnection scriptconnection
                                 * @param encodedResource encodedresource
                                 * @param fileName 文件名称
                                 */
                                executeScriptWithProgress(scriptConnection, encodedResource, fileName);
                                scriptConnection.commit();
                                success = "true";
                                successCount.incrementAndGet();
                                
                                long duration = System.currentTimeMillis() - startTime;
                                log.info("[异步] 成功执行数据脚本: {} (v{}) - 耗时: {}ms", fileName, version, duration);
                                
                            } catch (Exception e) {
                                try {
                                    scriptConnection.rollback();
                                } catch (SQLException rollbackEx) {
                                    log.error("[异步] 回滚事务失败", rollbackEx);
                                }
                                throw e;
                            } finally {
                                scriptConnection.setAutoCommit(originalAutoCommit);
                            }
                        }
                        
                        // 记录执行结果
                        saveScriptExecutionRecord(
                                tableName,
                                tablePrefix,
                                version,
                                fileName,
                                scriptType,
                                description,
                                currentChecksum,
                                success);
                        
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        failedScripts.add(fileName);
                        log.error("[异步] 执行数据脚本失败: {} (v{})", fileName, version, e);
                        
                        // 记录失败
                        try {
                            String currentChecksum = checksum(res);
                            saveScriptExecutionRecord(
                                    tableName,
                                    tablePrefix,
                                    version,
                                    fileName,
                                    scriptType,
                                    description,
                                    currentChecksum,
                                    "false");
                        } catch (Exception recordEx) {
                            log.error("[异步] 记录失败信息失败", recordEx);
                        }
                    } finally {
                        // 释放信号量
                        if (semaphore != null) {
                            semaphore.release();
                        }
                        latch.countDown();
                    }
                });
            }
            
            // 启动一个虚拟线程等待所有脚本执行完成并输出统计信息
            Thread.startVirtualThread(() -> {
                try {
                    boolean completed = latch.await(timeoutMinutes, TimeUnit.MINUTES);
                    if (completed) {
                        log.info("异步数据脚本执行完成: 成功 {} 个, 失败 {} 个", 
                                successCount.get(), failCount.get());
                        if (!failedScripts.isEmpty()) {
                            log.warn("失败的脚本: {}", failedScripts);
                        }
                    } else {
                        log.warn("异步数据脚本执行超时（{}分钟），部分脚本可能未完成", timeoutMinutes);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("等待异步脚本执行被中断");
                }
            });
        }

        /**
         * 执行SQL脚本并显示进度
         * 优化版本：使用批量执行提高性能，使用进步bar显示详细进度
         *
         * @param connection 数据库连接
         * @param resource 脚本资源
         * @param fileName 文件名（用于日志）
         * @throws SQLException SQL执行异常
         */
        private void executeScriptWithProgress(
                Connection connection,
                EncodedResource resource,
                String fileName) throws SQLException {
            ScriptUtils.executeSqlScript(
                    connection,
                    resource,
                    false,
                    dataSourceScriptProperties.isIgnoreFailedDrops(),
                    ScriptUtils.DEFAULT_COMMENT_PREFIXES,
                    dataSourceScriptProperties.getSeparator(),
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER
            );
        }

        /**
         * needs执行
         *
         * @param executionRecord 执行record
         * @return needs执行的结果
         */
        private boolean needsExecution(ScriptExecutionRecord executionRecord) {
            return executionRecord == null || !executionRecord.isSuccess();
        }

        /**
         * 是否拥有成功执行
         *
         * @param executionRecord 执行record
         * @return 是否包含成功执行的结果
         */
        private boolean hasSuccessfulExecution(ScriptExecutionRecord executionRecord) {
            return executionRecord != null && executionRecord.isSuccess();
        }

        /**
         * 查找script执行record
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param fileName 文件名称
         * @param version 版本
         * @param checksum 校验和
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param fileName 文件名称
         * @param version 版本
         * @param null 空
         * @param null 空
         * @param executionRecord 执行record
         * @param currentChecksum 当前校验和
         * @param fileName 文件名称
         * @param version 版本
         * @param storedChecksum 存储校验和
         * @param fileName 文件名称
         * @param version 版本
         * @param storedChecksum 存储校验和
         * @param currentChecksum 当前校验和
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param version 版本
         * @param fileName 文件名称
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param fileName 文件名称
         * @param version 版本
         * @param 0 0
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param version 版本
         * @param fileName 文件名称
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param checksum 校验和
         * @param success 成功
         * @param scripts script
         * @param scanMode 扫描mode
         * @param emptyDatabase 空database
         * @param scripts script
         * @param scanMode 扫描mode
         * @param scripts script
         * @param scanMode 扫描mode
         * @param hasBusinessData 是否包含业务数据
         * @param schema 模式
         * @param e e
         * @param e e
         * @param 列注释 列注释
         * @param KEY Key
         * @param AUTOINCREMENT AUTOINCREMENT
         * @param NULL 空
         * @param CURRENT_TIMESTAMP 当前_时间戳
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param e e
         * @param e e
         * @param tableName table名称
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param e e
         * @param e e
         * @param resolver 解析器
         * @param paths 路径
         * @param e e
         * @param path 路径
         * @param rawPaths raw路径
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param versionStart 版本启动
         * @param 0 0
         * @param separatorIndex separator索引
         * @param fileName 文件名称
         * @param 0 0
         * @param descriptionEnd description结束
         * @param res res
         * @param tableName table名称
         * @param originalPath 原始路径
         * @param databaseType database类型
         * @param lastDotIndex 最后一个dot索引
         */
        private ScriptExecutionRecord findScriptExecutionRecord(
                String tableName,
                String tablePrefix,
                String fileName,
                String version) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    String.format("SELECT %s_checksum AS checksum, %s_success AS success FROM %s WHERE %s_script_name = ? AND %s_version = ?",
                            tablePrefix, tablePrefix, tableName, tablePrefix, tablePrefix),
                    fileName, version);
            if (rows.isEmpty()) {
                return null;
            }

            Map<String, Object> row = rows.getFirst();
            return new ScriptExecutionRecord(
                    Objects.toString(row.get("checksum"), null),
                    Objects.toString(row.get("success"), null));
        }

        /**
         * 校验校验和ifnecessary
         * @param executionRecord 执行record
         * @param currentChecksum 当前校验和
         * @param fileName 文件名称
         * @param version 版本
         * @param storedChecksum 存储校验和
         * @param fileName 文件名称
         * @param version 版本
         * @param storedChecksum 存储校验和
         * @param currentChecksum 当前校验和
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param version 版本
         * @param fileName 文件名称
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param fileName 文件名称
         * @param version 版本
         * @param 0 0
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param version 版本
         * @param fileName 文件名称
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param checksum 校验和
         * @param success 成功
         * @param scripts script
         * @param scanMode 扫描mode
         * @param emptyDatabase 空database
         * @param scripts script
         * @param scanMode 扫描mode
         * @param scripts script
         * @param scanMode 扫描mode
         * @param hasBusinessData 是否包含业务数据
         * @param schema 模式
         * @param e e
         * @param e e
         * @param 列注释 列注释
         * @param KEY Key
         * @param AUTOINCREMENT AUTOINCREMENT
         * @param NULL 空
         * @param CURRENT_TIMESTAMP 当前_时间戳
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param e e
         * @param e e
         * @param tableName table名称
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param e e
         * @param e e
         * @param resolver 解析器
         * @param paths 路径
         * @param e e
         * @param path 路径
         * @param rawPaths raw路径
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param versionStart 版本启动
         * @param 0 0
         * @param separatorIndex separator索引
         * @param fileName 文件名称
         * @param 0 0
         * @param descriptionEnd description结束
         * @param res res
         * @param tableName table名称
         * @param originalPath 原始路径
         * @param databaseType database类型
         * @param lastDotIndex 最后一个dot索引
         */
        private void validateChecksumIfNecessary(
                ScriptExecutionRecord executionRecord,
                String currentChecksum,
                String fileName,
                String version) throws SQLException {
            if (!dataSourceScriptProperties.isValidateChecksum() || executionRecord == null) {
                return;
            }

            String storedChecksum = executionRecord.checksum();
            if (Objects.equals(currentChecksum, storedChecksum)) {
                return;
            }

            String errorMsg = String.format("脚本 %s (版本: %s) 的校验和不匹配。期望值: %s, 实际值: %s",
                    fileName, version, storedChecksum, currentChecksum);
            log.error(errorMsg);
            if (!dataSourceScriptProperties.isContinueOnError()) {
                /**
                 * SQL异常。
                 * @param errorMsg 错误msg
                 * @return sql异常的结果
                 */
                throw new SQLException(errorMsg);
            }
        }

        /**
         * 保存script执行record
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param version 版本
         * @param fileName 文件名称
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param fileName 文件名称
         * @param version 版本
         * @param 0 0
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param version 版本
         * @param fileName 文件名称
         * @param scriptType script类型
         * @param description description
         * @param checksum 校验和
         * @param success 成功
         * @param checksum 校验和
         * @param success 成功
         * @param scripts script
         * @param scanMode 扫描mode
         * @param emptyDatabase 空database
         * @param scripts script
         * @param scanMode 扫描mode
         * @param scripts script
         * @param scanMode 扫描mode
         * @param hasBusinessData 是否包含业务数据
         * @param schema 模式
         * @param e e
         * @param e e
         * @param 列注释 列注释
         * @param KEY Key
         * @param AUTOINCREMENT AUTOINCREMENT
         * @param NULL 空
         * @param CURRENT_TIMESTAMP 当前_时间戳
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param e e
         * @param e e
         * @param tableName table名称
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param e e
         * @param e e
         * @param resolver 解析器
         * @param paths 路径
         * @param e e
         * @param path 路径
         * @param rawPaths raw路径
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param versionStart 版本启动
         * @param 0 0
         * @param separatorIndex separator索引
         * @param fileName 文件名称
         * @param 0 0
         * @param descriptionEnd description结束
         * @param res res
         * @param tableName table名称
         * @param originalPath 原始路径
         * @param databaseType database类型
         * @param lastDotIndex 最后一个dot索引
         */
        private void saveScriptExecutionRecord(
                String tableName,
                String tablePrefix,
                String version,
                String fileName,
                String scriptType,
                String description,
                String checksum,
                String success) {
            int updated = jdbc.update(
                    String.format("UPDATE %s SET %s_script_type = ?, %s_description = ?, %s_checksum = ?, %s_success = ?, %s_executed_on = CURRENT_TIMESTAMP WHERE %s_script_name = ? AND %s_version = ?",
                            tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                    scriptType, description, checksum, success, fileName, version);
            if (updated > 0) {
                return;
            }

            jdbc.update(
                    String.format("INSERT INTO %s(%s_version,%s_script_name,%s_script_type,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?,?,?)",
                            tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                    version, fileName, scriptType, description, checksum, success);
        }

        /* ---------- 工具方法 ---------- */

        /**
         * script执行record
         * @param checksum 校验和
         * @param checksum 字符串
         * @param success 成功
         * @return script执行record的结果
         */
        private record ScriptExecutionRecord(String checksum, String success) {
            /**
             * 是否成功
             *
             * @return 是否成功的结果
             */
            private boolean isSuccess() {
                return "true".equalsIgnoreCase(success);
            }
        }

        /**
         * 过滤模式script
         * @param scripts script
         * @param scanMode 扫描mode
         * @param emptyDatabase 空database
         * @param scripts script
         * @param scanMode 扫描mode
         * @param scripts script
         * @param scanMode 扫描mode
         * @param hasBusinessData 是否包含业务数据
         * @param schema 模式
         * @param e e
         * @param e e
         * @param 列注释 列注释
         * @param KEY Key
         * @param AUTOINCREMENT AUTOINCREMENT
         * @param NULL 空
         * @param CURRENT_TIMESTAMP 当前_时间戳
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param e e
         * @param e e
         * @param tableName table名称
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param e e
         * @param e e
         * @param resolver 解析器
         * @param paths 路径
         * @param e e
         * @param path 路径
         * @param rawPaths raw路径
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param versionStart 版本启动
         * @param 0 0
         * @param separatorIndex separator索引
         * @param fileName 文件名称
         * @param 0 0
         * @param descriptionEnd description结束
         * @param res res
         * @param tableName table名称
         * @param originalPath 原始路径
         * @param databaseType database类型
         * @param lastDotIndex 最后一个dot索引
         */
        private List<Resource> filterSchemaScripts(
                Collection<Resource> scripts,
                DataSourceScriptProperties.ScanMode scanMode,
                Boolean emptyDatabase) {
            if (scanMode == DataSourceScriptProperties.ScanMode.NONE) {
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("表结构脚本扫描模式为 NONE，跳过 {} 个表结构脚本", scripts.size());
                }
                return List.of();
            }

            if (scanMode == DataSourceScriptProperties.ScanMode.ONCE && Boolean.FALSE.equals(emptyDatabase)) {
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("表结构脚本扫描模式为 ONCE，检测到当前库已有表，跳过 {} 个表结构脚本", scripts.size());
                }
                return List.of();
            }

            return new ArrayList<>(scripts);
        }

        /**
         * 过滤migrationscript
         * @param scripts script
         * @param scanMode 扫描mode
         * @param scripts script
         * @param scanMode 扫描mode
         * @param hasBusinessData 是否包含业务数据
         * @param schema 模式
         * @param e e
         * @param e e
         * @param 列注释 列注释
         * @param KEY Key
         * @param AUTOINCREMENT AUTOINCREMENT
         * @param NULL 空
         * @param CURRENT_TIMESTAMP 当前_时间戳
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param e e
         * @param e e
         * @param tableName table名称
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param e e
         * @param e e
         * @param resolver 解析器
         * @param paths 路径
         * @param e e
         * @param path 路径
         * @param rawPaths raw路径
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param versionStart 版本启动
         * @param 0 0
         * @param separatorIndex separator索引
         * @param fileName 文件名称
         * @param 0 0
         * @param descriptionEnd description结束
         * @param res res
         * @param tableName table名称
         * @param originalPath 原始路径
         * @param databaseType database类型
         * @param lastDotIndex 最后一个dot索引
         */
        private List<Resource> filterMigrationScripts(
                Collection<Resource> scripts,
                DataSourceScriptProperties.RepeatableScanMode scanMode) {
            if (scanMode == DataSourceScriptProperties.RepeatableScanMode.NONE) {
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("补丁脚本扫描模式为 NONE，跳过 {} 个补丁脚本", scripts.size());
                }
                return List.of();
            }
            return new ArrayList<>(scripts);
        }

        /**
         * 过滤数据script
         * @param scripts script
         * @param scanMode 扫描mode
         * @param hasBusinessData 是否包含业务数据
         * @param schema 模式
         * @param e e
         * @param e e
         * @param 列注释 列注释
         * @param KEY Key
         * @param AUTOINCREMENT AUTOINCREMENT
         * @param NULL 空
         * @param CURRENT_TIMESTAMP 当前_时间戳
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tableName table名称
         * @param e e
         * @param e e
         * @param tableName table名称
         * @param tableName table名称
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param tablePrefix table前缀
         * @param e e
         * @param e e
         * @param resolver 解析器
         * @param paths 路径
         * @param e e
         * @param path 路径
         * @param rawPaths raw路径
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param fileName 文件名称
         * @param versionStart 版本启动
         * @param 0 0
         * @param separatorIndex separator索引
         * @param fileName 文件名称
         * @param 0 0
         * @param descriptionEnd description结束
         * @param res res
         * @param tableName table名称
         * @param originalPath 原始路径
         * @param databaseType database类型
         * @param lastDotIndex 最后一个dot索引
         */
        private List<Resource> filterDataScripts(
                Collection<Resource> scripts,
                DataSourceScriptProperties.ScanMode scanMode,
                Boolean hasBusinessData) {
            if (scanMode == DataSourceScriptProperties.ScanMode.NONE) {
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("初始化数据脚本扫描模式为 NONE，跳过 {} 个数据脚本", scripts.size());
                }
                return List.of();
            }

            if (scanMode == DataSourceScriptProperties.ScanMode.ONCE && Boolean.TRUE.equals(hasBusinessData)) {
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("初始化数据脚本扫描模式为 ONCE，检测到当前库已有业务数据，跳过 {} 个数据脚本", scripts.size());
                }
                return List.of();
            }

            return new ArrayList<>(scripts);
        }

        /**
         * 判断当前数据源对应的库是否为空
         * <p>
         * 根据 scope 配置决定判断方式：
         * <ul>
         *   <li>scope=table（默认）：排除配置的系统表前缀（如 sys_），仅检查业务表是否存在</li>
         *   <li>scope=file：检查所有表，只要有一个表就认为非空（原行为）</li>
         * </ul>
         * </p>
         *
         * @return true-空库, false-已有表, 空-无法判断
         */
        private Boolean isDatabaseEmpty() {
            /**
             * 尝试。
             * @param ds.getConnection( ds.获取connection(
             */
            try (Connection connection = ds.getConnection()) {
                String catalog = connection.getCatalog();
                String schema = connection.getSchema();
                boolean isTableScope = "table".equals(dataSourceScriptProperties.getScope());
                String[] excludePrefixes = dataSourceScriptProperties.getExcludeTablePrefixes();

                /**
                 * 尝试。
                 * @param connection.getMetaData( connection.获取meta数据(
                 */
                try (var tables = connection.getMetaData().getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                    tableLoop:
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        if (!StringUtils.hasText(tableName)) {
                            continue;
                        }
                        // table 模式下，跳过排除前缀的表
                        if (isTableScope && excludePrefixes != null) {
                            for (String prefix : excludePrefixes) {
                                if (tableName.startsWith(prefix)) {
                                    continue tableLoop;
                                }
                            }
                        }
                        return false;
                    }
                    return true;
                }
            } catch (Exception e) {
                log.warn("判断当前库是否为空失败: {}", e.getMessage());
                return null;
            }
        }

        /**
         * 判断当前库是否已有业务数据
         * <p>
         * 根据 scope 配置决定判断方式：
         * <ul>
         *   <li>scope=table（默认）：排除配置的系统表前缀（如 sys_），仅检查业务表是否有数据</li>
         *   <li>scope=file：检查所有表（原行为）</li>
         * </ul>
         * </p>
         *
         * @return true-已有数据, false-无数据, 空-无法判断
         */
        private Boolean hasBusinessData() {
            String versionTableName = extractTablePrefix(dataSourceScriptProperties.getVersionTable());
            boolean isTableScope = "table".equals(dataSourceScriptProperties.getScope());
            String[] excludePrefixes = dataSourceScriptProperties.getExcludeTablePrefixes();

            /**
             * 尝试。
             * @param ds.getConnection( ds.获取connection(
             */
            try (Connection connection = ds.getConnection();
                 var tables = connection.getMetaData().getTables(
                         connection.getCatalog(),
                         connection.getSchema(),
                         "%",
                         new String[]{"TABLE"})) {
                tableLoop:
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (!StringUtils.hasText(tableName)
                            || tableName.equalsIgnoreCase(versionTableName)) {
                        continue;
                    }
                    // table 模式下，跳过排除前缀的表
                    if (isTableScope && excludePrefixes != null) {
                        for (String prefix : excludePrefixes) {
                            if (tableName.startsWith(prefix)) {
                                continue tableLoop;
                            }
                        }
                    }

                    /**
                     * 尝试。
                     * @param connection.createStatement( connection.创建对账单(
                     */
                    try (var statement = connection.createStatement();
                         var rows = statement.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1")) {
                        if (rows.next()) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (Exception e) {
                log.warn("判断当前库是否已有业务数据失败: {}", e.getMessage());
                return null;
            }
        }

        /**
         * 创建版本记录表
         * <p>
         * 兼容多种数据库类型, 字段名使用表名前缀。
         * MySQL/PostgreSQL 保持原语法 (含 评论 列注释);
         * sqlite/H2 不支持列注释和 UNIQUE Key, 自动降级为方言兼容语法。
         * </p>
         */
        private void createVersionTable() {
            String tableName = dataSourceScriptProperties.getVersionTable();
 // 提取表名作为字段前缀（去掉可能的模式前缀）
            String tablePrefix = extractTablePrefix(tableName);

            boolean sqlite = isSqliteDatabase();

            String createTableSql;
            if (sqlite) {
                createTableSql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        %s_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        %s_version VARCHAR(32),
                        %s_script_name VARCHAR(200) NOT NULL,
                        %s_script_type VARCHAR(16),
                        %s_description VARCHAR(100),
                        %s_checksum VARCHAR(64),
                        %s_executed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        %s_success VARCHAR(8),
                        CONSTRAINT uk_script_name_version UNIQUE (%s_script_name, %s_version)
                    )
                    """, tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix);
            } else {
                createTableSql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        %s_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
                        %s_version VARCHAR(32) COMMENT '脚本版本号',
                        %s_script_name VARCHAR(200) NOT NULL COMMENT '脚本文件名',
                        %s_script_type VARCHAR(16) COMMENT '脚本类型：INIT-初始化，ADD-增量',
                        %s_description VARCHAR(100) COMMENT '脚本描述信息',
                        %s_checksum VARCHAR(64) COMMENT '脚本文件MD5校验和',
                        %s_executed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '脚本执行时间',
                        %s_success VARCHAR(8) COMMENT '执行状态：true-成功，false-失败',
                        UNIQUE KEY uk_script_name_version (%s_script_name, %s_version)
                    )
                    """, tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix);
            }

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
         * 判断当前数据源是否 sqlite。
         *
         * @return 是否 sqlite
         */
        private boolean isSqliteDatabase() {
            /**
             * 尝试。
             * @param ds.getConnection( ds.获取connection(
             */
            try (java.sql.Connection connection = ds.getConnection()) {
                String productName = connection.getMetaData().getDatabaseProductName();
                if (productName == null) {
                    return false;
                }
                return productName.toLowerCase(java.util.Locale.ROOT).contains("sqlite");
            } catch (java.sql.SQLException e) {
                log.debug("判断数据库类型失败: {}", e.getMessage());
                return false;
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
                            String.format("INSERT INTO %s(%s_version,%s_script_name,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?,?)",
                                    tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                            dataSourceScriptProperties.getBaselineVersion(),
                            "BASELINE",
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
         * 通过 服务加载 加载各模块提供的数据库脚本
         * <p>
         * 使用 SPI 机制自动发现并加载实现了 database文件提供者 接口的模块
         * </p>
         *
         * @return 所有模块提供的脚本资源列表
         */
        private List<Resource> loadResourcesFromProviders() {
            List<Resource> resources = new ArrayList<>();
            ServiceLoader<DatabaseFileProvider> providers = ServiceLoader.load(DatabaseFileProvider.class);
            
            // 收集并排序提供者
            List<DatabaseFileProvider> sortedProviders = new ArrayList<>();
            for (DatabaseFileProvider provider : providers) {
                sortedProviders.add(provider);
            }
            sortedProviders.sort(Comparator.comparingInt(DatabaseFileProvider::getOrder));
            
            for (DatabaseFileProvider provider : sortedProviders) {
                try {
                    if (provider.isSupported()) {
                        List<Resource> providerResources = provider.getResources();
                        if (providerResources != null && !providerResources.isEmpty()) {
                            resources.addAll(providerResources);
                            if (dataSourceScriptProperties.isVerbose()) {
                                log.info("从 {} 加载 {} 个数据库脚本 (order={})", 
                                        provider.getName(), 
                                        providerResources.size(),
                                        provider.getOrder());
                            }
                        }
                    } else {
                        if (dataSourceScriptProperties.isVerbose()) {
                            log.debug("跳过未启用的数据库脚本提供者: {}", provider.getName());
                        }
                    }
                } catch (Exception e) {
                    log.warn("加载 {} 的数据库脚本失败: {}", provider.getName(), e.getMessage());
                }
            }
            
            return resources;
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
                if (!StringUtils.hasText(path)) {
                    continue;
                }
                try {
                    Resource[] resources = resolver.getResources(path.trim());
                    allResources.addAll(Arrays.asList(resources));
                    if (dataSourceScriptProperties.isVerbose()) {
                        log.debug("从路径 {} 扫描到 {} 个脚本", path.trim(), resources.length);
                    }
                } catch (IOException e) {
                    log.debug("路径不存在或无法访问: {}", path);
                }
            }
            return allResources;
        }

        /**
         * 分割script路径
         *
         * @param rawPaths raw路径
         * @return 分割script路径的结果
         */
        private List<String> splitScriptPaths(String rawPaths) {
            if (!StringUtils.hasText(rawPaths)) {
                return List.of(DataSourceScriptProperties.DEFAULT_SCRIPT_PATH.split(","));
            }

            List<String> paths = Arrays.stream(rawPaths.split("[,;\\r\\n]+"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            if (!paths.isEmpty()) {
                return paths;
            }

            return List.of(DataSourceScriptProperties.DEFAULT_SCRIPT_PATH.split(","));
        }

        /**
         * 获取脚本类型
         * 根据文件名格式判断：
         * <ul>
         *   <li>V版本号__init_任意.sql     → INIT（初始化表结构脚本，同步执行）</li>
         *   <li>V版本号__initdata_任意.sql → INITDATA（初始化数据脚本，异步执行）</li>
         *   <li>V版本号__add_任意.sql      → ADD（增量脚本，同步执行）</li>
         *   <li>V版本号__任意.sql          → NORMAL（普通脚本，同步执行）</li>
         *   <li>其他格式                  → null（不执行）</li>
         * </ul>
         *
         * @param fileName 文件名
         * @return 脚本类型：INIT、INITDATA、ADD、NORMAL 或 空（不符合格式）
         */
        private String getScriptType(String fileName) {
            String separator = dataSourceScriptProperties.getVersionSeparator();
            
            // 查找分隔符位置
            int separatorIndex = fileName.indexOf(separator);
            if (separatorIndex == -1) {
                // 不符合格式，跳过
                return null;
            }
            
            // 获取分隔符后的描述部分
            String afterSeparator = fileName.substring(separatorIndex + separator.length());
            
 // 判断脚本类型（注意：initdata_ 必须在 初始化_ 之前判断，因为 initdata_ 也以 初始化 开头）
            if (afterSeparator.startsWith("initdata_")) {
                return "INITDATA";
            } else if (afterSeparator.startsWith("init_")) {
                return "INIT";
            } else if (afterSeparator.startsWith("add_")) {
                return "ADD";
            } else if (!afterSeparator.isEmpty() && !afterSeparator.startsWith("_")) {
 // 普通脚本：V版本号__任意.SQL（不以下划线开头）
                return "NORMAL";
            }
            
            // 不符合规范格式，不执行
            return null;
        }

        /**
         * 检查脚本的版本类型是否允许执行
         * 根据配置的 release类型 或 allowedrelease类型 判断
         *
         * @param fileName 文件名
         * @return 是否允许执行
         */
        private boolean isReleaseTypeAllowed(String fileName) {
            // 解析版本号
            String versionStr = versionOf(fileName);
            Version version = Version.of(versionStr);
            String suffix = version.getSuffix();
            
            // 获取脚本的版本类型
            DataSourceScriptProperties.ReleaseType scriptReleaseType = 
                    DataSourceScriptProperties.ReleaseType.fromSuffix(suffix);
            
 // 如果配置了 allowedrelease类型，使用精确匹配
            if (dataSourceScriptProperties.getAllowedReleaseTypes() != null 
                    && !dataSourceScriptProperties.getAllowedReleaseTypes().isEmpty()) {
                return dataSourceScriptProperties.getAllowedReleaseTypes().contains(scriptReleaseType)
                        || dataSourceScriptProperties.getAllowedReleaseTypes()
                                .contains(DataSourceScriptProperties.ReleaseType.ALL);
            }
            
 // 否则使用 release类型 的优先级判断
            DataSourceScriptProperties.ReleaseType configuredType = dataSourceScriptProperties.getReleaseType();
            return configuredType.isAllowed(scriptReleaseType);
        }

        /**
         * 从文件名中提取版本号
         * 例如: V1.0.0__创建_用户.SQL -> 1.0.0
         *
         * @param fileName 文件名
         * @return 版本号
         */
        private String versionOf(String fileName) {
            String prefix = dataSourceScriptProperties.getScriptPrefix();
            String suffix = dataSourceScriptProperties.getScriptSuffix();
            String separator = dataSourceScriptProperties.getVersionSeparator();
            if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
                return fileName;
            }

            int versionStart = prefix.length();
            int separatorIndex = fileName.indexOf(separator, versionStart);
            if (separatorIndex < 0) {
                return fileName;
            }

            return fileName.substring(versionStart, separatorIndex);
        }

        /**
         * 从文件名中提取描述信息
         * 例如: V1.0.0__创建_用户.SQL -> 创建_用户
         *
         * @param fileName 文件名
         * @return 描述信息
         */
        private String descriptionOf(String fileName) {
            String suffix = dataSourceScriptProperties.getScriptSuffix();
            String separator = dataSourceScriptProperties.getVersionSeparator();
            if (!fileName.endsWith(suffix)) {
                return fileName;
            }

            int separatorIndex = fileName.indexOf(separator);
            if (separatorIndex < 0) {
                return fileName;
            }

            int descriptionStart = separatorIndex + separator.length();
            int descriptionEnd = fileName.length() - suffix.length();
            if (descriptionStart >= descriptionEnd) {
                return fileName;
            }

            String description = fileName.substring(descriptionStart, descriptionEnd);
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
         * 提取表名前缀
         * 例如：db_版本_历史 -> db_版本_历史
         * 模式.db_版本 -> db_版本
         *
         * @param tableName 表名（可能包含模式）
         * @return 表名前缀
         */
        private String extractTablePrefix(String tableName) {
 // 如果包含模式前缀（如 模式.table），只取表名部分
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
