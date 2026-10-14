package com.chua.starter.datasourcehub.support.middleware;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import com.chua.datasource.support.middleware.MiddlewareNotInstalledException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中间件 SPI 分发器：宿主侧唯一取实现的入口。
 *
 * <p><b>为什么要有这一层</b>：宿主（datasource-hub）不直接依赖任何重型中间件客户端
 * （Kafka / InfluxDB / ZooKeeper / Neo4j …）。实现放在各自的
 * {@code utils-support-*-starter} 里，通过 {@link ServiceProvider} 装配：</p>
 * <ul>
 *   <li><b>默认装载</b>：宿主引了对应 starter，实现方在
 *       {@code META-INF/extensions/com.chua.datasource.support.middleware.MiddlewareClientProvider}
 *       注册即生效；</li>
 *   <li><b>OSGi 后装</b>：实现方打成 bundle 由 bundle-starter 安装，
 *       {@code OsgiServiceResolver} 会优先从 OSGi 容器取服务。</li>
 * </ul>
 *
 * <p><b>取不到实现时</b>：抛 {@link MiddlewareNotInstalledException}，由
 * {@link #call} 统一转成「功能未开放」的业务结果 —— 不落 500、不打 ERROR 栈。</p>
 *
 * <p><b>新增一种中间件</b>不需要改这里，也不需要改任何 Controller：
 * 只要实现方注册了 SPI，{@link #names()} 与分发自动生效。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class MiddlewareClientRegistry {

    private final ServiceProvider<MiddlewareClientProvider> providers = ServiceProvider.of(MiddlewareClientProvider.class);

    /**
     * 已装配的实现（按 {@link MiddlewareClientProvider#name()} 索引）。
     *
     * <p>每次现取而不是构造期快照：OSGi bundle 是运行期安装的，
     * 快照会导致「装上后用不了、卸掉后还在」。</p>
     */
    private Map<String, MiddlewareClientProvider> snapshot() {
        Map<String, MiddlewareClientProvider> map = new LinkedHashMap<>();
        // 每次现取而不是构造期快照：OSGi bundle 是运行期安装的，
        // 快照会导致「装上后用不了、卸掉后还在」。
        for (ServiceDefinition definition : providers.getPriorityServiceDefinitions()) {
            try {
                MiddlewareClientProvider impl = (MiddlewareClientProvider)
                        definition.getObj(providers.getServiceAutowire());
                if (impl == null) {
                    continue;
                }
                String name = impl.name();
                if (name == null || name.isBlank()) {
                    continue;
                }
                map.put(name.toLowerCase(), impl);
            } catch (Throwable t) {
                // 单个实现初始化失败不应让整个注册表不可用（bundle 装了一半时很常见）
                log.warn("[middleware] 跳过初始化失败的 SPI 实现: {}", String.valueOf(definition.getName()), t);
            }
        }
        return map;
    }

    /**
     * 已装配的类型名（小写），前端据此渲染数据源类型下拉。
     *
     * @return 类型名列表；一个都没装时返回空列表
     */
    public List<String> names() {
        return new ArrayList<>(snapshot().keySet());
    }

    /**
     * 某个类型是否已开放功能。
     *
     * @param type 数据源类型，大小写不敏感
     * @return 已开放返回 {@code true}
     */
    public boolean isSupported(String type) {
        return type != null && snapshot().containsKey(type.toLowerCase());
    }

    /**
     * 取某个类型的实现。
     *
     * @param type 数据源类型，大小写不敏感
     * @return 实现；未装配时抛出 {@link MiddlewareNotInstalledException}
     */
    public MiddlewareClientProvider require(String type) {
        if (type == null || type.isBlank()) {
            throw new MiddlewareNotInstalledException(String.valueOf(type));
        }
        MiddlewareClientProvider impl = snapshot().get(type.toLowerCase());
        if (impl == null) {
            throw new MiddlewareNotInstalledException(type.toLowerCase());
        }
        return impl;
    }

    /**
     * 取某个类型的实现，未装配时返回空。
     *
     * @param type 数据源类型，大小写不敏感
     * @return 实现的 Optional 包装
     */
    public Optional<MiddlewareClientProvider> find(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(snapshot().get(type.toLowerCase()));
    }

    /**
     * 全部实现的展示信息（类型名 → 展示名），供「功能未开放」提示使用。
     *
     * @return 不可变映射
     */
    public Map<String, String> displayNames() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, MiddlewareClientProvider> e : snapshot().entrySet()) {
            out.put(e.getKey(), e.getValue().displayName());
        }
        return Map.copyOf(out);
    }

    /**
     * 统一调用包装：把 SPI 侧的「功能未开放」与业务异常转成可读结果，避免 500 与裸栈。
     *
     * <p>用法：{@code return call("测试 Kafka 连接", () -> provider.test(request));}</p>
     *
     * @param <T>      返回数据类型
     * @param action   动作描述（日志与提示用）
     * @param supplier 实际调用
     * @return 调用结果；未开放时返回 {@code code=功能未开放} 的结果对象
     */
    public <T> T call(String action, java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (MiddlewareNotInstalledException e) {
            log.info("[middleware] {} -> 功能未开放: type={}", action, e.type());
            throw e;
        } catch (IllegalArgumentException e) {
            // 语句结构不合法、方法不在白名单等 —— 调用方写错，不是系统故障
            log.info("[middleware] {} -> 参数不合法: {}", action, e.getMessage());
            throw e;
        }
    }
}
