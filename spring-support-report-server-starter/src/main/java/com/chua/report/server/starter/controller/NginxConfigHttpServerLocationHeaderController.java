package com.chua.report.server.starter.controller;

import com.chua.report.server.starter.entity.MonitorNginxHttpServerLocationHeader;
import com.chua.report.server.starter.service.MonitorNginxHttpServerLocationHeaderService;
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
@RequestMapping("v1/nginx/config/http/server/location/header")
@Tag(name = "nginx配置header接口")
@RequiredArgsConstructor
public class NginxConfigHttpServerLocationHeaderController extends AbstractSwaggerController<MonitorNginxHttpServerLocationHeaderService, MonitorNginxHttpServerLocationHeader> {

    final MonitorNginxHttpServerLocationHeaderService monitorNginxHttpServerLocationHeaderService;
    @Override
    public MonitorNginxHttpServerLocationHeaderService getService() {
        return monitorNginxHttpServerLocationHeaderService;
    }
}
