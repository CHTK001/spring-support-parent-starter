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
     * 开始
     *
     * @param monitorTerminalProject monitorTerminalProject
     * @return boolean
     */
    ReturnResult<Boolean> start(MonitorTerminalProject monitorTerminalProject);

    /**
     * 停止
     *
     * @param monitorTerminalProject monitorTerminalProject
     * @return boolean
     */
    ReturnResult<Boolean> stop(MonitorTerminalProject monitorProxy);
}
