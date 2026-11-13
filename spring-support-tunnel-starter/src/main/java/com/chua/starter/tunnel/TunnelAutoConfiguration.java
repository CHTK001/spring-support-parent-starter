package com.chua.starter.tunnel;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.tunnel.properties.TunnelProperties;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;
import com.chua.starter.tunnel.support.TunnelController;
import com.chua.starter.tunnel.support.TunnelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 内网穿透统一入口自动配置。
 *
 * <p>组织方式对齐 {@code QueueAutoConfiguration} / {@code SocketAutoConfiguration}：
 * 本模块是「标准 starter」，只提供 SPI、编排服务与统一接口，<b>不含任何厂商客户端</b>。
 * 具体提供方由独立 starter 提供（{@code spring-support-tunnel-ngrok-starter}、
 * {@code spring-support-tunnel-cloudflared-starter}），引入哪个就有哪个，二者可同时存在。</p>
 *
 * <p>开关：{@code plugin.tunnel.enable}（默认 true）。关闭后不注册接口与编排服务，
 * 各提供方 starter 自己的一次性隧道能力不受影响。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = TunnelProperties.PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TunnelProperties.class)
public class TunnelAutoConfiguration {

    /**
     * 收集容器内全部 {@link TunnelProvider}，按白名单裁剪后建注册表。
     *
     * <p>用 {@link ObjectProvider} 是为了允许「一个实现都没引入」的场景：
     * 此时注册表为空，接口会返回空提供方列表而不是启动失败。</p>
     *
     * @param providers  容器内全部实现
     * @param properties 统一配置（白名单）
     * @return 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public TunnelProviderRegistry tunnelProviderRegistry(
            ObjectProvider<TunnelProvider> providers, TunnelProperties properties) {
        List<TunnelProvider> all = new ArrayList<>();
        providers.orderedStream().forEach(all::add);

        List<String> whitelist = properties.getProviders();
        if (whitelist != null && !whitelist.isEmpty()) {
            List<String> wanted = whitelist.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> name.trim().toLowerCase(Locale.ROOT))
                    .toList();
            List<TunnelProvider> filtered = all.stream()
                    .filter(provider -> wanted.contains(provider.name().trim().toLowerCase(Locale.ROOT)))
                    .toList();
            log.info("[Tunnel] 按 plugin.tunnel.providers={} 裁剪提供方：{} → {}",
                    wanted, all.size(), filtered.size());
            all = new ArrayList<>(filtered);
        }
        return new TunnelProviderRegistry(all);
    }

    /**
     * 统一编排服务。
     *
     * @param properties 统一配置
     * @param registry   提供方注册表
     * @param environment Spring 环境（取 {@code server.port} 推导本实例控制地址）
     * @return 编排服务
     */
    @Bean
    @ConditionalOnMissingBean
    public TunnelService tunnelService(TunnelProperties properties, TunnelProviderRegistry registry,
                                       org.springframework.core.env.Environment environment) {
        String instanceUrl = properties.getInstanceUrl();
        if (instanceUrl == null || instanceUrl.isBlank()) {
            String port = environment.getProperty("server.port", "8080");
            instanceUrl = "http://127.0.0.1:" + port;
        }
        return new TunnelService(properties, registry, instanceUrl);
    }

    /**
     * 服务信息登记（控制台「模块能力」可见）。
     *
     * @param registry 提供方注册表
     * @return 登记器
     */
    @Bean
    public ServiceInfoContributor tunnelServiceInfoContributor(TunnelProviderRegistry registry) {
        return info -> info.registerFlag("tunnel", "内网穿透", true,
                "内网穿透统一入口，已注册提供方: " + registry.names());
    }

    /**
     * Web 环境下的统一接口。
     *
     * <p>非 Web 应用不注册控制器；编排服务本身不依赖 {@code spring-web}，
     * 因此纯后台进程也能使用。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    public static class TunnelWebConfiguration {

        /**
         * 注册统一接口。
         * <p>
         * 不能使用 bean 名 {@code tunnelController}（与 server-starter 组件扫描得到的
         * {@code @RestController TunnelController} 同名——实际是 /ms/tunnel/** 与
         * /ms/server/host/{hostId}/tunnel 两个独立端点集，需要兼容共存），
         * 所以这里的统一接口换一个名称。条件不再存在，本模块就是统一端点的提供者。
         *
         * @param service    编排服务
         * @param properties 统一配置
         * @return 控制器
         */
        @Bean
        public TunnelController tunnelApiController(TunnelService service, TunnelProperties properties) {
            return new TunnelController(service, properties);
        }
    }
}
