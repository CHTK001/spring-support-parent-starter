package com.chua.starter.scheduler.client.support.executor;

import com.chua.starter.common.support.annotations.Job;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 执行器
 *
 * @author CH
 */
@Slf4j
public class JobSpringExecutor extends XxlJobSpringExecutor {


    // start
    @Override
    public void afterSingletonsInstantiated() {

        // init JobHandler Repository
        /*initJobHandlerRepository(applicationContext);*/

        // init JobHandler Repository (for method)
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory
        GlueFactory.refreshInstance(1);

        // super start
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // destroy
    @Override
    public void destroy() {
        super.destroy();
    }


    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }
        // init job handler from method
        String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
        for (String beanDefinitionName : beanDefinitionNames) {

            // get bean
            Object bean = null;
            Lazy onBean = applicationContext.findAnnotationOnBean(beanDefinitionName, Lazy.class);
            if (onBean != null) {
                log.debug("xxl-job annotation scan, skip @Lazy Bean:{}", beanDefinitionName);
                continue;
            } else {
                bean = applicationContext.getBean(beanDefinitionName);
            }

            doAnalysisJob(doAnalysisXxlJob(beanDefinitionName, bean), beanDefinitionName, bean);
        }
    }

    private void doAnalysisJob(Map<Method, XxlJob> doAnalysisXxlJob, String beanDefinitionName, Object bean) {
        // filter method
        Map<Method, Job> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method -> AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
        } catch (Throwable ex) {
            log.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
        }
        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return;
        }

        for (Method method : doAnalysisXxlJob.keySet()) {
            annotatedMethods.remove(method);
        }

        // generate and regist method job handler
        for (Map.Entry<Method, Job> methodXxlJobEntry : annotatedMethods.entrySet()) {
            Method executeMethod = methodXxlJobEntry.getKey();
            Job xxlJob = methodXxlJobEntry.getValue();
            // regist
            registJobHandler(xxlJob, bean, executeMethod);
        }

    }

    private Map<Method, XxlJob> doAnalysisXxlJob(String beanDefinitionName, Object bean) {
        // filter method
        Map<Method, XxlJob> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    (MethodIntrospector.MetadataLookup<XxlJob>) method -> AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class));
        } catch (Throwable ex) {
            log.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
        }
        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return annotatedMethods;
        }

        // generate and regist method job handler
        for (Map.Entry<Method, XxlJob> methodXxlJobEntry : annotatedMethods.entrySet()) {
            Method executeMethod = methodXxlJobEntry.getKey();
            XxlJob xxlJob = methodXxlJobEntry.getValue();
            // regist
            registJobHandler(xxlJob, bean, executeMethod);
        }

        return annotatedMethods;
    }

    // ---------------------- applicationContext ----------------------
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        JobSpringExecutor.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    protected void registJobHandler(Object job, Object bean, Method executeMethod){
        if (job == null) {
            return;
        }

        if(job instanceof Job) {
            registerJob((Job) job, bean, executeMethod);
        }
    }

    private void registerJob(Job job, Object bean, Method executeMethod) {
        String name = job.value();
        //make and simplify the variables since they'll be called several times later
        Class<?> clazz = bean.getClass();
        String methodName = executeMethod.getName();
        if (name.trim().length() == 0) {
            throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
        if (loadJobHandler(name) != null) {
            throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
        }

        executeMethod.setAccessible(true);

        // init and destroy
        Method initMethod = null;
        Method destroyMethod = null;

        if (job.init().trim().length() > 0) {
            try {
                initMethod = clazz.getDeclaredMethod(job.init());
                initMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }
        if (job.destroy().trim().length() > 0) {
            try {
                destroyMethod = clazz.getDeclaredMethod(job.destroy());
                destroyMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        // registry jobhandler
        registJobHandler(name, new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod));

    }

}
