package com.chua.report.server.starter.ngxin;

import com.chua.report.server.starter.mapper.*;
import com.github.odiszapc.nginxparser.NgxConfig;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;

/**
 * nginx拆解
 * @author CH
 * @since 2024/12/29
 */
@RequiredArgsConstructor
public class NginxDisAssembly {
    final MonitorNginxConfigMapper baseMapper;
    final MonitorNginxHttpMapper monitorNginxHttpMapper;
    final MonitorNginxHttpServerMapper monitorNginxHttpServerMapper;
    final MonitorNginxHttpServerLocationMapper monitorNginxHttpServerLocationMapper;
    final MonitorNginxHttpServerLocationHeaderMapper monitorNginxHttpServerLocationHeaderMapper;
    final MonitorNginxUpstreamMapper monitorNginxUpstreamMapper;
    final MonitorNginxEventMapper monitorNginxEventMapper;


    public Boolean handle(InputStream inputStream) throws IOException {
        NgxConfig ngxConfig = NgxConfig.read(inputStream);
        return false;
    }
}
