package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.entity.OrderPartitionConfig;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.OrderPartitionConfigMapper;
import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.payment.support.vo.OrderPartitionConfigVO;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 订单分表配置服务实现，管理支付订单的按时间分片策略及预创建任务。
 *
 * @author CH
 * @since 4.0.0.50
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderPartitionConfigServiceImpl implements OrderPartitionConfigService {

    /**
     * 订单分表月份格式化器
     */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 订单分表日期格式化器
     */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 订单分表默认配置映射
     */
    private static final Map<String, OrderPartitionConfig> DEFAULTS = createDefaults();

    /**
     * 订单分区配置映射器
     */
    private final OrderPartitionConfigMapper orderPartitionConfigMapper;
    /**
     * 数据源，用于分表结构元数据校验与方言识别
     */
    private final DataSource dataSource;

    @PostConstruct
    /**
     * 初始化默认配置
     */
    public void initDefaults() {
        /**
         * 同步默认配置
         */
        syncDefaults();
    }

    @Override
    /**
     * 列表分表配置
     * @return 返回值
    */
    public List<OrderPartitionConfigVO> listConfigs() {
        /**
         * 同步默认配置
         */
        syncDefaults();
        return orderPartitionConfigMapper.selectList(new LambdaQueryWrapper<OrderPartitionConfig>().orderByAsc(OrderPartitionConfig::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新分表配置
     *
     * @param businessType 业务类型
     * @param dto dto
     * @return 更新后的分表配置
     */
    public OrderPartitionConfigVO updateConfig(String businessType, OrderPartitionConfigDTO dto) {
        OrderPartitionConfig config = requireConfig(businessType);
        if (dto != null) {
            config.setPartitionPrefix(validateTableName(fallback(dto.partitionPrefix(), config.getPartitionPrefix()), "分表前缀"));
            config.setPartitionGranularity(resolveGranularity(dto.partitionGranularity()));
            config.setRetentionDays(resolvePositive(dto.retentionDays(), config.getRetentionDays(), "保留天数"));
            config.setCreateAheadDays(resolveNonNegative(dto.createAheadDays(), config.getCreateAheadDays(), "提前建表天数"));
            config.setMigrateBeforeDays(resolvePositive(dto.migrateBeforeDays(), config.getMigrateBeforeDays(), "迁移阈值天数"));
            config.setAutoCreateEnabled(Boolean.TRUE.equals(dto.autoCreateEnabled()));
            config.setAutoMigrateEnabled(Boolean.TRUE.equals(dto.autoMigrateEnabled()));
            config.setKeepSourceData(Boolean.TRUE.equals(dto.keepSourceData()));
            if (dto.migrationDryRun() != null) {
                config.setMigrationDryRun(Boolean.TRUE.equals(dto.migrationDryRun()));
            }
            if (StringUtils.hasText(dto.tableTimeColumn())) {
                config.setTableTimeColumn(dto.tableTimeColumn().trim());
            }
            if (StringUtils.hasText(dto.tableIdColumn())) {
                config.setTableIdColumn(dto.tableIdColumn().trim());
            }
            config.setRemark(dto.remark());
        }
        orderPartitionConfigMapper.updateById(config);
        return toVO(requireConfig(businessType));
    }

    @Override
    /**
     * 预览分表执行计划
     * @param businessType 字符串
     * @return 返回值
    */
    public OrderPartitionPreviewVO preview(String businessType) {
        OrderPartitionConfig config = requireConfig(businessType);
        LocalDateTime migrateBeforeTime = LocalDateTime.now().minusDays(config.getMigrateBeforeDays());
        return new OrderPartitionPreviewVO(
                config.getBusinessType(),
                config.getSourceTable(),
                resolvePartitionTable(config, LocalDate.now().plusDays(config.getCreateAheadDays())),
                resolvePartitionTable(config, migrateBeforeTime.toLocalDate()),
                migrateBeforeTime,
                config.getCreateTaskKey(),
                config.getMigrateTaskKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 执行自动建表
     *
     * @param businessType 业务类型
     */
    public void executeAutoCreate(String businessType) {
        OrderPartitionConfig config = requireConfig(businessType);
        String tableName = resolvePartitionTable(config, LocalDate.now().plusDays(config.getCreateAheadDays()));
        /**
         * 确保目标分表已存在
         * @param config.getSourceTable() 获取源表名
         */
        ensurePartitionTableExists(config.getSourceTable(), tableName);
        config.setLastPartitionTable(tableName);
        config.setLastPartitionAt(LocalDateTime.now());
        orderPartitionConfigMapper.updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 执行自动迁移
     *
     * @param businessType 业务类型
     */
    public void executeAutoMigrate(String businessType) {
        OrderPartitionConfig config = requireConfig(businessType);
        String timeCol = config.getTableTimeColumn();
        String idCol = config.getTableIdColumn();
        if (!StringUtils.hasText(timeCol)) {
            throw new PaymentException("分表配置缺少 timeColumn");
        }
        if (!StringUtils.hasText(idCol)) {
            throw new PaymentException("分表配置缺少 idColumn");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(config.getMigrateBeforeDays());
        String targetTable = resolvePartitionTable(config, cutoff.toLocalDate());
        /**
         * 确保目标分表已存在
         * @param config.getSourceTable() 获取源表名
         */
        ensurePartitionTableExists(config.getSourceTable(), targetTable);
        String sourceTable = validateTableName(config.getSourceTable(), "源表名");
        String safeTimeCol = validateTableName(timeCol, "timeColumn");
        String safeIdCol = validateTableName(idCol, "idColumn");
        validatePartitionMetadata(sourceTable, safeTimeCol, safeIdCol);
        int migrated = orderPartitionConfigMapper.migrateToPartition(
                sourceTable, targetTable, safeTimeCol, safeIdCol, cutoff);
        log.info("[分表] {} 迁移完成: {} 条数据已迁移至 {}，截止时间 {}", businessType, migrated, targetTable, cutoff);

        boolean dryRun = Boolean.TRUE.equals(config.getMigrationDryRun());
        if (dryRun) {
            log.info("[分表] dry-run 模式开启，跳过源数据删除: {} <= {}", sourceTable, targetTable);
        } else if (!Boolean.TRUE.equals(config.getKeepSourceData())) {
            int verified = verifyPartitionQuery(businessType, cutoff);
            if (verified > 0) {
                int deleted = orderPartitionConfigMapper.deleteMigratedRows(sourceTable, safeTimeCol, cutoff);
                log.info("[分表] {} 源数据清理完成，删除 {} 条", businessType, deleted);
            } else {
                log.warn("[分表] {} 分表查询校验未通过，保留源数据避免丢单", businessType);
            }
        } else {
            log.info("[分表] {} keepSourceData=true，保留源数据", businessType);
        }
        config.setLastPartitionTable(targetTable);
        config.setLastMigrateAt(LocalDateTime.now());
        orderPartitionConfigMapper.updateById(config);
    }

    /**
     * 校验分表查询结果，用于确认源数据可安全清理
     *
     * @param businessType 业务类型
     * @param cutoff 迁移截止时间
     * @return 分表查询到的记录数
     */
    private int verifyPartitionQuery(String businessType, LocalDateTime cutoff) {
        try {
            OrderPartitionConfig config = requireConfig(businessType);
            String timeCol = config.getTableTimeColumn();
            if (!StringUtils.hasText(timeCol)) {
                log.warn("[分表] {} 未配置 timeColumn，跳过分表校验", businessType);
                return 0;
            }
            String targetTable = resolvePartitionTable(config, cutoff.toLocalDate());
            String safeTimeCol = validateTableName(timeCol, "timeColumn");
            return orderPartitionConfigMapper.countPartitionRows(targetTable, safeTimeCol, cutoff);
        } catch (Exception e) {
            log.error("[分表] 分表查询校验异常: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    /**
     * 获取分表配置实体
     * @param businessType 字符串
     * @return 返回值
    */
    public OrderPartitionConfig getConfigEntity(String businessType) {
        return requireConfig(businessType);
    }

    /**
     * 同步默认分表配置
     */
    private void syncDefaults() {
        for (OrderPartitionConfig item : DEFAULTS.values()) {
            OrderPartitionConfig existing = orderPartitionConfigMapper.selectOne(new LambdaQueryWrapper<OrderPartitionConfig>()
                    .eq(OrderPartitionConfig::getBusinessType, item.getBusinessType())
                    .last("limit 1"));
            if (existing != null) {
                if (!StringUtils.hasText(existing.getCreateTaskKey())) {
                    existing.setCreateTaskKey(item.getCreateTaskKey());
                }
                if (!StringUtils.hasText(existing.getMigrateTaskKey())) {
                    existing.setMigrateTaskKey(item.getMigrateTaskKey());
                }
                if (!StringUtils.hasText(existing.getSourceTable())) {
                    existing.setSourceTable(item.getSourceTable());
                }
                if (!StringUtils.hasText(existing.getTableTimeColumn()) && StringUtils.hasText(item.getTableTimeColumn())) {
                    existing.setTableTimeColumn(item.getTableTimeColumn());
                }
                if (!StringUtils.hasText(existing.getTableIdColumn()) && StringUtils.hasText(item.getTableIdColumn())) {
                    existing.setTableIdColumn(item.getTableIdColumn());
                }
                orderPartitionConfigMapper.updateById(existing);
                continue;
            }
            OrderPartitionConfig copy = new OrderPartitionConfig();
            BeanUtils.copyProperties(item, copy);
            orderPartitionConfigMapper.insert(copy);
        }
    }

    /**
     * 获取分表配置，不存在时抛出异常
     *
     * @param businessType 业务类型
     * @return 获取到的分表配置
     */
    private OrderPartitionConfig requireConfig(String businessType) {
        if (!DEFAULTS.containsKey(normalizeBusinessType(businessType))) {
            throw new PaymentException("不支持的业务类型: " + businessType);
        }
        OrderPartitionConfig config = orderPartitionConfigMapper.selectOne(new LambdaQueryWrapper<OrderPartitionConfig>()
                .eq(OrderPartitionConfig::getBusinessType, normalizeBusinessType(businessType))
                .last("limit 1"));
        if (config == null) {
            throw new PaymentException("分表配置不存在: " + businessType);
        }
        return config;
    }

    /**
     * 解析分表表名
     *
     * @param config 分表配置
     * @param date 日期
     * @return 解析出的分表表名
     */
    private String resolvePartitionTable(OrderPartitionConfig config, LocalDate date) {
        String suffix = "DAY".equalsIgnoreCase(config.getPartitionGranularity()) ? DAY_FORMAT.format(date) : MONTH_FORMAT.format(date.withDayOfMonth(1));
        return validateTableName(config.getPartitionPrefix(), "分表前缀") + "_" + suffix;
    }

    /**
     * 确保分表存在
     *
     * @param sourceTable 源表名
     * @param targetTable 目标表名
     */
    private void ensurePartitionTableExists(String sourceTable, String targetTable) {
        String safeSourceTable = validateTableName(sourceTable, "源表名");
        String safeTargetTable = validateTableName(targetTable, "目标表名");
        validateSourceTableExists(safeSourceTable);
        if (isH2Database()) {
            orderPartitionConfigMapper.createPartitionFromEmptyResult(safeSourceTable, safeTargetTable);
        } else {
            orderPartitionConfigMapper.createPartitionLike(safeSourceTable, safeTargetTable);
        }
    }

    /**
     * 校验源分表及迁移列确实存在于当前数据库元数据中
     *
     * @param sourceTable 已通过正则白名单的源表名
     * @param timeColumn 已通过正则白名单的时间列名
     * @param idColumn 已通过正则白名单的主键列名
     * @throws PaymentException 表或列不存在、元数据读取失败时抛出
     */
    private void validatePartitionMetadata(String sourceTable, String timeColumn, String idColumn) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (!tableExists(connection, metaData, sourceTable)) {
                throw new PaymentException("分表源表不存在: " + sourceTable);
            }
            if (!columnExists(connection, metaData, sourceTable, timeColumn)) {
                throw new PaymentException("分表时间列不存在: " + sourceTable + "." + timeColumn);
            }
            if (!columnExists(connection, metaData, sourceTable, idColumn)) {
                throw new PaymentException("分表主键列不存在: " + sourceTable + "." + idColumn);
            }
        } catch (SQLException e) {
            throw new PaymentException("读取分表元数据失败: " + sourceTable, e);
        }
    }

    /**
     * 校验源表存在
     *
     * @param sourceTable 已通过正则白名单的源表名
     * @throws PaymentException 表不存在或元数据读取失败时抛出
     */
    private void validateSourceTableExists(String sourceTable) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, connection.getMetaData(), sourceTable)) {
                throw new PaymentException("分表源表不存在: " + sourceTable);
            }
        } catch (SQLException e) {
            throw new PaymentException("读取分表源表元数据失败: " + sourceTable, e);
        }
    }

    /**
     * 通过 JDBC 元数据确认表存在（兼容不同数据库的大小写与 schema 行为）
     *
     * @param connection 数据库连接
     * @param metaData 数据库元数据
     * @param tableName 已通过正则白名单的表名
     * @return 表存在时返回 true
     * @throws SQLException 读取元数据失败时抛出
     */
    private boolean tableExists(Connection connection, DatabaseMetaData metaData, String tableName)
            throws SQLException {
        for (String candidate : metadataCandidates(tableName)) {
            try (ResultSet resultSet = metaData.getTables(
                    connection.getCatalog(), connection.getSchema(), candidate, new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
            try (ResultSet resultSet = metaData.getTables(null, null, candidate, new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 通过 JDBC 元数据确认列存在
     *
     * @param connection 数据库连接
     * @param metaData 数据库元数据
     * @param tableName 已通过正则白名单的表名
     * @param columnName 已通过正则白名单的列名
     * @return 列存在时返回 true
     * @throws SQLException 读取元数据失败时抛出
     */
    private boolean columnExists(Connection connection, DatabaseMetaData metaData,
                                 String tableName, String columnName) throws SQLException {
        for (String tableCandidate : metadataCandidates(tableName)) {
            for (String columnCandidate : metadataCandidates(columnName)) {
                try (ResultSet resultSet = metaData.getColumns(
                        connection.getCatalog(), connection.getSchema(), tableCandidate, columnCandidate)) {
                    while (resultSet.next()) {
                        if (columnName.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 生成数据库元数据常用大小写候选值
     *
     * @param value 已规范化标识符
     * @return 原值、大写与小写候选值
     */
    private List<String> metadataCandidates(String value) {
        return List.of(value, value.toUpperCase(Locale.ROOT), value.toLowerCase(Locale.ROOT));
    }

    /**
     * 判断目标数据库是否为 H2
     *
     * @return 产品名包含 H2 时返回 true
     * @throws PaymentException 读取数据库产品名失败时抛出
     */
    private boolean isH2Database() {
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            return database.contains("h2");
        } catch (SQLException e) {
            throw new PaymentException("无法识别分表数据库类型", e);
        }
    }

    /**
     * 转换为视图对象
     *
     * @param config 分表配置
     * @return 分表配置视图对象
     */
    private OrderPartitionConfigVO toVO(OrderPartitionConfig config) {
        return new OrderPartitionConfigVO(
                config.getBusinessType(),
                config.getSourceTable(),
                config.getPartitionPrefix(),
                config.getPartitionGranularity(),
                config.getRetentionDays(),
                config.getCreateAheadDays(),
                config.getMigrateBeforeDays(),
                config.getAutoCreateEnabled(),
                config.getAutoMigrateEnabled(),
                config.getKeepSourceData(),
                config.getMigrationDryRun(),
                config.getTableTimeColumn(),
                config.getTableIdColumn(),
                config.getCreateTaskKey(),
                config.getMigrateTaskKey(),
                config.getLastPartitionTable(),
                config.getLastPartitionAt(),
                config.getLastMigrateAt(),
                config.getRemark());
    }

    /**
     * 创建默认分表配置
     *
     * @return 默认分表配置映射
     */
    private static Map<String, OrderPartitionConfig> createDefaults() {
        Map<String, OrderPartitionConfig> result = new LinkedHashMap<>();
        result.put("ORDER", buildDefault("ORDER", "pay_order", "pay_order_part", "payment.order.partition.create", "payment.order.partition.migrate"));
        result.put("TRANSACTION", buildDefault("TRANSACTION", "pay_transaction_record", "pay_transaction_record_part", "payment.transaction.partition.create", "payment.transaction.partition.migrate"));
        return result;
    }

    /**
     * 构建默认分表配置
     *
     * @param businessType 业务类型
     * @param sourceTable 源表名
     * @param prefix 分表前缀
     * @param createTaskKey 建表任务标识
     * @param migrateTaskKey 迁移任务标识
     * @return 构建出的默认分表配置
     */
    private static OrderPartitionConfig buildDefault(String businessType, String sourceTable, String prefix, String createTaskKey, String migrateTaskKey) {
        OrderPartitionConfig config = new OrderPartitionConfig();
        config.setBusinessType(businessType);
        config.setSourceTable(sourceTable);
        config.setPartitionPrefix(prefix);
        config.setPartitionGranularity("MONTH");
        config.setTableTimeColumn(sourceTable + "_created_at");
        config.setTableIdColumn(sourceTable + "_id");
        config.setRetentionDays(365);
        config.setCreateAheadDays(7);
        config.setMigrateBeforeDays(90);
        config.setAutoCreateEnabled(Boolean.TRUE);
        config.setAutoMigrateEnabled(Boolean.TRUE);
        config.setKeepSourceData(Boolean.FALSE);
        config.setMigrationDryRun(Boolean.TRUE);
        config.setCreateTaskKey(createTaskKey);
        config.setMigrateTaskKey(migrateTaskKey);
        config.setRemark("默认按月分表");
        return config;
    }

    /**
     * 规范化业务类型
     *
     * @param businessType 业务类型
     * @return 规范化后的业务类型
     */
    private String normalizeBusinessType(String businessType) {
        return fallback(businessType, "").trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析分表粒度
     * @param value 值
     * @return 返回值
    */
    private String resolveGranularity(String value) {
        String result = fallback(value, "MONTH").trim().toUpperCase(Locale.ROOT);
        if (!"MONTH".equals(result) && !"DAY".equals(result)) {
            throw new PaymentException("不支持的分表粒度: " + value);
        }
        return result;
    }

    /**
     * 解析正整数参数
     * @param fallback 缺省值
     * @param label 参数名称
     * @param value 值
     * @return 返回值
    */
    private Integer resolvePositive(Integer value, Integer fallback, String label) {
        int result = value != null ? value : fallback;
        if (result <= 0) {
            throw new PaymentException(label + "必须大于 0");
        }
        return result;
    }

    /**
     * 解析非负整数参数
     * @param fallback 缺省值
     * @param label 参数名称
     * @param value 值
     * @return 返回值
    */
    private Integer resolveNonNegative(Integer value, Integer fallback, String label) {
        int result = value != null ? value : fallback;
        if (result < 0) {
            throw new PaymentException(label + "不能小于 0");
        }
        return result;
    }

    /**
     * 校验表名或列名格式
     *
     * @param value 值
     * @param label 参数名称
     * @return 校验通过的标识符
     */
    private String validateTableName(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new PaymentException(label + "不能为空");
        }
        try {
            return SqlName.checkSimple(value.trim(), label);
        } catch (IllegalArgumentException e) {
            throw new PaymentException(label + "不合法: " + value, e);
        }
    }

    /**
     * 取值或降级
     *
     * @param value 值
     * @param fallback 缺省值
     * @return 降级后的值
     */
    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
