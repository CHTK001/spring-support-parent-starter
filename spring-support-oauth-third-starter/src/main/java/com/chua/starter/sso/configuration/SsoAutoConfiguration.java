package com.chua.starter.sso.configuration;

import com.chua.starter.sso.properties.SsoProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * SSO 统一认证自动配置
 * <p>自动扫描第三方登录相关的组件和 Mapper</p>
 *
 * @since 2026/11/24
 * @author CH
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = {
        "com.chua.starter.sso"
})
/**
 * SsoAuto配置类，提供相关能力。
 *
 * @author CH
 * @since 1.0.0
 */
@MapperScan(basePackages = "com.chua.starter.sso.mapper")
@EnableConfigurationProperties({SsoProperties.class})
public class SsoAutoConfiguration {

    /**
     * SSO 自动配置构造方法
     */
    public SsoAutoConfiguration() {
        log.info("SSO 统一认证模块已启用");
    }
}
