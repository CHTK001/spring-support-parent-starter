package com.chua.starter.spider.support;

import com.chua.spider.support.brain.SpiderBrainRegistry;
import com.chua.spider.support.model.SpiderBrainDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SpiderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SpiderConfiguration.class);

    @AfterEach
    void tearDown() {
        SpiderBrainRegistry.clearDefault();
    }

    @Test
    void 应该注册默认大脑配置() {
        contextRunner
                .withPropertyValues(
                        "plugin.spider.enable=true",
                        "plugin.spider.brain.enable=true",
                        "plugin.spider.brain.provider=siliconflow",
                        "plugin.spider.brain.app-key=test-app-key",
                        "plugin.spider.brain.model=Qwen/Qwen2.5-7B-Instruct",
                        "plugin.spider.brain.base-url=https://api.siliconflow.cn"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SpiderBrainDefinition.class);
                    SpiderBrainDefinition definition = context.getBean(SpiderBrainDefinition.class);
                    assertThat(definition.getProvider()).isEqualTo("siliconflow");
                    assertThat(definition.getApiKey()).isEqualTo("test-app-key");
                    assertThat(definition.getModel()).isEqualTo("Qwen/Qwen2.5-7B-Instruct");

                    SpiderBrainDefinition global = SpiderBrainRegistry.resolveDefault();
                    assertThat(global).isNotNull();
                    assertThat(global.getProvider()).isEqualTo("siliconflow");
                    assertThat(global.getApiKey()).isEqualTo("test-app-key");
                });
    }

    @Test
    void 大脑关闭时不应该注册默认配置() {
        contextRunner
                .withPropertyValues(
                        "plugin.spider.enable=true",
                        "plugin.spider.brain.enable=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpiderBrainDefinition.class);
                    assertThat(SpiderBrainRegistry.resolveDefault()).isNull();
                });
    }
}
