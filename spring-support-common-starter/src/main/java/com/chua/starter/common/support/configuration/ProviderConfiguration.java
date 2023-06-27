package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.pojo.SysArrange;
import com.chua.starter.common.support.pojo.SysArrangeEdge;
import com.chua.starter.common.support.pojo.SysArrangeLogger;
import com.chua.starter.common.support.pojo.SysArrangeNode;
import com.chua.starter.common.support.properties.CoreProperties;
import com.chua.starter.common.support.provider.ArrangeProvider;
import com.chua.starter.common.support.provider.DemoArrangeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 校验码配置
 *
 * @author CH
 */
@EnableAutoTable(packageType = {SysArrange.class, SysArrangeEdge.class, SysArrangeNode.class, SysArrangeLogger.class})
@ComponentScan(basePackageClasses = DemoArrangeHandler.class)
public class ProviderConfiguration {


    @Bean
    @ConditionalOnProperty(prefix = CoreProperties.PRE, name = "open-arrange", matchIfMissing = true, havingValue = "true")
    @ConditionalOnMissingBean
    public ArrangeProvider arrangeProvider() {
        return new ArrangeProvider();
    }
}
