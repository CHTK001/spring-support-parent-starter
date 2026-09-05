package com.chua.starter.rpc.support.monitor;

import com.chua.common.support.network.rpc.RpcServer;

/**
 * RPC 服务端实例持有者，将 {@link RpcServer} 暴露给监控 endpoint 访问。
 *
 * <p>当前 {@code RpcServer} 由 {@code RpcServiceAnnotationBeanPostProcessor}
 * 在环境装配阶段创建，未注册为 Spring Bean；本类提供进程级单例持有，
 * 供监控 Controller 获取当前激活的服务端实例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class RpcServerHolder {

    /**
     * 当前激活的 RPC 服务端实例，未启用 RPC 时为 {@code null}
     */
    private static volatile RpcServer rpcServer;

    private RpcServerHolder() {
    }

    /**
     * 获取当前激活的 RPC 服务端实例。
     *
     * @return RPC 服务端实例，未启用 RPC 时返回 {@code null}
     */
    public static RpcServer getRpcServer() {
        return rpcServer;
    }

    /**
     * 设置当前激活的 RPC 服务端实例。
     *
     * @param server RPC 服务端实例，可为 {@code null}
     */
    public static void setRpcServer(RpcServer server) {
        rpcServer = server;
    }
}