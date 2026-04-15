package com.chua.starter.spider.support.config;

import com.chua.spider.support.SpiderToolkit;
import com.chua.starter.spider.support.controller.SpiderAiController;
import com.chua.starter.spider.support.controller.SpiderDashboardController;
import com.chua.starter.spider.support.controller.SpiderFlowController;
import com.chua.starter.spider.support.controller.SpiderTaskController;
import com.chua.starter.spider.support.controller.SpiderWorkbenchController;
import com.chua.starter.spider.support.engine.SpiderExecutionEngine;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.repository.SpiderWorkbenchTabRepository;
import com.chua.starter.spider.support.sample.SampleTaskFactory;
import com.chua.starter.spider.support.service.impl.NoOpJobStarterClient;
import com.chua.starter.spider.support.service.impl.SpiderAiServiceImpl;
import com.chua.starter.spider.support.service.impl.SpiderJobHandler;
import com.chua.starter.spider.support.service.impl.SpiderScheduledJobServiceImpl;
import com.chua.starter.spider.support.service.impl.SpiderTaskServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 爬虫平台 AutoConfiguration。
 *
 * <p>注册所有平台层 Service、Repository、Controller Bean。
 * 通过 {@code spring.spider.platform.enabled=false} 可关闭平台层（保留底层 Brain 配置）。</p>
 *
 * @author CH
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.spider.platform", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.spider.support.mapper")
@ComponentScan(basePackages = {
        "com.chua.starter.spider.support.repository",
        "com.chua.starter.spider.support.service",
        "com.chua.starter.spider.support.engine",
        "com.chua.starter.spider.support.controller"
})
public class SpiderPlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpiderToolkit spiderToolkit() {
        return new SpiderToolkit();
    }

    @Bean
    @ConditionalOnMissingBean
    public SampleTaskFactory sampleTaskFactory() {
        return new SampleTaskFactory();
    }

    @Bean
    @ConditionalOnMissingBean(name = "jobStarterClient")
    public NoOpJobStarterClient noOpJobStarterClient() {
        return new NoOpJobStarterClient();
    }
}
