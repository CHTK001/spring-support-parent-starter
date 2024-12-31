package com.chua.report.server.starter.controller;

import com.chua.report.server.starter.entity.MonitorNginxHttpServerLocation;
import com.chua.report.server.starter.service.MonitorNginxHttpServerLocationService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * nginx配置http -> Server接口
 * @author CH
 * @since 2024/12/29
 */
@RestController
@RequestMapping("v1/nginx/config/http/server/location")
@Tag(name = "nginx配置location接口")
@RequiredArgsConstructor
public class NginxConfigHttpServerLocationController extends AbstractSwaggerController<MonitorNginxHttpServerLocationService, MonitorNginxHttpServerLocation> {

    final MonitorNginxHttpServerLocationService monitorNginxHttpServerLocationService;
    @Override
    public MonitorNginxHttpServerLocationService getService() {
        return monitorNginxHttpServerLocationService;
    }
}
