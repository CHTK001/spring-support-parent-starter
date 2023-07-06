package com.chua.starter.task.support.manager;

import com.chua.common.support.eventbus.EventbusType;
import com.chua.common.support.eventbus.Subscribe;
import com.chua.common.support.log.Log;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.common.support.eventbus.EventbusTemplate;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.task.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
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
public class TaskManager implements ApplicationContextAware, DisposableBean, CommandLineRunner {
    private final StringRedisTemplate redisTemplate;

    private static final Log log = Log.getLogger(Task.class);
    private final Map<String, TaskInfo> taskMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor("check-heart");
    private final ScheduledExecutorService scheduledExecutorUpdateService = ThreadUtils.newScheduledThreadPoolExecutor("update-heart");
    @Resource(name = Constant.DEFAULT_TASK_EXECUTOR)
    private Executor executor;

    @Resource
    private EventbusTemplate eventbusTemplate;


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
        ThreadUtils.shutdownNow(scheduledExecutorUpdateService);
    }


    public void afterPropertiesSet() {
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
                    eventbusTemplate.post("update", entry);
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
            Task task = value.getTask();
            if(null == task) {
                continue;
            }
            executor.execute(task::worker);
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

    /**
     * 重置
     *
     * @param taskTid
     */
    public void reset(String taskTid) {
        eventbusTemplate.post("reset", taskTid);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }

    @Override
    public void run(String... args) throws Exception {
        afterPropertiesSet();
    }


    @Data
    @AllArgsConstructor
    static
    class TaskInfo {
        private Task task;

        private SysTask sysTask;
    }


    @Subscribe(type = EventbusType.GUAVA, name = "task")
    public void listener(SysTask sysTask) {
        taskMap.put(sysTask.getTaskTid(), new TaskInfo(ServiceProvider.of(Task.class)
                .getNewExtension(sysTask.getTaskType() + ":" + sysTask.getTaskCid(),
                        sysTask, this),
                sysTask
        ));
    }

    @Subscribe(type = EventbusType.GUAVA, name = "task")
    public void listener(String taskTid) {
        taskStepQueue.remove(taskTid);
        taskMap.remove(taskTid);
    }
}
