package com.chua.starter.pay.support.configuration;

import com.chua.starter.pay.support.properties.PayProperties;
import com.chua.starter.pay.support.statemachine.PayOrderCommonStateMachineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 utils-common 模块的支付状态机自动配置
 * <p>
 * 当配置 plugin.pay.state-machine-provider 不等于 spring 时，使用 utils-common 模块的状态机实现。
 * 支持的提供者：
 * <ul>
 *   <li>spring: Spring StateMachine 实现（默认）</li>
 *   <li>common: utils-common 模块的轻量级状态机实现</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/12/03
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PayProperties.class)
@ConditionalOnProperty(prefix = "plugin.pay", name = "state-machine-provider", havingValue = "common")
@ComponentScan(basePackages = {
        "com.chua.starter.pay.support.statemachine"
})
public class PayCommonStateMachineAutoConfiguration {

    /**
     * 创建支付订单状态机服务 Bean
     *
     * @param payProperties 支付配置属性
     * @return 状态机服务实例
     */
    @Bean
    @ConditionalOnMissingBean(PayOrderCommonStateMachineService.class)
    public PayOrderCommonStateMachineService payOrderCommonStateMachineServiceForCommon(PayProperties payProperties) {
        log.info("初始化支付订单状态机服务（utils-common 实现）, 提供者: {}...", payProperties.getStateMachineProvider());
        return new PayOrderCommonStateMachineService(payProperties);
    }

    /**
     * 配置完成后的日志输出
     *
     * @param payProperties 支付配置
     * @return 占位对象
     */
    @Bean
    public Object payCommonStateMachineConfigurationLoggerForCommon(PayProperties payProperties) {
        log.info("========================================");
        log.info("支付状态机配置（utils-common 实现）");
        log.info("========================================");
        log.info("状态机提供者: {}", payProperties.getStateMachineProvider());
        log.info("持久化类型: {}", payProperties.getStateMachinePersistType().getDescription());
        log.info("订单超时时间: {} 分钟", payProperties.getOrderTimeoutMinutes());
        log.info("========================================");
        return new Object();
    }
}
