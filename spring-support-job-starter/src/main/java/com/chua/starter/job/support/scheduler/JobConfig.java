package com.chua.starter.job.support.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.entity.MonitorJob;
import com.chua.starter.job.support.entity.MonitorJobLog;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.mapper.MonitorJobLogMapper;
import com.chua.starter.job.support.mapper.MonitorJobMapper;
import com.chua.starter.job.support.thread.JobContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 作业配置管理器
 * <p>
 * 采用单例模式，负责管理任务调度的核心配置和数据访问。
 * 主要功能包括：
 * <ul>
 *     <li>管理JobProperties配置</li>
 *     <li>提供任务和日志的数据库操作</li>
 *     <li>提供本地任务执行能力</li>
 *     <li>提供分布式锁支持（当前为本地锁实现）</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Slf4j
public class JobConfig {

    /**
     * 单例实例
     */
    private static final JobConfig INSTANCE = new JobConfig();

    /**
     * 任务配置属性
     */
    private JobProperties jobProperties;

    /**
     * Spring应用上下文
     */
    private ApplicationContext applicationContext;

    /**
     * 任务Mapper
     */
    private MonitorJobMapper monitorJobMapper;

    /**
     * 任务日志Mapper
     */
    private MonitorJobLogMapper monitorJobLogMapper;

    /**
     * 本地锁，用于保证调度线程安全（单机版本）
     */
    private final ReentrantLock localLock = new ReentrantLock();

    /**
     * 私有构造函数，防止外部实例化
     */
    private JobConfig() {
    }

    /**
     * 获取单例实例
     *
     * @return JobConfig实例
     */
    public static JobConfig getInstance() {
        return INSTANCE;
    }

    /**
     * 注册任务配置属性
     *
     * @param jobProperties 任务配置属性
     */
    public void register(JobProperties jobProperties) {
        this.jobProperties = jobProperties;
        log.info("JobConfig: 任务配置属性已注册");
    }

    /**
     * 获取快速最大触发池
     *
     * @return int
     */
    public int getTriggerPoolFastMax() {
        return null == jobProperties ? 200 : jobProperties.getTriggerPoolFastMax();
    }

    /**
     * 获取触发池慢速最大值
     *
     * @return int
     */
    public int getTriggerPoolSlowMax() {
        return null == jobProperties ? 100 : jobProperties.getTriggerPoolSlowMax();
    }

    /**
     * 通过作业ID加载监控作业。
     *
     * @param jobId 作业ID
     * @return 返回MonitorJob对象，如果找不到则返回null。
     */
    public MonitorJob loadById(int jobId) {
        return monitorJobMapper.selectById(jobId);
    }

    /**
     * 注册应用上下文。
     *
     * @param applicationContext 应用上下文实例
     */
    public void register(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.monitorJobMapper = applicationContext.getBean(MonitorJobMapper.class);
        this.monitorJobLogMapper = applicationContext.getBean(MonitorJobLogMapper.class);
        log.info("JobConfig: Spring应用上下文已注册");
    }

    /**
     * 保存监控作业日志。
     *
     * @param jobLog 监控作业日志对象
     */
    public void saveLog(MonitorJobLog jobLog) {
        monitorJobLogMapper.insert(jobLog);
    }

    /**
     * 更新监控作业日志。
     *
     * @param jobLog 监控作业日志对象
     */
    public void updateLog(MonitorJobLog jobLog) {
        monitorJobLogMapper.updateById(jobLog);
    }

    /**
     * 执行本地任务
     *
     * @param jobLog    任务日志
     * @param jobInfo   任务信息
     * @return 执行结果
     */
    public void runLocal(MonitorJobLog jobLog, MonitorJob jobInfo) {
        String handlerName = jobInfo.getJobExecuteBean();
        JobHandler handler = JobHandlerFactory.getInstance().get(handlerName);

        // 检查处理器是否存在
        if (handler == null) {
            log.warn("任务处理器未找到: {}", handlerName);
            jobLog.setJobLogExecuteCode("FAILURE");
            jobLog.setJobLogTriggerMsg("处理器未找到: " + handlerName);
            updateLog(jobLog);
            return;
        }

        // 设置任务执行上下文
        JobContext context = new JobContext(
                jobInfo.getJobId(),
                jobInfo.getJobExecuteParam(),
                null,
                0,
                1
        );
        JobContext.setJobContext(context);

        long startTime = System.currentTimeMillis();
        try {
            // 执行任务
            log.debug("开始执行本地任务: {}", handlerName);
            handler.execute();
            long cost = System.currentTimeMillis() - startTime;

            // 记录成功结果
            jobLog.setJobLogExecuteCode("SUCCESS");
            jobLog.setJobLogCost(BigDecimal.valueOf(cost));
            jobLog.setJobLogTriggerMsg("执行成功，耗时: " + cost + "ms");
            log.info("本地任务执行成功: {}, 耗时: {}ms", handlerName, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            // 记录失败结果
            jobLog.setJobLogExecuteCode("FAILURE");
            jobLog.setJobLogCost(BigDecimal.valueOf(cost));
            jobLog.setJobLogTriggerMsg("执行异常: " + e.getMessage());
            log.error("本地任务执行失败: {}, 错误: {}", handlerName, e.getMessage(), e);
        } finally {
            // 更新日志并清除上下文
            updateLog(jobLog);
            JobContext.removeJobContext();
        }
    }

    /**
     * 本地锁
     *
     * @param name 名称
     * @return JobLock
     */
    public JobLock lock(String name) {
        return new LocalJobLock(localLock);
    }

    /**
     * 查询待调度任务
     *
     * @param nextTime      下次触发时间
     * @param preReadCount  预读数量
     * @return 任务列表
     */
    public List<MonitorJob> scheduleJobQuery(long nextTime, int preReadCount) {
        return monitorJobMapper.selectPage(
                new Page<>(1, preReadCount),
                Wrappers.<MonitorJob>lambdaQuery()
                        .eq(MonitorJob::getJobTriggerStatus, 1)
                        .le(MonitorJob::getJobTriggerNextTime, nextTime)
                        .orderByDesc(MonitorJob::getJobId)
        ).getRecords();
    }

    /**
     * 更新任务调度信息
     *
     * @param jobInfo 任务信息
     */
    public void scheduleUpdate(MonitorJob jobInfo) {
        monitorJobMapper.updateById(jobInfo);
    }

    /**
     * 本地锁实现
     */
    public static class LocalJobLock implements JobLock {
        private final ReentrantLock lock;

        public LocalJobLock(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void lock() {
            lock.lock();
        }

        @Override
        public void unlock() {
            lock.unlock();
        }
    }

    /**
     * 锁接口
     */
    public interface JobLock {
        void lock();
        void unlock();
    }
}
