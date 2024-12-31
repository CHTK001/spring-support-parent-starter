package com.chua.report.server.starter.ngxin;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.common.support.constant.Projects;
import com.chua.common.support.utils.CmdUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;

/**
 * nginx启动
 * @author CH
 * @since 2024/12/29
 */
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
        if(StringUtils.isNotBlank(monitorNginxConfigNginxPath)) {
            return runPath();
        }

        if(Projects.isWindows()) {
            return runWindow();
        }

        if(Projects.isLinux()) {
            return runLinux();
        }
        return null;
    }

    private String runWindow() {
        return CmdUtils.exec("nginx -c " + monitorNginxConfigPath);
    }

    private String runPath() {
        return CmdUtils.exec(monitorNginxConfigNginxPath + " -c " + monitorNginxConfigPath);
    }

    private String runLinux() {
        return CmdUtils.exec("nginx -c " + monitorNginxConfigPath);
    }
}
