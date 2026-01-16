package com.chua.report.client.starter.job;

import com.chua.report.client.starter.job.handler.BeanJobHandler;
import com.chua.report.client.starter.job.handler.JobHandlerFactory;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Job注解自动扫描器
 * <p>
 * 自动扫描标注了 @Job 注解的方法，并将其注册为 xxl-job 的任务处理器。
 * </p>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>定时任务自动注册</li>
 *   <li>分布式任务调度</li>
 *   <li>替代 xxl-job 的 @XxlJob 注解</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * &#64;Component
 * public class MyJobHandler {
 *
 *     &#64;Job("demoJobHandler")
 *     public void demoJob() {
 *         // 任务执行逻辑
 *     }
 *
 *     &#64;Job(value = "initJobHandler", init = "initMethod", destroy = "destroyMethod")
 *     public void initJob() {
 *         // 任务执行逻辑
 *     }
 *
 *     public void initMethod() {
 *         // 初始化方法
 *     }
 *
 *     public void destroyMethod() {
 *         // 销毁方法
 *     }
 * }
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 * @see Job
 * @see JobHandlerFactory
 */
@Slf4j
@RequiredArgsConstructor
public class JobAnnotationScanner implements BeanPostProcessor {

    private final JobHandlerFactory jobHandlerFactory = JobHandlerFactory.getInstance();

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        // 扫描带有 @Job 注解的方法
        Map<Method, Job> annotatedMethods = null;
        try {
            annotatedMethods = MethodIntrospector.selectMethods(
                    bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method -> 
                            AnnotatedElementUtils.findMergedAnnotation(method, Job.class)
            );
        } catch (Throwable ex) {
            log.debug("[JobScanner] 解析Bean[{}]的@Job注解时出错: {}", beanName, ex.getMessage());
        }

        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return bean;
        }

        // 注册 Job 处理器
        for (Map.Entry<Method, Job> entry : annotatedMethods.entrySet()) {
            Method method = entry.getKey();
            Job job = entry.getValue();
            registerJobHandler(bean, beanName, method, job);
        }

        return bean;
    }

    /**
     * 注册 Job 处理器
     *
     * @param bean     Bean 实例
     * @param beanName Bean 名称
     * @param method   方法
     * @param job      Job 注解
     */
    private void registerJobHandler(Object bean, String beanName, Method method, Job job) {
        String jobName = job.value();
        if (!StringUtils.hasText(jobName)) {
            log.warn("[JobScanner] Bean[{}]的方法[{}]上@Job注解的value为空，跳过注册", beanName, method.getName());
            return;
        }

        // 检查是否已存在同名处理器
        if (jobHandlerFactory.get(jobName) != null) {
            log.warn("[JobScanner] JobHandler[{}]已存在，跳过重复注册", jobName);
            return;
        }

        // 确保方法可访问
        method.setAccessible(true);

        // 获取初始化方法
        Method initMethod = null;
        if (StringUtils.hasText(job.init())) {
            try {
                initMethod = bean.getClass().getDeclaredMethod(job.init());
                initMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                log.warn("[JobScanner] Bean[{}]的初始化方法[{}]不存在", beanName, job.init());
            }
        }

        // 获取销毁方法
        Method destroyMethod = null;
        if (StringUtils.hasText(job.destroy())) {
            try {
                destroyMethod = bean.getClass().getDeclaredMethod(job.destroy());
                destroyMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                log.warn("[JobScanner] Bean[{}]的销毁方法[{}]不存在", beanName, job.destroy());
            }
        }

        // 创建并注册处理器
        BeanJobHandler jobHandler = new BeanJobHandler(bean, method, initMethod, destroyMethod);
        jobHandlerFactory.register(jobName, jobHandler);

        log.info("[JobScanner] 注册JobHandler: {} -> {}.{}", jobName, bean.getClass().getSimpleName(), method.getName());
    }
}
