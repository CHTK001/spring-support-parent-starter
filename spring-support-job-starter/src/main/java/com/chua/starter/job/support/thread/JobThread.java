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
 * 作业线程
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
@SuppressWarnings("ALL")
public class JobThread extends Thread {

    private final int jobId;
    private final JobHandler handler;
    private final LinkedBlockingQueue<TriggerParam> triggerQueue;
    private final Set<Long> triggerLogIdSet;

    private volatile boolean toStop = false;
    private String stopReason;

    private boolean running = false;
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
            log.info(">>>>>>>>>>> 重复触发任务, logId:{}", triggerParam.getLogId());
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

                    DefaultJobLog.log("<br>----------- job execute start -----------<br>----------- Param:" + jobContext.getJobParam());

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
                            DefaultJobLog.log("<br>----------- job execute timeout");
                            DefaultJobLog.log(e.getMessage());
                            DefaultJobLog.log("任务执行超时");
                        } finally {
                            if (futureThread != null) {
                                futureThread.interrupt();
                            }
                        }
                    } else {
                        handler.execute();
                    }

                    if (JobContext.getJobContext().getHandleCode() <= 0) {
                        DefaultJobLog.log("任务处理结果丢失");
                    } else {
                        String tempHandleMsg = JobContext.getJobContext().getHandleMsg();
                        tempHandleMsg = (tempHandleMsg != null && tempHandleMsg.length() > 50000)
                                ? tempHandleMsg.substring(0, 50000).concat("...")
                                : tempHandleMsg;
                        JobContext.getJobContext().setHandleMsg(tempHandleMsg);
                    }
                    DefaultJobLog.log("<br>----------- job execute end(finish) -----------<br>----------- Result: handleCode="
                            + JobContext.getJobContext().getHandleCode()
                            + ", handleMsg = "
                            + JobContext.getJobContext().getHandleMsg());

                } else {
                    if (idleTimes > 30 && triggerQueue.isEmpty()) {
                        JobThreadFactory.removeJobThread(jobId, "任务空闲次数超过限制");
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    DefaultJobLog.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
                }

                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                String errorMsg = stringWriter.toString();

                DefaultJobLog.log(errorMsg);
                DefaultJobLog.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- job execute end(error) -----------");
            }
        }

        // 销毁
        try {
            handler.destroy();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        log.info(">>>>>>>>>>> job JobThread stoped, hashCode:{}", Thread.currentThread());
    }
}
