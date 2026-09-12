package com.chua.starter.datasource.monitor;

import com.chua.common.support.collection.OverflowPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Arrays;
import java.util.List;

import static com.chua.starter.datasource.monitor.SqlMonitorProperties.PRE;

/**
 * SQL 监听配置属性
 *
 * <p>纯 JDBC 层 SQL 监听，数据源无关，不依赖 druid/p6spy/mybatis。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = PRE)
public class SqlMonitorProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.datasource.monitor";

    /**
     * 是否启用 SQL 监听
     * <p>未设置（null）时根据 Spring 环境自动切换：匹配 {@link #autoEnableEnvironments}
      * 中任一环境（dev/测试/uat/本地）则启用，否则关闭。显式设置后以设置为准。</p>
     */
    private Boolean enabled = null;

    /**
     * 自动启用监听的环境列表
     * <p>仅当 {@code enabled} 未显式设置时生效。任一激活 profile 命中该列表即启用。</p>
     */
    private List<String> autoEnableEnvironments = Arrays.asList("dev", "test", "uat", "local");

    /**
     * 慢 SQL 阈值（毫秒），超过该阈值的 SQL 视为慢 SQL
     */
    private long slowSqlThresholdMs = 1000L;

    /**
     * SQL 记录最大长度，超过部分截断，防止大 SQL 占用内存
     */
    private int maxSqlLength = 2000;

    /**
     * 是否记录 SQL 文本，关闭后事件只携带统计信息，减少内存开销
     */
    private boolean recordSql = true;

    /**
     * 是否发布 Spring 事件，关闭后只更新统计与慢 SQL 日志
     */
    private boolean publishEvent = true;

    /**
     * 统计跟踪的数据源数量上限
     * <p>防止数据源名称动态增长导致内存无限膨胀。达到上限后，新数据源的行为由
     * {@link #overflowPolicy} 决定。单个统计对象仅约 80 字节，默认 1000 个约 80KB。</p>
     */
    private int maxTrackedDataSources = 1000;

    /**
     * 数据源统计达到上限时的溢出策略
     * <p>默认 {@link OverflowPolicy#REJECT}：达到上限后不再跟踪新数据源（慢 SQL 日志与
     * 事件仍正常），已跟踪数据源不受影响。也可配置为 {@link OverflowPolicy#EVICT_ELDEST}
     * 自动淘汰最早跟踪的数据源。</p>
     */
    private OverflowPolicy overflowPolicy = OverflowPolicy.REJECT;

    /**
     * 解析最终启用状态
     * <p>显式设置 {@code enabled} 时以其为准；未设置时根据激活环境自动判断。</p>
     *
     * @param environment Spring 环境
     * @return 是否启用
     */
    public boolean resolveEnabled(Environment environment) {
        if (enabled != null) {
            return enabled;
        }
        if (autoEnableEnvironments == null || autoEnableEnvironments.isEmpty() || environment == null) {
            return false;
        }
        for (String envName : autoEnableEnvironments) {
            if (environment.acceptsProfiles(Profiles.of(envName))) {
                return true;
            }
        }
        return false;
    }
}
