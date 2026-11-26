// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.payment.support.configuration;

import com.chua.payment.support.spi.UserOpenIdResolver;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.common.support.strategy.template.IdempotentTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * 支付模块自动配置类，注册默认支付 OpenId 解析器与服务信息登记。
 *
 * <p>所有 Bean 均使用 {@code @ConditionalOnMissingBean} 或
 * {@code @ConditionalOnProperty} 声明，允许宿主应用覆盖默认实现。</p>
 *
 * <p>MyBatis Plus 拦截器链统一由 spring-support-mybatis-starter 的
 * {@code MybatisPlusConfiguration} 装配（含分页/乐观锁/数据权限/加解密），
 * 本模块不再重复定义（R4）。</p>
 *
 * <p>调度任务统一由 spring-support-job-starter 的 {@code @Job} 契约承接，
 * 本模块不再维护内置线程池调度引擎（R4：调度能力归口 job-starter）。</p>
 *
 * <p>{@code IdempotentTemplate} 位于 common-starter 的
 * {@code com.chua.starter.common.support.strategy.template} 包，宿主组件扫描默认覆盖不到该包，
 * 这里显式 {@code @Import} 注册，保证钱包幂等能力开箱可用。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@AutoConfiguration
@MapperScan("com.chua.payment.support.mapper")
@ComponentScan("com.chua.payment.support")
@Import(IdempotentTemplate.class)
public class PaymentAutoConfiguration {

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