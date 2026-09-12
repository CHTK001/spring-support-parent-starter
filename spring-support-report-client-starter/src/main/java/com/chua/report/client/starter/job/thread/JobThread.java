package com.chua.report.client.starter.job.thread;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.client.starter.job.JobReporter;
import com.chua.report.client.starter.job.TriggerParam;
import com.chua.report.client.starter.job.handler.JobHandler;
import com.chua.report.client.starter.job.log.DefaultJobLog;
import com.chua.report.client.starter.job.log.JobFileAppender;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * 作业执行线程
 * <p>
   * 每个任务对应一个独立的作业thread实例，通过队列接收触发参数并执行任务。
 * 支持超时控制、重复触发检测、空闲自动回收等特性。
 * </p>
 *
   * @版本 1.0.0
 * @since 2024/03/11
 * @author CH
 */
@Slf4j
public class JobThread extends Thread {

    /**
      * 任务标识
     */
    private final int jobId;

    /**
     * 任务处理器
     */
    private final JobHandler handler;

    /**
     * 触发参数队列
     */
    private final LinkedBlockingQueue<TriggerParam> triggerQueue;

    /**
      * 触发日志标识集合，用于防止重复触发
     */
    private final Set<Long> triggerLogIdSet;

    /**
     * 停止标志
     */
    private volatile boolean toStop = false;

    /**
     * 停止原因
     */
    private String stopReason;

    /**
     * 是否正在执行任务
     */
    private boolean running = false;

    /**
     * 空闲次数计数器
     */
    private int idleTimes = 0;


    /**
     * 最大空闲次数，超过后线程将被回收
     */
    private static final int MAX_IDLE_TIMES = 30;

    /**
     * 构造函数
     *
     * @param jobId   任务标识
     * @param handler 任务处理器
     */
    public JobThread(int jobId, JobHandler handler) {
        this.jobId = jobId;
        this.handler = handler;
        this.triggerQueue = new LinkedBlockingQueue<>();
        this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<>());
        // 设置线程名称
        this.setName("JobThread-" + jobId + "-" + System.currentTimeMillis());
    }

    /**
     * 获取任务处理器
     *
     * @return 任务处理器实例
     */
    public JobHandler getHandler() {
        return handler;
    }

    /**
     * 将触发参数推送到队列
     *
     * @param triggerParam 触发参数
     * @return 推送结果
     */
    public ReturnResult<String> pushTriggerQueue(TriggerParam triggerParam) {
        // 防止重复触发
        if (triggerLogIdSet.contains(triggerParam.getLogId())) {
            log.warn("重复触发任务, 已忽略, 日志ID: {}", triggerParam.getLogId());
            return ReturnResult.illegal("重复触发任务, 日志ID: " + triggerParam.getLogId());
        }

        triggerLogIdSet.add(triggerParam.getLogId());
        triggerQueue.add(triggerParam);
        log.debug("任务已加入触发队列, 任务ID: {}, 日志ID: {}", jobId, triggerParam.getLogId());
        return ReturnResult.success();
    }

    /**
     * 停止任务线程
     * <p>
     * 通过共享变量方式通知线程停止，而不是使用Thread.interrupt。
     * 因为interrupt只能终止线程的阻塞状态，不能终止运行中的线程。
     * </p>
     *
     * @param stopReason 停止原因
     */
    public void toStop(String stopReason) {
        this.toStop = true;
        this.stopReason = stopReason;
        log.info("任务线程停止中, 任务ID: {}, 原因: {}", jobId, stopReason);
    }

    /**
     * 检查任务是否正在运行或队列中有待处理任务
     *
     * @return true表示正在运行或有待处理任务
     */
    public boolean isRunningOrHasQueue() {
        return running || !triggerQueue.isEmpty();
    }

    @Override
    /** 运行 */
    public void run() {

 // 初始化
        try {
            handler.init();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

 // 执行
        while(!toStop){
            running = false;
            idleTimes++;

            TriggerParam triggerParam = null;
            try {
 // 转为 检查 转为停止 标志, we need 循环, so wo cannot use 队列.取(), instand 的 poll(超时)
                triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
                if (triggerParam!=null) {
                    running = true;
                    idleTimes = 0;
                    triggerLogIdSet.remove(triggerParam.getLogId());

 // 日志 文件名, like "日志路径/yyyy-MM-dd/9999.日志"
                    String logFileName = JobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
                    JobContext xxlJobContext = new JobContext(
                            triggerParam.getJobId(),
                            triggerParam.getExecutorParams(),
                            logFileName,
                            triggerParam.getBroadcastIndex(),
                            triggerParam.getBroadcastTotal());

 // 初始化 作业 上下文
                    JobContext.setJobContext(xxlJobContext);

 // 执行
                    DefaultJobLog.log("<br>----------- job job execute start -----------<br>----------- Param:" + xxlJobContext.getJobParam());

                    if (triggerParam.getExecutorTimeout() > 0) {
 // 限制 超时
                        Thread futureThread = null;
                        try {
                            FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
                                @Override
                                /** 调用 */
                                public Boolean call() throws Exception {

 // 初始化 作业 上下文
                                    JobContext.setJobContext(xxlJobContext);

                                    handler.execute();
                                    return true;
                                }
                            });
                            futureThread = new Thread(futureTask);
                            futureThread.start();

                            Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
                            /**
                             * report作业。
                             * @param triggerParam trigger参数
                             * @param "SUCCESS" "成功"
                             * @param null 空
                             */
                            reportJob(triggerParam, "SUCCESS", null);

                        } catch (TimeoutException e) {

                            DefaultJobLog.log("<br>----------- job job execute timeout");
                            DefaultJobLog.log(e.getMessage());

 // 把手 结果
                            DefaultJobLog.log("job execute timeout ");

                            /**
                             * report作业。
                             * @param triggerParam trigger参数
                             * @param "FAILURE" "失败"
                             * @param " "
                             */
                            reportJob(triggerParam, "FAILURE", "job execute timeout ");

                        } finally {
                            futureThread.interrupt();
                        }
                    } else {
 // just 执行
                        handler.execute();
                    }

 // valid 执行 把手 数据
                    if (JobContext.getXxlJobContext().getHandleCode() <= 0) {
                        DefaultJobLog.log("job handle result lost.");
                    } else {
                        String tempHandleMsg = JobContext.getXxlJobContext().getHandleMsg();
                        tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000)
                                ?tempHandleMsg.substring(0, 50000).concat("...")
                                :tempHandleMsg;
                        JobContext.getXxlJobContext().setHandleMsg(tempHandleMsg);
                    }
                    DefaultJobLog.log("<br>----------- job job execute end(finish) -----------<br>----------- Result: handleCode="
                            + JobContext.getXxlJobContext().getHandleCode()
                            + ", handleMsg = "
                            + JobContext.getXxlJobContext().getHandleMsg()
                    );

                } else {
                    // 空闲次数超过限制，回收线程
                    if (idleTimes > MAX_IDLE_TIMES) {
                        // 避免并发触发导致任务丢失
                        if (triggerQueue.isEmpty()) {
                            JobThreadFactory.removeJobThread(jobId, "空闲次数超过限制，回收线程");
                        }
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    DefaultJobLog.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
                }

 // 把手 结果
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                String errorMsg = stringWriter.toString();

                /**
                 * report作业。
                 * @param triggerParam trigger参数
                 * @param "FAILURE" "失败"
                 * @param errorMsg 错误msg
                 */
                reportJob(triggerParam, "FAILURE", errorMsg);

                DefaultJobLog.log(errorMsg);

                DefaultJobLog.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- job job execute end(error) -----------");
            }
        }


 // 销毁
        try {
            handler.destroy();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        log.info("任务线程已停止, 任务ID: {}", jobId);
    }

    /**
     * report作业
     *
     * @param triggerParam trigger参数
     * @param status 状态
     * @param message 消息
     */
    private void reportJob(TriggerParam triggerParam, String status, String message) {
        JobReporter.getInstance().report(triggerParam.getJobId(), triggerParam.getLogId(), status, message);
    }
}
