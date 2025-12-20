package com.chua.starter.job.support.thread;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.job.support.TriggerParam;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.log.DefaultJobLog;
import com.chua.starter.job.support.log.JobFileAppender;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 任务执行线程
 * <p>
 * 每个任务对应一个JobThread实例，负责该任务的实际执行。
 * 线程会从队列中获取触发参数并调用{@link JobHandler}执行任务。
 * </p>
 *
 * <h3>线程生命周期</h3>
 * <ol>
 *     <li><b>初始化</b> - 调用JobHandler.init()方法</li>
 *     <li><b>循环执行</b> - 从队列获取任务并执行，支持超时控制</li>
 *     <li><b>空闲检测</b> - 空闲超过30次后自动销毁</li>
 *     <li><b>销毁</b> - 调用JobHandler.destroy()方法</li>
 * </ol>
 *
 * <h3>核心特性</h3>
 * <ul>
 *     <li><b>重复触发检测</b> - 通过logId去重，避免同一任务重复执行</li>
 *     <li><b>超时控制</b> - 支持任务执行超时设置，超时后强制中断</li>
 *     <li><b>执行上下文</b> - 自动维护{@link JobContext}生命周期</li>
 *     <li><b>日志记录</b> - 自动记录任务执行的开始、结束和异常信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 通过工厂创建并注册线程
 * JobThread thread = JobThreadFactory.registJobThread(jobId, handler, "new handler");
 *
 * // 推送任务到执行队列
 * ReturnResult<String> result = thread.pushTriggerQueue(triggerParam);
 * if (result.isSuccess()) {
 *     // 任务已加入队列
 * }
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see JobHandler
 * @see JobThreadFactory
 * @see JobContext
 */
@Slf4j
@SuppressWarnings("ALL")
public class JobThread extends Thread {

    /** 任务ID */
    private final int jobId;
    
    /** 任务处理器 */
    private final JobHandler handler;
    
    /** 触发参数队列，用于存放待执行的任务 */
    private final LinkedBlockingQueue<TriggerParam> triggerQueue;
    
    /** 已触发的日志ID集合，用于去重 */
    private final Set<Long> triggerLogIdSet;

    /** 停止标志 */
    private volatile boolean toStop = false;
    
    /** 停止原因 */
    private String stopReason;

    /** 是否正在运行 */
    private boolean running = false;
    
    /** 空闲次数计数器，超过30次后自动销毁线程 */
    private int idleTimes = 0;

    public JobThread(int jobId, JobHandler handler) {
        this.jobId = jobId;
        this.handler = handler;
        this.triggerQueue = new LinkedBlockingQueue<>();
        this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<>());
        this.setName("job, JobThread-" + jobId + "-" + System.currentTimeMillis());
    }

    public JobHandler getHandler() {
        return handler;
    }

    /**
     * 推送触发参数到队列
     *
     * @param triggerParam 触发参数
     * @return 结果
     */
    public ReturnResult<String> pushTriggerQueue(TriggerParam triggerParam) {
        if (triggerLogIdSet.contains(triggerParam.getLogId())) {
            log.info(">>>>>>>>>>> 重复触发任务已忽略, logId={}", triggerParam.getLogId());
            return ReturnResult.illegal("重复触发任务, logId:" + triggerParam.getLogId());
        }

        triggerLogIdSet.add(triggerParam.getLogId());
        triggerQueue.add(triggerParam);
        return ReturnResult.SUCCESS;
    }

    /**
     * 停止线程
     *
     * @param stopReason 停止原因
     */
    public void toStop(String stopReason) {
        this.toStop = true;
        this.stopReason = stopReason;
    }

    /**
     * 是否正在运行或队列中有任务
     *
     * @return 是否运行中
     */
    public boolean isRunningOrHasQueue() {
        return running || !triggerQueue.isEmpty();
    }

    @Override
    public void run() {
        // 初始化
        try {
            handler.init();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        // 执行
        while (!toStop) {
            running = false;
            idleTimes++;

            TriggerParam triggerParam = null;
            try {
                triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
                if (triggerParam != null) {
                    running = true;
                    idleTimes = 0;
                    triggerLogIdSet.remove(triggerParam.getLogId());

                    String logFileName = JobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
                    JobContext jobContext = new JobContext(
                            triggerParam.getJobId(),
                            triggerParam.getExecutorParams(),
                            logFileName,
                            triggerParam.getBroadcastIndex(),
                            triggerParam.getBroadcastTotal());

                    JobContext.setJobContext(jobContext);

                    DefaultJobLog.log("<br>----------- 任务开始执行 -----------<br>----------- 参数: " + jobContext.getJobParam());

                    if (triggerParam.getExecutorTimeout() > 0) {
                        Thread futureThread = null;
                        try {
                            FutureTask<Boolean> futureTask = new FutureTask<>(() -> {
                                JobContext.setJobContext(jobContext);
                                handler.execute();
                                return true;
                            });
                            futureThread = new Thread(futureTask);
                            futureThread.start();
                            futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            DefaultJobLog.log("<br>----------- 任务执行超时 -----------");
                            DefaultJobLog.log("超时异常: " + e.getMessage());
                        } finally {
                            if (futureThread != null) {
                                futureThread.interrupt();
                            }
                        }
                    } else {
                        handler.execute();
                    }

                    if (JobContext.getJobContext().getHandleCode() <= 0) {
                        DefaultJobLog.log("<br>----------- 警告: 任务处理结果未设置 -----------");
                    } else {
                        String tempHandleMsg = JobContext.getJobContext().getHandleMsg();
                        tempHandleMsg = (tempHandleMsg != null && tempHandleMsg.length() > 50000)
                                ? tempHandleMsg.substring(0, 50000).concat("...")
                                : tempHandleMsg;
                        JobContext.getJobContext().setHandleMsg(tempHandleMsg);
                    }
                    DefaultJobLog.log("<br>----------- 任务执行完成 -----------<br>----------- 结果: 状态码="
                            + JobContext.getJobContext().getHandleCode()
                            + ", 消息="
                            + JobContext.getJobContext().getHandleMsg());

                } else {
                    if (idleTimes > 30 && triggerQueue.isEmpty()) {
                        JobThreadFactory.removeJobThread(jobId, "任务空闲次数超过限制");
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    DefaultJobLog.log("<br>----------- 任务线程被停止, 原因: " + stopReason);
                }

                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                String errorMsg = stringWriter.toString();

                DefaultJobLog.log("异常信息: " + errorMsg);
                DefaultJobLog.log("<br>----------- 任务执行异常结束 -----------");
            }
        }

        // 销毁
        try {
            handler.destroy();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        log.info(">>>>>>>>>>> 任务线程已停止, 线程={}", Thread.currentThread().getName());
    }
}
