package com.chua.report.server.starter.ngxin;

import com.chua.common.support.function.Joiner;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.report.server.starter.mapper.*;
import com.chua.report.server.starter.service.MonitorNginxConfigService;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.github.odiszapc.nginxparser.NgxConfig;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * nginx拆解
 * @author CH
 * @since 2024/12/29
 */
public class NginxDisAssembly {
    private final String eventName;
    @Autowired
    private MonitorNginxConfigService monitorNginxConfigService;
    @Autowired
    private MonitorNginxConfigMapper baseMapper;
    @Autowired
    private MonitorNginxHttpMapper monitorNginxHttpMapper;
    @Autowired
    private MonitorNginxHttpServerMapper monitorNginxHttpServerMapper;
    @Autowired
    private MonitorNginxHttpServerLocationMapper monitorNginxHttpServerLocationMapper;
    @Autowired
    private MonitorNginxHttpServerLocationHeaderMapper monitorNginxHttpServerLocationHeaderMapper;
    @Autowired
    private MonitorNginxUpstreamMapper monitorNginxUpstreamMapper;
    @Autowired
    private MonitorNginxEventMapper monitorNginxEventMapper;
    @Autowired
    private SocketSessionTemplate socketSessionTemplate;

    public NginxDisAssembly(MonitorNginxConfig monitorNginxConfig) {
        this.eventName = getEventName(monitorNginxConfig.getMonitorNginxConfigId());
    }

    public String getEventName(Integer monitorNginxConfigId) {
        return "nginx-analysis-" + monitorNginxConfigId;
    }

    public Boolean handle(InputStream inputStream) throws IOException {
        NgxConfig ngxConfig = NgxConfig.read(inputStream);
        return false;
    }


    public static String getFullPath(String path) {
        path = path.replace("\\", "/");
        List<String> sep = new LinkedList();

        for(String item : path.split("/")) {
            if (item.contains("*") || item.contains("?")) {
                break;
            }

            sep.add(item);
        }

        return Joiner.on("/").join(sep);
    }

    public static String getMatchPath(String path) {
        path = path.replace("\\", "/");
        List<String> sep = new LinkedList();

        for(String item : path.split("/")) {
            if (item.contains("*") || item.contains("?")) {
                sep.add(item);
            }
        }

        return Joiner.on("/").join(sep);
    }
}
