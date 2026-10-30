package com.chua.starter.rpc.support.spring;

import com.chua.common.support.network.rpc.RpcException;
import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 本地 Bean 调用处理器（springbean 协议）
 *
 * <p>不执行任何网络传输：调用被路由到同一个 Spring Application Context 内与
 * {@code targetType} 匹配的本地 Bean 上，使其返回结构与远程 RPC 完全相同。</p>
 *
 * <p>与 {@code dubbo/sofa/json} 协议的代理不同，本地 Bean 的调用必须在</p>
 * <ul>
 *   <li>{@link #invoke(Object, Method, Object[])} 中保留原始异常（使用 {@link ReflectUtils#invokeProxy}），
 *       以便重试/熔断/降级等上层组件按异常类型做决策；</li>
 *   <li>没有找到本地 Bean 时，严格按 {@link SpringBeanRpcClient#isException()} 开关执行：
 *       抛 {@link RpcException}（{@code true}，默认）或静默返回 null（{@code false}）。</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class SpringBeanRpcInvocationHandler<T> implements InvocationHandler {

    /**
     * 目标接口类型
     */
    private final Class<T> targetType;

    /**
     * 发起调用的客户端（本地 Bean 供给者）
     */
    private final SpringBeanRpcClient client;

    /**
     * 创建本地 Bean 调用处理器
     *
     * @param targetType 目标类型
     * @param client     springbean 客户端
     */
    public SpringBeanRpcInvocationHandler(Class<T> targetType, SpringBeanRpcClient client) {
        this.targetType = targetType;
        this.client = client;
    }

    /**
     * 代理调用：将通信层的方法调用交给本地 Bean 执行
     *
     * @param proxy  代理
     * @param method 方法
     * @param args   参数
     * @return 调用结果
     * @throws Throwable 目标方法抛出的原始异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 直接解析最新目标：Bean 可能更换（容错场景下的语义对齐）
        Object target = client.getLocalBean(targetType);
        if (target == null) {
            if (client.isException()) {
                log.warn("[SpringBean RPC] 未找到本地 Bean: {}", targetType.getName());
                throw new RpcException(RpcException.Type.TRANSPORT,
                        "未在本地 Spring Application Context 中找到 Bean: " + targetType.getName());
            }
            log.debug("[SpringBean RPC] 未找到本地 Bean: {}", targetType.getName());
            return null;
        }
        return ReflectUtils.invokeProxy(target, method, args);
    }

    /**
     * 目标接口类型
     *
     * @return 目标类型
     */
    public Class<T> getTargetType() {
        return targetType;
    }

    /**
     * 发起调用的客户端
     *
     * @return 客户端
     */
    public SpringBeanRpcClient getClient() {
        return client;
    }
}
