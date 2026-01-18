package com.chua.starter.job.support.handler;

import com.chua.common.support.core.annotation.Spi;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Spring Bean方式的作业处理程序
 * <p>
 * 通过反射调用Spring Bean中标注了@Job注解的方法来执行任务。
 * 支持配置初始化方法和销毁方法。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Spi("bean")
@Slf4j
public class BeanJobHandler implements JobHandler {

    /**
     * 目标Bean对象
     */
    private final Object target;

    /**
     * 执行方法
     */
    private final Method method;

    /**
     * 初始化方法
     */
    private final Method initMethod;

    /**
     * 销毁方法
     */
    private final Method destroyMethod;

    /**
     * 构造函数
     *
     * @param target        目标Bean对象
     * @param method        执行方法
     * @param initMethod    初始化方法，可为null
     * @param destroyMethod 销毁方法，可为null
     */
    public BeanJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    @Override
    public void execute() throws Exception {
        log.debug("开始执行Bean任务: {}#{}", target.getClass().getSimpleName(), method.getName());
        Class<?>[] paramTypes = method.getParameterTypes();
        // 根据方法参数类型决定调用方式
        if (paramTypes.length > 0) {
            // 如果方法有参数，传入空参数数组
            method.invoke(target, new Object[paramTypes.length]);
        } else {
            // 无参方法直接调用
            method.invoke(target);
        }
        log.debug("Bean任务执行完成: {}#{}", target.getClass().getSimpleName(), method.getName());
    }

    @Override
    public void init() throws Exception {
        if (initMethod != null) {
            log.debug("执行Bean任务初始化方法: {}#{}", target.getClass().getSimpleName(), initMethod.getName());
            initMethod.invoke(target);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (destroyMethod != null) {
            log.debug("执行Bean任务销毁方法: {}#{}", target.getClass().getSimpleName(), destroyMethod.getName());
            destroyMethod.invoke(target);
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[" + target.getClass().getSimpleName() + "#" + method.getName() + "]";
    }
}
