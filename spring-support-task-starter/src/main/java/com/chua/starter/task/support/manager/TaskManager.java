package com.chua.starter.task.support.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.log.Log;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.service.SystemTaskService;
import com.chua.starter.task.support.task.Task;
import com.google.common.eventbus.Subscribe;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 任務管理器
 *
 * @author CH
 */
public class TaskManager implements InitializingBean, DisposableBean {
    private final StringRedisTemplate redisTemplate;

    private static final Log log = Log.getLogger(Task.class);
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor("check-heart");
    private final ScheduledExecutorService scheduledExecutorUpdateService = ThreadUtils.newScheduledThreadPoolExecutor("update-heart");
    @Resource(name = Constant.DEFAULT_TASK_EXECUTOR)
    private Executor executor;
    @Resource
    private SystemTaskService systemTaskService;

    private Map<String, Integer> taskStepQueue = new ConcurrentHashMap<>(100000);

    public TaskManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void destroy() throws Exception {
        for (TaskInfo it : taskMap.values()) {
            try {
                it.getTask().close();
            } catch (Exception ignored) {
            }
        }

        ThreadUtils.shutdownNow(scheduledExecutorService);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("开始装载任务");
        List<SysTask> sysTasks = systemTaskService.list(Wrappers.<SysTask>lambdaQuery()
                .in(SysTask::getTaskStatus, 0, 2));
        for (SysTask sysTask : sysTasks) {
            taskMap.put(sysTask.getTaskTid(), new TaskInfo(ServiceProvider.of(Task.class)
                    .getNewExtension(sysTask.getTaskType() + ":" + sysTask.getTaskCid(),
                            sysTask, this),
                    sysTask
            ));

        }
        log.info("装载完成");
        log.info("开始设置心跳检测机制");
        doInitialHeart();
        log.info("心跳检测机制初始化完成");
        doTaskWorker();
    }

    private void doInitialHeart() {
        scheduledExecutorService.scheduleAtFixedRate(() -> {

        }, 0, 1, TimeUnit.MINUTES);

        scheduledExecutorUpdateService.scheduleAtFixedRate(() -> {
            try {
                Map<String, Integer> tpl = new HashMap<>(taskStepQueue);
                taskStepQueue.clear();
                for (Map.Entry<String, Integer> entry : tpl.entrySet()) {
                    try {
                        SysTask taskByTaskTid = systemTaskService.getTaskByTaskTid(entry.getKey());
                        taskByTaskTid.setTaskCurrent(entry.getValue());
                        systemTaskService.updateWithId(taskByTaskTid);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }

        }, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * 获取任务
     *
     * @param taskId 任务ID
     * @return 任务
     */
    public SysTask getTask(String taskId) {
        TaskInfo taskInfo = taskMap.get(taskId);
        return null == taskInfo ? null : taskInfo.getSysTask();
    }

    /**
     * 开启任务
     */
    private void doTaskWorker() {
        for (TaskInfo value : taskMap.values()) {
            value.getTask().worker();
        }
    }

    /**
     * 更新长度
     *
     * @param taskTid 任务ID
     * @param size    当前任务位置
     */
    public void doUpdateStep(String taskTid, int size) {
        taskStepQueue.put(taskTid, size);
    }


    @Data
    @AllArgsConstructor
    static
    class TaskInfo {
        private Task task;

        private SysTask sysTask;
    }


    @Subscribe
    public void listener(SysTask sysTask) {
        taskMap.put(sysTask.getTaskTid(), new TaskInfo(ServiceProvider.of(Task.class)
                .getNewExtension(sysTask.getTaskType() + ":" + sysTask.getTaskCid(),
                        sysTask, this),
                sysTask
        ));
    }

    @Subscribe
    public void listener(String taskTid) {
        taskStepQueue.remove(taskTid);
        taskMap.remove(taskTid);
    }
}
