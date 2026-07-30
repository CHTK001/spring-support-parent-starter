package com.chua.starter.job.support.remote;

import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.DefaultJobLog;
import com.chua.starter.job.support.log.JobDetailLogger;
import com.chua.starter.job.support.log.JobFileAppender;
import com.chua.starter.job.support.scheduler.JobConfig;
import com.chua.starter.job.support.scheduler.JobTriggerPoolHelper;
import com.chua.starter.job.support.scheduler.TriggerTypeEnum;
import com.chua.starter.job.support.thread.JobContext;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 远程执行器下发服务。
 * <p>
 * 调度中心把任务请求推到业务服务后，这里负责把请求转换成当前进程里的
 * {@link TriggerParam}，再复用现有的 {@link JobThread} 串行执行模型。
 * </p>
 */
public class RemoteJobExecutorDispatchService {

    private static final String EXECUTE_CODE_PADDING = "PADDING";
    private static final String EXECUTE_CODE_SUCCESS = "SUCCESS";
    private static final String EXECUTE_CODE_FAILURE = "FAILURE";

    /**
     * 远程直连执行模式下，仍按 jobId 串行化，避免同一个 handler 被并发重复触发。
     */
    private final ConcurrentMap<Integer, ReentrantLock> directExecutionLocks = new ConcurrentHashMap<>();

    /**
     * 把中心侧请求下发到本地执行线程。
     */
    public RemoteJobTriggerResponse dispatch(RemoteJobTriggerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("远程任务请求不能为空");
        }

        String executorHandler = requireText(request.getExecutorHandler(), "执行器处理器不能为空");
        JobHandler handler = JobHandlerFactory.getInstance().get(executorHandler);
        if (handler == null) {
            throw new IllegalArgumentException("执行器处理器不存在: " + executorHandler);
        }

        int jobId = resolveJobId(request, executorHandler);
        RemoteJobTriggerResponse response = new RemoteJobTriggerResponse();
        response.setJobId(jobId);
        response.setJobNo(trimToNull(request.getJobNo()));
        response.setExecutorHandler(executorHandler);
        response.setLogId(resolveLogId(request));
        response.setLogNo(resolveLogNo(request, response.getLogId()));

        if (tryDispatchWithLocalScheduler(request, executorHandler, response)) {
            return response;
        }

        DirectExecutionResult result = executeDirectly(request, jobId, handler, response.getLogId());
        response.setAccepted(true);
        response.setLogHandledByExecutor(false);
        response.setExecuteCode(result.executeCode());
        response.setExecuteMessage(result.executeMessage());
        response.setCostMs(result.costMs());
        response.setMessage(result.summaryMessage());
        return response;
    }

    private boolean tryDispatchWithLocalScheduler(RemoteJobTriggerRequest request,
                                                  String executorHandler,
                                                  RemoteJobTriggerResponse response) {
        if (request.getJobId() == null || request.getJobId() <= 0) {
            return false;
        }
        try {
            SysJob jobInfo = JobConfig.getInstance().loadById(request.getJobId());
            if (jobInfo == null) {
                return false;
            }
            if (!executorHandler.equals(trimToNull(jobInfo.getJobExecuteBean()))) {
                return false;
            }
            JobTriggerPoolHelper.trigger(
                    request.getJobId(),
                    resolveTriggerType(request.getTriggerType()),
                    -1,
                    null,
                    request.getExecutorParams()
            );
            response.setAccepted(true);
            response.setLogHandledByExecutor(true);
            response.setExecuteCode(EXECUTE_CODE_PADDING);
            response.setExecuteMessage("任务已通过本地调度链路异步执行");
            response.setCostMs(0L);
            response.setMessage("任务已通过本地调度链路下发");
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private DirectExecutionResult executeDirectly(RemoteJobTriggerRequest request,
                                                  int jobId,
                                                  JobHandler handler,
                                                  long logId) {
        ReentrantLock lock = directExecutionLocks.computeIfAbsent(jobId, key -> new ReentrantLock());
        lock.lock();
        try {
            long startTime = System.currentTimeMillis();
            long triggerTime = request.getLogDateTime() == null ? startTime : request.getLogDateTime();
            JobContext jobContext = createJobContext(request, jobId, logId, triggerTime);
            JobContext.setJobContext(jobContext);
            jobContext.getAttributes().put("handler", trimToNull(request.getExecutorHandler()));
            jobContext.getAttributes().put("address", trimToNull(request.getRemoteExecutorAddress()));

            DefaultJobLog.log("<br>----------- 远程任务开始执行 -----------<br>----------- 参数: " + jobContext.getJobParam());
            JobDetailLogger.info("远程任务开始执行，handler=" + request.getExecutorHandler(), "START", 5);
            try {
                executeHandler(handler, jobContext, request.getExecutorTimeout());
            } catch (Throwable e) {
                String stackTrace = stackTrace(e);
                jobContext.setFail("执行异常: " + safeMessage(e));
                DefaultJobLog.log("异常信息: " + stackTrace);
                DefaultJobLog.log("<br>----------- 远程任务执行异常结束 -----------");
            }

            long cost = System.currentTimeMillis() - startTime;
            String executeCode = resolveExecuteCode(jobContext);
            String executeMessage = resolveExecuteMessage(jobContext, executeCode, cost);
            DefaultJobLog.log("<br>----------- 远程任务执行完成 -----------<br>----------- 结果: 状态="
                    + executeCode + ", 消息=" + executeMessage + ", 耗时=" + cost + "ms");
            JobDetailLogger.info("远程任务执行完成，状态=" + executeCode + "，耗时=" + cost + "ms", "END", 100);
            return new DirectExecutionResult(
                    executeCode,
                    executeMessage,
                    cost,
                    EXECUTE_CODE_SUCCESS.equals(executeCode) ? "任务执行成功" : "任务执行失败"
            );
        } finally {
            JobContext.removeJobContext();
            lock.unlock();
        }
    }

    private JobContext createJobContext(RemoteJobTriggerRequest request, int jobId, long logId, long triggerTime) {
        String logNo = resolveLogNo(request, logId);
        String logFileName = JobFileAppender.makeLogFileName(new Date(triggerTime), trimToNull(request.getJobNo()), logNo);
        return new JobContext(
                logId,
                jobId,
                trimToNull(request.getJobNo()),
                logNo,
                trimToNull(request.getExecutorParams()),
                logFileName,
                Math.max(0, request.getBroadcastIndex() == null ? 0 : request.getBroadcastIndex()),
                Math.max(1, request.getBroadcastTotal() == null ? 1 : request.getBroadcastTotal())
        );
    }

    private void executeHandler(JobHandler handler, JobContext jobContext, Integer timeoutSeconds) throws Throwable {
        int safeTimeout = Math.max(0, timeoutSeconds == null ? 0 : timeoutSeconds);
        if (safeTimeout <= 0) {
            handler.execute();
            return;
        }

        FutureTask<Boolean> futureTask = new FutureTask<>(() -> {
            JobContext.setJobContext(jobContext);
            handler.execute();
            return true;
        });
        Thread futureThread = new Thread(futureTask, "job-remote-executor-" + jobContext.getJobId());
        futureThread.start();
        try {
            futureTask.get(safeTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            jobContext.setTimeout("执行超时，限制 " + safeTimeout + " 秒");
            DefaultJobLog.log("<br>----------- 远程任务执行超时 -----------");
            throw e;
        } finally {
            futureThread.interrupt();
        }
    }

    private long resolveLogId(RemoteJobTriggerRequest request) {
        return request.getLogId() == null ? System.currentTimeMillis() : request.getLogId();
    }

    private String resolveLogNo(RemoteJobTriggerRequest request, long logId) {
        String logNo = trimToNull(request.getLogNo());
        if (StringUtils.hasText(logNo)) {
            return logNo;
        }
        return "JOBLOG" + logId;
    }

    private String resolveExecuteCode(JobContext jobContext) {
        if (jobContext == null) {
            return EXECUTE_CODE_FAILURE;
        }
        if (jobContext.getHandleCode() == JobContext.HANDLE_CODE_SUCCESS) {
            return EXECUTE_CODE_SUCCESS;
        }
        return EXECUTE_CODE_FAILURE;
    }

    private String resolveExecuteMessage(JobContext jobContext, String executeCode, long cost) {
        String message = jobContext == null ? null : trimToNull(jobContext.getHandleMsg());
        if (StringUtils.hasText(message)) {
            return message;
        }
        if (EXECUTE_CODE_SUCCESS.equals(executeCode)) {
            return "执行成功，耗时: " + cost + "ms";
        }
        if (jobContext != null && jobContext.getHandleCode() == JobContext.HANDLE_CODE_TIMEOUT) {
            return "执行超时";
        }
        return "执行失败";
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : trimToNull(throwable.getMessage());
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private String stackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private int resolveJobId(RemoteJobTriggerRequest request, String executorHandler) {
        if (request.getJobId() != null && request.getJobId() > 0) {
            return request.getJobId();
        }
        String jobName = trimToNull(request.getJobName());
        return Math.abs(Objects.hash(executorHandler, jobName == null ? "" : jobName));
    }

    private TriggerTypeEnum resolveTriggerType(String triggerType) {
        String normalized = trimToNull(triggerType);
        if (normalized == null) {
            return TriggerTypeEnum.API;
        }
        try {
            return TriggerTypeEnum.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return TriggerTypeEnum.API;
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record DirectExecutionResult(String executeCode,
                                         String executeMessage,
                                         long costMs,
                                         String summaryMessage) {
    }
}
