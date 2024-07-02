package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.monitor.server.entity.MonitorLimit;

import java.util.Set;

public interface MonitorLimitService extends IService<MonitorLimit>{


    /**
     * 批量删除
     * @param strings id
     * @return boolean
     */
    Boolean removeBatchByIdsAndNotify(Set<String> strings);
}
