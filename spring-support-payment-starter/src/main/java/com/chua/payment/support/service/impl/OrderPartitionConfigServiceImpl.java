package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.entity.OrderPartitionConfig;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.OrderPartitionConfigMapper;
import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.payment.support.vo.OrderPartitionConfigVO;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OrderPartitionConfigServiceImpl implements OrderPartitionConfigService {

    private static final Pattern SAFE_TABLE = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, OrderPartitionConfig> DEFAULTS = createDefaults();

    private final OrderPartitionConfigMapper orderPartitionConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @PostConstruct
    public void initDefaults() {
        syncDefaults();
    }

    @Override
    public List<OrderPartitionConfigVO> listConfigs() {
        syncDefaults();
        return orderPartitionConfigMapper.selectList(new LambdaQueryWrapper<OrderPartitionConfig>().orderByAsc(OrderPartitionConfig::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPartitionConfigVO updateConfig(String businessType, OrderPartitionConfigDTO dto) {
        OrderPartitionConfig config = requireConfig(businessType);
        if (dto != null) {
            config.setPartitionPrefix(validateTableName(fallback(dto.getPartitionPrefix(), config.getPartitionPrefix()), "分表前缀"));
            config.setPartitionGranularity(resolveGranularity(dto.getPartitionGranularity()));
            config.setRetentionDays(resolvePositive(dto.getRetentionDays(), config.getRetentionDays(), "保留天数"));
            config.setCreateAheadDays(resolveNonNegative(dto.getCreateAheadDays(), config.getCreateAheadDays(), "提前建表天数"));
            config.setMigrateBeforeDays(resolvePositive(dto.getMigrateBeforeDays(), config.getMigrateBeforeDays(), "迁移阈值天数"));
            config.setAutoCreateEnabled(Boolean.TRUE.equals(dto.getAutoCreateEnabled()));
            config.setAutoMigrateEnabled(Boolean.TRUE.equals(dto.getAutoMigrateEnabled()));
            config.setKeepSourceData(Boolean.TRUE.equals(dto.getKeepSourceData()));
            config.setRemark(dto.getRemark());
        }
        orderPartitionConfigMapper.updateById(config);
        return toVO(requireConfig(businessType));
    }

    @Override
    public OrderPartitionPreviewVO preview(String businessType) {
        OrderPartitionConfig config = requireConfig(businessType);
        LocalDateTime migrateBeforeTime = LocalDateTime.now().minusDays(config.getMigrateBeforeDays());
        OrderPartitionPreviewVO vo = new OrderPartitionPreviewVO();
        vo.setBusinessType(config.getBusinessType());
        vo.setSourceTable(config.getSourceTable());
        vo.setNextPartitionTable(resolvePartitionTable(config, LocalDate.now().plusDays(config.getCreateAheadDays())));
        vo.setMigrateTargetTable(resolvePartitionTable(config, migrateBeforeTime.toLocalDate()));
        vo.setMigrateBeforeTime(migrateBeforeTime);
        vo.setCreateTaskKey(config.getCreateTaskKey());
        vo.setMigrateTaskKey(config.getMigrateTaskKey());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeAutoCreate(String businessType) {
        OrderPartitionConfig config = requireConfig(businessType);
        String tableName = resolvePartitionTable(config, LocalDate.now().plusDays(config.getCreateAheadDays()));
        ensurePartitionTableExists(config.getSourceTable(), tableName);
        config.setLastPartitionTable(tableName);
        config.setLastPartitionAt(LocalDateTime.now());
        orderPartitionConfigMapper.updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeAutoMigrate(String businessType) {
        OrderPartitionConfig config = requireConfig(businessType);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(config.getMigrateBeforeDays());
        String targetTable = resolvePartitionTable(config, cutoff.toLocalDate());
        ensurePartitionTableExists(config.getSourceTable(), targetTable);
        jdbcTemplate.update("insert into " + targetTable + " select * from " + config.getSourceTable() + " where created_at < ? and id not in (select id from " + targetTable + ")", cutoff);
        if (!Boolean.TRUE.equals(config.getKeepSourceData())) {
            jdbcTemplate.update("delete from " + config.getSourceTable() + " where created_at < ?", cutoff);
        }
        config.setLastPartitionTable(targetTable);
        config.setLastMigrateAt(LocalDateTime.now());
        orderPartitionConfigMapper.updateById(config);
    }

    @Override
    public OrderPartitionConfig getConfigEntity(String businessType) {
        return requireConfig(businessType);
    }

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
                orderPartitionConfigMapper.updateById(existing);
                continue;
            }
            OrderPartitionConfig copy = new OrderPartitionConfig();
            BeanUtils.copyProperties(item, copy);
            orderPartitionConfigMapper.insert(copy);
        }
    }

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

    private String resolvePartitionTable(OrderPartitionConfig config, LocalDate date) {
        String suffix = "DAY".equalsIgnoreCase(config.getPartitionGranularity()) ? DAY_FORMAT.format(date) : MONTH_FORMAT.format(date.withDayOfMonth(1));
        return validateTableName(config.getPartitionPrefix(), "分表前缀") + "_" + suffix;
    }

    private void ensurePartitionTableExists(String sourceTable, String targetTable) {
        validateTableName(sourceTable, "源表");
        validateTableName(targetTable, "目标表");
        jdbcTemplate.execute(resolveCloneSql(sourceTable, targetTable));
    }

    private String resolveCloneSql(String sourceTable, String targetTable) {
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (database.contains("h2")) {
                return "create table if not exists " + targetTable + " as select * from " + sourceTable + " where 1 = 0";
            }
        } catch (Exception ignored) {
        }
        return "create table if not exists " + targetTable + " like " + sourceTable;
    }

    private OrderPartitionConfigVO toVO(OrderPartitionConfig config) {
        OrderPartitionConfigVO vo = new OrderPartitionConfigVO();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }

    private static Map<String, OrderPartitionConfig> createDefaults() {
        Map<String, OrderPartitionConfig> result = new LinkedHashMap<>();
        result.put("ORDER", buildDefault("ORDER", "payment_order", "payment_order_part", "payment.order.partition.create", "payment.order.partition.migrate"));
        result.put("TRANSACTION", buildDefault("TRANSACTION", "transaction_record", "transaction_record_part", "payment.transaction.partition.create", "payment.transaction.partition.migrate"));
        return result;
    }

    private static OrderPartitionConfig buildDefault(String businessType, String sourceTable, String prefix, String createTaskKey, String migrateTaskKey) {
        OrderPartitionConfig config = new OrderPartitionConfig();
        config.setBusinessType(businessType);
        config.setSourceTable(sourceTable);
        config.setPartitionPrefix(prefix);
        config.setPartitionGranularity("MONTH");
        config.setRetentionDays(365);
        config.setCreateAheadDays(7);
        config.setMigrateBeforeDays(90);
        config.setAutoCreateEnabled(Boolean.TRUE);
        config.setAutoMigrateEnabled(Boolean.FALSE);
        config.setKeepSourceData(Boolean.TRUE);
        config.setCreateTaskKey(createTaskKey);
        config.setMigrateTaskKey(migrateTaskKey);
        config.setRemark("默认按月分表");
        return config;
    }

    private String normalizeBusinessType(String businessType) {
        return fallback(businessType, "").trim().toUpperCase(Locale.ROOT);
    }

    private String resolveGranularity(String value) {
        String result = fallback(value, "MONTH").trim().toUpperCase(Locale.ROOT);
        if (!"MONTH".equals(result) && !"DAY".equals(result)) {
            throw new PaymentException("不支持的分表粒度: " + value);
        }
        return result;
    }

    private Integer resolvePositive(Integer value, Integer fallback, String label) {
        int result = value != null ? value : fallback;
        if (result <= 0) {
            throw new PaymentException(label + "必须大于 0");
        }
        return result;
    }

    private Integer resolveNonNegative(Integer value, Integer fallback, String label) {
        int result = value != null ? value : fallback;
        if (result < 0) {
            throw new PaymentException(label + "不能小于 0");
        }
        return result;
    }

    private String validateTableName(String value, String label) {
        if (!StringUtils.hasText(value) || !SAFE_TABLE.matcher(value.trim()).matches()) {
            throw new PaymentException(label + "不合法: " + value);
        }
        return value.trim();
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
