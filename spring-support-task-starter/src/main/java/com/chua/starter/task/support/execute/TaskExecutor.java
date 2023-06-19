package com.chua.starter.task.support.execute;

import com.chua.common.support.log.Log;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.task.support.creator.TaskCreator;
import com.chua.starter.task.support.factory.TaskTemplate;
import com.chua.starter.task.support.pojo.SystemTask;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 执行器
 *
 * @author CH
 */
@Component
public class TaskExecutor implements InitializingBean, DisposableBean {

    private static final Log log = Log.getLogger(TaskCreator.class);
    private static final List<SystemTask> CACHE = new CopyOnWriteArrayList<>();

    @Resource
    private SystemTaskService systemTaskService;

    private static final AtomicBoolean status = new AtomicBoolean(true);
    private ExecutorService executorService = ThreadUtils.newSingleThreadExecutor("任务执行器");
    private TaskTemplate taskTemplate;

    @Override
    public void afterPropertiesSet() throws Exception {
        executorService.execute(() -> {
            while (status.get()) {
                doExecute();
                try {
                    ThreadUtils.sleepSecondsQuietly(1);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void doExecute() {
        List<SystemTask> less = new LinkedList<>();
        for (SystemTask systemTask : CACHE) {
            String taskType = systemTask.getTaskType();
            TaskCreator taskCreator = taskTemplate.getTaskCreator(taskType);
            if (null == taskCreator) {
                less.add(systemTask);
                continue;
            }

            if (systemTask.getTaskStatus() == 1) {
                less.add(systemTask);
                continue;
            }

            if (systemTask.getTaskStatus() == 2) {
                continue;
            }

            doExecute(taskCreator, systemTask);
        }
        check(less);
    }

    private void doExecute(TaskCreator taskCreator, SystemTask systemTask) {
        taskCreator.execute(systemTask);
    }

    private void check(List<SystemTask> less) {
        CACHE.removeAll(less);
    }

    @Override
    public void destroy() throws Exception {
        status.set(false);
    }

    public void register(SystemTask systemTask) {
        systemTask.setTaskStatus(3);
        systemTaskService.updateById(systemTask);
        String taskType = systemTask.getTaskType();
        TaskCreator taskCreator = taskTemplate.getTaskCreator(taskType);
        if (null == taskCreator) {
            log.warn("{}任务处理器不存在", taskType);
            return;
        }

        CACHE.add(systemTask);
    }

    public void register(TaskTemplate taskTemplate) {
        this.taskTemplate = taskTemplate;
    }

    public void unregister(SystemTask task) {
        List<SystemTask> less = new LinkedList<>();
        for (SystemTask systemTask : CACHE) {
            if (systemTask.compareTo(task) == 0) {
                less.add(systemTask);
            }
        }
        check(less);
    }
}
