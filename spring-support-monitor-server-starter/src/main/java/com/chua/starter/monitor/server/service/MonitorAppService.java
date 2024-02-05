package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
}
