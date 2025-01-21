package com.chua.report.server.starter.ngxin;

import com.chua.common.support.constant.Projects;
import com.chua.common.support.utils.CmdUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;

/**
 * nginx启动
 * @author CH
 * @since 2024/12/29
 */
public class NginxRestart {
    private final MonitorNginxConfig monitorNginxConfig;
    private final  String monitorNginxConfigNginxPath;
    private final  String monitorNginxConfigPath;

    public NginxRestart(MonitorNginxConfig monitorNginxConfig) {
        this.monitorNginxConfig = monitorNginxConfig;
        this.monitorNginxConfigPath = monitorNginxConfig.getMonitorNginxConfigPath();
        this.monitorNginxConfigNginxPath = monitorNginxConfig.getMonitorNginxConfigNginxPath();
    }

    public String run() {
               if(Projects.isWindows()) {
            if(monitorNginxConfig.getMonitorNginxConfigType() == 0) {
                new NginxStop(monitorNginxConfig).run();
                new NginxStart(monitorNginxConfig).run();
                return null;
            }
            return runService();
        }

        if(Projects.isLinux()) {
            if(monitorNginxConfig.getMonitorNginxConfigType() == 0) {
                new NginxStop(monitorNginxConfig).run();
                new NginxStart(monitorNginxConfig).run();
                return null;
            }
            return runService();
        }
        return null;
    }


    private String runService() {
        return CmdUtils.exec("nginx -s reload  -c " + monitorNginxConfigPath);
    }

}
