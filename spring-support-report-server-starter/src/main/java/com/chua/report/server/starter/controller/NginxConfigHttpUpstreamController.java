package com.chua.report.server.starter.controller;

import com.chua.report.server.starter.entity.MonitorNginxUpstream;
import com.chua.report.server.starter.service.MonitorNginxUpstreamService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * nginx配置http -> upstream接口
 * @author CH
 * @since 2024/12/29
 */
@RestController
@RequestMapping("v1/nginx/config/http/upstream")
@Tag(name = "nginx配置upstream接口")
@RequiredArgsConstructor
public class NginxConfigHttpUpstreamController extends AbstractSwaggerController<MonitorNginxUpstreamService, MonitorNginxUpstream> {

    final MonitorNginxUpstreamService monitorNginxUpstreamService;
    @Override
    public MonitorNginxUpstreamService getService() {
        return monitorNginxUpstreamService;
    }
}
