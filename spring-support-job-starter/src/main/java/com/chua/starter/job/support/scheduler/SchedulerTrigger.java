package com.chua.starter.job.support.scheduler;

import com.chua.starter.job.support.JobProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.concurrent.TimeUnit;

/**
 * 调度程序触发器 - Job调度系统的核心启动器
 * <p>
 * 实现 {@link InitializingBean} 和 {@link DisposableBean} 接口，
 * 在Spring容器初始化时启动调度系统，在销毁时优雅关闭。
 * </p>
 * 
 * <h3>启动流程:</h3>
 * <ol>
 *     <li>初始化 {@link CoreTriggerHandler} - 核心触发处理器，负责任务调度</li>
 *     <li>初始化 {@link RingTriggerHandler} - 时间环处理器，负责精确时间触发</li>
 *     <li>注册配置和Spring上下文到 {@link JobConfig}</li>
 *     <li>启动 {@link JobTriggerPoolHelper} - 任务触发线程池</li>
 * </ol>
 * 
 * <h3>关闭流程:</h3>
 * <ol>
 *     <li>等待1秒让正在执行的任务完成</li>
 *     <li>停止核心触发处理器</li>
 *     <li>停止时间环处理器</li>
 *     <li>关闭触发线程池</li>
 * </ol>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see CoreTriggerHandler
 * @see RingTriggerHandler
 * @see JobTriggerPoolHelper
 */
@Slf4j
public class SchedulerTrigger implements InitializingBean, DisposableBean, ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(SchedulerTrigger.class);

    /**
     * 核心触发处理器，负责扫描即将执行的任务
     */
    private CoreTriggerHandler coreTriggerHandler;

    /**
     * 时间环处理器，负责在精确时间点触发任务
     */
    private RingTriggerHandler ringTriggerHandler;

    /**
     * Spring应用上下文
     */
    private ApplicationContext applicationContext;

    /**
     * Job配置属性
     */
    @Autowired
    private JobProperties jobProperties;

    /**
     * 应用关闭时优雅停止调度系统
     * <p>
     * 执行顺序:
     * 1. 等待1秒让正在执行的任务有机会完成
     * 2. 停止核心触发处理器
     * 3. 停止时间环处理器
     * 4. 关闭触发线程池
     * </p>
     */
    @Override
    public void destroy() throws Exception {
        log.info(">>>>>>>>>>> 开始关闭Job调度系统...");
        try {
            // 等待正在执行的任务完成
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("关闭等待被中断");
        }

        // 依次关闭各组件
        coreTriggerHandler.stop();
        ringTriggerHandler.stop();
        JobTriggerPoolHelper.toStop();

        log.info(">>>>>>>>>>> Job调度系统已关闭");
    }

    /**
     * Spring容器初始化后启动调度系统
     * <p>
     * 执行顺序:
     * 1. 创建核心触发处理器和时间环处理器
     * 2. 对处理器进行Spring依赖注入
     * 3. 注册配置和Spring上下文到JobConfig
     * 4. 启动各处理器和线程池
     * </p>
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info(">>>>>>>>>>> 开始初始化Job调度系统...");

        // 创建处理器实例
        coreTriggerHandler = new CoreTriggerHandler(jobProperties);
        ringTriggerHandler = new RingTriggerHandler(jobProperties);

        // Spring依赖注入
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
        beanFactory.autowireBean(coreTriggerHandler);
        beanFactory.autowireBean(ringTriggerHandler);

        // 注册全局配置
        JobConfig.getInstance().register(jobProperties);
        JobConfig.getInstance().register(applicationContext);

        // 启动各组件
        coreTriggerHandler.start();
        ringTriggerHandler.start();
        JobTriggerPoolHelper.toStart();

        log.info(">>>>>>>>>>> Job调度系统初始化完成");
    }

    /**
     * 注入Spring应用上下文
     *
     * @param applicationContext Spring应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
