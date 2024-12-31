package com.chua.report.server.starter.controller;

import com.chua.report.server.starter.entity.MonitorNginxEvent;
import com.chua.report.server.starter.service.MonitorNginxEventService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * nginx配置http接口
 * @author CH
 * @since 2024/12/29
 */
@RestController
@RequestMapping("v1/nginx/config/event")
@Tag(name = "nginx配置event接口")
@RequiredArgsConstructor
public class NginxConfigEventController extends AbstractSwaggerController<MonitorNginxEventService, MonitorNginxEvent> {

    final MonitorNginxEventService monitorNginxEventService;
    @Override
    public MonitorNginxEventService getService() {
        return monitorNginxEventService;
    }
}
