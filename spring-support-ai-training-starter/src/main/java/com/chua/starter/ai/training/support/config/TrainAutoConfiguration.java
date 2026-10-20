package com.chua.starter.ai.training.support.config;

import com.chua.starter.ai.training.support.controller.TrainController;
import com.chua.starter.ai.training.support.service.DataService;
import com.chua.starter.ai.training.support.service.TrainService;
import com.chua.starter.ai.training.support.service.impl.DataServiceImpl;
import com.chua.starter.ai.training.support.service.impl.TrainServiceImpl;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * AI 训练平台自动配置。
 *
 * <p>注册 {@link DataService}、{@link TrainService} 和 {@link TrainController} 到 Spring 容器，
 * 属性前缀 {@code plugin.ai.training.*}，默认开启，可通过 {@code plugin.ai.training.enabled=false} 整体关闭。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.ai.training", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TrainProperties.class)
public class TrainAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /**
     * 数据服务。
     * @param properties 属性
     * @return 数据服务的结果
     */
    public DataService dataService(TrainProperties properties) {
        log.info("[Train] 注册 DataService");
        /**
         * 数据服务impl。
         * @param properties 属性
         * @return 数据服务impl的结果
         */
        return new DataServiceImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * train服务。
     * @param properties 属性
     * @param eventPublisher 事件发布
     * @return train服务的结果
     */
    public TrainService trainService(TrainProperties properties, ApplicationEventPublisher eventPublisher) {
        log.info("[Train] 注册 TrainService");
        /**
         * train服务impl。
         * @param properties 属性
         * @param eventPublisher 事件发布
         * @return train服务impl的结果
         */
        return new TrainServiceImpl(properties, eventPublisher);
    }

    /**
     * 导出 REST 控制器：当宿主未通过组件扫描注册 {@link TrainController} 时生效，
     * 避免训练端点依赖宿主的扫描包路径（与 通用/filesystem 启动 的惯例一致）。
     * @param dataService 数据服务
     * @param trainService train服务
     * @return train控制器的结果
     */
    @Bean
    @ConditionalOnMissingBean
    public TrainController trainController(DataService dataService, TrainService trainService) {
        log.info("[Train] 注册 TrainController");
        /**
         * train控制器。
         * @param dataService 数据服务
         * @param trainService train服务
         * @return train控制器的结果
         */
        return new TrainController(dataService, trainService);
    }

    /**
     * AI 训练平台的 服务信息 贡献者。
     *
     * @param properties 训练配置
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor trainServiceInfoContributor(TrainProperties properties) {
        return registry -> registry.registerFlag("ai-training", "AI训练", properties.isEnabled(),
                "AI 训练平台",
                "[上传限制: " + properties.getMaxSize() + "MB]");
    }
}
