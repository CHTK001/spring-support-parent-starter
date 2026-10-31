package com.chua.starter.rpc.support.resource;

import com.chua.common.support.network.rpc.RpcException;
import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 调用期降级处理器
 * <p>
 * {@link RpcResourceBean#getObject()} 只能捕获「创建远程代理」阶段的失败；
 * 部分协议（如 JSON-RPC）创建代理不发起网络请求，失败发生在真正调用时，
 * 此时需要在这里切到 {@code degrade} 指定的本地实现。
 * </p>
 * <p>
 * 仅对传输层失败降级：业务异常（{@link RpcException#isBusiness()}）原样抛出，
 * 避免把「远端业务校验失败」误判成「远端不可用」而重复执行本地逻辑。
 * </p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
class RpcFailoverInvocationHandler implements InvocationHandler {

    /**
     * 远端协议代理
     */
    private final Object remote;

    /**
     * 所属引用 Bean（提供降级代理与降级提供者名称）
     */
    private final RpcResourceBean<?> owner;

    /**
     * 创建调用期降级处理器
     *
     * @param remote 远端协议代理
     * @param owner  引用 Bean
     */
    RpcFailoverInvocationHandler(Object remote, RpcResourceBean<?> owner) {
        this.remote = remote;
        this.owner = owner;
    }

    /**
     * 先走远端；传输层失败时切换到 degrade 提供的本地实现
     *
     * @param proxy  代理
     * @param method 方法
     * @param args   参数
     * @return 调用结果
     * @throws Throwable 远端或降级实现的原始异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return ReflectUtils.invokeProxy(remote, method, args);
        }
        try {
            return ReflectUtils.invokeProxy(remote, method, args);
        } catch (Throwable e) {
            if (!isTransportFailure(e)) {
                throw e;
            }
            Object fallback;
            try {
                fallback = owner.getDegradeProxy();
            } catch (Throwable de) {
                // 降级实现不可用：以远端失败为主因抛出，降级失败作为被抑制异常保留现场
                log.warn("[RPC] 降级到 {} provider 失败: {}", owner.getDegradeName(), de.getMessage());
                if (de != e) {
                    e.addSuppressed(de);
                }
                throw e;
            }
            if (fallback == null) {
                log.warn("[RPC] 降级到 {} provider 未找到实现", owner.getDegradeName());
                throw e;
            }
            log.warn("[RPC] 远程调用失败，降级到 {} provider: {}", owner.getDegradeName(), e.getMessage());
            try {
                return ReflectUtils.invokeProxy(fallback, method, args);
            } catch (Throwable de) {
                de.addSuppressed(e);
                throw de;
            }
        }
    }

    /**
     * 判断是否为传输层失败（可降级）
     *
     * @param e 调用异常
     * @return true 表示远端不可用，可切换到降级实现
     */
    private static boolean isTransportFailure(Throwable e) {
        if (e instanceof RpcException rpcException) {
            return !rpcException.isBusiness();
        }
        if (e instanceof IOException) {
            return true;
        }
        return "org.apache.dubbo.rpc.RpcException".equals(e.getClass().getName());
    }
}
