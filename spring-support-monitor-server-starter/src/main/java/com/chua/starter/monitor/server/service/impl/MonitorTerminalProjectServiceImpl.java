package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorTerminalProject;
import com.chua.starter.monitor.server.mapper.MonitorTerminalProjectMapper;
import com.chua.starter.monitor.server.service.MonitorTerminalProjectService;
import org.springframework.stereotype.Service;
/**
 *
 *
 * @since 2024/6/19 
 * @author CH
 */
@Service
public class MonitorTerminalProjectServiceImpl extends ServiceImpl<MonitorTerminalProjectMapper, MonitorTerminalProject> implements MonitorTerminalProjectService{

    @Override
    public ReturnResult<Boolean> start(MonitorTerminalProject monitorTerminalProject) {
        return null;
    }

    @Override
    public ReturnResult<Boolean> stop(MonitorTerminalProject monitorProxy) {
        return null;
    }
}
