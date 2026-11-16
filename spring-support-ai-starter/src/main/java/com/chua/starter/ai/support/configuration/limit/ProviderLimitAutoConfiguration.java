package com.chua.starter.ai.support.configuration.limit;

import com.chua.starter.ai.support.manage.limit.ProviderLimitProvider;
import com.chua.starter.ai.support.manage.limit.ProviderLimitSpi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.util.List;

/**
 * 限制机制装配：把容器里的 {@link ProviderLimitProvider} 交给 {@link ProviderLimitSpi}。
 *
 * <p>装配顺序即执行顺序：Spring Bean 优先，其次是 {@code META-INF/services} 登记的
 * 扩展实现，最后是内置兜底；同一 {@code kind} 只保留一个。外部模块接限制机制时，
 * 声明一个 Bean 或加一行 SPI 文件即可，不需要改本 starter 的源码。</p>
 *
 * <p>用构造器注入而不是 {@code @Bean}：注册表本身是静态的，只需在容器启动时灌一次；
 * 延迟注入（{@link ObjectProvider}）避免与其它自动配置形成环。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@AutoConfiguration
public class ProviderLimitAutoConfiguration {

    /**
     * 收集容器里的机制实现并安装到注册表。
     *
     * @param providers 容器注入的实现（可能为空）
     */
    public ProviderLimitAutoConfiguration(ObjectProvider<List<ProviderLimitProvider>> providers) {
        ProviderLimitSpi.install(providers.getIfAvailable(List::of));
    }
}
