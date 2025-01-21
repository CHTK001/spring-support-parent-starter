package com.chua.report.server.starter.ngxin;

import com.chua.common.support.constant.Projects;
import com.chua.common.support.utils.CmdUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * nginx启动
 * @author CH
 * @since 2024/12/29
 */
@Slf4j
public class NginxStop {
    private final MonitorNginxConfig monitorNginxConfig;
    private final  String monitorNginxConfigNginxPath;
    private final  String monitorNginxConfigPath;

    public NginxStop(MonitorNginxConfig monitorNginxConfig) {
        this.monitorNginxConfig = monitorNginxConfig;
        this.monitorNginxConfigPath = monitorNginxConfig.getMonitorNginxConfigPath();
        this.monitorNginxConfigNginxPath = monitorNginxConfig.getMonitorNginxConfigNginxPath();
    }

    public String run() {
        if(Projects.isWindows()) {
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
        // 构造关闭 Nginx 进程的命令
        // 使用 taskkill 命令强制结束名为 nginx.exe 的进程
        String command = "taskkill /IM nginx.exe /F";
        Process process = null;
        try {
            // 执行命令
            process = Runtime.getRuntime().exec(command);
            // 等待命令执行完成
            int exitCode = process.waitFor();
            if (exitCode == 0 || exitCode == 128) {
                log.info("Nginx 进程已成功关闭");
                return null;
            }
            return "关闭 Nginx 进程时出错，退出码：" + exitCode;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IoUtils.closeQuietly(process);
        }
    }

    private String runService() {
        return CmdUtils.exec("nginx -s stop ");
    }

    private String runLinux() {
        // 构造关闭 Nginx 进程的命令
        // 使用 taskkill 命令强制结束名为 nginx.exe 的进程
        String command = "pkill -f nginx";
        Process process = null;
        try {
            // 执行命令
            process = Runtime.getRuntime().exec(command);
            // 等待命令执行完成
            int exitCode = process.waitFor();
            if (exitCode == 0 || exitCode == 128) {
                log.info("Nginx 进程已成功关闭");
                return null;
            }
            return "关闭 Nginx 进程时出错，退出码：" + exitCode;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IoUtils.closeQuietly(process);
        }
    }
}
