package com.chua.starter.sse.support;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 服务信息配置
 *
 * <p>负责注册 SSE 模块的服务信息。</p>
 *
 * @version 1.0.0
 * @since 2025/5/8
 * @author CH
 */
@Configuration
public class SseServiceInfoConfiguration {

    /**
     * 注册 SSE 模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public ServiceInfoContributor sseServiceInfoContributor() {
        return registry -> registry.registerFlag("sse", "SSE", true, "SSE 服务端推送模块");
    }
}
