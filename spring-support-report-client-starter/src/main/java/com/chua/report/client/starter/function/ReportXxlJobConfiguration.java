package com.chua.report.client.starter.function;

import com.chua.common.support.invoke.annotation.RequestLine;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.OkResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.utils.ClassUtils;
import com.chua.report.client.starter.entity.JobCat;
import com.chua.report.client.starter.entity.JobValue;
import com.chua.report.client.starter.job.execute.DefaultJobExecute;
import com.chua.report.client.starter.job.execute.JobExecute;
import com.chua.report.client.starter.job.handler.BeanJobHandler;
import com.chua.report.client.starter.job.handler.JobHandler;
import com.chua.report.client.starter.job.handler.JobHandlerFactory;
import com.chua.report.client.starter.job.log.JobFileAppender;
import com.chua.report.client.starter.job.log.LogResult;
import com.chua.starter.common.support.annotations.Job;
import com.chua.starter.common.support.project.Project;
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

/**
 * 外壳配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
@Slf4j
public class ReportXxlJobConfiguration implements BeanFactoryAware, SmartInstantiationAwareBeanPostProcessor {

    private ConfigurableListableBeanFactory beanFactory;

    @RequestLine("job")
    public Response listen(Request request) {
        JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
        String profile = jsonObject.getString("profile");
        String applicationActive = Project.getInstance().getApplicationActive();
        String applicationActiveInclude = Project.getInstance().getApplicationActiveInclude();
        if (!profile.equals(applicationActive) && !applicationActiveInclude.contains(profile)) {
            return new BadResponse(request, "环境不支持");
        }

        String content = jsonObject.getString("content");
        JobValue jobValue = Json.fromJson(content, JobValue.class);
        JobExecute jobExecute = new DefaultJobExecute();
        return jobExecute.run(request, jobValue);
    }

    /**
     * bean
     *
     * @param request 要求
     * @return {@link Response}
     */
    @RequestLine("job_log_cat")
    public Response log(Request request) {
        JsonObject jsonObject = Json.getJsonObject(new String(request.getBody()));
        String content = jsonObject.getString("content");
        JobCat jobCat = Json.fromJson(content, JobCat.class);

        String fileName = JobFileAppender.makeLogFileName(jobCat.getDate(), jobCat.getLogId());
        LogResult logResult = JobFileAppender.readLog(fileName, jobCat.getFromLineNum());
        return new OkResponse(request, Json.toJson(logResult));
    }

    /**
     * bean
     *
     * @param request 要求
     * @return {@link Response}
     */
    @RequestLine("job-bean")
    public Response bean(Request request) {
        return new OkResponse(request, Json.toJson(JobHandlerFactory.getInstance().keys()));
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
    }

    private void doAnalysisJob(Map<Method, Job> methodJobMap, String beanDefinitionName, Object bean) {
        // filter method
        Map<Method, Job> annotatedMethods = null;
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method -> AnnotatedElementUtils
                            .findMergedAnnotation(method, Job.class));
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
        Map<Method, Job> annotatedMethods = null;
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    (MethodIntrospector.MetadataLookup<Job>) method -> AnnotatedElementUtils
                            .findMergedAnnotation(method, Job.class));
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

    protected void registJobHandler(Object job, Object bean, Method executeMethod) {
        if (job == null) {
            return;
        }

        if (job instanceof Job) {
            registerJob((Job) job, bean, executeMethod);
        }
    }

    private void registerJob(Job job, Object bean, Method executeMethod) {
        String name = job.value();
        // make and simplify the variables since they'll be called several times later
        Class<?> clazz = bean.getClass();
        String methodName = executeMethod.getName();
        if (name.trim().isEmpty()) {
            throw new RuntimeException("job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
        if (loadJobHandler(name) != null) {
            throw new RuntimeException("job jobhandler[" + name + "] naming conflicts.");
        }

        ClassUtils.setAccessible(executeMethod);

        // init and destroy
        Method initMethod = null;
        Method destroyMethod = null;

        if (!job.init().trim().isEmpty()) {
            try {
                initMethod = clazz.getDeclaredMethod(job.init());
                ClassUtils.setAccessible(initMethod);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(
                        "job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }
        if (!job.destroy().trim().isEmpty()) {
            try {
                destroyMethod = clazz.getDeclaredMethod(job.destroy());
                ClassUtils.setAccessible(destroyMethod);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(
                        "job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        log.info("注册任务: {} ", job.value());
        // registry jobhandler
        JobHandlerFactory.getInstance().register(name,
                new BeanJobHandler(bean, executeMethod, initMethod, destroyMethod));

    }

    public static JobHandler loadJobHandler(String name) {
        return JobHandlerFactory.getInstance().get(name);
    }

}
