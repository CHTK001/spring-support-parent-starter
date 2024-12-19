package com.chua.starter.common.support.scheduler;

import com.chua.common.support.utils.ClassUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义定时任务
 *
 * @author CH
 * @since 2024/12/9
 */
@SuppressWarnings("ALL")
@Import(SchedulerEndpoint.class)
@AutoConfigureAfter( {ScheduledAnnotationBeanPostProcessor.class, ScheduledTaskRegistrar.class})
public class SchedulingRegistration implements SchedulingConfigurer {

    private  ScheduledTaskRegistrar scheduledTaskRegistrar;
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;
    protected List<CronTask> cacheCronTask;
    protected List<FixedRateTask> cacheFixedRateTasks;
    protected Set<ScheduledTask> scheduledTasks;

    public SchedulingRegistration(@Autowired(required = false) ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor) {
        this.scheduledAnnotationBeanPostProcessor = scheduledAnnotationBeanPostProcessor;
    }




    /**
     * 注册定时任务
     */
    @SuppressWarnings("ALL")
    private void registerScheduledTask() {
        Map<Object, Set<ScheduledTask>> scheduledTasks = (Map<Object, Set<ScheduledTask>>) ClassUtils.getFieldValue("scheduledTasks", scheduledAnnotationBeanPostProcessor);
        scheduledTasks.values().forEach(it -> {
            this.scheduledTasks.addAll(it);
        });
        scheduledTasks.clear();
    }

    /**
     * 更新配置信息
     * <p>
     * 该方法通过调用schedulingConfigurer的updateConfigurer方法来更新配置信息
     * 主要用于在运行时对配置进行修改，以适应动态配置的需求
     *
     * @param name 配置项的名称，用于标识特定的配置项
     * @param time 新的时间配置，表示配置项的新值
     * @return 返回更新操作的结果，通常是一个表示成功或失败的字符串消息
     */
    public String updateConfigurer(String name, String time) {
        ScheduledTask task = getTask(name);
        if (null == task) {
            CronTask cronTask = checkCronTask(name);
            if (null != cronTask) {
                return updateConfigurerForCronTask(null, cronTask, time);
            }

            IntervalTask fixedRateTask = checkFixedRateTask(name);
            if (null != fixedRateTask) {
                return updateConfigurerForFixedRateTask(null, (FixedRateTask) fixedRateTask, time);
            }
        }
        if (null == task) {
            return "未找到任务";
        }

        return updateConfigurer(task, time);
    }

    /**
     * 检查FixedRateTask
     *
     * @param name 要检查的任务名称
     * @return 返回一个FixedRateTask对象，表示要更新的任务
     */
    private IntervalTask checkFixedRateTask(String name) {
        for (IntervalTask intervalTask : scheduledTaskRegistrar.getFixedRateTaskList()) {
            if (intervalTask.toString().equals(name)) {
                return intervalTask;
            }
        }

        return null;
    }


    /**
     * 更新配置信息
     *
     * @param task 要更新的任务对象
     * @param time 新的时间配置，表示配置项的新值
     * @return 返回更新操作的结果，通常是一个表示成功或失败的字符串消息
     */
    private String updateConfigurer(ScheduledTask task, String time) {
        if (task.getTask() instanceof CronTask cronTask) {
            return updateConfigurerForCronTask(task, cronTask, time);
        }

        if (task.getTask() instanceof FixedRateTask fixedRateTask) {
            return updateConfigurerForFixedRateTask(task, fixedRateTask, time);
        }

        return "不支持该任务时间更新";
    }

    /**
     * 更新FixedRateTask配置信息
     *
     * @param fixedRateTask 要更新的FixedRateTask对象
     * @param time          新的时间配置，表示配置项的新值
     * @return 返回更新操作的结果，通常是一个表示成功或失败的字符串消息
     */
    private String updateConfigurerForFixedRateTask(ScheduledTask task, FixedRateTask fixedRateTask, String time) {
        Duration ofMillis = Duration.ofMillis(Long.parseLong(time));
        if (ofMillis.toMillis() == fixedRateTask.getIntervalDuration().toMillis()) {
            return "时间未改变";
        }
        if (null != task) {
            task.cancel();
            scheduledTasks.remove(task);
        }
        Runnable runnable = fixedRateTask.getRunnable();
        cacheFixedRateTasks.remove(fixedRateTask);
        FixedRateTask newFixedRateTask = new FixedRateTask(runnable, ofMillis, Duration.ZERO);
        scheduledTasks.add(scheduledTaskRegistrar.scheduleFixedRateTask(newFixedRateTask));
        return "更新成功";
    }

    /**
     * 更新CronTask配置信息
     *
     * @param task     要更新的CronTask对象
     * @param cronTask
     * @param time     新的时间配置，表示配置项的新值
     * @return 返回更新操作的结果，通常是一个表示成功或失败的字符串消息
     */
    private String updateConfigurerForCronTask(ScheduledTask task, CronTask cronTask, String time) {
        time = time.replace('_', '/');
        if (cronTask.getExpression().equals(time)) {
            return "时间未改变";
        }
        if (null != task) {
            task.cancel();
            scheduledTasks.remove(task);
        }
        Runnable runnable = cronTask.getRunnable();
        CronTask newCronTask = new CronTask(runnable, time);
        cacheCronTask.remove(cronTask);
        scheduledTasks.add(scheduledTaskRegistrar.scheduleCronTask(newCronTask));
        return "更新成功";
    }

    /**
     * 获取任务
     *
     * @param name 任务名称
     * @return 返回一个Task对象，表示要更新的任务
     */
    private ScheduledTask getTask(String name) {
        Set<ScheduledTask> scheduledTasks = scheduledTaskRegistrar.getScheduledTasks();
        for (ScheduledTask scheduledTask : scheduledTasks) {
            if (scheduledTask.getTask().toString().equals(name)) {
                return scheduledTask;
            }
        }

        return null;
    }

    /**
     * 检查CronTask
     *
     * @param name 要检查的任务名称
     * @return 返回一个CronTask对象，表示要更新的任务
     */
    private CronTask checkCronTask(String name) {
        for (CronTask cronTask : scheduledTaskRegistrar.getCronTaskList()) {
            if (cronTask.toString().equals(name)) {
                return cronTask;
            }
        }

        return null;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.scheduledTaskRegistrar = taskRegistrar;
        cacheCronTask = (List<CronTask>) ClassUtils.getFieldValue("cronTasks", scheduledTaskRegistrar);
        cacheFixedRateTasks = (List<FixedRateTask>) ClassUtils.getFieldValue("fixedRateTasks", scheduledTaskRegistrar);
        scheduledTasks = (Set<ScheduledTask>) ClassUtils.getFieldValue("scheduledTasks", scheduledTaskRegistrar);
        registerScheduledTask();
    }
}
