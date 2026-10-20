package com.chua.starter.agent.support.config;

import com.chua.starter.agent.support.service.AgentAttachService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentAutoConfiguration} 自动配置契约测试。
 *
 * <p>覆盖三种装配场景：默认装配、配置禁用、用户自定义 Bean 覆盖。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class AgentAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentAutoConfiguration.class));

    /**
     * 默认（不配任何属性）应装配 AgentAttachService 与配置项 Bean。
     */
    @Test
    void shouldAutoConfigureByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AgentAttachService.class);
            assertThat(context).hasSingleBean(AgentAttachProperties.class);
            assertThat(context.getBean(AgentAttachService.class).isEnabled()).isTrue();
        });
    }

    /**
     * plugin.agent.enabled=false 时整体不装配。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("plugin.agent.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AgentAttachService.class)
                        .doesNotHaveBean(AgentAttachProperties.class));
    }

    /**
     * 宿主自定义同类型 Bean 时自动配置应退避。
     */
    @Test
    void shouldBackOffOnUserDefinedBean() {
        runner.withBean(AgentAttachService.class,
                        () -> new AgentAttachService(new AgentAttachProperties()))
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentAttachService.class);
                    assertThat(context.getBean(AgentAttachService.class).isEnabled()).isTrue();
                });
    }
}
