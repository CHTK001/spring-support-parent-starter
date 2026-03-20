package com.chua.starter.rpc.support.interceptor;

import java.lang.reflect.Method;

/**
 * RPC拦截器接口
 * <p>
 * 用于在RPC调用前后进行拦截处理，支持：
 * - 参数验证
 * - 日志记录
 * - 性能监控
 * - 权限校验
 * - 异常处理
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
public interface RpcInterceptor {

    /**
     * 调用前拦截
     *
     * @param method 方法
     * @param args   参数
     * @return 是否继续执行，返回false表示拦截
     */
    default boolean preHandle(Method method, Object[] args) {
        return true;
    }

    /**
     * 调用后处理
     *
     * @param method 方法
     * @param args   参数
     * @param result 返回结果
     * @return 处理后的结果
     */
    default Object postHandle(Method method, Object[] args, Object result) {
        return result;
    }

    /**
     * 异常处理
     *
     * @param method 方法
     * @param args   参数
     * @param error  异常
     * @throws Throwable 处理后的异常
     */
    default void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        throw error;
    }

    /**
     * 最终处理（无论成功失败都会执行）
     *
     * @param method 方法
     * @param args   参数
     */
    default void afterCompletion(Method method, Object[] args) {
        // 默认不处理
    }

    /**
     * 拦截器优先级
     *
     * @return 优先级，数字越小优先级越高
     */
    default int getOrder() {
        return 0;
    }
}
