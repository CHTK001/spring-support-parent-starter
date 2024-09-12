package com.chua.report.server.starter.job.lock;

/**
 * 作业锁定
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
public interface JobLock extends AutoCloseable{
    /**
     * 锁
     */
    void lock();

    /**
     * 解锁
     */
    void unlock();
}
