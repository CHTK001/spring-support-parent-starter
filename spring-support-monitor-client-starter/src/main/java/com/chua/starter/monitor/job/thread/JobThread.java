package com.chua.starter.monitor.job.thread;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.job.handler.JobHandler;
import com.chua.starter.monitor.job.log.JobFileAppender;
import com.chua.starter.monitor.job.log.JobLog;
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
public class JobThread extends Thread{

    private final int jobId;
    private JobHandler handler;
    private LinkedBlockingQueue<TriggerParam> triggerQueue;
    private Set<Long> triggerLogIdSet;		// avoid repeat trigger for the same TRIGGER_LOG_ID

    private volatile boolean toStop = false;
    private String stopReason;

    private boolean running = false;    // if running job
    private int idleTimes = 0;			// idel times


    public JobThread(int jobId, JobHandler handler) {
        this.jobId = jobId;
        this.handler = handler;
        this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
        this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<Long>());

        // assign job thread name
        this.setName("job, JobThread-"+jobId+"-"+System.currentTimeMillis());
    }
    public JobHandler getHandler() {
        return handler;
    }

    /**
     * new trigger to queue
     *
     * @param triggerParam
     * @return
     */
    public ReturnResult<String> pushTriggerQueue(TriggerParam triggerParam) {
        // avoid repeat
        if (triggerLogIdSet.contains(triggerParam.getLogId())) {
            log.info(">>>>>>>>>>> repeate trigger job, logId:{}", triggerParam.getLogId());
            return ReturnResult.illegal("repeate trigger job, logId:" + triggerParam.getLogId());
        }

        triggerLogIdSet.add(triggerParam.getLogId());
        triggerQueue.add(triggerParam);
        return ReturnResult.SUCCESS;
    }

    /**
     * kill job thread
     *
     * @param stopReason
     */
    public void toStop(String stopReason) {
        /**
         * Thread.interrupt只支持终止线程的阻塞状态(wait、join、sleep)，
         * 在阻塞出抛出InterruptedException异常,但是并不会终止运行的线程本身；
         * 所以需要注意，此处彻底销毁本线程，需要通过共享变量方式；
         */
        this.toStop = true;
        this.stopReason = stopReason;
    }

    /**
     * is running job
     * @return
     */
    public boolean isRunningOrHasQueue() {
        return running || !triggerQueue.isEmpty();
    }

    @Override
    public void run() {

        // init
        try {
            handler.init();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        // execute
        while(!toStop){
            running = false;
            idleTimes++;

            TriggerParam triggerParam = null;
            try {
                // to check toStop signal, we need cycle, so wo cannot use queue.take(), instand of poll(timeout)
                triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
                if (triggerParam!=null) {
                    running = true;
                    idleTimes = 0;
                    triggerLogIdSet.remove(triggerParam.getLogId());

                    // log filename, like "logPath/yyyy-MM-dd/9999.log"
                    String logFileName = JobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
                    JobContext xxlJobContext = new JobContext(
                            triggerParam.getJobId(),
                            triggerParam.getExecutorParams(),
                            logFileName,
                            triggerParam.getBroadcastIndex(),
                            triggerParam.getBroadcastTotal());

                    // init job context
                    JobContext.setXxlJobContext(xxlJobContext);

                    // execute
                    JobLog.getDefault().info("<br>----------- xxl-job job execute start -----------<br>----------- Param:" + xxlJobContext.getJobParam());

                    if (triggerParam.getExecutorTimeout() > 0) {
                        // limit timeout
                        Thread futureThread = null;
                        try {
                            FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {

                                    // init job context
                                    JobContext.setXxlJobContext(xxlJobContext);

                                    handler.execute();
                                    return true;
                                }
                            });
                            futureThread = new Thread(futureTask);
                            futureThread.start();

                            Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
                        } catch (TimeoutException e) {

                            JobLog.getDefault().info("<br>----------- xxl-job job execute timeout");
                            JobLog.getDefault().info("{}", e);

                            // handle result
                            JobLog.getDefault().error("job execute timeout ");
                        } finally {
                            futureThread.interrupt();
                        }
                    } else {
                        // just execute
                        handler.execute();
                    }

                    // valid execute handle data
                    if (JobContext.getXxlJobContext().getHandleCode() <= 0) {
                        JobLog.getDefault().error("job handle result lost.");
                    } else {
                        String tempHandleMsg = JobContext.getXxlJobContext().getHandleMsg();
                        tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000)
                                ?tempHandleMsg.substring(0, 50000).concat("...")
                                :tempHandleMsg;
                        JobContext.getXxlJobContext().setHandleMsg(tempHandleMsg);
                    }
                    JobLog.getDefault().info("<br>----------- xxl-job job execute end(finish) -----------<br>----------- Result: handleCode="
                            + JobContext.getXxlJobContext().getHandleCode()
                            + ", handleMsg = "
                            + JobContext.getXxlJobContext().getHandleMsg()
                    );

                } else {
                    if (idleTimes > 30) {
                        if(triggerQueue.size() == 0) {	// avoid concurrent trigger causes jobId-lost
                            JobThreadFactory.removeJobThread(jobId, "excutor idel times over limit.");
                        }
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    JobLog.getDefault().info("<br>----------- JobThread toStop, stopReason:" + stopReason);
                }

                // handle result
                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                String errorMsg = stringWriter.toString();

                JobLog.getDefault().error(errorMsg);

                JobLog.getDefault().info("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- xxl-job job execute end(error) -----------");
            }
        }


        // destroy
        try {
            handler.destroy();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }

        log.info(">>>>>>>>>>> xxl-job JobThread stoped, hashCode:{}", Thread.currentThread());
    }
}
