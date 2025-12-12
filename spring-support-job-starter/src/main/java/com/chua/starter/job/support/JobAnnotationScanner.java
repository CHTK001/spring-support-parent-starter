package com.chua.starter.job.support;

import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.glue.GlueFactory;
import com.chua.starter.job.support.handler.BeanJobHandler;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Job注解扫描器
 * <p>
 * 扫描所有带有@Job注解的方法，并注册为JobHandler
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class JobAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton, DisposableBean {

    @Override
    public void afterSingletonsInstantiated() {
        // 初始化Spring Glue工厂
        GlueFactory.refreshInstance(1);
    }

    @Override
    public void destroy() throws Exception {
        // 清理资源
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 扫描带有@Job注解的方法
        Map<Method, Job> annotatedMethods = null;
        try {
            annotatedMethods = MethodIntrospector.selectMethods(
                    bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
        } catch (Throwable ex) {
            log.error("扫描@Job注解方法失败: {}", beanName, ex);
        }

        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return bean;
        }

        for (Map.Entry<Method, Job> methodJobEntry : annotatedMethods.entrySet()) {
            Method executeMethod = methodJobEntry.getKey();
            Job job = methodJobEntry.getValue();
            registJobHandler(job, bean, executeMethod);
        }

        return bean;
    }

    /**
     * 注册JobHandler
     *
     * @param job          Job注解
     * @param bean         Bean实例
     * @param executeMethod 执行方法
     */
    protected void registJobHandler(Job job, Object bean, Method executeMethod) {
        if (job == null) {
            return;
        }

        String name = job.value();
        Class<?> clazz = bean.getClass();
        String methodName = executeMethod.getName();

        if (name.trim().isEmpty()) {
            throw new RuntimeException("job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
        if (JobHandlerFactory.getInstance().get(name) != null) {
            throw new RuntimeException("job jobhandler[" + name + "] naming conflicts.");
        }

        executeMethod.setAccessible(true);

        // 初始化方法
        Method initMethod = null;
        if (!job.init().trim().isEmpty()) {
            try {
                initMethod = clazz.getDeclaredMethod(job.init());
                initMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        // 销毁方法
        Method destroyMethod = null;
        if (!job.destroy().trim().isEmpty()) {
            try {
                destroyMethod = clazz.getDeclaredMethod(job.destroy());
                destroyMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        // 注册Handler
        JobHandler jobHandler = new BeanJobHandler(bean, executeMethod, initMethod, destroyMethod);
        JobHandlerFactory.getInstance().register(name, jobHandler);

        log.info(">>>>>>>>>>> job register jobhandler success, name:{}, handler:{}", name, jobHandler);
    }
}
