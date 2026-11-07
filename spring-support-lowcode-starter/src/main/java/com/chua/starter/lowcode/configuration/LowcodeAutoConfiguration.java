package com.chua.starter.lowcode.configuration;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.lowcode.properties.LowcodeProperties;
import com.chua.starter.lowcode.support.LowcodeMetaObjectHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 低代码表单管理自动装配：把表单/页面设计能力做成「加依赖即生效」的独立模块。
 *
 * <p>本 starter 承载低代码侧的全部能力：基础数据模型（directory / form / page / version）、
 * 业务服务（service）、Mapper 与实体（mapper / entity）、对外接口（controller），
 * 以及随包携带的建表脚本（{@code db/init/**}）。</p>
 *
 * <p>消费方只需：</p>
 *
 * <pre>
 * 1) 依赖 spring-support-lowcode-starter
 * 2) 需要时用 plugin.lowcode.enable=false 关闭（默认开，与 {@link LowcodeProperties} 一致）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = LowcodeProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LowcodeProperties.class)
@ComponentScan(
        basePackages = LowcodeAutoConfiguration.BASE_PACKAGE,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = LowcodeAutoConfiguration.class))
@MapperScan(LowcodeAutoConfiguration.MAPPER_PACKAGE)
public class LowcodeAutoConfiguration {

    /**
     * 低代码模块的根包，消费方自行扫描时使用。
     */
    public static final String BASE_PACKAGE = "com.chua.starter.lowcode";

    /**
     * 低代码模块的 Mapper 包，消费方自行扫描时使用。
     */
    public static final String MAPPER_PACKAGE = "com.chua.starter.lowcode.mapper";

    /**
     * 审计字段自动填充。
     *
     * <p>必须在自动配置里用 {@code @Bean} 声明：{@code @ConditionalOnMissingBean} 只有在这里
     * 才能看到消费方已注册的同类型 bean 并正确让位；挂在 {@code @Component} 上时判定发生在
     * 组件扫描期，实测会导致本 bean 完全不注册，审计列全部为 NULL。</p>
     *
     * @param authServiceProvider 当前登录用户来源，可为空
     * @return 审计字段处理器
     */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler lowcodeMetaObjectHandler(ObjectProvider<AuthService> authServiceProvider) {
        return new LowcodeMetaObjectHandler(authServiceProvider);
    }
}
