package com.chua.starter.pay.support.configuration;

import com.chua.starter.mqtt.support.template.MqttTemplate;
import com.chua.starter.pay.support.properties.PayMqttProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 支付配置
 *
 * @author CH
 * @since 2024/12/27
 */
@Slf4j
@MapperScan("com.chua.starter.pay.support.mapper")
@ComponentScan({
        "com.chua.starter.pay.support.service",
        "com.chua.starter.pay.support.controller",
        "com.chua.starter.pay.support.scheduler",
})
@EnableConfigurationProperties(PayMqttProperties.class)
@EnableScheduling
@EnableTransactionManagement
public class PayConfiguration {



}
