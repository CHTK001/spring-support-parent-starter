package com.chua.payment.support.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.payment.support.spi.UserOpenIdResolver;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
* 支付模块自动配置类，注册 MyBatis Plus 拦截器、默认 打开标识 解析器与线程池调度器。
*
* @author CH
* @since 4.0.0.50
 */
@AutoConfiguration
@MapperScan("com.chua.payment.support.mapper")
@ComponentScan("com.chua.payment.support")
public class PaymentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    /**
    * 薪酬mybatisplus拦截器
    *
    * @return 薪酬mybatisplus拦截器的结果
    */
    public MybatisPlusInterceptor paymentMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(UserOpenIdResolver.class)
    /**
    * 默认用户打开标识解析器
    *
    * @return 默认用户打开id解析器的结果
    */
    public UserOpenIdResolver defaultUserOpenIdResolver() {
        return (merchantId, userId, channelType) -> null;
    }

    @Bean(name = "paymentThreadPoolTaskScheduler")
    @ConditionalOnMissingBean(name = "paymentThreadPoolTaskScheduler")
    @ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "internal", matchIfMissing = true)
    /**
    * 薪酬thread游泳池任务调度器
    *
    * @return 薪酬thread游泳池任务调度器的结果
    */
    public ThreadPoolTaskScheduler paymentThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("payment-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Bean
    /**
    * 薪酬服务信息contributor
    *
    * @return 薪酬服务信息contributor的结果
    */
    public ServiceInfoContributor paymentServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("payment", "支付", true, "支付模块");
        };
    }
}

