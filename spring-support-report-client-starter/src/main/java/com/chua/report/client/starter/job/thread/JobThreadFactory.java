package com.chua.report.client.starter.job.thread;

import com.chua.report.client.starter.job.handler.JobHandler;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 作业线程工厂
 * <p>
 * 采用单例模式，负责管理所有JobThread实例的生命周期。
 * 使用ConcurrentHashMap保证线程安全。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
public class JobThreadFactory {

    /**
     * 单例实例
     */
    private static final JobThreadFactory INSTANCE = new JobThreadFactory();

    /**
     * 作业线程仓库
     * key: 任务ID
     * value: JobThread实例
     */
    private static final ConcurrentMap<Integer, JobThread> JOB_THREAD_REPOSITORY = new ConcurrentHashMap<>();

    /**
     * 私有构造函数
     */
    private JobThreadFactory() {
    }

    /**
     * 注册作业线程
     * <p>
     * 创建新的JobThread并启动，如果已存在同ID的线程则先停止旧线程。
     * </p>
     *
     * @param jobId           任务ID
     * @param handler         任务处理器
     * @param removeOldReason 删除旧线程的原因
     * @return 新创建的JobThread实例
     */
    public static JobThread registJobThread(int jobId, JobHandler handler, String removeOldReason) {
        JobThread newJobThread = new JobThread(jobId, handler);
        newJobThread.start();
        log.info("作业线程注册成功, 任务ID: {}, 处理器: {}", jobId, handler);

        // 自动注入Spring Bean
        SpringBeanUtils.autowireBean(newJobThread);

        // 替换旧线程
        JobThread oldJobThread = JOB_THREAD_REPOSITORY.put(jobId, newJobThread);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
            log.debug("已停止旧作业线程, 任务ID: {}, 原因: {}", jobId, removeOldReason);
        }

        return newJobThread;
    }

    /**
     * 删除作业线程
     *
     * @param jobId           任务ID
     * @param removeOldReason 删除原因
     * @return 被删除的JobThread实例，如果不存在返回null
     */
    public static JobThread removeJobThread(int jobId, String removeOldReason) {
        JobThread oldJobThread = JOB_THREAD_REPOSITORY.remove(jobId);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
            log.info("作业线程已删除, 任务ID: {}, 原因: {}", jobId, removeOldReason);
            return oldJobThread;
        }
        return null;
    }

    /**
     * 加载作业线程
     *
     * @param jobId 任务ID
     * @return JobThread实例，如果不存在返回null
     */
    public static JobThread loadJobThread(int jobId) {
        return JOB_THREAD_REPOSITORY.get(jobId);
    }

    /**
     * 获取工厂单例实例
     *
     * @return JobThreadFactory实例
     */
    public static JobThreadFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 获取当前活跃的作业线程数量
     *
     * @return 线程数量
     */
    public static int size() {
        return JOB_THREAD_REPOSITORY.size();
    }
}
