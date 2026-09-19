package com.chua.starter.rpc.support.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * RPC 自动配置
 *
 * <p>负责注册 RPC 模块的服务信息。</p>
 *
 * @版本 1.0.0
 * @since 2025/5/8
 * @author CH
 */
@AutoConfiguration
public class RpcAutoConfiguration {

    /**
     * 注册 RPC 模块服务信息。
     *
     * @return 服务信息贡献器
     */
    @Bean
    public ServiceInfoContributor rpcServiceInfoContributor() {
        return registry -> registry.registerFlag("rpc", "RPC", true, "RPC 远程调用模块");
    }
}
