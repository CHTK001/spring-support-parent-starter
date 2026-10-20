package com.chua.starter.filesystem.support.config;

import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.server.embedded.FileStorageEmbeddedFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件服务器两种部署形态的自动装配测试。
 *
 * <p>验证 {@code plugin.filesystem.storage-server.mode} 的条件装配：</p>
 * <ul>
 *   <li>默认（不配置）→ 只挂内置过滤器，不另起独立服务（不额外占端口）；</li>
 *   <li>{@code standalone} → 只起独立服务，不挂内置过滤器；</li>
 *   <li>{@code storage-server.enable=false} → 两者都不装配。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileSystemAutoConfigurationModeTest {

    /**
     * 自动配置运行器（只加载文件系统自动配置，避免拖入整个应用上下文）
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FileSystemAutoConfiguration.class)
            .withPropertyValues("plugin.filesystem.enable=true");

    /**
     * 默认形态：内置过滤器，无独立服务。
     */
    @Test
    void defaultModeRegistersEmbeddedFilterOnly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).doesNotHaveBean(FileStorageServerLauncher.class);

            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getUrlPatterns()).containsExactly("/fs/*");
            assertThat(registration.getFilter()).isInstanceOf(FileStorageEmbeddedFilter.class);
        });
    }

    /**
     * 独立形态：只起独立服务（端口用 0 避免占用真实端口）。
     */
    @Test
    void standaloneModeRegistersLauncherOnly() {
        runner.withPropertyValues(
                        "plugin.filesystem.storage-server.mode=standalone",
                        "plugin.filesystem.storage-server.host=127.0.0.1",
                        "plugin.filesystem.storage-server.port=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileStorageServerLauncher.class);
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    /**
     * 总开关关闭：两种形态都不装配。
     */
    @Test
    void disabledServerRegistersNothing() {
        runner.withPropertyValues("plugin.filesystem.storage-server.enable=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FileStorageEmbeddedFilter.class);
                    assertThat(context).doesNotHaveBean(FileStorageServerLauncher.class);
                    assertThat(context.getBeansOfType(FilterRegistrationBean.class)).isEmpty();
                });
    }
}
