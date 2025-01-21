package com.chua.report.server.starter.ngxin;

import com.chua.common.support.constant.Projects;
import com.chua.common.support.utils.CmdUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * nginx启动
 * @author CH
 * @since 2024/12/29
 */
@Slf4j
public class NginxStart {
    private final MonitorNginxConfig monitorNginxConfig;
    private final  String monitorNginxConfigNginxPath;
    private final  String monitorNginxConfigPath;

    public NginxStart(MonitorNginxConfig monitorNginxConfig) {
        this.monitorNginxConfig = monitorNginxConfig;
        this.monitorNginxConfigPath = monitorNginxConfig.getMonitorNginxConfigPath();
        this.monitorNginxConfigNginxPath = monitorNginxConfig.getMonitorNginxConfigNginxPath();
    }

    public String run() {

        if(Projects.isWindows()) {
            new NginxStop(monitorNginxConfig).run();
            if(monitorNginxConfig.getMonitorNginxConfigType() == 0) {
                return runWindow();
            }
            return runService();
        }

        if(Projects.isLinux()) {
            if(monitorNginxConfig.getMonitorNginxConfigType() == 0) {
                return runLinux();
            }
            return runService();
        }
        return null;
    }

    private String runWindow() {
        Thread.ofVirtual()
                .start(() ->{
                    try {
                        CmdUtils.exec(monitorNginxConfigNginxPath + " -c " + monitorNginxConfigPath);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        return null;

    }

    private String runLinux() {
        return CmdUtils.exec(monitorNginxConfigNginxPath + " -c " + monitorNginxConfigPath);
    }

    private String runService() {
        return CmdUtils.exec("nginx -c " + monitorNginxConfigPath);
    }
}
