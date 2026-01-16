package com.chua.starter.job.support.thread;

import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.job.support.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 任务线程工厂
 * <p>
 * 采用单例模式，管理所有任务执行线程的生命周期。
 * 每个任务ID对应一个{@link JobThread}实例，确保任务串行执行。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>{@link #registJobThread(int, JobHandler, String)} - 注册新的任务线程</li>
 *     <li>{@link #removeJobThread(int, String)} - 移除并停止任务线程</li>
 *     <li>{@link #loadJobThread(int)} - 加载已存在的任务线程</li>
 * </ul>
 *
 * <h3>线程管理策略</h3>
 * <ul>
 *     <li><b>替换策略</b> - 注册新线程时，如果已存在旧线程，会先停止旧线程</li>
 *     <li><b>自动注入</b> - 新创建的线程会自动进行Spring依赖注入</li>
 *     <li><b>优雅停止</b> - 移除线程时会等待当前任务执行完成</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册任务线程
 * JobThread thread = JobThreadFactory.registJobThread(jobId, handler, "新任务处理器");
 *
 * // 加载已存在的线程
 * JobThread existing = JobThreadFactory.loadJobThread(jobId);
 *
 * // 移除任务线程
 * JobThreadFactory.removeJobThread(jobId, "任务已禁用");
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see JobThread
 * @see JobHandler
 */
@Slf4j
public class JobThreadFactory {

    /** 工厂单例 */
    private static final JobThreadFactory INSTANCE = new JobThreadFactory();
    
    /** 
     * 任务线程编存器
     * key: 任务ID
     * value: 对应的JobThread实例
     */
    private static final ConcurrentMap<Integer, JobThread> jobThreadRepository = new ConcurrentHashMap<>();

    /**
     * 注册作业线程
     *
     * @param jobId           作业ID
     * @param handler         处理程序
     * @param removeOldReason 删除旧线程的原因
     * @return 作业线程
     */
    public static JobThread registJobThread(int jobId, JobHandler handler, String removeOldReason) {
        JobThread newJobThread = new JobThread(jobId, handler);
        newJobThread.start();
        log.info(">>>>>>>>>>> 注册任务线程成功, jobId={}, handler={}", jobId, handler);
        SpringBeanUtils.autowireBean(newJobThread);
        JobThread oldJobThread = jobThreadRepository.put(jobId, newJobThread);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }
        return newJobThread;
    }

    /**
     * 删除作业线程
     *
     * @param jobId           作业ID
     * @param removeOldReason 删除原因
     * @return 旧的作业线程
     */
    public static JobThread removeJobThread(int jobId, String removeOldReason) {
        JobThread oldJobThread = jobThreadRepository.remove(jobId);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
            return oldJobThread;
        }
        return null;
    }

    /**
     * 加载作业线程
     *
     * @param jobId 作业ID
     * @return 作业线程
     */
    public static JobThread loadJobThread(int jobId) {
        return jobThreadRepository.get(jobId);
    }

    /**
     * 获取实例
     *
     * @return 工厂实例
     */
    public static JobThreadFactory getInstance() {
        return INSTANCE;
    }
}
