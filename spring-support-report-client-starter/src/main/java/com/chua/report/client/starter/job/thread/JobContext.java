package com.chua.report.client.starter.job.thread;

import lombok.Data;
import lombok.Getter;

/**
 * 作业执行上下文
 * <p>
 * 使用ThreadLocal存储当前线程的任务执行上下文信息。
 * 在任务执行过程中，可通过此类获取任务ID、参数、分片信息等。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Data
public class JobContext {

    /**
     * 线程本地存储，使用InheritableThreadLocal支持子线程继承
     */
    private static final InheritableThreadLocal<JobContext> CONTEXT_HOLDER = new InheritableThreadLocal<>();

    /**
     * 处理成功状态码
     */
    public static final int HANDLE_CODE_SUCCESS = 200;

    /**
     * 处理失败状态码
     */
    public static final int HANDLE_CODE_FAIL = 500;

    /**
     * 处理超时状态码
     */
    public static final int HANDLE_CODE_TIMEOUT = 502;

    /**
     * 任务ID
     */
    @Getter
    private final long jobId;

    /**
     * 任务执行参数
     */
    @Getter
    private final String jobParam;

    /**
     * 日志文件名
     */
    @Getter
    private final String jobLogFileName;

    /**
     * 分片索引（从0开始）
     */
    @Getter
    private final int shardIndex;

    /**
     * 分片总数
     */
    @Getter
    private final int shardTotal;

    /**
     * 处理状态码
     * 200: 成功
     * 500: 失败
     * 502: 超时
     */
    private int handleCode;

    /**
     * 处理结果消息
     */
    private String handleMsg;

    /**
     * 构造函数
     *
     * @param jobId          任务ID
     * @param jobParam       任务参数
     * @param jobLogFileName 日志文件名
     * @param shardIndex     分片索引
     * @param shardTotal     分片总数
     */
    public JobContext(long jobId, String jobParam, String jobLogFileName, int shardIndex, int shardTotal) {
        this.jobId = jobId;
        this.jobParam = jobParam;
        this.jobLogFileName = jobLogFileName;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.handleCode = HANDLE_CODE_SUCCESS;
    }

    /**
     * 设置当前线程的任务上下文
     *
     * @param jobContext 任务上下文
     */
    public static void setJobContext(JobContext jobContext) {
        CONTEXT_HOLDER.set(jobContext);
    }

    /**
     * 获取当前线程的任务上下文
     *
     * @return 任务上下文，如果不存在返回null
     */
    public static JobContext getXxlJobContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前线程的任务上下文
     */
    public static void removeJobContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 设置任务执行成功
     *
     * @param msg 成功消息
     */
    public void setSuccess(String msg) {
        this.handleCode = HANDLE_CODE_SUCCESS;
        this.handleMsg = msg;
    }

    /**
     * 设置任务执行失败
     *
     * @param msg 失败消息
     */
    public void setFail(String msg) {
        this.handleCode = HANDLE_CODE_FAIL;
        this.handleMsg = msg;
    }

    /**
     * 设置任务执行超时
     *
     * @param msg 超时消息
     */
    public void setTimeout(String msg) {
        this.handleCode = HANDLE_CODE_TIMEOUT;
        this.handleMsg = msg;
    }
}
