// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * 支付模块自动配置类，注册 MyBatis Plus 拦截器、默认支付 OpenId 解析器与线程池调度器。
 *
 * <p>所有 Bean 均使用 {@code @ConditionalOnMissingBean} 或
 * {@code @ConditionalOnProperty} 声明，允许宿主应用覆盖默认实现。</p>
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
     * 构建支付模块的 MyBatis Plus 拦截器。
     *
     * @return MyBatis Plus 拦截器
     */
    public MybatisPlusInterceptor paymentMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(UserOpenIdResolver.class)
    /**
     * 默认用户 OpenId 解析器。
     *
     * <p>默认实现恒返回 {@code null}，表示不解析 OpenId，由宿主应用按需覆盖。</p>
     *
     * @return 默认用户 OpenId 解析器
     */
    public UserOpenIdResolver defaultUserOpenIdResolver() {
        return (merchantId, userId, channelType) -> null;
    }

    @Bean(name = "paymentThreadPoolTaskScheduler")
    @ConditionalOnMissingBean(name = "paymentThreadPoolTaskScheduler")
    @ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "internal", matchIfMissing = true)
    /**
     * 构建支付模块的线程池任务调度器。
     *
     * @return 支付线程池任务调度器
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
     * 构建支付模块的服务信息贡献者。
     *
     * @return 服务信息贡献者
     */
    public ServiceInfoContributor paymentServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("payment", "支付", true, "支付模块");
        };
    }
}