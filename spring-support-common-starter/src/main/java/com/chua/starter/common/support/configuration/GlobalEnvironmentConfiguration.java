package com.chua.starter.common.support.configuration;
import com.chua.common.support.application.GlobalSettingFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局环境工厂 Bean 配置。
 *
 * <p>仅暴露 {@link GlobalSettingFactory} 单例 Bean, 供其它模块注入使用。</p>
 *
 * <p><b>已废弃:</b> 原先 {@code onApplicationEvent(ApplicationReadyEvent)} 中输出
 * 所有模块配置开启/关闭日志的逻辑已删除 - 该职责由 {@code ServiceInfoRegistry}
 * + {@code ServiceInfoBannerListener} 统一承担, 详见
 * {@link com.chua.starter.common.support.serviceinfo.ServiceInfoAutoConfiguration}。</p>
 *
 * @since 2026/01/14
 * @author CH
 */
@Configuration
public class GlobalEnvironmentConfiguration {

    /**
     * 全局环境工厂 Bean。
     *
     * @return 全局环境工厂
     */
    @Bean
    public GlobalSettingFactory globalSettingFactory() {
        return GlobalSettingFactory.getInstance();
    }
}
