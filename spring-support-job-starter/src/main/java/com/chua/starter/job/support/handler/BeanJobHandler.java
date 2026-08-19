package com.chua.starter.job.support.handler;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

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
     * 目标 bean 实例
     */
    private final Object target;

    /**
     * execute 方法
     */
    private final Method method;

    /**
     * init 方法, 可为 null
     */
    private final Method initMethod;

    /**
     * destroy 方法, 可为 null
     */
    private final Method destroyMethod;

    /**
     * 构造 Bean 任务处理器。
     *
     * @param name          处理器名称
     * @param desc          任务描述
     * @param target        目标 bean
     * @param method        execute 方法
     * @param initMethod    init 方法, 允许为 null
     * @param destroyMethod destroy 方法, 允许为 null
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
    public String name() {
        return name;
    }

    @Override
    public String desc() {
        return desc == null ? "" : desc;
    }

    @Override
    public void execute() throws Exception {
        log.debug("[job] execute handler={} target={}#{}",
                name, target.getClass().getSimpleName(), method.getName());
        method.invoke(target);
    }

    @Override
    public void init() throws Exception {
        if (initMethod == null) {
            return;
        }
        log.debug("[job] init handler={}", name);
        initMethod.invoke(target);
    }

    @Override
    public void destroy() throws Exception {
        if (destroyMethod == null) {
            return;
        }
        log.debug("[job] destroy handler={}", name);
        destroyMethod.invoke(target);
    }
}
