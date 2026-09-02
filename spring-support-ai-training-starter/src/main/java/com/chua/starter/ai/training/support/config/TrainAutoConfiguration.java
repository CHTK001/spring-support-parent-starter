package com.chua.starter.ai.training.support.config;

import com.chua.starter.ai.training.support.controller.TrainController;
import com.chua.starter.ai.training.support.service.DataService;
import com.chua.starter.ai.training.support.service.TrainService;
import com.chua.starter.ai.training.support.service.impl.DataServiceImpl;
import com.chua.starter.ai.training.support.service.impl.TrainServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI 训练平台自动配置。
 *
 * <p>注册 {@link DataService}、{@link TrainService} 和 {@link TrainController} 到 Spring 容器，
 * 属性前缀 {@code ai.training.*}，默认开启，可通过 {@code ai.training.enabled=false} 整体关闭。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.training", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TrainProperties.class)
public class TrainAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataService dataService(TrainProperties properties) {
        log.info("[Train] 注册 DataService");
        return new DataServiceImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TrainService trainService(TrainProperties properties) {
        log.info("[Train] 注册 TrainService");
        return new TrainServiceImpl(properties);
    }

    /**
     * 导出 REST 控制器：当宿主未通过组件扫描注册 {@link TrainController} 时生效，
     * 避免训练端点依赖宿主的扫描包路径（与 common/filesystem starter 的惯例一致）。
     */
    @Bean
    @ConditionalOnMissingBean
    public TrainController trainController(DataService dataService, TrainService trainService) {
        log.info("[Train] 注册 TrainController");
        return new TrainController(dataService, trainService);
    }
}