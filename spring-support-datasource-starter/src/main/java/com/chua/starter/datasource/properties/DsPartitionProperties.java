package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 分区表自动创建配置属性。
 *
 * <p>配置示例:</p>
 * <pre>
 * plugin:
 *   datasource:
 *     partition:
 *       auto-create-enabled: true
 *       check-interval-minutes: 60
 *       tables:
 *         - source-table: t_order
 *           prefix: t_order_part
 *           granularity: MONTH
 * </pre>
 *
 * <p>分区表名口径与 {@code PartitionRoutingService} 一致：
 * {@code prefix_yyyyMM}（MONTH）或 {@code prefix_yyyyMMdd}（DAY）。
 * 建表语句为幂等 DDL（{@code CREATE TABLE IF NOT EXISTS}）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "plugin.datasource.partition")
public class DsPartitionProperties {

    /**
     * 是否启用分区表自动创建（启动预热 + 周期检查）。
     * <p>默认 {@code false}；关闭时仍可通过
     * {@code POST /ms/ds-runtime/partition/create-current} 手动触发。</p>
     */
    private boolean autoCreateEnabled = false;

    /**
     * 自动检查间隔（分钟），最小值 1。每次检查只为「当前周期」补建缺失的分区表。
     */
    private long checkIntervalMinutes = 60;

    /**
     * 分区表配置清单（推荐方式）。
     * <p>未配置时回退读取 {@code pay_partition_config} 中
     * auto_create_enabled=true 的行（只读，兼容既有配置链路）。</p>
     */
    private List<PartitionTableSpec> tables = new ArrayList<>();

    /**
     * 单个分区表配置项。
     */
    @Data
    public static class PartitionTableSpec {

        /**
         * 源表（热表）名，必须已存在于当前数据库。
         */
        private String sourceTable;

        /**
         * 分区表前缀，生成表名为 {@code prefix_yyyyMM}（MONTH）或
         * {@code prefix_yyyyMMdd}（DAY），经 SQL 标识符白名单校验。
         */
        private String prefix;

        /**
         * 分表粒度：MONTH / DAY，默认 MONTH。
         */
        private String granularity = "MONTH";
    }
}
