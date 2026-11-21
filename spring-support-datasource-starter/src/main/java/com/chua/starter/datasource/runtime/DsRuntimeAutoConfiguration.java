package com.chua.starter.datasource.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 数据源运行时管理自动配置。
 *
 * <p>装配 Calcite 联邦与分库分表的运行期管理能力（配置表存储、热刷新、
 * REST 管理接口）。仅在宿主存在 Servlet Web 环境时生效；
 * 可通过 {@code plugin.datasource.runtime.enabled=false} 关闭。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "plugin.datasource.runtime", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.chua.starter.datasource.runtime")
public class DsRuntimeAutoConfiguration {
}
