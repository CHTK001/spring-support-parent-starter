package com.chua.starter.monitor.server.controller;


import com.chua.starter.monitor.server.entity.MonitorJob;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorJobService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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
}
