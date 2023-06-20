package com.chua.starter.task.support.factory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.SpiOption;
import com.chua.common.support.log.Log;
import com.chua.common.support.spi.Option;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.task.support.creator.TaskCreator;
import com.chua.starter.task.support.execute.SystemTaskExecutor;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 任务管理器
 *
 * @author CH
 */
@Component
@Lazy
@SuppressWarnings("ALL")
public class TaskTemplate implements ApplicationContextAware, InitializingBean {

    private static final Log log = Log.getLogger(TaskCreator.class);
    private static final Map<String, TaskCreator> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Option<String>> LABEL = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;
    @Resource
    private SystemTaskExecutor systemTaskExecutor;
    private final ExecutorService singleExecutorService = ThreadUtils.newSingleThreadExecutor("暂停任务检测器");
    @Resource
    private SystemTaskService systemTaskService;

    public Set<String> type() {
        return CACHE.keySet();
    }


    public TaskCreator getTaskCreator(String name) {
        return CACHE.get(name);
    }

    public void removeTaskCreator(String name) {
        TaskCreator taskCreator1 = getTaskCreator(name);
        if (null != taskCreator1 && taskCreator1 instanceof AutoCloseable) {
            try {
                ((AutoCloseable) taskCreator1).close();
            } catch (Exception ignored) {
            }
        }

        CACHE.remove(name);
        LABEL.remove(name);

    }

    public void addTaskCreator(String name, TaskCreator taskCreator) {
        TaskCreator taskCreator1 = getTaskCreator(name);
        if (null != taskCreator1 && taskCreator1 instanceof AutoCloseable) {
            try {
                ((AutoCloseable) taskCreator1).close();
            } catch (Exception ignored) {
            }
        }

        CACHE.put(name, taskCreator);
        SpiOption spiOption = taskCreator.getClass().getDeclaredAnnotation(SpiOption.class);
        LABEL.put(name, new Option<>(name, null == spiOption ? name : spiOption.value()));

    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        ServiceProvider<TaskCreator> serviceProvider = ServiceProvider.of(TaskCreator.class);
        Map<String, TaskCreator> list = serviceProvider.list();
        for (Map.Entry<String, TaskCreator> entry : list.entrySet()) {
            TaskCreator value = entry.getValue();
            SpiOption spiOption = value.getClass().getDeclaredAnnotation(SpiOption.class);
            String key = entry.getKey();
            CACHE.put(key, value);
            LABEL.put(key, new Option<>(key, null == spiOption ? key : spiOption.value()));
        }
        Map<String, TaskCreator> beansOfType = applicationContext.getBeansOfType(TaskCreator.class);
        for (Map.Entry<String, TaskCreator> entry : beansOfType.entrySet()) {
            TaskCreator value = entry.getValue();
            SpiOption spiOption = value.getClass().getDeclaredAnnotation(SpiOption.class);
            String key = entry.getKey();
            CACHE.put(key, value);
            LABEL.put(key, new Option<>(key, null == spiOption ? key : spiOption.value()));
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        systemTaskExecutor.register(this);
        List<SysTask> systemTasks = systemTaskService.list(Wrappers.<SysTask>lambdaQuery().in(SysTask::getTaskStatus, 0));
        for (SysTask systemTask : systemTasks) {
            systemTaskExecutor.register(systemTask);
        }
    }

    private void doExecute(TaskCreator taskCreator, SysTask systemTask) {
        taskCreator.execute(systemTask);
    }

    /**
     * 注册任务
     *
     * @param task 任务
     */
    public void register(SysTask task) {
        String taskType = task.getTaskType();
        TaskCreator taskCreator = getTaskCreator(taskType);
        if (null == taskCreator) {
            log.warn("{}任务处理器不存在", taskType);
            return;
        }
        this.doExecute(taskCreator, task);
    }

    public void update(SysTask task) {
        String taskType = task.getTaskType();
        TaskCreator taskCreator = getTaskCreator(taskType);
        if (null == taskCreator) {
            log.warn("{}任务处理器不存在", taskType);
            return;
        }
//        this.doExecute(taskCreator, task);
    }

    public void refresh() {
        List<SysTask> systemTasks = systemTaskService.list(Wrappers.<SysTask>lambdaQuery().in(SysTask::getTaskStatus, 0));
        for (SysTask systemTask : systemTasks) {
            systemTaskExecutor.register(systemTask);
        }
    }


}
