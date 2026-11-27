package com.chua.starter.datasource.properties;

import com.chua.datasource.support.engine.CachingEngine;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine 读缓存自动配置属性。
 *
 * <p>开启后无需手工包装：容器里唯一的 {@code JdbcEngine} Bean 会被自动装饰成
 * {@link CachingEngine} 并以独立 Bean 暴露，底层 Engine 保持原样供迁移、DDL 等写入场景使用。</p>
 *
 * <pre>{@code
 * plugin.h2.enable=true
 * plugin.engine.cache.enabled=true
 * plugin.engine.cache.tables=sys_user,sys_dict
 * # 不写 refresh-interval-seconds 时使用内部默认 60s
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = EngineCacheProperties.PRE, ignoreInvalidFields = true)
public class EngineCacheProperties {

    /**
     * 配置前缀常量。
     */
    public static final String PRE = "plugin.engine.cache";

    /**
     * 开启自动装配且未显式配置刷新间隔时采用的内部默认值（秒）。
     *
     * <p>只对自动装配路径生效：容器里只配置底层引擎、打开 {@code enabled} 即可获得
     * 「定时把外部改动刷新进缓存」的能力，无需再手工填写间隔。显式配置
     * {@code refresh-interval-seconds=0} 表示关闭定时刷新，按需 {@code refreshNow()} 手动刷新。</p>
     */
    public static final long DEFAULT_REFRESH_INTERVAL_SECONDS = 60L;

    /**
     * 是否启用 Engine 读缓存自动配置，默认 false（关闭时不注册任何 Bean）。
     * <p>示例：{@code plugin.engine.cache.enabled=true}</p>
     */
    private boolean enabled = false;

    /**
     * 要缓存的底层引擎 Bean 名；留空时要求容器中只有一个 {@code JdbcEngine} Bean。
     * <p>多个引擎并存（如同时启用 H2 与 SQLite）时必须显式指定。</p>
     */
    private String delegateBeanName;

    /**
     * 允许缓存的表名（大小写不敏感），{@code *} 表示全部。
     * <p>默认全部：缓存会改变读一致性语义，建议显式列出高频只读表。</p>
     */
    private List<String> tables = new ArrayList<>(List.of("*"));

    /**
     * 单表行数上限，超过即旁路该表（读直落数据库）。
     * <p>默认 {@link CachingEngine#DEFAULT_MAX_ROWS}，小于等于 0 时取默认值。</p>
     */
    private int maxRows = CachingEngine.DEFAULT_MAX_ROWS;

    /**
     * 全部副本行数上限，超限按 LRU 逐出。
     * <p>默认 {@link CachingEngine#DEFAULT_MAX_TOTAL_ROWS}，小于等于 0 时取默认值。</p>
     */
    private int maxTotalRows = CachingEngine.DEFAULT_MAX_TOTAL_ROWS;

    /**
     * 副本存活秒数，{@code 0} 表示不过期（仅靠写入失效与主动 reload）。
     */
    private long ttlSeconds = CachingEngine.DEFAULT_TTL_SECONDS;

    /**
     * 后台刷新间隔秒数，{@code null} 表示未配置，自动装配时取内部默认值
     * {@value #DEFAULT_REFRESH_INTERVAL_SECONDS} 秒；显式 {@code 0} 表示关闭定时刷新。
     * <p>大于 0 时按固定间隔重装载已缓存的表，用于自动吸收「外部进程直接改库」带来的不一致；
     * 内部默认值保证「只配置底层引擎」即可自动维护数据。</p>
     */
    private Long refreshIntervalSeconds;

    /**
     * 刷新时间轮的槽位数，{@code null} 表示使用内部默认
     * {@link CachingEngine#DEFAULT_REFRESH_WHEEL_SLOTS}。
     * <p>调度跨度 = 槽位数 × tick（默认 512 槽 × 1000ms = 512s）；刷新间隔超过跨度时任务会多绕一圈后才到期。</p>
     */
    private Integer refreshWheelSlots;

    /**
     * 刷新时间轮的 tick 毫秒数，{@code null} 表示使用内部默认
     * {@link CachingEngine#DEFAULT_REFRESH_WHEEL_TICK_MILLIS}。
     * <p>决定时间轮唤醒粒度；刷新是秒级任务，不建议配成 1ms。</p>
     */
    private Long refreshWheelTickMillis;
}
