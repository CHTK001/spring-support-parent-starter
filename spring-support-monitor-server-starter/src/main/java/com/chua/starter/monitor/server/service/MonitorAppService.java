package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorApp;
import com.chua.starter.monitor.server.entity.MonitorConfig;

import java.util.List;

public interface MonitorAppService extends IService<MonitorApp>{
    /**
     * 下发配置
     *
     * @param monitorConfig 监视器配置
     * @return {@link Boolean}
     */
    Boolean upload(List<MonitorConfig> monitorConfig);

    /**
     * 上载
     *
     * @param config         配置
     * @param monitorRequest 监视器请求
     * @return {@link BootResponse}
     */
    BootResponse upload(MonitorConfig config, MonitorRequest monitorRequest, String content, String moduleType, CommandType commandType);
}
