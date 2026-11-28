package com.chua.starter.discovery.support.service;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务发现地址解析器：把配置里的「服务名」换成发现中心里的真实实例地址。
 *
 * <p>被认证（oauth）、同步等「配置里要写对端地址」的模块共用，避免各模块各写一套解析逻辑。</p>
 *
 * <p>识别三种写法：</p>
 * <ul>
 *     <li>{@code discovery://<服务名>} —— 显式声明「按服务名走发现中心」；</li>
 *     <li>裸服务名（不含协议、冒号、逗号）—— 先按服务名解析，解析不到再用原值；</li>
 *     <li>完整地址 / 地址列表（含 {@code http://}、{@code host:port}、逗号分隔）—— 原样返回。</li>
 * </ul>
 *
 * <p><b>发现服务未装配时的行为</b>：容器里没有 {@link DiscoveryService}（或它只有默认实现、
 * 查不到实例）时原样返回配置值，静态地址的接入方式完全不变。</p>
 *
 * <p>解析结果按配置值做 5 秒缓存：这类调用落在每请求路径上，不能每次都打发现中心；
 * 5 秒窗口内实例变更最多延迟一个窗口生效。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class DiscoveryAddressResolver {

    /**
     * 显式声明走服务发现的协议前缀。
     */
    public static final String DISCOVERY_SCHEME = "discovery://";

    /**
     * 解析结果缓存时长（毫秒）。
     */
    private static final long CACHE_TTL_MILLIS = 5000L;

    /**
     * 全局解析器。部分调用方（如由 SPI 创建、不归 Spring 管的协议实现）拿不到注入，只能经此静态入口取。
     */
    private static volatile DiscoveryAddressResolver instance;

    /**
     * 发现服务（可能为空：宿主没装配发现能力时保持静态地址语义）。
     */
    private final ObjectProvider<DiscoveryService> discoveryServiceProvider;

    /**
     * 缓存键（配置值原文）。
     */
    private volatile String cachedKey;

    /**
     * 缓存值（解析后的地址串）。
     */
    private volatile String cachedValue;

    /**
     * 缓存写入时间。
     */
    private volatile long cachedAt;

    /**
     * 创建解析器。
     *
     * @param discoveryServiceProvider 发现服务提供者
     */
    public DiscoveryAddressResolver(ObjectProvider<DiscoveryService> discoveryServiceProvider) {
        this.discoveryServiceProvider = discoveryServiceProvider;
    }

    /**
     * 注册全局解析器（由自动配置在装配时调用）。
     *
     * @param resolver 解析器，可为 null（等价于关闭发现寻址）
     */
    public static void install(DiscoveryAddressResolver resolver) {
        instance = resolver;
    }

    /**
     * 按全局解析器解析地址。
     *
     * <p>解析过程中的任何异常都回落原值：调用链不能因为发现中心抖动而整体不可用，
     * 静态配置始终是兜底路径。</p>
     *
     * @param configured 配置值
     * @return 解析后的地址串（多个实例以英文逗号分隔）
     */
    public static String resolve(String configured) {
        DiscoveryAddressResolver resolver = instance;
        if (null == resolver) {
            return configured;
        }
        try {
            return resolver.doResolve(configured);
        } catch (Exception e) {
            log.warn("[Discovery] 地址解析失败，回落配置值: {}", configured, e);
            return configured;
        }
    }

    /**
     * 解析地址：显式 {@code discovery://} 或裸服务名才走发现中心，其余原样返回。
     *
     * @param configured 配置值
     * @return 解析后的地址串
     */
    public String doResolve(String configured) {
        if (StringUtils.isBlank(configured)) {
            return configured;
        }
        String value = configured.trim();
        boolean explicit = value.startsWith(DISCOVERY_SCHEME);
        String serviceName = explicit ? value.substring(DISCOVERY_SCHEME.length()) : value;
        if (StringUtils.isBlank(serviceName) || (!explicit && !isServiceName(value))) {
            return configured;
        }

        String cached = readCache(value);
        if (null != cached) {
            return cached;
        }

        DiscoveryService discoveryService = discoveryServiceProvider.getIfAvailable();
        if (null == discoveryService) {
            // 没装发现能力：显式声明走发现却解析不了时保留原值并告警，避免静默指向不存在的地址
            if (explicit) {
                log.warn("[Discovery] 未装配 DiscoveryService，无法解析服务名: {}", serviceName);
            }
            return configured;
        }

        Set<Discovery> all = discoveryService.getDiscoveryAll(serviceName);
        String resolved = toAddressList(all);
        if (StringUtils.isBlank(resolved)) {
            if (explicit) {
                log.warn("[Discovery] 服务名在发现中心无实例: {}", serviceName);
            }
            return configured;
        }

        writeCache(value, resolved);
        log.info("[Discovery] 服务名解析完成: {} -> {}", serviceName, resolved);
        return resolved;
    }

    /**
     * 判断配置值是否为「裸服务名」：不含协议、端口与多地址分隔符。
     *
     * @param value 配置值
     * @return 是裸服务名返回 true
     */
    private static boolean isServiceName(String value) {
        return value.indexOf("://") < 0 && value.indexOf(':') < 0 && value.indexOf(',') < 0;
    }

    /**
     * 把发现中心的实例集合拼成地址列表（与「多实例用英文逗号分隔」的配置格式一致）。
     *
     * @param all 实例集合
     * @return 逗号分隔的地址串；无实例时返回空串
     */
    private static String toAddressList(Set<Discovery> all) {
        if (null == all || all.isEmpty()) {
            return "";
        }
        Set<String> addresses = new LinkedHashSet<>();
        for (Discovery discovery : all) {
            if (null == discovery || StringUtils.isBlank(discovery.getHost())) {
                continue;
            }
            String protocol = StringUtils.isBlank(discovery.getProtocol()) ? "http" : discovery.getProtocol();
            int port = discovery.getPort();
            addresses.add(port > 0 ? protocol + "://" + discovery.getHost() + ":" + port
                    : protocol + "://" + discovery.getHost());
        }
        return String.join(",", addresses);
    }

    /**
     * 读缓存。
     *
     * @param key 配置值原文
     * @return 命中且未过期时返回缓存值，否则 null
     */
    private String readCache(String key) {
        if (null != cachedValue && key.equals(cachedKey)
                && System.currentTimeMillis() - cachedAt < CACHE_TTL_MILLIS) {
            return cachedValue;
        }
        return null;
    }

    /**
     * 写缓存。
     *
     * @param key   配置值原文
     * @param value 解析结果
     */
    private void writeCache(String key, String value) {
        this.cachedKey = key;
        this.cachedValue = value;
        this.cachedAt = System.currentTimeMillis();
    }
}
