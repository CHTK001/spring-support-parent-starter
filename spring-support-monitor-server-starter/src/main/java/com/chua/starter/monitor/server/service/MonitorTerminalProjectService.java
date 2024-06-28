package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorTerminalProject;

/**
 * @author CH
 * @since 2024/6/19
 */
public interface MonitorTerminalProjectService extends IService<MonitorTerminalProject> {

    /**
     * 运行启动脚本。
     *
     * @param monitorTerminalProject 监控终端项目对象，包含运行脚本所需的信息。
     * @return 返回执行结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> runStartScript(MonitorTerminalProject monitorTerminalProject);

    /**
     * 运行停止脚本。
     *
     * @param monitorProxy 监控代理对象，包含停止脚本所需的信息。
     * @return 返回执行结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> runStopScript(MonitorTerminalProject monitorProxy);

    /**
     * 启动日志记录。
     *
     * @param monitorTerminalProject 监控终端项目对象，包含启动日志记录所需的信息。
     * @param event
     * @return 返回执行结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> logStart(MonitorTerminalProject monitorTerminalProject, String event);

    /**
     * 停止日志记录。
     *
     * @param monitorTerminalProject 监控终端项目对象，包含停止日志记录所需的信息。
     * @return 返回执行结果，成功为true，失败为false。
     */
    ReturnResult<Boolean> logStop(MonitorTerminalProject monitorTerminalProject);
}
