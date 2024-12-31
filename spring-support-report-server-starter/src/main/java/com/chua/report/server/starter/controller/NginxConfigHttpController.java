package com.chua.report.server.starter.controller;

import com.chua.report.server.starter.entity.MonitorNginxHttp;
import com.chua.report.server.starter.service.MonitorNginxHttpService;
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
@RequestMapping("v1/nginx/config/http")
@Tag(name = "nginx配置http接口")
@RequiredArgsConstructor
public class NginxConfigHttpController extends AbstractSwaggerController<MonitorNginxHttpService, MonitorNginxHttp> {

    final MonitorNginxHttpService monitorNginxHttpService;
    @Override
    public MonitorNginxHttpService getService() {
        return monitorNginxHttpService;
    }
}
