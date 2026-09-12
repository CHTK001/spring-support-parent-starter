package com.chua.starter.job.support.handler;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

import com.chua.common.support.reflection.ReflectUtils;

/**
* 基于 Spring Bean + 反射的任务处理器。
*
* <p>{@link com.chua.starter.job.support.annotation.Job} 标注的方法
* 由 {@link JobAnnotationScanner} 包装为本处理器并注册。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
public class BeanJobHandler implements JobHandler {

    /**
    * 处理器名称
     */
    private final String name;

    /**
    * 任务描述
     */
    private final String desc;

    /**
    * 目标 Bean 实例
     */
    private final Object target;

    /**
    * 执行 方法
     */
    private final Method method;

    /**
    * 初始化 方法, 可为 空
     */
    private final Method initMethod;

    /**
    * 销毁 方法, 可为 空
     */
    private final Method destroyMethod;

    /**
    * 构造 Bean 任务处理器。
    *
    * @param name          处理器名称
    * @param desc          任务描述
    * @param target        目标 Bean
    * @param method        执行 方法
    * @param initMethod    初始化 方法, 允许为 空
    * @param destroyMethod 销毁 方法, 允许为 空
     */
    public BeanJobHandler(String name,
                          String desc,
                          Object target,
                          Method method,
                          Method initMethod,
                          Method destroyMethod) {
        this.name = name;
        this.desc = desc;
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
        method.setAccessible(true);
        if (initMethod != null) {
            initMethod.setAccessible(true);
        }
        if (destroyMethod != null) {
            destroyMethod.setAccessible(true);
        }
    }

    @Override
    /** 名称 */
    public String name() {
        return name;
    }

    @Override
    /** Desc */
    public String desc() {
        return desc == null ? "" : desc;
    }

    @Override
    /** 执行 */
    public void execute() throws Exception {
        log.debug("[job] execute handler={} target={}#{}",
                name, target.getClass().getSimpleName(), method.getName());
        ReflectUtils.invoke(target, method.getName(), method.getReturnType(), method.getParameterTypes());
    }

    @Override
    /** 初始化 */
    public void init() throws Exception {
        if (initMethod == null) {
            return;
        }
        log.debug("[job] init handler={}", name);
        ReflectUtils.invoke(target, initMethod.getName(), initMethod.getReturnType(), initMethod.getParameterTypes());
    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {
        if (destroyMethod == null) {
            return;
        }
        log.debug("[job] destroy handler={}", name);
        ReflectUtils.invoke(target, destroyMethod.getName(), destroyMethod.getReturnType(), destroyMethod.getParameterTypes());
    }
}
