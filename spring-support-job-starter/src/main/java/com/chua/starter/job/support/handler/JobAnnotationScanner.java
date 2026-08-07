package com.chua.starter.job.support.handler;

import com.chua.starter.job.support.annotation.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * {@link Job} 注解扫描器,负责注册本地任务处理器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class JobAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = ClassUtils.getUserClass(bean);
        java.util.Map<Method, Job> annotated = null;
        try {
            annotated = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<Job>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
        } catch (Throwable ex) {
            log.error("[job] 扫描 @Job 失败, bean={}", beanName, ex);
        }
        if (annotated == null || annotated.isEmpty()) {
            return bean;
        }
        for (java.util.Map.Entry<Method, Job> entry : annotated.entrySet()) {
            registerHandler(bean, targetClass, entry.getKey(), entry.getValue());
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("[job] @Job 扫描完成, 共注册 {} 个处理器", JobHandlerFactory.getInstance().size());
    }

    /**
     * 注册单个处理器。
     *
     * @param bean        业务 bean
     * @param targetClass 业务类
     * @param method      execute 方法
     * @param job         {@link Job} 注解
     */
    private void registerHandler(Object bean, Class<?> targetClass, Method method, Job job) {
        if (job == null) {
            return;
        }
        String name = job.value();
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("@Job value 不能为空, target=" + targetClass.getName());
        }
        if (method.getParameterCount() > 0) {
            throw new IllegalStateException("@Job 方法必须无参, target="
                    + targetClass.getName() + "#" + method.getName());
        }
        Method initMethod = resolveLifecycleMethod(targetClass, job.init(), "init");
        Method destroyMethod = resolveLifecycleMethod(targetClass, job.destroy(), "destroy");

        BeanJobHandler handler = new BeanJobHandler(
                name,
                job.desc(),
                bean,
                method,
                initMethod,
                destroyMethod
        );
        if (JobHandlerFactory.getInstance().contains(name)) {
            throw new IllegalStateException("@Job 名冲突, name=" + name);
        }
        JobHandlerFactory.getInstance().register(handler);
        log.info("[job] 注册处理器 name={} desc={}", name, job.desc());
    }

    /**
     * 解析生命周期方法, 未配置时返回 null。
     *
     * @param targetClass 业务类
     * @param methodName  方法名
     * @param lifecycle   阶段名
     * @return 方法, 允许为 null
     */
    private Method resolveLifecycleMethod(Class<?> targetClass, String methodName, String lifecycle) {
        if (!StringUtils.hasText(methodName)) {
            return null;
        }
        Method found = ReflectionUtils.findMethod(targetClass, methodName);
        if (found == null) {
            throw new IllegalStateException(lifecycle + " 方法未找到: "
                    + targetClass.getName() + "#" + methodName);
        }
        if (found.getParameterCount() > 0) {
            throw new IllegalStateException(lifecycle + " 方法必须无参: "
                    + targetClass.getName() + "#" + methodName);
        }
        return found;
    }
}
