package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 分库分表sphere分库分表配置属性
 * <p>
 * 配置示例:
 * <pre>
 * plugin:
 *   datasource:
 *     sharding:
 *       enabled: true
 *       show-sql: true
 *       tables:
 *         - logic-table: t_order_log
 *           sharding-column: create_time
 *           time-unit: MONTH          # 配置后走 INTERVAL 时间分片，物理表 t_order_log_yyyyMM
 *         - logic-table: t_user
 *           sharding-column: user_id  # 不配 time-unit 走 MOD 取模，物理表 t_user_0..t_user_15
 *       databases:                    # 数量大于 1 时启用分库（按同一分片列对库数取模）
 *         - db_order_0
 *         - db_order_1
 * </pre>
 * <p>
 * 物理表不会自动创建：规则先行生效（缺失的物理表按分片数合成数据节点），
 * 建表请走受限 DDL 端点（{@code /ms/ds-runtime/tables/*}）或自行执行 CREATE TABLE；
 * 需要参数化分片数/分库成员时改用运行期规则表 {@code ds_sharding_rule}。
 *
 * @since 2024/12/21
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.datasource.sharding")
public class ShardingDataSourceProperties {

    /**
     * 是否启用分片
     * 默认值: false
     * 示例: plugin.datasource.sharding.enabled=true
     */
    private boolean enabled = false;

    /**
     * 是否显示SQL（打印改写后的 SQL）
     * 默认值: false
     * 示例: plugin.datasource.sharding.show-sql=true
     */
    private boolean showSql = false;

    /**
     * 分表配置列表
     * 示例: plugin.datasource.sharding.tables[0].logic-table=t_order
     */
    private List<ShardingTableConfig> tables;

    /**
     * 分库配置列表（逻辑库名）。
     *
     * <p>列表数量大于 1 时启用分库：对每个逻辑表按其分片列对库数取模（db_mod 算法）路由，
     * 分库与分表使用同一个分片列；只有 1 个或未配置时不分库。</p>
     * 示例: plugin.datasource.sharding.databases[0]=db_order_0
     */
    private List<String> databases;

    /**
     * 分表配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class ShardingTableConfig {
        /**
         * 逻辑表名（同时作为物理表前缀）
         * 示例: plugin.datasource.sharding.tables[0].logic-table=t_order
         */
        private String logicTable;

        /**
         * 分片列（分片键）
         * 示例: plugin.datasource.sharding.tables[0].sharding-column=user_id
         */
        private String shardingColumn;

        /**
         * 时间分片单位：YEAR / QUARTER / MONTH / WEEK / DAY / HOUR。
         *
         * <p>配置了 time-unit 时走 INTERVAL 时间分片：分片范围固定为 2000-01-01 ~ 2099-12-31，
         * 分片数固定 12；物理表名为 {@code 逻辑表_时间后缀}，时间后缀按单位分别为
         * YEAR=yyyy、QUARTER=yyyyQ、MONTH=yyyyMM、WEEK=yyyyww、DAY=yyyyMMdd、HOUR=yyyyMMddHH
         * （如 {@code t_order_log_202601}）。</p>
         *
         * <p>未配置（null/空）时走 MOD 取模分片，物理表名为
         * {@code 逻辑表_0 .. 逻辑表_15}（固定 16 片）。</p>
         * 示例: plugin.datasource.sharding.tables[0].time-unit=MONTH
         */
        private String timeUnit;

        /**
         * 分片策略: TABLE, DB。
         *
         * <p>当前仅解析该字段、未参与路由判定：是否分库由
         * {@link ShardingDataSourceProperties#getDatabases()} 的数量是否大于 1 决定，
         * 分表始终按 logic-table / sharding-column / time-unit 生效。</p>
         * 默认值: TABLE
         * 示例: plugin.datasource.sharding.tables[0].strategy=TABLE
         */
        private String strategy = "TABLE";

        // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
        /**
         * 获取逻辑table
         *
         * @return 获取逻辑table的结果
         */
        public String getLogicTable() {
            return logicTable;
        }

        /**
         * 获取分库分表column
         *
         * @return 获取分库分表column的结果
         */
        public String getShardingColumn() {
            return shardingColumn;
        }

        /**
         * 获取时间unit
         *
         * @return 获取时间unit的结果
         */
        public String getTimeUnit() {
            return timeUnit;
        }

        /**
         * 获取Strategy
         *
         * @return 获取strategy的结果
         */
        public String getStrategy() {
            return strategy;
        }
    }

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /**
     * 是否showsql
     *
     * @return 是否showsql的结果
     */
    public boolean isShowSql() {
        return showSql;
    }

    /**
     * 获取Databases
     *
     * @return 获取databases的结果
     */
    public List<String> getDatabases() {
        return databases;
    }

    /**
     * 获取Tables
     *
     * @return 获取tables的结果
     */
    public List<ShardingTableConfig> getTables() {
        return tables;
    }
}
