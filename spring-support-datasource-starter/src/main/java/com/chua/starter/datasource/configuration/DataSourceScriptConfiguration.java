package com.chua.starter.datasource.configuration;

import com.chua.common.support.lang.process.ProgressBar;
import com.chua.common.support.lang.process.ProgressBarBuilder;
import com.chua.common.support.lang.process.ProgressBarStyle;
import com.chua.common.support.lang.version.Version;
import com.chua.common.support.utils.ObjectUtils;
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
import org.springframework.util.DigestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 数据源脚本配置类
 * <p>
 * 实现 BeanPostProcessor 接口，在 DataSource 初始化完成后立即执行数据库脚本，
 * 确保脚本在其他依赖数据库的 Bean（如 EntityManagerFactory、SqlSessionFactory）之前执行。
 * </p>
 *
 * @author CH
 * @since 2025/9/3 9:07
 */
@Slf4j
public class DataSourceScriptConfiguration implements BeanPostProcessor {

    private final DataSourceScriptProperties dataSourceScriptProperties;

    /**
     * 已处理的 DataSource Bean 名称集合，避免重复执行
     */
    private final Set<String> processedDataSources = Collections.synchronizedSet(new HashSet<>());

    public DataSourceScriptConfiguration(DataSourceScriptProperties dataSourceScriptProperties) {
        this.dataSourceScriptProperties = dataSourceScriptProperties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource ds && dataSourceScriptProperties.isEnable()) {
            // 避免重复处理同一个 DataSource
            if (processedDataSources.add(beanName)) {
                try {
                    log.info("检测到 DataSource [{}] 初始化完成，开始执行数据库脚本...", beanName);
                    FlywayLikePopulator populator = new FlywayLikePopulator(ds, dataSourceScriptProperties);
                    try (Connection connection = ds.getConnection()) {
                        populator.populate(connection);
                    }
                    log.info("DataSource [{}] 数据库脚本执行完成", beanName);
                } catch (SQLException e) {
                    throw new RuntimeException("执行数据库脚本失败: " + beanName, e);
                }
            }
        }
        return bean;
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
     * @author CH
     * @version 1.2.0
     * @since 2025/9/3 9:07
     */
    static class FlywayLikePopulator {

        private final DataSource ds;
        private final JdbcTemplate jdbc;
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
         * @param connection 数据库连接
         * @throws SQLException SQL异常
         */
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

                // 通过 ServiceLoader 加载各模块提供的数据库脚本
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
                // INIT 脚本：同名脚本只执行最高版本（同步执行）
                // ADD/NORMAL 脚本：全部执行，按脚本名+版本判断是否已执行（同步执行）
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
                        // INIT 脚本按描述分组，使用 Version 比较保留最高版本
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
                        // ADD 和 NORMAL 脚本全部加入同步执行列表
                        otherScriptsToExecute.add(res);
                    }
                }
                
                if (skippedCount > 0) {
                    log.info("跳过 {} 个不符合格式的脚本", skippedCount);
                }

                // 合并同步执行脚本列表：先执行 INIT（表结构），再执行 ADD/NORMAL
                List<Resource> scriptsToExecute = new ArrayList<>();
                scriptsToExecute.addAll(initScriptsToExecute.values());
                scriptsToExecute.addAll(otherScriptsToExecute);
                
                if (dataSourceScriptProperties.isVerbose()) {
                    log.info("同步脚本: {} 个, 异步数据脚本: {} 个", scriptsToExecute.size(), initDataScriptsToExecute.size());
                }

                String tableName = dataSourceScriptProperties.getVersionTable();
                String tablePrefix = extractTablePrefix(tableName);

                // 第一遍遍历：统计需要实际执行的脚本数量（排除已执行的）
                int actualScriptsToExecute = 0;
                for (Resource res : scriptsToExecute) {
                    String fileName = Objects.requireNonNull(res.getFilename());
                    String version = versionOf(fileName);
                    
                    Integer count = jdbc.queryForObject(
                            String.format("SELECT COUNT(*) FROM %s WHERE %s_script_name = ? AND %s_version = ?",
                                    tableName, tablePrefix, tablePrefix),
                            Integer.class, fileName, version);
                    
                    if (count == null || count == 0) {
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

                    // 5. 检查是否已执行过（根据脚本名称+版本一起判断）
                    Integer count = jdbc.queryForObject(
                            String.format("SELECT COUNT(*) FROM %s WHERE %s_script_name = ? AND %s_version = ?",
                                    tableName, tablePrefix, tablePrefix),
                            Integer.class, fileName, version);

                        if (count != null && count > 0) {
                            // 如果启用校验和验证，检查脚本是否被修改
                            if (dataSourceScriptProperties.isValidateChecksum()) {
                                String storedChecksum = jdbc.queryForObject(
                                        String.format("SELECT %s_checksum FROM %s WHERE %s_script_name = ? AND %s_version = ?",
                                                tablePrefix, tableName, tablePrefix, tablePrefix),
                                        String.class, fileName, version);
                                if (!currentChecksum.equals(storedChecksum)) {
                                    String errorMsg = String.format("脚本 %s (版本: %s) 的校验和不匹配。期望值: %s, 实际值: %s",
                                            fileName, version, storedChecksum, currentChecksum);
                                    log.error(errorMsg);
                                    if (!dataSourceScriptProperties.isContinueOnError()) {
                                        throw new SQLException(errorMsg);
                                    }
                                }
                            }

                            if (dataSourceScriptProperties.isVerbose()) {
                                log.info("跳过已执行的脚本: {} (版本: {})", fileName, version);
                            }
                            continue;
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
                        jdbc.update(
                                String.format("INSERT INTO %s(%s_version,%s_script_name,%s_script_type,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?,?,?)",
                                        tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                                version, fileName, scriptType, description, currentChecksum, success);

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
                                jdbc.update(
                                        String.format("INSERT INTO %s(%s_version,%s_script_name,%s_script_type,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?,?,?)",
                                                tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                                        version, fileName, scriptType, description, currentChecksum, "false");
                            } catch (Exception recordException) {
                                log.error("记录迁移失败信息失败", recordException);
                            }

                            // 更新总体进度条（即使失败也要更新）
                            if (overallProgressBar != null) {
                                overallProgressBar.step();
                            }

                            if (!dataSourceScriptProperties.isContinueOnError()) {
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
                    executeInitDataScriptsAsync(initDataScriptsToExecute, tableName, tablePrefix);
                }
            } catch (IOException e) {
                throw new RuntimeException("读取脚本资源失败", e);
            }
        }

        /**
         * 使用虚拟线程异步执行 INITDATA 脚本
         * <p>
         * 这些脚本主要是初始化数据（INSERT语句），不影响系统启动
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
            
            // 使用 CountDownLatch 等待所有脚本执行完成
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
                        
                        // 检查是否已执行过
                        Integer count = jdbc.queryForObject(
                                String.format("SELECT COUNT(*) FROM %s WHERE %s_script_name = ? AND %s_version = ?",
                                        tableName, tablePrefix, tablePrefix),
                                Integer.class, fileName, version);
                        
                        if (count != null && count > 0) {
                            if (dataSourceScriptProperties.isVerbose()) {
                                log.debug("[异步] 跳过已执行的数据脚本: {} (v{})", fileName, version);
                            }
                            return;
                        }
                        
                        long startTime = System.currentTimeMillis();
                        String success = "false";
                        
                        try (Connection scriptConnection = ds.getConnection()) {
                            boolean originalAutoCommit = scriptConnection.getAutoCommit();
                            scriptConnection.setAutoCommit(false);
                            
                            try {
                                EncodedResource encodedResource = new EncodedResource(res, dataSourceScriptProperties.getSqlScriptEncoding());
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
                        jdbc.update(
                                String.format("INSERT INTO %s(%s_version,%s_script_name,%s_script_type,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?,?,?)",
                                        tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                                version, fileName, scriptType, description, currentChecksum, success);
                        
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        failedScripts.add(fileName);
                        log.error("[异步] 执行数据脚本失败: {} (v{})", fileName, version, e);
                        
                        // 记录失败
                        try {
                            String currentChecksum = checksum(res);
                            jdbc.update(
                                    String.format("INSERT INTO %s(%s_version,%s_script_name,%s_script_type,%s_description,%s_checksum,%s_success) VALUES(?,?,?,?,?,?)",
                                            tableName, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix, tablePrefix),
                                    version, fileName, scriptType, description, currentChecksum, "false");
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
         * 优化版本：使用批量执行提高性能，使用ProgressBar显示详细进度
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
            
            try {
                // 读取脚本内容
                String script = new String(resource.getResource().getInputStream().readAllBytes(),
                        ObjectUtils.defaultIfNull(resource.getCharset(), StandardCharsets.UTF_8));
                
                // 先移除多行注释 /* ... */（避免注释中的分号干扰分割）
                script = removeBlockComments(script);
                
                // 分割SQL语句
                String separator = dataSourceScriptProperties.getSeparator();
                String[] statements = script.split(separator);
                
                // 过滤有效的SQL语句
                List<String> validStatements = new ArrayList<>();
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    // 跳过空语句和单行注释
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        validStatements.add(trimmed);
                    }
                }
                
                int totalStatements = validStatements.size();
                int batchSize = 50; // 批量执行大小
                
                // 创建脚本级进度条（仅当语句数量较多且开启详细模式时）
                ProgressBar scriptProgressBar = null;
                if (dataSourceScriptProperties.isVerbose() && totalStatements > 100) {
                    scriptProgressBar = new ProgressBarBuilder()
                            .setTaskName(String.format("  执行 %s", fileName))
                            .setInitialMax(totalStatements)
                            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
                            .setUnit("条SQL", 1)
                            .build();
                }
                
                try (var stmt = connection.createStatement()) {
                    for (int i = 0; i < validStatements.size(); i++) {
                        String sql = validStatements.get(i);
                        
                        // 添加到批处理
                        stmt.addBatch(sql);
                        
                        // 每50条执行一次批处理，或者到达最后一条
                        if ((i + 1) % batchSize == 0 || i == validStatements.size() - 1) {
                            stmt.executeBatch();
                            stmt.clearBatch();
                            
                            // 更新进度条
                            if (scriptProgressBar != null) {
                                int executed = Math.min(i + 1, totalStatements);
                                scriptProgressBar.stepTo(executed);
                            }
                        }
                    }
                } finally {
                    // 关闭脚本级进度条
                    if (scriptProgressBar != null) {
                        scriptProgressBar.close();
                    }
                }
                
            } catch (IOException e) {
                throw new SQLException("读取脚本文件失败: " + fileName, e);
            } catch (SQLException e) {
                log.error("执行脚本失败: {}", fileName, e);
                if (!dataSourceScriptProperties.isContinueOnError()) {
                    throw e;
                }
            }
        }

        /* ---------- 工具方法 ---------- */

        /**
         * 移除SQL脚本中的多行注释
         * <p>
         * 避免注释中的分号干扰SQL语句分割
         * </p>
         *
         * @param script SQL脚本内容
         * @return 移除多行注释后的脚本
         */
        private String removeBlockComments(String script) {
            StringBuilder result = new StringBuilder();
            int i = 0;
            while (i < script.length()) {
                // 检查是否是多行注释开始
                if (i < script.length() - 1 && script.charAt(i) == '/' && script.charAt(i + 1) == '*') {
                    // 跳过整个注释块
                    i += 2;
                    while (i < script.length() - 1) {
                        if (script.charAt(i) == '*' && script.charAt(i + 1) == '/') {
                            i += 2;
                            break;
                        }
                        i++;
                    }
                } else {
                    result.append(script.charAt(i));
                    i++;
                }
            }
            return result.toString();
        }

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
         * 通过 ServiceLoader 加载各模块提供的数据库脚本
         * <p>
         * 使用 SPI 机制自动发现并加载实现了 DatabaseFileProvider 接口的模块
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
         * @return 脚本类型：INIT、INITDATA、ADD、NORMAL 或 null（不符合格式）
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
            
            // 判断脚本类型（注意：initdata_ 必须在 init_ 之前判断，因为 initdata_ 也以 init 开头）
            if (afterSeparator.startsWith("initdata_")) {
                return "INITDATA";
            } else if (afterSeparator.startsWith("init_")) {
                return "INIT";
            } else if (afterSeparator.startsWith("add_")) {
                return "ADD";
            } else if (!afterSeparator.isEmpty() && !afterSeparator.startsWith("_")) {
                // 普通脚本：V版本号__任意.sql（不以下划线开头）
                return "NORMAL";
            }
            
            // 不符合规范格式，不执行
            return null;
        }

        /**
         * 检查脚本的版本类型是否允许执行
         * 根据配置的 releaseType 或 allowedReleaseTypes 判断
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
            
            // 如果配置了 allowedReleaseTypes，使用精确匹配
            if (dataSourceScriptProperties.getAllowedReleaseTypes() != null 
                    && !dataSourceScriptProperties.getAllowedReleaseTypes().isEmpty()) {
                return dataSourceScriptProperties.getAllowedReleaseTypes().contains(scriptReleaseType)
                        || dataSourceScriptProperties.getAllowedReleaseTypes()
                                .contains(DataSourceScriptProperties.ReleaseType.ALL);
            }
            
            // 否则使用 releaseType 的优先级判断
            DataSourceScriptProperties.ReleaseType configuredType = dataSourceScriptProperties.getReleaseType();
            return configuredType.isAllowed(scriptReleaseType);
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
