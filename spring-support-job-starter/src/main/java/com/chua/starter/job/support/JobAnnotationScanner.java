package com.chua.starter.job.support;

import com.chua.starter.job.support.annotation.Job;
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
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code @Job} 注解扫描器 (骨架版)。
 * <p>
 * 仅承担 {@code @Job} 方法发现 + handler 注册 + 注解侧配置登记三项职责;
 * 调度/触发/任务持久化由后续重构补齐。当前不会触发任何调度动作。
 * </p>
 *
 * @author CH
 * @see Job
 * @see JobHandlerFactory
 * @see BeanJobHandler
 */
@Slf4j
public class JobAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton, DisposableBean {

    private final Map<String, JobDefinition> discoveredJobs = new LinkedHashMap<>();

    @Override
    public void afterSingletonsInstantiated() {
        log.info("[JobScanner] 共扫描到 {} 个 @Job handler", discoveredJobs.size());
    }

    @Override
    public void destroy() {
        // 清理资源占位
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Map<Method, Job> annotatedMethods = null;
        Class<?> targetClass = ClassUtils.getUserClass(bean);
        try {
            annotatedMethods = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<Job>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
        } catch (Throwable ex) {
            log.error("扫描@Job注解方法失败, beanName={}", beanName, ex);
        }
        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return bean;
        }
        for (Map.Entry<Method, Job> entry : annotatedMethods.entrySet()) {
            registJobHandler(entry.getValue(), bean, targetClass, entry.getKey());
        }
        return bean;
    }

    protected void registJobHandler(Job job, Object bean, Class<?> targetClass, Method executeMethod) {
        if (job == null) {
            return;
        }
        String name = job.value();
        Class<?> clazz = targetClass == null ? bean.getClass() : targetClass;
        String methodName = executeMethod.getName();

        if (!StringUtils.hasText(name)) {
            throw new RuntimeException("job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
        if (JobHandlerFactory.getInstance().get(name) != null) {
            throw new RuntimeException("job jobhandler[" + name + "] naming conflicts.");
        }
        if (executeMethod.getParameterCount() > 0) {
            throw new RuntimeException("@Job 方法必须是无参方法, for[" + clazz + "#" + methodName + "] .");
        }
        executeMethod.setAccessible(true);

        Method initMethod = null;
        if (StringUtils.hasText(job.init())) {
            initMethod = resolveLifecycleMethod(clazz, job.init(), methodName, "initMethod");
        }
        Method destroyMethod = null;
        if (StringUtils.hasText(job.destroy())) {
            destroyMethod = resolveLifecycleMethod(clazz, job.destroy(), methodName, "destroyMethod");
        }

        JobHandler jobHandler = new BeanJobHandler(bean, executeMethod, initMethod, destroyMethod);
        JobHandlerFactory.getInstance().register(name, jobHandler);
        discoveredJobs.put(name, new JobDefinition(name, job, clazz, methodName));

        Map<String, String> configMap = new LinkedHashMap<>();
        if (StringUtils.hasText(job.scheduleType())) {
            configMap.put("scheduleType", job.scheduleType().trim());
        }
        if (StringUtils.hasText(job.scheduleTime())) {
            configMap.put("scheduleTime", job.scheduleTime().trim());
        }
        if (job.executeTimeout() >= 0) {
            configMap.put("executeTimeout", String.valueOf(job.executeTimeout()));
        }
        if (StringUtils.hasText(job.misfireStrategy())) {
            configMap.put("misfireStrategy", job.misfireStrategy().trim());
        }
        if (StringUtils.hasText(job.dispatchMode())) {
            configMap.put("dispatchMode", job.dispatchMode().trim());
        }
        if (!configMap.isEmpty()) {
            JobHandlerFactory.registerConfig(name, configMap);
        }
        log.info(">>>>>>>>>>> 注册JobHandler成功, 名称={}, 处理器={}", name, jobHandler);
    }

    private Method resolveLifecycleMethod(Class<?> targetClass, String lifecycleMethod, String executeMethod, String lifecycleName) {
        Method method = ReflectionUtils.findMethod(targetClass, lifecycleMethod);
        if (method == null) {
            throw new RuntimeException("job method-jobhandler " + lifecycleName + " invalid, for[" + targetClass + "#" + executeMethod + "] .");
        }
        if (method.getParameterCount() > 0) {
            throw new RuntimeException("job method-jobhandler " + lifecycleName + " must be no-args, for[" + targetClass + "#" + executeMethod + "] .");
        }
        method.setAccessible(true);
        return method;
    }

    /** 当前对外部仅暴露只读快照,避免误改 */
    public Map<String, JobDefinition> getDiscoveredJobs() {
        return Collections.unmodifiableMap(discoveredJobs);
    }

    public record JobDefinition(String name, Job job, Class<?> beanClass, String methodName) {
    }
}
