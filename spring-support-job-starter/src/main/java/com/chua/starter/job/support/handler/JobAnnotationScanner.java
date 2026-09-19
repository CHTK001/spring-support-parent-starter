package com.chua.starter.job.support.handler;

import com.chua.starter.job.support.annotation.Job;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.dispatch.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Job} 注解扫描器, 负责注册本地任务处理器, 并在单例初始化完成后
 * 将扫描到的任务定义同步到 {@link JobRepository} (MyBatis/入内存)。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class JobAnnotationScanner implements BeanPostProcessor, SmartInitializingSingleton, ApplicationContextAware {

    /**
     * 收集到的 作业definition 列表 (在 post处理之后initialization 阶段累积)
     */
    private final List<JobDefinition> collectedDefinitions = new ArrayList<>();

    /**
     * Spring 上下文 (用于 之后单例instantiated 阶段按类型查找 作业仓库)
     */
    private ApplicationContext applicationContext;

    @Override
    /** Post处理之后initialization */
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
            /**
    * 注册处理器。
    * @param bean Bean
    * @param targetClass 目标类
    * @param entry.getKey( entry.获取键(
    */
            registerHandler(bean, targetClass, entry.getKey(), entry.getValue());
        }
        return bean;
    }

    @Override
    /** 之后单例instantiated */
    public void afterSingletonsInstantiated() {
        log.info("[job] @Job 扫描完成, 共注册 {} 个处理器", JobHandlerFactory.getInstance().size());
 // 同步到 作业仓库, 让 作业铅循环 调度时能查到任务
        if (collectedDefinitions.isEmpty() || applicationContext == null) {
            return;
        }
        try {
            JobRepository repository = applicationContext.getBeanProvider(JobRepository.class)
                    .getIfAvailable();
            if (repository == null) {
                return;
            }
            for (JobDefinition definition : collectedDefinitions) {
                repository.upsert(definition);
            }
            log.info("[job] @Job 任务定义同步到仓库 {} 条", collectedDefinitions.size());
        } catch (Exception ex) {
            log.warn("[job] @Job 任务仓库同步失败 cause={}", ex.getMessage());
        }
    }

    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
    * 注册单个处理器。
    *
    * @param bean        业务 Bean
    * @param targetClass 业务类
    * @param method      执行 方法
    * @param job         {@link Job} 注解
    */
    private void registerHandler(Object bean, Class<?> targetClass, Method method, Job job) {
        if (job == null) {
            return;
        }
        String name = job.value();
        if (!StringUtils.hasText(name)) {
            /**
             * illegal状态异常。
             * @param 不能为空 不能为空
             * @param targetClass.getName( Targetclass.getname(
             * @return illegal状态异常的结果
             */
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
            /**
             * illegal状态异常。
             * @param 名冲突 名冲突
             * @param name 名称
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("@Job 名冲突, name=" + name);
        }
        JobHandlerFactory.getInstance().register(handler);
 // 收集 作业definition 用于同步到仓库
        JobDefinition definition = new JobDefinition();
        definition.setName(name);
        definition.setCron(job.cron());
        definition.setFixedDelayMs(job.fixedDelay());
        definition.setFixedRateMs(job.fixedRate());
        definition.setEnabled(true);
        definition.setDesc(job.desc());
        synchronized (collectedDefinitions) {
            collectedDefinitions.add(definition);
        }
        log.info("[job] 注册处理器 name={} desc={}", name, job.desc());
    }

    /**
     * 解析生命周期方法, 未配置时返回 空。
     *
     * @param targetClass 业务类
     * @param methodName  方法名
     * @param lifecycle   阶段名
     * @return 方法, 允许为 空
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
