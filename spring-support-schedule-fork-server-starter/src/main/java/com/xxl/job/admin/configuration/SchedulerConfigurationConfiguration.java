package com.xxl.job.admin.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * freemarker
 *
 * @author CH
 */
public class SchedulerConfigurationConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        //## 调度线程池最大线程配置【必填】
        properties.setProperty("xxl.job.triggerpool.fast.max", "2000");
        properties.setProperty("xxl.job.triggerpool.slow.max", "1000");
        //### 调度中心日志表数据保存天数 [必填]：过期日志自动清理；限制大于等于7时生效，否则, 如-1，关闭自动清理功能；
        properties.setProperty("xxl.job.logretentiondays", "7");
        properties.setProperty("mybatis.mapper-locations", "classpath*:/mybatis-mapper/**/*.xml");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("scheduler", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
