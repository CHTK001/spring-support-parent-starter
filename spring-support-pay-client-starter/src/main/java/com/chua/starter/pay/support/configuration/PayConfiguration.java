package com.chua.starter.pay.support.configuration;

import com.chua.starter.common.support.properties.IpProperties;
import com.chua.starter.common.support.service.IptablesService;
import com.chua.starter.common.support.service.impl.IptablesServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author CH
 * @since 2025/9/3 8:42
 */
@AutoConfiguration
@MapperScan("com.chua.starter.pay.support.mapper")
@ComponentScan("com.chua.starter.pay.support")
public class PayConfiguration {


    /**
     * 创建 iptables 服务实例
     *
     * @return {@link IptablesService} iptables服务接口实现类实例
     * <p>
     * 示例：
     * 当容器中没有提供 IptablesService 类型的 Bean 时，将自动创建并注册一个 IptablesServiceImpl 实例。
     * <p>
     * 异常情况：
     * 如果在实例化 IptablesServiceImpl 过程中发生错误，将会抛出相应异常信息（中文提示）
     * @author CH
     * @since 2025/9/3 8:42
     */
    @Bean
    @ConditionalOnMissingBean
    public IptablesService iptablesService(IpProperties ipProperties) {
        return new IptablesServiceImpl(ipProperties);
    }
}
