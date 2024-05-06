package com.chua.starter.monitor.configuration;

import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.annotations.ServiceMapping;
import com.chua.common.support.protocol.boot.BootProtocolServer;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.common.support.annotations.Job;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.job.execute.DefaultJobExecute;
import com.chua.starter.monitor.job.execute.JobExecute;
import com.chua.starter.monitor.job.handler.BeanJobHandler;
import com.chua.starter.monitor.job.handler.JobHandler;
import com.chua.starter.monitor.job.handler.JobHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static com.chua.common.support.lang.message.AbstractMessagePush.OK;

/**
 * 外壳配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
@Slf4j
public class JobConfiguration implements BeanFactoryAware, SmartInstantiationAwareBeanPostProcessor {

    private BootProtocolServer protocolServer;
    private ConfigurableListableBeanFactory beanFactory;

    @ServiceMapping("job")
    public BootResponse listen(BootRequest request) {
        if(request.getCommandType() != CommandType.REQUEST) {
            return BootResponse.notSupport("The non-register command is not supported");
        }
        JobExecute jobExecute = new DefaultJobExecute();
        return jobExecute.run(Json.fromJson(request.getContent(), TriggerParam.class));
    }

    /**
     * bean
     *
     * @param request 要求
     * @return {@link BootResponse}
     */
    @ServiceMapping("job-bean")
    public BootResponse bean(BootRequest request) {
        if(request.getCommandType() != CommandType.REQUEST) {
            return BootResponse.notSupport("The non-register command is not supported");
        }
        return BootResponse.builder()
                .code(OK)
                .data(Json.toJson(JobHandlerFactory.getInstance().keys()))
                .build();
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        doAnalysisJob(beanName, bean);
        return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
    }


    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "ConfigValueAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(BootProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }

        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }
        this.protocolServer = this.beanFactory.getBean(BootProtocolServer.class);
        this.protocolServer.addMapping(this);
    }

    private void doAnalysisJob(Map<Method, Job> methodJobMap, String beanDefinitionName, Object bean) {
        // filter method
        Map<Method, Job> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method -> AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
        } catch (Throwable ex) {
            log.error("job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
        }
        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return;
        }

        for (Method method : methodJobMap.keySet()) {
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

    private Map<Method, Job> doAnalysisJob(String beanDefinitionName, Object bean) {
        // filter method
        Map<Method, Job> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method -> AnnotatedElementUtils.findMergedAnnotation(method, Job.class));
        } catch (Throwable ex) {
            log.error("job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
        }
        if (annotatedMethods == null || annotatedMethods.isEmpty()) {
            return annotatedMethods;
        }

        // generate and regist method job handler
        for (Map.Entry<Method, Job> methodXxlJobEntry : annotatedMethods.entrySet()) {
            Method executeMethod = methodXxlJobEntry.getKey();
            Job xxlJob = methodXxlJobEntry.getValue();
            // regist
            registJobHandler(xxlJob, bean, executeMethod);
        }

        return annotatedMethods;
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
            throw new RuntimeException("job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
        if (loadJobHandler(name) != null) {
            throw new RuntimeException("job jobhandler[" + name + "] naming conflicts.");
        }

        ClassUtils.setAccessible(executeMethod);

        // init and destroy
        Method initMethod = null;
        Method destroyMethod = null;

        if (job.init().trim().length() > 0) {
            try {
                initMethod = clazz.getDeclaredMethod(job.init());
                ClassUtils.setAccessible(initMethod);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }
        if (job.destroy().trim().length() > 0) {
            try {
                destroyMethod = clazz.getDeclaredMethod(job.destroy());
                ClassUtils.setAccessible(destroyMethod);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        log.info("注册任务: {} ", job.value());
        // registry jobhandler
        JobHandlerFactory.getInstance().register(name, new BeanJobHandler(bean, executeMethod, initMethod, destroyMethod));

    }


    public static JobHandler loadJobHandler(String name){
        return JobHandlerFactory.getInstance().get(name);
    }

}
