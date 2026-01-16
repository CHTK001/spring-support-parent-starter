package com.chua.starter.job.support.service;

import com.chua.starter.job.support.entity.SysJob;

import java.util.List;

/**
 * Job动态配置服务接口
 * <p>
 * 提供给外部模块动态配置定时任务的能力。
 * 支持任务的创建、更新、删除、启停等操作。
 * </p>
 *
 * @author CH
 * @since 2024/12/20
 */
public interface JobDynamicConfigService {

    /**
     * 注册或更新任务
     * 如果任务名称已存在则更新，否则创建新任务
     *
     * @param jobName      任务名称（唯一标识）
     * @param cronExpr     Cron表达式
     * @param beanName     执行器Bean名称
     * @param param        执行参数
     * @param desc         任务描述
     * @param autoStart    是否自动启动
     * @return 任务ID
     */
    Integer registerOrUpdateJob(String jobName, String cronExpr, String beanName, 
                                String param, String desc, boolean autoStart);

    /**
     * 创建任务
     *
     * @param job 任务配置
     * @return 任务ID
     */
    Integer createJob(SysJob job);

    /**
     * 更新任务
     *
     * @param job 任务配置
     * @return 是否成功
     */
    boolean updateJob(SysJob job);

    /**
     * 删除任务
     *
     * @param jobId 任务ID
     * @return 是否成功
     */
    boolean deleteJob(Integer jobId);

    /**
     * 根据名称删除任务
     *
     * @param jobName 任务名称
     * @return 是否成功
     */
    boolean deleteJobByName(String jobName);

    /**
     * 启动任务
     *
     * @param jobId 任务ID
     * @return 是否成功
     */
    boolean startJob(Integer jobId);

    /**
     * 停止任务
     *
     * @param jobId 任务ID
     * @return 是否成功
     */
    boolean stopJob(Integer jobId);

    /**
     * 根据名称获取任务
     *
     * @param jobName 任务名称
     * @return 任务配置
     */
    SysJob getJobByName(String jobName);

    /**
     * 根据ID获取任务
     *
     * @param jobId 任务ID
     * @return 任务配置
     */
    SysJob getJobById(Integer jobId);

    /**
     * 获取所有任务
     *
     * @return 任务列表
     */
    List<SysJob> getAllJobs();

    /**
     * 根据Bean名称获取任务列表
     *
     * @param beanName Bean名称
     * @return 任务列表
     */
    List<SysJob> getJobsByBeanName(String beanName);

    /**
     * 手动触发任务执行
     *
     * @param jobId 任务ID
     * @param param 执行参数（可选，为null时使用任务配置的参数）
     * @return 是否成功触发
     */
    boolean triggerJob(Integer jobId, String param);

    /**
     * 更新任务Cron表达式
     *
     * @param jobId    任务ID
     * @param cronExpr Cron表达式
     * @return 是否成功
     */
    boolean updateJobCron(Integer jobId, String cronExpr);

    /**
     * 更新任务执行参数
     *
     * @param jobId 任务ID
     * @param param 执行参数
     * @return 是否成功
     */
    boolean updateJobParam(Integer jobId, String param);
}
