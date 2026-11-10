package com.chua.starter.datasource.endpoint;

import com.chua.common.support.collection.wrap.Wraps;
import com.chua.common.support.concurrent.circuitbreaker.CircuitBreakerFlow;
import com.chua.common.support.concurrent.lock.LockFlow;
import com.chua.common.support.concurrent.rate.RateLimiterFlow;
import com.chua.starter.datasource.entity.ConcurrentCircuitBreakerConfig;
import com.chua.starter.datasource.entity.ConcurrentDistributedLockConfig;
import com.chua.starter.datasource.entity.ConcurrentRateLimiterConfig;
import com.chua.starter.datasource.repository.CircuitBreakerConfigRepository;
import com.chua.starter.datasource.repository.DistributedLockConfigRepository;
import com.chua.starter.datasource.repository.RateLimiterConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import com.chua.common.support.concurrent.rate.RateLimiterProvider;

/**
 * 并发工具 Actuator 端点
 * <p>
 * 提供限流器、熔断器、分布式锁的配置持久化（sqlite）与运行状态查看能力。
 * </p>
 *
 * <p><b>Boot 4 路径约束重构</b>：端点路径不含方法名，多个无参 {@code @ReadOperation}
 * 会同时映射到根路径 {@code /actuator/concurrent}（天然冲突，此前因此回退无 @web端点）。
 * 现统一为<b>指标前缀选择器</b>结构（每类操作路径互斥，全部功能保留）：</p>
 * <ul>
 *     <li>{@code GET  /actuator/concurrent} —— 总览（计数 + 各指标配置列表 + 运行状态）</li>
 *     <li>{@code GET  /actuator/concurrent/{metric}} —— 单指标配置列表 + 运行状态（metric=ratelimiter|circuitbreaker|lock）</li>
 *     <li>{@code GET  /actuator/concurrent/{metric}/{name}} —— 单配置详情 + 运行状态</li>
 *     <li>{@code POST /actuator/concurrent/{metric}/{name}} —— 配置保存</li>
 *     <li>{@code DELETE /actuator/concurrent/{metric}/{name}} —— 配置删除</li>
 *     <li>{@code POST /actuator/concurrent/{metric}/clear} —— 运行时缓存清空（action=clear）</li>
 *     <li>{@code POST /actuator/concurrent/{metric}/{name}/reset} —— 单点复位（熔断器）</li>
 * </ul>
 * <p>监控页面（HTML）由 /api/strategy/monitor 回退路径承载（编辑器不再作为端点暴露）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@WebEndpoint(id = "concurrent")
@ConditionalOnClass(name = {
        "com.chua.common.support.concurrent.rate.RateLimiterFlow",
        "com.chua.common.support.concurrent.circuitbreaker.CircuitBreakerFlow",
        "com.chua.common.support.concurrent.lock.LockFlow",
        // 端点依赖 datasource 仓储类：仓储 starter 缺失时整体退避（避免类加载失败）
/**
 * 并发端点类。
 *
 * @author CH
 * @since 4.0.0
 */
        "com.chua.starter.datasource.repository.RateLimiterConfigRepository",
        "com.chua.starter.datasource.repository.CircuitBreakerConfigRepository",
        "com.chua.starter.datasource.repository.DistributedLockConfigRepository"
})
/**
 * ConcurrentEndpoint类，提供相关能力。
 *
 * @author CH
 * @since 1.0.0
 */
public class ConcurrentEndpoint {

    private static final String METRIC_RATE_LIMITER = "ratelimiter"; // 指标rate限制
    private static final String METRIC_CIRCUIT_BREAKER = "circuitbreaker"; // 指标熔断中断
    private static final String METRIC_LOCK = "lock"; // 指标锁

    private final RateLimiterConfigRepository rateLimiterRepo; // rate限制repo
    private final CircuitBreakerConfigRepository cbRepo; // cbrepo
    private final DistributedLockConfigRepository lockRepo; // 锁repo

    public ConcurrentEndpoint(ObjectProvider<RateLimiterConfigRepository> rateLimiterRepo,
                              ObjectProvider<CircuitBreakerConfigRepository> cbRepo,
                              ObjectProvider<DistributedLockConfigRepository> lockRepo) {
        this.rateLimiterRepo = rateLimiterRepo.getIfAvailable();
        this.cbRepo = cbRepo.getIfAvailable();
        this.lockRepo = lockRepo.getIfAvailable();
    }

    /**
     * 根路径：计数 + 各指标配置列表 + 运行状态（唯一无参 @读取operation——根路径不冲突）
     *
     * @return overview的结果
     */
    @ReadOperation
    public Map<String, Object> overview() {
        Map<String, Object> result = Wraps.map();
        result.put("rateLimiterCount", RateLimiterFlow.list().size());
        result.put("circuitBreakerCount", CircuitBreakerFlow.list().size());
        result.put("lockCount", LockFlow.list().size());
        result.put("ratelimiter", configAndStatus(METRIC_RATE_LIMITER, null));
        result.put("circuitbreaker", configAndStatus(METRIC_CIRCUIT_BREAKER, null));
        result.put("lock", configAndStatus(METRIC_LOCK, null));
        return result;
    }

    /**
     * 获取 /{指标}：单指标配置列表 + 运行状态
     *
     * @param metric 指标
     * @return 指标的结果
     */
    @ReadOperation
    public Map<String, Object> metric(@Selector String metric) {
        return configAndStatus(metric, null);
    }

    /**
     * 获取 /{指标}/{名称}：单配置详情 + 运行状态
     *
     * @param metric 指标
     * @param name 名称
     * @return detail的结果
     */
    @ReadOperation
    public Map<String, Object> detail(@Selector String metric, @Selector String name) {
        return configAndStatus(metric, name);
    }

    /**
     * POST /{指标}/{名称}：配置保存
    */
    @WriteOperation
    public Map<String, Object> save(@Selector String metric, @Selector String name,
                                    Map<String, Object> body) {
        if (METRIC_RATE_LIMITER.equals(metric)) {
            return ratelimiterConfigSave(name, body);
        }
        if (METRIC_CIRCUIT_BREAKER.equals(metric)) {
            return circuitbreakerConfigSave(name, body);
        }
        if (METRIC_LOCK.equals(metric)) {
            return lockConfigSave(name, body);
        }
        return Map.of("error", "unknown metric: " + metric);
    }

    /**
     * 删除 /{指标}/{名称}：配置删除
     *
     * @param metric 指标
     * @param name 名称
     * @return 删除的结果
     */
    @DeleteOperation
    public Map<String, Object> delete(@Selector String metric, @Selector String name) {
        if (METRIC_RATE_LIMITER.equals(metric)) {
            if (rateLimiterRepo == null) {
                return Map.of("error", "配置存储未初始化（未配置 datasource）");
            }
            rateLimiterRepo.delete(name);
            RateLimiterFlow.remove(name);
            return Map.of("name", name, "message", "已删除");
        }
        if (METRIC_CIRCUIT_BREAKER.equals(metric)) {
            if (cbRepo == null) {
                return Map.of("error", "配置存储未初始化（未配置 datasource）");
            }
            cbRepo.delete(name);
            CircuitBreakerFlow.remove(name);
            return Map.of("name", name, "message", "已删除");
        }
        if (METRIC_LOCK.equals(metric)) {
            if (lockRepo == null) {
                return Map.of("error", "配置存储未初始化（未配置 datasource）");
            }
            lockRepo.delete(name);
            LockFlow.remove(name);
            return Map.of("name", name, "message", "已删除");
        }
        return Map.of("error", "unknown metric: " + metric);
    }

    /**
     * POST /{指标}/clear：运行时缓存清空（动作 须为 clear——路径第 3 段）
     *
     * @param metric 指标
     * @param action 动作
     * @return clear全部的结果
     */
    @WriteOperation
    public Map<String, Object> clearAll(@Selector String metric, @Selector String action) {
        if (!"clear".equals(action)) {
            return Map.of("error", "invalid action: " + action + "（应为 clear）");
        }
        if (METRIC_RATE_LIMITER.equals(metric)) {
            RateLimiterFlow.clear();
            return Map.of("message", "限流器缓存已清空");
        }
        if (METRIC_CIRCUIT_BREAKER.equals(metric)) {
            CircuitBreakerFlow.clear();
            return Map.of("message", "熔断器缓存已清空");
        }
        if (METRIC_LOCK.equals(metric)) {
            for (var p : LockFlow.list().values()) {
                try {
                    p.unlock();
                } catch (Exception e) {
                    log.warn("[ConcurrentEndpoint] 释放锁失败: {}", p.getName(), e);
                }
            }
            LockFlow.clear();
            return Map.of("message", "锁缓存已清空");
        }
        return Map.of("error", "unknown metric: " + metric);
    }

    /**
     * POST /{指标}/{名称}/reset：单点复位（当前仅熔断器支持）
    */
    @WriteOperation
    public Map<String, Object> resetOne(@Selector String metric, @Selector String name,
                                        @Selector String action) {
        if (!"reset".equals(action)) {
            return Map.of("error", "invalid action: " + action + "（应为 reset）");
        }
        if (METRIC_CIRCUIT_BREAKER.equals(metric)) {
            var p = CircuitBreakerFlow.get(name);
            if (p == null) {
                return Map.of("error", "not found: " + name);
            }
            p.reset();
            return Map.of("name", name, "message", "熔断器已重置");
        }
        return Map.of("error", "reset 不支持该指标: " + metric);
    }

    // ==================== 旧 API 委托器（兼容直接调用方——无 actuator 注解，不参与端点映射）====================
 // 重构前方法名/形状被 /api/strategy/监控 回退路径（demo 控制器）直接调用；保留为纯方法
    // 委托到新内部实现，actuator 仅暴露新指标前缀路径（旧方法不产生重复根操作）

    /**
     * [旧] 限流器配置列表（直接调用方兼容）
     *
     * @return 限流配置的结果
     */
    public List<Map<String, Object>> ratelimiterConfig() {
        Object configs = metric(METRIC_RATE_LIMITER).get("configs");
        return configs instanceof List ? (List<Map<String, Object>>) configs : List.of();
    }

    /**
     * [旧] 限流器配置单点（名称）——仓储缺失时保留原"配置存储未初始化"语义（配置查询经仓储，不依赖运行时流）
     *
     * @param name 名称
     * @return 限流配置的结果
     */
    public Map<String, Object> ratelimiterConfig(String name) {
        if (rateLimiterRepo == null) {
            return Map.of("error", "配置存储未初始化（未配置 datasource）");
        }
        return rateLimiterRepo.findByName(name)
                .map(c -> {
                    Map<String, Object> m = Wraps.map();
                    m.put("name", c.getName());
                    m.put("permitsPerSecond", c.getPermitsPerSecond());
                    m.put("warmupPeriodMs", c.getWarmupPeriodMs());
                    m.put("timeoutMs", c.getTimeoutMs());
                    m.put("enabled", c.isEnabled());
                    return m;
                })
                .orElse(Map.of("error", "not found: " + name));
    }

    /**
     * [旧] 限流器运行时状态列表
     *
     * @return 限流的结果
     */
    public List<Map<String, Object>> ratelimiter() {
        Object status = metric(METRIC_RATE_LIMITER).get("status");
        return status instanceof List ? (List<Map<String, Object>>) status : List.of();
    }

    /**
     * [旧] 熔断器单点运行时状态（名称）
     *
     * @param name 名称
     * @return circuitbreaker的结果
     */
    public Map<String, Object> circuitbreaker(String name) {
        return detail(METRIC_CIRCUIT_BREAKER, name);
    }

    /**
     * [旧] 锁运行时状态列表
     *
     * @return 锁的结果
     */
    public List<Map<String, Object>> lock() {
        Object status = metric(METRIC_LOCK).get("status");
        return status instanceof List ? (List<Map<String, Object>>) status : List.of();
    }

    // ==================== 内部实现 ====================

    /**
     * 单指标（可选 名称）的配置列表 + 运行状态
     *
     * @param metric 指标
     * @param name 名称
     * @return 配置和状态的结果
     */
    private Map<String, Object> configAndStatus(String metric, String name) {
        if (METRIC_RATE_LIMITER.equals(metric)) {
            return ratelimiterView(name);
        }
        if (METRIC_CIRCUIT_BREAKER.equals(metric)) {
            return circuitbreakerView(name);
        }
        if (METRIC_LOCK.equals(metric)) {
            return lockView(name);
        }
        return Map.of("error", "unknown metric: " + metric);
    }

    /**
     * 限流器：配置 + 运行状态（名称 为空返回列表，否则单点）
     *
     * @param name 名称
     * @return 限流view的结果
     */
    private Map<String, Object> ratelimiterView(String name) {
        List<Map<String, Object>> configs = Wraps.list();
        if (rateLimiterRepo != null) {
            for (ConcurrentRateLimiterConfig c : rateLimiterRepo.findAll()) {
                Map<String, Object> item = Wraps.map();
                item.put("name", c.getName());
                item.put("permitsPerSecond", c.getPermitsPerSecond());
                item.put("warmupPeriodMs", c.getWarmupPeriodMs());
                item.put("timeoutMs", c.getTimeoutMs());
                item.put("enabled", c.isEnabled());
                configs.add(item);
            }
        }
        List<Map<String, Object>> status = Wraps.list();
        for (Map.Entry<String, RateLimiterProvider> e : RateLimiterFlow.list().entrySet()) {
            var p = e.getValue();
            Map<String, Object> item = Wraps.map();
            item.put("name", p.getName());
            item.put("availablePermits", p.availablePermits());
            status.add(item);
        }
        if (name != null) {
            var p = RateLimiterFlow.get(name);
            if (p == null) {
                return Map.of("error", "not found: " + name);
            }
            Map<String, Object> r = Wraps.map();
            r.put("name", p.getName());
            r.put("availablePermits", p.availablePermits());
            r.put("config", configs.stream()
                    .filter(c -> name.equals(c.get("name"))).findFirst().orElse(null));
            return r;
        }
        return Map.of("configs", configs, "status", status);
    }

    /**
     * 熔断器：配置 + 运行状态（名称 为空返回列表，否则单点）
     *
     * @param name 名称
     * @return circuitbreakerView的结果
     */
    private Map<String, Object> circuitbreakerView(String name) {
        List<Map<String, Object>> configs = Wraps.list();
        if (cbRepo != null) {
            for (ConcurrentCircuitBreakerConfig c : cbRepo.findAll()) {
                Map<String, Object> item = Wraps.map();
                item.put("name", c.getName());
                item.put("failureThreshold", c.getFailureThreshold());
                item.put("successThreshold", c.getSuccessThreshold());
                item.put("waitDurationMs", c.getWaitDurationMs());
                item.put("enabled", c.isEnabled());
                configs.add(item);
            }
        }
        List<Map<String, Object>> status = Wraps.list();
        for (var p : CircuitBreakerFlow.list().values()) {
            Map<String, Object> item = Wraps.map();
            item.put("name", p.getName());
            item.put("open", p.isOpen());
            status.add(item);
        }
        if (name != null) {
            var p = CircuitBreakerFlow.get(name);
            if (p == null) {
                return Map.of("error", "not found: " + name);
            }
            Map<String, Object> r = Wraps.map();
            r.put("name", p.getName());
            r.put("open", p.isOpen());
            r.put("config", configs.stream()
                    .filter(c -> name.equals(c.get("name"))).findFirst().orElse(null));
            return r;
        }
        return Map.of("configs", configs, "status", status);
    }

    /**
     * 锁：配置 + 运行状态（名称 为空返回列表，否则单点）
     *
     * @param name 名称
     * @return 锁配置保存的结果
     */
    private Map<String, Object> lockView(String name) {
        List<Map<String, Object>> configs = Wraps.list();
        if (lockRepo != null) {
            for (ConcurrentDistributedLockConfig c : lockRepo.findAll()) {
                Map<String, Object> item = Wraps.map();
                item.put("name", c.getName());
                item.put("lockType", c.getLockType());
                item.put("fair", c.isFair());
                item.put("waitTimeMs", c.getWaitTimeMs());
                item.put("leaseTimeMs", c.getLeaseTimeMs());
                item.put("enabled", c.isEnabled());
                configs.add(item);
            }
        }
        List<Map<String, Object>> status = Wraps.list();
        for (var e : LockFlow.list().entrySet()) {
            var p = e.getValue();
            Map<String, Object> item = Wraps.map();
            item.put("key", e.getKey());
            item.put("name", p.getName());
            item.put("type", p.getType());
            status.add(item);
        }
        if (name != null) {
            var p = LockFlow.get(name);
            if (p == null) {
                return Map.of("error", "not found: " + name);
            }
            Map<String, Object> r = Wraps.map();
            r.put("name", p.getName());
            r.put("type", p.getType());
            r.put("config", configs.stream()
                    /**
                     * 限流配置保存。
                     * @param name 名称
                     * @param body 主体
                     * @return 限流配置保存的结果
                     */
                    .filter(c -> name.equals(c.get("name"))).findFirst().orElse(null));
            return r;
        }
        return Map.of("configs", configs, "status", status);
    }

    /**
     * ratelimiter配置保存。
     *
     * @param name 名称，不允许为 null
     * @param body 请求体，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    private Map<String, Object> ratelimiterConfigSave(String name, Map<String, Object> body) {
        if (rateLimiterRepo == null) {
            return Map.of("error", "配置存储未初始化（未配置 datasource）");
        }
        if (body == null) {
            body = Map.of();
        }
        double pps = toDouble(body.get("permitsPerSecond"), 1.0);
        long warmup = toLong(body.get("warmupPeriodMs"), 0);
        long timeout = toLong(body.get("timeoutMs"), 0);
        boolean enabled = toBool(body.get("enabled"), true);

        ConcurrentRateLimiterConfig config = new ConcurrentRateLimiterConfig();
        config.setName(name);
        config.setPermitsPerSecond(pps);
        config.setWarmupPeriodMs(warmup);
        config.setTimeoutMs(timeout);
        config.setEnabled(enabled);
/**
 * circuitbreaker配置保存。
 * @return circuitbreaker配置保存的结果
 */

        rateLimiterRepo.save(config);
        reloadRateLimiter(name);
        return Map.of("name", name, "message", "保存成功");
    }

    /**
     * circuitbreaker配置保存。
     *
     * @param name 名称，不允许为 null
     * @param body 请求体，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    private Map<String, Object> circuitbreakerConfigSave(String name, Map<String, Object> body) {
        if (cbRepo == null) {
            return Map.of("error", "配置存储未初始化（未配置 datasource）");
        }
        if (body == null) {
            body = Map.of();
        }
        int failure = body.containsKey("failureThreshold") && !body.get("failureThreshold").toString().isEmpty()
                ? Integer.parseInt(body.get("failureThreshold").toString()) : 5;
        int success = body.containsKey("successThreshold") && !body.get("successThreshold").toString().isEmpty()
                ? Integer.parseInt(body.get("successThreshold").toString()) : 2;
        long waitMs = toLong(body.get("waitDurationMs"), 60000);
        boolean enabled = toBool(body.get("enabled"), true);

        ConcurrentCircuitBreakerConfig config = new ConcurrentCircuitBreakerConfig();
        config.setName(name);
        config.setFailureThreshold(failure);
        config.setSuccessThreshold(success);
        config.setWaitDurationMs(waitMs);
        config.setEnabled(enabled);

        cbRepo.save(config);
        CircuitBreakerFlow.clear();
        return Map.of("name", name, "message", "保存成功，缓存已清除");
    }

    /**
     * lock配置保存。
     *
     * @param name 名称，不允许为 null
     * @param body 请求体，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    private Map<String, Object> lockConfigSave(String name, Map<String, Object> body) {
        if (lockRepo == null) {
            return Map.of("error", "配置存储未初始化（未配置 datasource）");
        }
        if (body == null) {
            body = Map.of();
        }
        String lockType = toString(body.get("lockType"), "object");
        boolean fair = toBool(body.get("fair"), false);
        long waitMs = toLong(body.get("waitTimeMs"), 0);
        long leaseMs = toLong(body.get("leaseTimeMs"), -1);
        boolean enabled = toBool(body.get("enabled"), true);

        ConcurrentDistributedLockConfig config = new ConcurrentDistributedLockConfig();
        config.setName(name);
        config.setLockType(lockType);
        config.setFair(fair);
        config.setWaitTimeMs(waitMs);
        config.setLeaseTimeMs(leaseMs);
        config.setEnabled(enabled);

        lockRepo.save(config);
        LockFlow.remove(name);
        return Map.of("name", name, "message", "保存成功，缓存已清除");
    }

    /**
     * 重载限流器（保存后生效）——原实现语义：移除缓存后经 的() 重建
     *
     * @param name 名称
     */
    private void reloadRateLimiter(String name) {
        if (rateLimiterRepo == null) {
            return;
        }
        rateLimiterRepo.findByName(name).ifPresent(c -> {
            RateLimiterFlow.remove(name);
            double pps = c.getPermitsPerSecond();
            // warmup 无法经 of() 精确恢复（remove 已清缓存，下次访问用默认值重建），与原始实现一致
            com.chua.common.support.concurrent.rate.RateLimiterFlow.of(name, pps);
        });
    }

    /**
     * 转为double。
     * @param value 值
     * @param def def
     * @return 转为double的结果
     */
    private double toDouble(Object value, double def) {
        try {
            return value == null ? def : Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 转为long。
     * @param value 值
     * @param def def
     * @return 转为long的结果
     */
    private long toLong(Object value, long def) {
        try {
            return value == null ? def : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 解析int。
     * @param value 值
     * @param def def
     * @return 解析int的结果
     */
    private int parseInt(Object value, int def) {
        try {
            return value == null ? def : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 转为bool。
     * @param value 值
     * @param def def
     * @return 转为bool的结果
     */
    private boolean toBool(Object value, boolean def) {
        return value == null ? def : Boolean.parseBoolean(value.toString());
    }

    /**
     * 转为字符串。
     * @param value 值
     * @param def def
     * @return 转为字符串的结果
     */
    private String toString(Object value, String def) {
        return value == null ? def : value.toString();
    }
}
