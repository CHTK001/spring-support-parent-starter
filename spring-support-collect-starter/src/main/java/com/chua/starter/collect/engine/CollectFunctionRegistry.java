package com.chua.starter.collect.engine;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.collect.engine.spi.CollectFunctionDef;
import com.chua.starter.collect.engine.spi.CollectFunctionProvider;
import com.chua.starter.collect.properties.CollectProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义函数注册表。
 *
 * <p>把 {@link CollectFunctionProvider} 交出来的函数缓存成一张名字到定义的表，注册为
 * {@link CollectBuiltinInvoker} 的用户函数回调，于是脚本里的 {@code fn.xxx(...)} 与参数表达式里的
 * {@code ${xxx(...)}} 共用同一批函数。缓存按 {@value #REFRESH_MS}ms 过期，配置写入方调
 * {@link #invalidate()} 立即生效。</p>
 *
 * <p>函数体是完整代码执行面，边界与脚本节点一致；递归超过 {@value #MAX_DEPTH} 层直接拒绝，
 * 避免成环的函数把采集线程拖死。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class CollectFunctionRegistry implements CollectBuiltinInvoker.UserHook {

    /**
     * 缓存有效期毫秒，兜住多实例部署下别的节点改了函数库。
     */
    private static final long REFRESH_MS = 5000L;

    /**
     * 单线程内函数嵌套调用上限。
     */
    private static final int MAX_DEPTH = 8;

    /**
     * 函数来源，Spring Bean 与 @Spi 都收。
     */
    private final ObjectProvider<CollectFunctionProvider> providers;

    /**
     * 运行期配置，用于判断自定义函数库开关。
     */
    private final CollectProperties properties;

    /**
     * 当前线程的函数嵌套层数。
     */
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    /**
     * 函数名到定义。
     */
    private volatile Map<String, CollectFunctionDef> cached = Map.of();

    /**
     * 上次装载时间。
     */
    private volatile long loadedAt;

    /**
     * 构造注册表。
     *
     * @param provider   Spring 容器内的函数来源
     * @param properties 运行期配置
     */
    public CollectFunctionRegistry(ObjectProvider<CollectFunctionProvider> provider,
                                   CollectProperties properties) {
        this.providers = provider;
        this.properties = properties;
    }

    /**
     * 挂到表达式求值的函数解析链上；开关关闭时不挂，重启后按新值决定。
     */
    @PostConstruct
    public void bind() {
        if (!properties.getFunction().isEnable()) {
            log.info("[collect] 自定义函数库已在运行参数中关闭，不挂载函数解析链");
            return;
        }
        CollectBuiltinInvoker.registerUserHook(this);
        log.info("[collect] 自定义函数注册表就绪 refreshMs={} maxDepth={}", REFRESH_MS, MAX_DEPTH);
    }

    /**
     * 摘除回调，容器关闭后不再解析用户函数。
     */
    @PreDestroy
    public void unbind() {
        CollectBuiltinInvoker.registerUserHook(null);
    }

    @Override
    public CollectBuiltinInvoker.Outcome call(String name, List<Object> arguments) {
        if (!properties.getFunction().isEnable()) {
            return CollectBuiltinInvoker.Outcome.MISS;
        }
        CollectFunctionDef def = snapshot().get(name);
        if (def == null) {
            return CollectBuiltinInvoker.Outcome.MISS;
        }
        int level = depth.get() + 1;
        if (level > MAX_DEPTH) {
            depth.remove();
            throw new IllegalStateException("函数 " + name + " 递归超过 " + MAX_DEPTH + " 层，已拒绝");
        }
        depth.set(level);
        try {
            Map<String, Object> bindings = Map.of("fn", CollectGroovyRuntime.functionObject());
            return CollectBuiltinInvoker.Outcome.of(
                    CollectGroovyRuntime.callClosure(def.script(), bindings, arguments.toArray()));
        } finally {
            if (level == 1) {
                depth.remove();
            } else {
                depth.set(level - 1);
            }
        }
    }

    /**
     * 让缓存立即失效，函数配置写入后调用。
     */
    public void invalidate() {
        loadedAt = 0L;
    }

    /**
     * 函数是否已登记，供界面与校验提示。
     *
     * @param name 函数名
     * @return 命中返回 true
     */
    public boolean contains(String name) {
        return snapshot().containsKey(name);
    }

    /**
     * 取缓存快照，过期则重新装载。
     *
     * @return 函数名到定义
     */
    private synchronized Map<String, CollectFunctionDef> snapshot() {
        if (!properties.getFunction().isEnable()) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        if (now - loadedAt < REFRESH_MS) {
            return cached;
        }
        Map<String, CollectFunctionDef> loaded = new LinkedHashMap<>();
        for (CollectFunctionProvider provider : resolve()) {
            List<CollectFunctionDef> defs;
            try {
                defs = provider.loadEnabled();
            } catch (Exception ex) {
                // 函数库读不出来不能影响流程执行，退化成本轮没有自定义函数。
                log.warn("[collect] 自定义函数装载失败 provider={} err={}",
                        provider.getClass().getName(), ex.getMessage());
                continue;
            }
            for (CollectFunctionDef def : defs) {
                if (def == null || def.name() == null || def.name().isBlank()) {
                    continue;
                }
                CollectFunctionDef previous = loaded.putIfAbsent(def.name(), def);
                if (previous != null) {
                    log.warn("[collect] 自定义函数重名，已忽略 name={} provider={}",
                            def.name(), provider.getClass().getName());
                }
            }
        }
        loaded.keySet().stream().filter(CollectBuiltinInvoker::isBuiltin).forEach(name ->
                log.warn("[collect] 自定义函数与内置函数同名，实际生效的是内置版本 name={}", name));
        cached = Map.copyOf(loaded);
        loadedAt = now;
        return cached;
    }

    /**
     * 收集函数来源，Spring 侧优先，SPI 侧补第三方 jar。
     *
     * <p>SPI 可能用登记名与注解值两个别名各交付一次同一个实现，这里按实现类去重，
     * 否则重名告警会重复刷屏。</p>
     *
     * @return 去重后的来源列表
     */
    private List<CollectFunctionProvider> resolve() {
        Map<Class<?>, CollectFunctionProvider> unique = new LinkedHashMap<>();
        providers.orderedStream().forEach(item -> unique.putIfAbsent(item.getClass(), item));
        try {
            ServiceProvider.of(CollectFunctionProvider.class)
                    .collect(item -> unique.putIfAbsent(item.getClass(), item));
        } catch (Exception ex) {
            log.debug("[collect] SPI 函数来源收集跳过: {}", ex.getMessage());
        }
        return List.copyOf(unique.values());
    }
}
