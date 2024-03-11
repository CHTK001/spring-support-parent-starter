package com.chua.starter.monitor.server.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.monitor.job.TriggerParam;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorJob;
import com.chua.starter.monitor.server.entity.MonitorJobLog;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.job.lock.JobLock;
import com.chua.starter.monitor.server.job.lock.RedisLock;
import com.chua.starter.monitor.server.job.route.ExecutorRouter;
import com.chua.starter.monitor.server.properties.JobProperties;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorJobLogService;
import com.chua.starter.monitor.server.service.MonitorJobService;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 作业配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
public class JobConfig {

    private static final JobConfig INSTANCE = new JobConfig();
    private JobProperties jobProperties;
    private ApplicationContext applicationContext;

    private MonitorJobService monitorJobService;
    private MonitorJobLogService monitorJobLogService;
    private MonitorServerFactory MonitorServerFactory;
    private MonitorAppService monitorAppService;
    private RedissonClient redissonClient;

    public static JobConfig getInstance() {
        return INSTANCE;
    }


    public void register(JobProperties jobProperties) {
        this.jobProperties = jobProperties;
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
        return monitorJobService.getById(jobId);
    }

    /**
     * 注册应用上下文。
     *
     * @param applicationContext 应用上下文实例
     */
    public void register(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        // 从应用上下文中获取各种服务的实例
        this.MonitorServerFactory = applicationContext.getBean(MonitorServerFactory.class);
        this.monitorJobService = applicationContext.getBean(MonitorJobService.class);
        this.monitorAppService = applicationContext.getBean(MonitorAppService.class);
        this.monitorJobLogService = applicationContext.getBean(MonitorJobLogService.class);
        this.redissonClient = applicationContext.getBean(RedissonClient.class);
    }

    /**
     * 保存监控作业日志。
     *
     * @param jobLog 监控作业日志对象
     */
    public void saveLog(MonitorJobLog jobLog) {
        monitorJobLogService.save(jobLog);
    }

    /**
     * 更新监控作业日志。
     *
     * @param jobLog 监控作业日志对象
     */
    public void updateLog(MonitorJobLog jobLog) {
        monitorJobLogService.updateById(jobLog);
    }

    /**
     * 根据触发参数和监控作业信息获取执行地址列表。
     *
     * @param triggerParam 触发参数
     * @param jobInfo 监控作业信息
     * @return 返回MonitorRequest对象列表，如果没有可用的执行地址则返回null。
     */
    public List<MonitorRequest> getAddress(TriggerParam triggerParam, MonitorJob jobInfo) {
        List<MonitorRequest> serviceInstance = MonitorServerFactory.getHeart(jobInfo.getJobApp());
        if(CollectionUtils.isEmpty(serviceInstance)) {
            return null;
        }
        ExecutorRouter executorRouter = ServiceProvider.of(ExecutorRouter.class).getNewExtension(jobInfo.getJobExecuteRouter());
        if(null == executorRouter) {
            return null;
        }
        // 路由选择合适的执行地址
        return executorRouter.route(triggerParam, serviceInstance);
    }

    /**
     * 执行监控任务。
     *
     * @param address 执行地址列表
     * @param triggerParam 触发参数
     * @return 返回执行结果
     */
    public ReturnResult<String> run( List<MonitorRequest> address, TriggerParam triggerParam) {
        // 遍历地址列表，发送触发参数
        for (MonitorRequest monitorRequest : address) {
            monitorAppService.upload(null, monitorRequest, Json.toJSONString(triggerParam), ModuleType.JOB, CommandType.REQUEST);
        }
        // 执行成功
        return ReturnResult.success();
    }

    /**
     * 锁
     *
     * @param name 名称
     */
    public JobLock lock(String name) {
        return new RedisLock(redissonClient.getLock(name));
    }

    public List<MonitorJob> scheduleJobQuery(long l, int preReadCount) {
        return monitorJobService.page(new Page<MonitorJob>(1, preReadCount), Wrappers.<MonitorJob>lambdaQuery().eq(MonitorJob::getJobStatus, 1)
                .le(MonitorJob::getJobTriggerNextTime, l)
                .orderByDesc(MonitorJob::getJobId)
        ).getRecords();
    }

    public void scheduleUpdate(MonitorJob jobInfo) {
        monitorJobService.updateById(jobInfo);
    }
}
