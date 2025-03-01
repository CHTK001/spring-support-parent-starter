package com.chua.report.server.starter.controller;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorJob;
import com.chua.report.server.starter.entity.MonitorJobLog;
import com.chua.report.server.starter.job.JobHelper;
import com.chua.report.server.starter.job.TriggerTypeEnum;
import com.chua.report.server.starter.job.pojo.JobStatisticResult;
import com.chua.report.server.starter.job.trigger.JobTriggerPoolHelper;
import com.chua.report.server.starter.service.MonitorJobLogService;
import com.chua.report.server.starter.service.MonitorJobService;
import com.chua.report.server.starter.service.MonitorSender;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/job")
@Tag(name = "任务中心")
@RequiredArgsConstructor
public class MonitorJobController extends AbstractSwaggerController<MonitorJobService, MonitorJob> {

    @Getter
    private final MonitorJobService service;
    final MonitorJobLogService monitorJobLogService;
    final MonitorSender monitorSender;


    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询日志")
    @GetMapping("log")
    public ReturnPageResult<MonitorJobLog> page(Query<MonitorJobLog> page, MonitorJobLog entity) {
        return PageResultUtils.ok(monitorJobLogService.page(page.createPage(),
                Wrappers.<MonitorJobLog>lambdaQuery()
                        .eq(StringUtils.isNotEmpty(entity.getJobLogProfile()), MonitorJobLog::getJobLogProfile, entity.getJobLogProfile())
                        .eq(StringUtils.isNotEmpty(entity.getJobLogApp()), MonitorJobLog::getJobLogApp, entity.getJobLogApp())
                        .eq("1".equals(entity.getJobLogTriggerCode()) , MonitorJobLog::getJobLogTriggerCode, "00000")
                        .ne("0".equals(entity.getJobLogTriggerCode()) , MonitorJobLog::getJobLogTriggerCode, "00000")
                        .ge(null != entity.getStartDate(), MonitorJobLog::getJobLogTriggerTime, entity.getStartDate())
                        .le(null != entity.getEndDate(), MonitorJobLog::getJobLogTriggerTime, entity.getEndDate())
                        .orderByDesc(MonitorJobLog::getJobLogId)));
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "清除日志信息")
    @GetMapping("clear")
    public ReturnResult<Boolean> clear(MonitorJobLog entity) {
        return ReturnResult.of(monitorJobLogService.clear(entity));
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询日志信息")
    @GetMapping("time")
    public ReturnResult<JobStatisticResult> time(MonitorJobLog entity) {
        return ReturnResult.ok(monitorJobLogService.time(entity));
    }

    /**
     * 日志详情
     *
     * @param jobLogId 日志id
     * @return {@link ReturnResult}<{@link String}>
     */
    @Operation(summary = "日志详情")
    @RequestMapping("/logCat")
    public ReturnResult<String> logCat(int jobLogId) {
        return ReturnResult.ok(monitorJobLogService.logCat(jobLogId));
    }

    @RequestMapping("/stop")
    @Permission("sys:monitor:job:stop")
    public ReturnResult<String> pause(int jobId) {
        return service.stop(jobId);
    }

    @RequestMapping("/start")
    @Permission("sys:monitor:job:start")
    public ReturnResult<String> start(int jobId) {
        return service.start(jobId);
    }
    @RequestMapping("/trigger")
    @Permission("sys:monitor:job:run")
    public ReturnResult<String> triggerJob(int id, String executorParam) {
        // force cover job param
        if (executorParam == null) {
            executorParam = "";
        }

        JobTriggerPoolHelper.trigger(id, TriggerTypeEnum.MANUAL, -1, null, executorParam);
        return ReturnResult.SUCCESS;
    }
    /**
     * 下一次触发时间
     *
     * @param jobScheduleType 时间表类型
     * @param jobScheduleTime 时间表conf
     * @return {@link ReturnResult}<{@link List}<{@link String}>>
     */
    @GetMapping("/nextTriggerTime")
    @Permission("sys:monitor:job:run")
    public ReturnResult<List<String>> nextTriggerTime(String jobScheduleType, String jobScheduleTime) {

        MonitorJob paramXxlJobInfo = new MonitorJob();
        paramXxlJobInfo.setJobScheduleType(jobScheduleType);
        paramXxlJobInfo.setJobScheduleTime(jobScheduleTime);

        List<String> result = new ArrayList<>();
        try {
            Date lastTime = new Date();
            for (int i = 0; i < 5; i++) {
                lastTime = JobHelper.generateNextValidTime(paramXxlJobInfo, lastTime);
                if (lastTime != null) {
                    result.add(DateUtil.formatDateTime(lastTime));
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            return ReturnResult.error(e);
        }
        return ReturnResult.success(result);

    }
}
