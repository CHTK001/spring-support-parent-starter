package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.actuator.ActuatorProperties;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.properties.*;
import com.chua.starter.common.support.utils.NonceUtils;
import org.springframework.core.env.Environment;
import com.chua.starter.common.support.service.IptablesService;
import com.chua.starter.common.support.service.impl.IptablesServiceImpl;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 通用配置
 *
 * @since 2023-07-01
 */
@EnableConfigurationProperties({
        ActuatorProperties.class,
        IpProperties.class,
        NonceSignProperties.class,
})
@ComponentScan("com.chua.starter.common.support.service")
public class CommonConfiguration {


    /**
     * 创建默认的认证服务实例。
     * 当容器中没有提供AuthService实例时，使用此默认实现。
     *
     * @return {@link AuthService} 默认认证服务实例
     * @example <pre>
     * // 使用示例
     * &#64;Autowired
     * private AuthService authService;
     * </pre>
     */
    @Bean(name = "defaultAuthService")
    @ConditionalOnMissingBean(AuthService.class)
    public AuthService authService() {
        return new AuthService.DefaultAuthService();
    }

    /**
     * 创建IP访问控制服务实例。
     * <p>
     * IptablesService 提供基于配置的 IP 黑白名单访问控制，
     * 不依赖系统 iptables 命令，仅在应用层做请求过滤。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public IptablesService authService(IpProperties ipProperties) {
        return new IptablesServiceImpl(ipProperties);
    }

    /**
     * 初始化 Nonce 签名密钥（与前端 secretKey 一致）
     * 支持多路径解析：plugin.nonce.sign、nonce.sign、环境变量
     */
    @Bean
    public Object nonceSignInitializer(NonceSignProperties props, Environment env) {
        var key = props.resolveSecretKey(env);
        if (key != null && !key.isBlank() && !NonceSignProperties.DEFAULT_SECRET.equals(key)) {
            NonceUtils.setSecretKey(key);
        }
        return new Object();
    }

    /**
     * 创建RestTemplate实例。
     * 当容器中没有提供RestTemplate实例时，使用此实现。
     *
     * @return {@link RestTemplate} RestTemplate实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private RestTemplate restTemplate;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    /**
     * 创建监控切入点顾问实例。
     * 当容器中没有提供WatchPointcutAdvisor实例时，使用此实现。
     *
     * @return {@link WatchPointcutAdvisor} 监控切入点顾问实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private WatchPointcutAdvisor watchPointcutAdvisor;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public WatchPointcutAdvisor watchPointcutAdvisor() {
        return new WatchPointcutAdvisor();
    }

}

