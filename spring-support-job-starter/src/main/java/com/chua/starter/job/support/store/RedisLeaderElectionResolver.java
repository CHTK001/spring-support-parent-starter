package com.chua.starter.job.support.store;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.job.support.cluster.LeaderElectionResolver;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的选主实现（SPI 键 {@code redis}）。
 *
 * <p>语义与 {@link MysqlLeaderElectionResolver} 逐条对齐：抢租约 / 续约 / 释放。
 * 租约用键的 TTL 表达，"过期"由 Redis 自己判定，不依赖任何节点的本机时钟。</p>
 *
 * <p><b>键</b>：{@code job:leader:{命名空间}}，值为持有者 nodeId。</p>
 *
 * <p><b>重要约束</b>：本实现与数据库实现的租约存在<b>不同存储</b>里，彼此不可见。
 * 同一集群必须用同一种实现，否则两个节点会各自当选（双 leader）。
 * 因此它只适合在确认全集群一致时启用（例如专门的测试期）。</p>
 *
 * <p><b>失败语义</b>：Redis 异常时一律返回"未抢到 / 续约失败"，
 * 由上层决定回落或让位，绝不把异常静默当成抢到——否则 Redis 抖动会变成双 leader。</p>
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
     * Redis 模板
     */
    private final StringRedisTemplate redis;

    /**
     * 由 SPI 带参实例化（本类不能在编译期缺少 Redis 时被加载，取不到即自动回落数据库实现）。
     *
     * @param redis Redis 模板
     */
    public RedisLeaderElectionResolver(StringRedisTemplate redis) {
        this.redis = redis;
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
        String key = KEY_PREFIX + group;
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(key, nodeId, Duration.ofSeconds(leaseSeconds));
            if (Boolean.TRUE.equals(acquired)) {
                log.info("[job] 抢占 Leader 成功 (Redis) group={} nodeId={} lease={}s",
                        group, nodeId, leaseSeconds);
                return true;
            }
            if (nodeId.equals(redis.opsForValue().get(key))) {
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
        try {
            Long rows = redis.execute(RENEW_SCRIPT, List.of(KEY_PREFIX + group),
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
        try {
            redis.execute(RELEASE_SCRIPT, List.of(KEY_PREFIX + group), nodeId);
        } catch (Exception ex) {
            log.warn("[job] Redis 释放异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
        }
    }
}
