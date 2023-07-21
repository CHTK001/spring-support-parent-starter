package com.chua.starter.task.support.task;


import com.chua.common.support.json.Json;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.sse.support.SseMessage;
import com.chua.starter.sse.support.SseMessageType;
import com.chua.starter.sse.support.SseTemplate;
import com.chua.starter.task.support.manager.TaskManager;
import com.chua.starter.task.support.params.TaskParam;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.chua.starter.sse.support.SseMessageType.*;

/**
 * 任务
 *
 * @author CH
 */
public abstract class Task implements AutoCloseable {

    public static final String SUBSCRIBE = "Task:Subscribe";
    private final AtomicBoolean status = new AtomicBoolean(true);
    public static final String PRE = "";
    protected static final Log log = Log.getLogger(Task.class);
    protected TaskParam taskParam;
    private String taskTid;
    private ValueOperations<String, String> opsForList;
    private String key;
    @Resource(name = com.chua.common.support.protocol.server.Constant.STRING_REDIS)
    private StringRedisTemplate redisTemplate;

    @Resource(name = Constant.DEFAULT_TASK_EXECUTOR)
    private Executor executor;
    @Resource(name = Constant.DEFAULT_EXECUTOR2)
    private Executor executor2;

    @Resource
    private SystemTaskService systemTaskService;

    String newKey;
    @Resource
    private SseTemplate sseTemplate;

    @Resource
    private TaskManager taskManager;

    private SysTask sysTask;

    public void setSysTask(SysTask sysTask) {
        this.newKey = sysTask.getKey();
        this.key = sysTask.getKey();
        this.taskTid = sysTask.getTaskTid();
        String taskParams = sysTask.getTaskParams();
        this.taskParam = new TaskParam(Json.toMapStringObject(taskParams));
        this.taskParam.addProfile("taskName", sysTask.getTaskName());
    }

    public Task() {

    }

    /**
     * 执行任务
     *
     * @param offset    当前位置
     * @param taskParam 任务参数
     */
    protected abstract void execute(long offset, TaskParam taskParam);


    /**
     * 开始任务
     */
    public void worker() {
        SysTask task = taskManager.getTask(key);
        if (null == task) {
            return;
        }

        doAnalysis();
    }

    private void doWork(Integer taskCurrent, TaskParam taskParam) {
        if (!status.get()) {
            return;
        }
        SysTask task = taskManager.getTask(key);
        if (null == task) {
            return;
        }
        try {
            execute(taskCurrent, taskParam);
            doAnalysis();
        } catch (Exception e) {
            log.error("运行失败: {}", e.getMessage());
        }
    }

    /**
     * 任務已完成
     *
     * @param sysTask 任務
     */
    protected void finish(SysTask sysTask) {

    }

    /**
     * 重置
     */
    protected void reset() {
        redisTemplate.delete(newKey);
        taskManager.reset(key);
    }

    private synchronized void doAnalysis() {
        check();
        ThreadUtils.sleepSecondsQuietly(0);
        int exact = NumberUtils.toInt(opsForList.get(newKey));
        SysTask sysTask = taskManager.getTask(key);
        if (null == sysTask) {
            return;
        }

        if(sysTask.getTaskStatus() == 1) {
            return;
        }
        notifyMessage(PROCESS, exact + "");

        if (isFinish(exact, sysTask)) {
            return;
        }

        ThreadUtils.sleepSecondsQuietly(0);

        doUpdateStep(exact);
        doWork(exact, taskParam);
    }

    private boolean isFinish(long exact, SysTask sysTask) {
        if(exact >= sysTask.getTaskTotal()) {
            log.info("任务已完成");
            sysTask.setTaskStatus(1);
            sysTask.setTaskCurrent(exact);
            doFinish(sysTask);
            taskManager.listener(sysTask.getTaskTid());
            return true;
        }

        return false;
    }

    protected void check() {
        if(null == opsForList) {
            synchronized (this) {
                if(null == opsForList) {
                    this.opsForList = redisTemplate.opsForValue();
                }
            }
        }
    }

    /**
     * 完成
     *
     * @param sysTask 任務完成
     */
    private void doFinish(SysTask sysTask) {
        try {
            systemTaskService.forUpdateCurrent(sysTask);
        } catch (Exception ignored) {
        }
        finish(sysTask);
        try {
            sseTemplate.emit(SseMessage.builder().message(sysTask.getTaskCost() + "").type(FINISH).tid(taskTid).build(), Task.SUBSCRIBE);
        } catch (Exception e) {
        }
        ThreadUtils.sleepSecondsQuietly(0);
    }

    /**
     * 记录业务唯一编码
     *
     * @param currentOffset 当前数量
     */
    protected synchronized void step(int currentOffset) {
        try {
            check();
            opsForList.set(newKey, NumberUtils.toInt(opsForList.get(newKey)) + currentOffset + "");
            redisTemplate.expire(newKey, taskManager.getTask(key).getTaskExpire(), TimeUnit.SECONDS);
            doAnalysis();
        } catch (Exception e) {
            log.info("任务处理异常");
        }
    }

    /**
     * 更新步长
     */
    private void doUpdateStep(int size) {
        executor2.execute(() -> {
            try {
                taskManager.doUpdateStep(key, size);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void close() throws Exception {
        status.set(false);
    }


    protected void notifyMessage(SseMessageType type, String message) {
        if(type == NOTIFY) {
            String s = opsForList.get(message);
            if(null != s) {
                return;
            }
            opsForList.set(message, message, 4, TimeUnit.SECONDS);
        }
        try {
            sseTemplate.emit(SseMessage.builder().message(message).type(type).tid(taskTid).build(), Task.SUBSCRIBE);
        } catch (Exception ignored) {
        }
    }
}
