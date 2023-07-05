package com.chua.starter.task.support.task;


import com.chua.common.support.json.Json;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.task.support.manager.TaskManager;
import com.chua.starter.task.support.pojo.SysTask;
import com.chua.starter.task.support.pojo.TaskParam;
import com.chua.starter.task.support.service.SystemTaskService;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务
 *
 * @author CH
 */
public abstract class Task implements AutoCloseable {

    private final AtomicBoolean status = new AtomicBoolean(true);
    public static final String PRE = "task:";
    protected static final Log log = Log.getLogger(Task.class);
    private final TaskParam taskParam;
    private final ListOperations<String, String> opsForList;
    private final String taskTid;
    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource(name = Constant.DEFAULT_TASK_EXECUTOR)
    private Executor executor;
    @Resource(name = Constant.DEFAULT_EXECUTOR2)
    private Executor executor2;

    @Resource
    private SystemTaskService systemTaskService;

    private final TaskManager taskManager;
    String newKey;


    public Task(SysTask sysTask, TaskManager taskManager) {
        this.newKey = PRE + sysTask.getTaskTid();
        this.taskManager = taskManager;
        this.taskTid = sysTask.getTaskTid();
        String taskParams = sysTask.getTaskParams();
        this.opsForList = redisTemplate.opsForList();
        this.taskParam = new TaskParam(Json.toMapStringObject(taskParams));
    }

    /**
     * 执行任务
     *
     * @param offset    当前位置
     * @param taskParam 任务参数
     */
    abstract void execute(long offset, TaskParam taskParam);


    /**
     * 开始任务
     */
    public void worker() {
        Integer taskCurrent = taskManager.getTask(taskTid).getTaskCurrent();
        doWork(taskCurrent, taskParam);
    }

    private void doWork(Integer taskCurrent, TaskParam taskParam) {
        executor.execute(() -> {
            if (!status.get()) {
                return;
            }
            try {
                execute(taskCurrent, taskParam);
                doAnalysis();
            } catch (Exception e) {
                log.error("运行失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 任務已完成
     *
     * @param sysTask 任務
     */
    protected void finish(SysTask sysTask) {

    }

    private synchronized void doAnalysis() {
        int exact = Math.toIntExact(opsForList.size(newKey));
        SysTask sysTask = taskManager.getTask(taskTid);
        if (exact >= sysTask.getTaskTotal()) {
            log.info("任务已完成");
            sysTask.setTaskStatus(1);
            sysTask.setTaskCurrent(exact);
            doFinish(sysTask);
            return;
        }

        doUpdateStep(exact);
        doWork(exact, taskParam);
        ThreadUtils.sleepSecondsQuietly(0);
    }

    /**
     * 完成
     *
     * @param sysTask 任務完成
     */
    private void doFinish(SysTask sysTask) {
        try {
            systemTaskService.forUpdateCurrent(sysTask.getTaskId(), sysTask.getTaskTotal());
        } catch (Exception ignored) {
        }
        finish(sysTask);
        ThreadUtils.sleepSecondsQuietly(0);
    }

    /**
     * 记录业务唯一编码
     *
     * @param code 业务唯一编码
     */
    protected synchronized void step(String... code) {
        try {
            opsForList.leftPushAll(newKey, code);
            redisTemplate.expire(newKey, taskManager.getTask(taskTid).getTaskExpire(), TimeUnit.SECONDS);
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
                taskManager.doUpdateStep(taskTid, size);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void close() throws Exception {
        status.set(false);
    }
}
