package com.chua.starter.job.support.cluster;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的选主实现（SPI 键 {@code redis}）。
 *
 * <p>语义与 {@code MysqlLeaderElectionResolver} 逐条对齐：抢租约 / 续约 / 释放。
 * 租约用键的 TTL 表达，"过期"由 Redis 自己判定，不依赖任何节点的本机时钟。</p>
 *
 * <p><b>键</b>：{@code job:leader:{命名空间}}，值为持有者 nodeId。</p>
 *
 * <p><b>为什么放在本包</b>：本 SPI 点（{@code LeaderElectionResolver}）实测只有
 * 「{@code @Spi} 注解 + 同包扫描」通道有效 —— {@code META-INF/extensions} 注册文件在该点上
 * 不生效（已实测：已注册键仅有 {@code [IN-MEMORY]}，即同包的那个实现）。
 * 因此实现必须与接口同包才能被发现（{@code MysqlLeaderElectionResolver} 在 store 包，
 * 正因如此只能作为"内置默认"直接构造）。</p>
 *
 * <p><b>为什么是无参构造</b>：{@code ServiceProvider} 带参实例化按<b>实参运行时类</b>精确匹配
 * 构造器，接口类型的依赖注不进来；故依赖改由 utils 层工具 {@link SpringBeanUtils}
 * 在首次使用时惰性获取，构造器保持无参，走纯 SPI 通道。</p>
 *
 * <p><b>重要约束</b>：本实现与数据库实现的租约存在<b>不同存储</b>里，彼此不可见。
 * 同一集群必须用同一种实现，否则两个节点会各自当选（双 leader）。</p>
 *
 * <p><b>失败语义</b>：Redis 不可用时一律返回"未抢到 / 续约失败"，
 * 绝不把异常静默当成抢到——否则 Redis 抖动会变成双 leader。
 * 注意：这不会自动回落到数据库实现（回落只发生在装配期），属已知取舍。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("redis")
@Slf4j
public class RedisLeaderElectionResolver implements LeaderElectionResolver {

    /**
     * 键前缀
     */
    private static final String KEY_PREFIX = "job:leader:";

    /**
     * 仅当持有者是自己时续期（get + pexpire 非原子，故用 Lua）
     */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    /**
     * 仅当持有者是自己时释放（避免把别人的租约删掉）
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 解析 Redis 模板：不可用（无 Redis 依赖 / 无该 Bean / 上下文未就绪）时返回 null，
     * 由调用方按"未抢到"处理，绝不抛异常穿透到调度循环。
     *
     * @return Redis 模板，或 null
     */
    private StringRedisTemplate redis() {
        try {
            ApplicationContext context = SpringBeanUtils.getApplicationContextOrNull();
            return context == null ? null : SpringBeanUtils.getBean(context, StringRedisTemplate.class, null);
        } catch (Exception ex) {
            log.warn("[job] 取 Redis 模板失败 cause={}", ex.getMessage());
            return null;
        }
    }

    @Override
    /**
     * 抢租约：{@code SET key nodeId NX PX leaseMs}；已持有者视为续约。
     *
     * @param group        命名空间
     * @param nodeId       节点标识
     * @param leaseSeconds 租约时长（秒）
     * @return 抢到或续约成功返回 true
     */
    public boolean tryAcquire(String group, String nodeId, int leaseSeconds) {
        StringRedisTemplate template = redis();
        if (template == null) {
            return false;
        }
        String key = KEY_PREFIX + group;
        try {
            Boolean acquired = template.opsForValue()
                    .setIfAbsent(key, nodeId, Duration.ofSeconds(leaseSeconds));
            if (Boolean.TRUE.equals(acquired)) {
                log.info("[job] 抢占 Leader 成功 (Redis) group={} nodeId={} lease={}s",
                        group, nodeId, leaseSeconds);
                return true;
            }
            if (nodeId.equals(template.opsForValue().get(key))) {
                return renew(group, nodeId, leaseSeconds);
            }
            return false;
        } catch (Exception ex) {
            log.warn("[job] Redis 抢占 Leader 异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
            return false;
        }
    }

    /**
     * 续约：仅当自己仍是持有者时推进 TTL。
     *
     * @param group        命名空间
     * @param nodeId       节点标识
     * @param leaseSeconds 租约时长（秒）
     * @return 续约成功返回 true
     */
    public boolean renew(String group, String nodeId, int leaseSeconds) {
        StringRedisTemplate template = redis();
        if (template == null) {
            return false;
        }
        try {
            Long rows = template.execute(RENEW_SCRIPT, List.of(KEY_PREFIX + group),
                    nodeId, String.valueOf(leaseSeconds * 1000L));
            return rows != null && rows > 0L;
        } catch (Exception ex) {
            log.warn("[job] Redis 续约异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
            return false;
        }
    }

    @Override
    /**
     * 释放：仅当自己仍是持有者时删除键。
     *
     * @param group  命名空间
     * @param nodeId 节点标识
     */
    public void release(String group, String nodeId) {
        StringRedisTemplate template = redis();
        if (template == null) {
            return;
        }
        try {
            template.execute(RELEASE_SCRIPT, List.of(KEY_PREFIX + group), nodeId);
        } catch (Exception ex) {
            log.warn("[job] Redis 释放异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
        }
    }
}
