package com.chua.report.server.starter.ngxin;

import com.chua.common.support.constant.Projects;
import com.chua.common.support.utils.CmdUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.starter.common.support.exception.RuntimeMessageException;
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
                FileUtils.forceMkdir("C:/temp");
                FileUtils.forceMkdir("C:/logs");
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

        try {
            Thread.ofVirtual()
                    .start(() ->{
                        CmdUtils.exec(monitorNginxConfigNginxPath + " -c " + monitorNginxConfigPath);
                    });
            ThreadUtils.sleepSecondsQuietly(1);
        } catch (Exception e) {
            throw new RuntimeMessageException(e);
        }
        return null;

    }

    private String runLinux() {
        return CmdUtils.exec(monitorNginxConfigNginxPath + " -c " + monitorNginxConfigPath);
    }

    private String runService() {
        try {
            return CmdUtils.exec("nginx -c " + monitorNginxConfigPath);
        } catch (Exception e) {
            throw new RuntimeMessageException(e.getMessage());
        }
    }
}
