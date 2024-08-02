package com.chua.scheduler.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.*;

import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * @author CH
 * @since 2024/8/1
 */
public class ScheduleCornChangeHandler implements SchedulingConfigurer {
    private final ScheduledAnnotationBeanPostProcessor processor;
    private ScheduledTaskRegistrar registrar;
    private Set<ScheduledTask> scheduledTasksList;
    private final Map<Object, Set<ScheduledTask>> scheduledTasks;
    public ScheduleCornChangeHandler(ScheduledAnnotationBeanPostProcessor processor) {
        this.processor = processor;
        this.scheduledTasks = findScheduledTasks();
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Set<ScheduledTask>> findScheduledTasks() {
        try {
            Field scheduledTasks1 = processor.getClass().getDeclaredField("scheduledTasks");
            scheduledTasks1.setAccessible(true);
            return (Map<Object, Set<ScheduledTask>>) scheduledTasks1.get(processor);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public String cornChanged(String express, String name) {
        // 获取所有定时任务
        Set<ScheduledTask> scheduledTasks = processor.getScheduledTasks();
        for (ScheduledTask scheduledTask : scheduledTasks) {
            Task task = scheduledTask.getTask();
            if(name.equals(task.toString())) {
                return changeTask(scheduledTask, task, express);
            }
        }

        return null;
    }

    private String changeTask(ScheduledTask scheduledTask, Task task, String express) {
        if(task instanceof CronTask cronTask) {
            return changeCronTask(scheduledTask, cronTask, express);
        }

        if(task instanceof FixedDelayTask fixedDelayTask) {
            return changeFixedDelayTask(scheduledTask, fixedDelayTask, express);
        }

        if(task instanceof FixedRateTask fixedRateTask) {
            return changeFixedRateTask(scheduledTask, fixedRateTask, express);
        }

        return express;
    }

    private String changeFixedRateTask(ScheduledTask oldScheduledTask, FixedRateTask fixedRateTask, String express) {
        Duration duration = Duration.ofMillis(Long.parseLong(express));
        if(fixedRateTask.getIntervalDuration().toMillis() == duration.toMillis()) {
            return "与修改时间相同";
        }

        oldScheduledTask.cancel();
        Runnable runnable = fixedRateTask.getRunnable();
        ScheduledTask newScheduledTask = registrar.scheduleFixedRateTask(new FixedRateTask(runnable, duration, Duration.ZERO));
        unregister(oldScheduledTask);
        register(newScheduledTask);
        return "修改成功";
    }

    private String changeFixedDelayTask(ScheduledTask oldScheduledTask, FixedDelayTask fixedDelayTask, String express) {
        Duration duration = Duration.ofMillis(Long.parseLong(express));
        if(fixedDelayTask.getIntervalDuration().toMillis() == duration.toMillis()) {
            return "与修改时间相同";
        }
        oldScheduledTask.cancel();
        Runnable runnable = fixedDelayTask.getRunnable();
        ScheduledTask newScheduledTask = registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, duration, Duration.ZERO));
        unregister(oldScheduledTask);
        register(newScheduledTask);
        return "修改成功";

    }

    private void unregister(ScheduledTask oldScheduledTask) {
        oldScheduledTask.cancel();
        scheduledTasksList.remove(oldScheduledTask);
        Iterator<Set<ScheduledTask>> iterator = scheduledTasks.values().iterator();
        while (iterator.hasNext()) {
            Set<ScheduledTask> next = iterator.next();
            Iterator<ScheduledTask> iterator1 = next.iterator();
            while (iterator1.hasNext()) {
                if(iterator1.next().equals(oldScheduledTask)) {
                    iterator1.remove();
                }
            }
        }
    }

    private void register(ScheduledTask scheduledTask) {
        scheduledTasksList.add(scheduledTask);
    }

    private String changeCronTask(ScheduledTask oldScheduledTask, CronTask cronTask, String express) {
        express = URLDecoder.decode(express, StandardCharsets.UTF_8).replace("@", "/");
        express = express.endsWith("?") ? express : express + " ?";
        if(cronTask.getExpression().equals(express)) {
            return "与修改时间相同";
        }

        oldScheduledTask.cancel();
        Runnable runnable = cronTask.getRunnable();
        ScheduledTask newScheduledTask = registrar.scheduleCronTask(new CronTask(runnable, express));
        unregister(oldScheduledTask);
        register(newScheduledTask);
        return "修改成功";
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 目的是拿到ScheduledTaskRegistrar对象来注册定时任务（注意：该对象无法直接通过自动注入获得）
        registrar = taskRegistrar;
        try {
            Field scheduledTasks = registrar.getClass().getDeclaredField("scheduledTasks");
            scheduledTasks.setAccessible(true);
            scheduledTasksList = (Set<ScheduledTask>) scheduledTasks.get(registrar);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取定时任务
     * @return
     */
    public String scheduler() {
        Set<ScheduledTask> scheduledTasks = processor.getScheduledTasks();
        List<Map<String, Object>> res = new LinkedList<>();
        for (ScheduledTask scheduledTask : scheduledTasks) {
            Task task = scheduledTask.getTask();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", task.toString());
            switch (task) {
                case CronTask cronTask -> {
                    item.put("express", cronTask.getExpression());
                    res.add(item);
                }
                case FixedRateTask fixedRateTask -> {
                    item.put("express", fixedRateTask.getInterval());
                    res.add(item);
                }
                case FixedDelayTask fixedDelayTask -> {
                    item.put("express", fixedDelayTask.getInterval());
                    res.add(item);
                }
                default -> {
                }
            }


        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(res);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
