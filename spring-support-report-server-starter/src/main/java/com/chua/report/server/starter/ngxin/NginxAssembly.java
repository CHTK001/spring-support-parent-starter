package com.chua.report.server.starter.ngxin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.report.server.starter.entity.*;
import com.chua.report.server.starter.mapper.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * nginx组装
 * @author CH
 * @since 2024/12/29
 */
@RequiredArgsConstructor
public class NginxAssembly {

    final MonitorNginxConfigMapper baseMapper;
    final MonitorNginxHttpMapper monitorNginxHttpMapper;
    final MonitorNginxHttpServerMapper monitorNginxHttpServerMapper;
    final MonitorNginxHttpServerLocationMapper monitorNginxHttpServerLocationMapper;
    final MonitorNginxHttpServerLocationHeaderMapper monitorNginxHttpServerLocationHeaderMapper;
    final MonitorNginxUpstreamMapper monitorNginxUpstreamMapper;
    final MonitorNginxEventMapper monitorNginxEventMapper;

    public Boolean handle(Integer nginxConfigId) {
        List<MonitorNginxEvent> monitorNginxEvents = monitorNginxEventMapper.selectList(Wrappers.<MonitorNginxEvent>lambdaQuery()
                .eq(MonitorNginxEvent::getMonitorNginxConfigId, nginxConfigId));

        List<MonitorNginxHttp> monitorNginxHttps = monitorNginxHttpMapper.selectList(Wrappers.<MonitorNginxHttp>lambdaQuery()
                .eq(MonitorNginxHttp::getMonitorNginxConfigId, nginxConfigId));

        Map<Integer, List<MonitorNginxHttpServer>> httpServer = monitorNginxHttpServerMapper.selectList(Wrappers.<MonitorNginxHttpServer>lambdaQuery()
                        .in(MonitorNginxHttpServer::getMonitorNginxHttpId, monitorNginxHttps.stream().map(MonitorNginxHttp::getMonitorNginxHttpId).toList()))
                .stream().collect(Collectors.groupingBy(MonitorNginxHttpServer::getMonitorNginxHttpId));

        List<Integer> httpServerIds = httpServer.values().stream()
                .flatMap(List::stream)
                .map(MonitorNginxHttpServer::getMonitorNginxHttpServerId).toList();
        Map<Integer, List<MonitorNginxHttpServerLocation>> collect = monitorNginxHttpServerLocationMapper.selectList(Wrappers.<MonitorNginxHttpServerLocation>lambdaQuery()
                        .in(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId, httpServerIds))
                .stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId));

        Map<Integer, List<MonitorNginxHttpServerLocationHeader>> collect1 = monitorNginxHttpServerLocationHeaderMapper.selectList(Wrappers.<MonitorNginxHttpServerLocationHeader>lambdaQuery()
                        .in(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId, collect.values().stream()
                                .flatMap(List::stream)
                                .map(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerLocationId).toList()))
                .stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId));

        List<MonitorNginxUpstream> monitorNginxUpstreams = monitorNginxUpstreamMapper.selectList(Wrappers.<MonitorNginxUpstream>lambdaQuery()
                .in(MonitorNginxUpstream::getMonitorNginxServerId, httpServerIds));

        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfigId);

        return null;
    }
}
