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
        // [P3C 1.10] ReflectUtils.invoke 内部使用 privateLookupIn，无需 setAccessible
    }

    @Override
    /**
     * 名称
     * @return 返回值
    */
    public String name() {
        return name;
    }

    @Override
    /**
     * Desc
     * @return 返回值
    */
    public String desc() {
        return desc == null ? "" : desc;
    }

    @Override
    /**
     * 执行
     * @return 返回值
     * @throws Exception 异常
    */
    public void execute() throws Exception {
        log.debug("[job] execute handler={} target={}#{}",
                name, target.getClass().getSimpleName(), method.getName());
        invoke(method);
    }

    @Override
    /**
     * 初始化
     * @return 返回值
     * @throws Exception 异常
    */
    public void init() throws Exception {
        if (initMethod == null) {
            return;
        }
        log.debug("[job] init handler={}", name);
        invoke(initMethod);
    }

    @Override
    /**
     * 销毁
     * @return 返回值
     * @throws Exception 异常
    */
    public void destroy() throws Exception {
        if (destroyMethod == null) {
            return;
        }
        log.debug("[job] destroy handler={}", name);
        invoke(destroyMethod);
    }

    /**
     * 调用钩子方法，并把目标方法抛出的异常原样上抛。
     *
     * <p>刻意不用 {@code ReflectUtils.invoke(Object, String, Class, Class[], Object...)}：
     * 一是它与同名的 {@code invoke(Object, String, Class, Object...)} 在本调用形态下构成
     * varargs 歧义、无法编译（补 {@code new Object[0]} 也消不掉，两者在可变参数阶段同时可用）；
     * 二是它失败时只记日志并返回 {@code null}，会让 {@code @Job} 方法抛出的异常被静默吞掉、
     * 任务被误记成成功。</p>
     *
     * @param hookMethod 钩子方法（无参）
     * @throws Exception 目标方法抛出的异常
     */
    private void invoke(Method hookMethod) throws Exception {
        try {
            ReflectUtils.invokeProxy(target, hookMethod, null);
        } catch (Throwable ex) {
            if (ex instanceof Exception exception) {
                throw exception;
            }
            if (ex instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("任务钩子调用失败: " + name + "#" + hookMethod.getName(), ex);
        }
    }
}
