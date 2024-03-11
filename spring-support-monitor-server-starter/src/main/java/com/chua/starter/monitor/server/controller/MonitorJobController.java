package com.chua.starter.monitor.server.controller;


import cn.hutool.core.date.DateUtil;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.monitor.server.entity.MonitorJob;
import com.chua.starter.monitor.server.job.JobHelper;
import com.chua.starter.monitor.server.job.TriggerTypeEnum;
import com.chua.starter.monitor.server.job.trigger.JobTriggerPoolHelper;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorJobService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
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
    @Resource
    private final MonitorAppService monitorAppService;
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
     * @param scheduleType 时间表类型
     * @param scheduleConf 时间表conf
     * @return {@link ReturnResult}<{@link List}<{@link String}>>
     */
    @GetMapping("/nextTriggerTime")
    @Permission("sys:monitor:job:run")
    public ReturnResult<List<String>> nextTriggerTime(String scheduleType, String scheduleConf) {

        MonitorJob paramXxlJobInfo = new MonitorJob();
        paramXxlJobInfo.setJobType(scheduleType);
        paramXxlJobInfo.setJobConf(scheduleConf);

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
