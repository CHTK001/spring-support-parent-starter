package com.chua.starter.job.support;

import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.glue.GlueFactory;
import com.chua.starter.job.support.handler.BeanJobHandler;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.scheduler.JobDispatchModeEnum;
import com.chua.starter.job.support.scheduler.JobStorageModeEnum;
import com.chua.starter.job.support.service.JobDynamicConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务注解扫描器
 * <p>
 * 在Spring容器初始化期间扫描所有带有{@link Job}注解的方法，
 * 并将其自动注册为JobHandler到{@link JobHandlerFactory}中。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *     <li>实现{@link BeanPostProcessor}，在Bean初始化后扫描@Job注解</li>
 *     <li>使用{@link MethodIntrospector}查找带有@Job注解的方法</li>
 *     <li>为每个找到的方法创建{@link BeanJobHandler}并注册</li>
 *     <li>在所有单例初始化完成后，初始化{@link GlueFactory}</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class MyJobHandler {
 *
 *     @Job(value = "demoJobHandler", init = "init", destroy = "destroy")
 *     public void execute() {
 *         // 任务执行逻辑
 *     }
 *
 *     public void init() {
 *         // 任务初始化
 *     }
 *
 *     public void destroy() {
 *         // 任务销毁
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see Job
 * @see JobHandlerFactory
 * @see BeanJobHandler
 */
@Slf4j
public class JobAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton, DisposableBean {
    private final Map<String, JobDefinition> discoveredJobs = new LinkedHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    private JobProperties jobProperties;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<JobDynamicConfigService> jobDynamicConfigServiceProvider;

    @Override
    public void afterSingletonsInstantiated() {
        // 初始化Spring Glue工厂
        GlueFactory.refreshInstance(1);
        syncJobsToConfigTable();
    }

    @Override
    public void destroy() throws Exception {
        // 清理资源
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 扫描带有@Job注解的方法
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

        for (Map.Entry<Method, Job> methodJobEntry : annotatedMethods.entrySet()) {
            Method executeMethod = methodJobEntry.getKey();
            Job job = methodJobEntry.getValue();
            registJobHandler(job, bean, targetClass, executeMethod);
        }

        return bean;
    }

    /**
     * 注册JobHandler
     *
     * @param job          Job注解
     * @param bean          Bean实例
     * @param targetClass   业务类
     * @param executeMethod 执行方法
     */
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

        // 初始化方法
        Method initMethod = null;
        if (StringUtils.hasText(job.init())) {
            initMethod = resolveLifecycleMethod(clazz, job.init(), methodName, "initMethod");
        }

        // 销毁方法
        Method destroyMethod = null;
        if (StringUtils.hasText(job.destroy())) {
            destroyMethod = resolveLifecycleMethod(clazz, job.destroy(), methodName, "destroyMethod");
        }

        // 注册Handler
        JobHandler jobHandler = new BeanJobHandler(bean, executeMethod, initMethod, destroyMethod);
        JobHandlerFactory.getInstance().register(name, jobHandler);
        discoveredJobs.put(name, new JobDefinition(name, job, clazz, methodName));

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

    private void syncJobsToConfigTable() {
        if (jobProperties == null || !jobProperties.isConfigTableEnabled()) {
            return;
        }
        AnnotationSyncMode mode = jobProperties.getJobAnnotationSyncMode();
        if (mode == null || mode == AnnotationSyncMode.NONE) {
            return;
        }
        JobDynamicConfigService service = jobDynamicConfigServiceProvider.getIfAvailable();
        if (service == null) {
            log.warn("[JobScanner] JobDynamicConfigService 不存在，跳过 @Job 自动入表");
            return;
        }
        for (JobDefinition definition : discoveredJobs.values()) {
            try {
                syncSingleJob(service, mode, definition);
            } catch (Exception e) {
                log.error("[JobScanner] @Job 自动入表失败: {}", definition.name, e);
            }
        }
    }

    private void syncSingleJob(JobDynamicConfigService service, AnnotationSyncMode mode, JobDefinition definition) {
        SysJob existing = service.getJobByName(definition.name);
        if (mode == AnnotationSyncMode.CREATE && existing != null) {
            return;
        }
        SysJob target = existing != null ? existing : new SysJob();
        Job source = definition.job;
        if (!StringUtils.hasText(target.getJobNo())) {
            target.setJobNo(JobNumberGenerator.nextJobNo());
        }
        target.setJobName(definition.name);
        if (StringUtils.hasText(source.scheduleType())) {
            target.setJobScheduleType(source.scheduleType());
        } else if (!StringUtils.hasText(target.getJobScheduleType())) {
            target.setJobScheduleType("cron");
        }
        if (StringUtils.hasText(source.scheduleTime())) {
            target.setJobScheduleTime(source.scheduleTime());
        }
        if (StringUtils.hasText(source.author())) {
            target.setJobAuthor(source.author());
        }
        if (StringUtils.hasText(source.alarmEmail())) {
            target.setJobAlarmEmail(source.alarmEmail());
        }
        if (StringUtils.hasText(source.desc())) {
            target.setJobDesc(source.desc());
        } else if (!StringUtils.hasText(target.getJobDesc())) {
            target.setJobDesc(definition.beanClass.getSimpleName() + "#" + definition.methodName);
        }
        target.setJobExecuteBean(definition.name);
        target.setJobGlueType("BEAN");
        target.setJobGlueUpdatetime(new java.util.Date());
        if (source.retryInterval() >= 0) {
            target.setJobRetryInterval(source.retryInterval());
        } else if (target.getJobRetryInterval() == null) {
            target.setJobRetryInterval(0);
        }
        if (source.failRetry() >= 0) {
            target.setJobFailRetry(source.failRetry());
        } else if (target.getJobFailRetry() == null) {
            target.setJobFailRetry(0);
        }
        if (source.executeTimeout() >= 0) {
            target.setJobExecuteTimeout(source.executeTimeout());
        } else if (target.getJobExecuteTimeout() == null) {
            target.setJobExecuteTimeout(0);
        }
        if (StringUtils.hasText(source.misfireStrategy())) {
            target.setJobExecuteMisfireStrategy(source.misfireStrategy());
        } else if (!StringUtils.hasText(target.getJobExecuteMisfireStrategy())) {
            target.setJobExecuteMisfireStrategy("DO_NOTHING");
        }
        if (StringUtils.hasText(source.dispatchMode())) {
            target.setJobDispatchMode(JobDispatchModeEnum.match(source.dispatchMode()).name());
        } else if (!StringUtils.hasText(target.getJobDispatchMode())) {
            target.setJobDispatchMode(JobDispatchModeEnum.LOCAL.name());
        }
        if (StringUtils.hasText(source.remoteExecutorAddress())) {
            target.setJobRemoteExecutorAddress(source.remoteExecutorAddress().trim());
        }
        if (!StringUtils.hasText(target.getJobStorageMode())) {
            target.setJobStorageMode(JobStorageModeEnum.DATABASE.name());
        }
        if (StringUtils.hasText(source.exceptionCallback())) {
            target.setJobExceptionCallbackBean(source.exceptionCallback().trim());
        }
        if (StringUtils.hasText(source.retryCallback())) {
            target.setJobRetryCallbackBean(source.retryCallback().trim());
        }
        boolean canAutoStart = source.autoStart() && StringUtils.hasText(target.getJobScheduleTime());
        target.setJobTriggerStatus(canAutoStart ? 1 : 0);
        if (existing == null) {
            service.createJob(target);
        } else if (mode == AnnotationSyncMode.UPDATE) {
            service.updateJob(target);
            if (canAutoStart) {
                service.startJob(target.getJobId());
            }
        }
    }

    private record JobDefinition(String name, Job job, Class<?> beanClass, String methodName) {
    }
}
