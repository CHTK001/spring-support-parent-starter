package com.chua.report.server.starter.job.trigger;

import com.chua.report.server.starter.job.JobConfig;
import com.chua.report.server.starter.job.handler.CoreTriggerHandler;
import com.chua.report.server.starter.job.handler.RingTriggerHandler;
import com.chua.report.server.starter.properties.JobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.concurrent.TimeUnit;

/**
 * 调度程序触发器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
@RequiredArgsConstructor
public class SchedulerTrigger implements InitializingBean, DisposableBean, ApplicationContextAware {

    private CoreTriggerHandler coreTriggerHandler;

    private RingTriggerHandler ringTriggerHandler;
    private ApplicationContext applicationContext;

    @Autowired
    private JobProperties jobProperties;


    @Override
    public void destroy() throws Exception {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        coreTriggerHandler.stop();
        ringTriggerHandler.stop();
        JobTriggerPoolHelper.toStop();
        log.info(">>>>>>>>>>> job stop");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        coreTriggerHandler = new CoreTriggerHandler(jobProperties);
        ringTriggerHandler = new RingTriggerHandler(jobProperties);
        AutowireCapableBeanFactory autowireCapableBeanFactory = applicationContext.getAutowireCapableBeanFactory();
        autowireCapableBeanFactory.autowireBean(coreTriggerHandler);
        autowireCapableBeanFactory.autowireBean(ringTriggerHandler);
        JobConfig.getInstance().register(jobProperties);
        JobConfig.getInstance().register(applicationContext);
        coreTriggerHandler.start();
        ringTriggerHandler.start();
        JobTriggerPoolHelper.toStart();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
