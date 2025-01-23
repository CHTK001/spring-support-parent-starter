package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.report.server.starter.entity.MonitorNginxHttpServerLocation;
import com.chua.report.server.starter.entity.MonitorNginxHttpServerLocationHeader;
import com.chua.report.server.starter.service.MonitorNginxHttpServerLocationHeaderService;
import com.chua.report.server.starter.service.MonitorNginxHttpServerLocationService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    final MonitorNginxHttpServerLocationHeaderService monitorNginxHttpServerLocationHeaderService;
    @Override
    public MonitorNginxHttpServerLocationService getService() {
        return monitorNginxHttpServerLocationService;
    }


    @Override
    protected void pageAfter(List<MonitorNginxHttpServerLocation> page) {
        List<Integer> list = page.stream().map(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerLocationId).toList();
        Map<Integer, List<MonitorNginxHttpServerLocationHeader>> collect = monitorNginxHttpServerLocationHeaderService.list(Wrappers.<MonitorNginxHttpServerLocationHeader>lambdaQuery().in(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId, list)).stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId));
        for (MonitorNginxHttpServerLocation record : page) {
            record.setHeaders(collect.getOrDefault(record.getMonitorNginxHttpServerLocationId(), Collections.emptyList()));
        }
    }

    @Override
    public void saveOrUpdateAfter(MonitorNginxHttpServerLocation monitorNginxHttpServerLocation) {
        List<MonitorNginxHttpServerLocationHeader> headers = monitorNginxHttpServerLocation.getHeaders();
        if(null == headers) {
            return;
        }
        for (MonitorNginxHttpServerLocationHeader header : headers) {
            header.setMonitorNginxHttpServerLocationId(monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationId());
        }
        monitorNginxHttpServerLocationHeaderService.remove(Wrappers.<MonitorNginxHttpServerLocationHeader>lambdaQuery().eq(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId, monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationId()));
        monitorNginxHttpServerLocationHeaderService.saveBatch(headers);
    }
}
