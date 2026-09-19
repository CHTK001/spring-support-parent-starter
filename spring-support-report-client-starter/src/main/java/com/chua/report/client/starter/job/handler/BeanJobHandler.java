package com.chua.report.client.starter.job.handler;

import com.chua.common.support.reflection.ReflectUtils;
import org.slf4j.Logger;
import com.chua.common.support.spi.annotations.Spi;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Spring Bean方式的作业处理程序
 * <p>
 * 通过反射调用Spring Bean中标注了@作业注解的方法来执行任务。
 * 支持配置初始化方法和销毁方法。
 * </p>
 *
 * @版本 1.0.0
 * @since 2024/03/11
 * @author CH
 */
@Spi("bean")
public class BeanJobHandler implements JobHandler {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(BeanJobHandler.class);

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
     * @param initMethod    初始化方法，可为空
     * @param destroyMethod 销毁方法，可为空
     */
    public BeanJobHandler(Object target, Method method, Method initMethod, Method destroyMethod) {
        this.target = target;
        this.method = method;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    @Override
    /** 执行 */
    public void execute() throws Exception {
        log.debug("开始执行Bean任务: {}#{}", target.getClass().getSimpleName(), method.getName());
        Class<?>[] paramTypes = method.getParameterTypes();
        // [P3C 1.10 豁免] 执行方法需要按参数类型构造空参数数组并保留异常传播语义，ReflectUtils.invoke 静默吞掉异常，保持原生反射
        if (paramTypes.length > 0) {
            method.invoke(target, new Object[paramTypes.length]);
        } else {
            method.invoke(target);
        }
        log.debug("Bean任务执行完成: {}#{}", target.getClass().getSimpleName(), method.getName());
    }

    @Override
    /** 初始化 */
    public void init() throws Exception {
        if (initMethod != null) {
            log.debug("执行Bean任务初始化方法: {}#{}", target.getClass().getSimpleName(), initMethod.getName());
            // [P3C 1.10 豁免] 同上，保持异常传播
            initMethod.invoke(target);
        }
    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {
        if (destroyMethod != null) {
            log.debug("执行Bean任务销毁方法: {}#{}", target.getClass().getSimpleName(), destroyMethod.getName());
            // [P3C 1.10 豁免] 同上，保持异常传播
            destroyMethod.invoke(target);
        }
    }

    @Override
    /** 转为字符串 */
    public String toString() {
        return super.toString() + "[" + target.getClass().getSimpleName() + "#" + method.getName() + "]";
    }
}
