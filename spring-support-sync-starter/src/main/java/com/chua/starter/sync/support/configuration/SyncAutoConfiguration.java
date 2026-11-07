package com.chua.starter.sync.support.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.sync.support.client.SyncClient;
import com.chua.starter.sync.support.client.SyncClientWrapper;
import com.chua.starter.sync.support.data.SyncDataExchangeService;
import com.chua.starter.sync.support.properties.SyncProperties;
import com.chua.starter.sync.support.server.SyncServer;
import com.chua.starter.sync.support.spi.SyncClientInfoCustomizer;
import com.chua.starter.sync.support.spi.SyncClientCustomizer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.chua.starter.common.support.logger.ModuleLog.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * 同步协议自动配置
 * <p>
 * 支持 服务端、客户端、both 三种模式，同时判断对应的 enable 属性
 * </p>
 *
 * @since 2024/12/04
 * @author CH
 */
@AutoConfiguration
@EnableConfigurationProperties(SyncProperties.class)
public class SyncAutoConfiguration {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(SyncAutoConfiguration.class);

    @Autowired
    /**
     * 环境
    */
    private Environment environment;

    @PostConstruct
    /**
     * 初始化
    */
    public void init() {
        String type = environment.getProperty("plugin.sync.type");
        Boolean serverEnable = environment.getProperty("plugin.sync.server.enable", Boolean.class, false);
        Boolean clientEnable = environment.getProperty("plugin.sync.client.enable", Boolean.class, false);
        log.info("[Sync] 模式: {}, 服务端: [{}], 客户端: [{}]", 
                highlight(type), status(serverEnable), status(clientEnable));
    }

    /**
     * 创建同步服务端
     * <p>
     * 条件：类型 为 服务端 或 both，且 服务端.enable = true
     * </p>
     *
     * @param syncProperties 同步配置属性
     * @return 同步服务端实例，如果未启用则返回 空
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(SyncServerCondition.class)
    public SyncServer syncServer(SyncProperties syncProperties,
                                 ObjectProvider<com.chua.common.support.network.discovery.ServiceDiscovery> discoveryProvider) {
        log.info("[Sync] 创建服务端");
        /**
         * 同步服务端。
         * @param syncProperties 同步属性
         * @return 同步服务端的结果
         */
        return new SyncServer(syncProperties, resolveServiceDiscovery(syncProperties, discoveryProvider));
    }

    /**
     * 创建同步客户端
     * <p>
     * 条件：类型 为 客户端 或 both，且 客户端.enable = true
     * </p>
     *
     * @param syncProperties 同步配置属性
     * @return 同步客户端实例，如果未启用则返回 空
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(SyncClientCondition.class)
    public SyncClient syncClient(SyncProperties syncProperties,
                                 ObjectProvider<SyncClientInfoCustomizer> syncClientInfoCustomizerProvider,
                                 ObjectProvider<SyncClientCustomizer> syncClientCustomizerProvider,
                                 ObjectProvider<com.chua.common.support.network.discovery.ServiceDiscovery> discoveryProvider) {
        log.info("[Sync] 创建客户端");
        return new SyncClientWrapper(
                syncProperties,
                environment,
                syncClientInfoCustomizerProvider.orderedStream().toList(),
                syncClientCustomizerProvider.orderedStream().toList(),
                resolveServiceDiscovery(syncProperties, discoveryProvider)
        );
    }

    /**
     * 选择同步通道要使用的服务发现实例。
     *
     * <p>容器里可能存在多个实现（如 {@code serviceDiscovery#redis} 与 {@code serviceDiscovery#nacos}）。
     * 装配了 {@code @Primary} 的直接用主实例；没有主实例且只有一个时才用那一个；
     * 有多个又没有主实例则无法判断，此时不接入发现并告警，让同步通道回落到显式地址，
     * 避免「随便挑一个」导致注册到 A 中心却从 B 中心查。</p>
     *
     * @param syncProperties     同步配置
     * @param discoveryProvider  服务发现提供器
     * @return 选中的服务发现实例；不使用时返回 null
     */
    private com.chua.common.support.network.discovery.ServiceDiscovery resolveServiceDiscovery(
            SyncProperties syncProperties,
            ObjectProvider<com.chua.common.support.network.discovery.ServiceDiscovery> discoveryProvider) {
        if (discoveryProvider == null) {
            return null;
        }
        List<com.chua.common.support.network.discovery.ServiceDiscovery> candidates = discoveryProvider.orderedStream().toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            log.warn("[Sync] 检测到 {} 个服务发现实例且未标记 @Primary，服务发现未接入，"
                    + "同步通道将使用显式地址。请通过 spring.main.primary 或删除多余实现使其唯一",
                    candidates.size());
            return null;
        }
        return candidates.getFirst();
    }

    /**
     * 创建数据同步交换服务。
     *
     * @param syncClientProvider 同步客户端提供器
     * @param syncServerProvider 同步服务端提供器
     * @param syncProperties     同步配置
     * @return 数据同步交换服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "plugin.sync.data-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SyncDataExchangeService syncDataExchangeService(ObjectProvider<com.chua.common.support.network.sync.SyncClient> syncClientProvider,
                                                           ObjectProvider<SyncServer> syncServerProvider,
                                                           SyncProperties syncProperties) {
        log.info("[Sync] 创建数据同步交换服务");
        /**
         * 同步数据exchange服务。
         * @param syncClientProvider 同步客户端提供者
         * @param syncServerProvider 同步服务端提供者
         * @param syncProperties 同步属性
         * @return 同步数据exchange服务的结果
         */
        return new SyncDataExchangeService(syncClientProvider, syncServerProvider, syncProperties);
    }

    /**
     * 注册同步模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public ServiceInfoContributor syncServiceInfoContributor() {
        return registry -> registry.registerFlag("sync", "数据同步", true, "数据同步模块");
    }
}
