package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.starter.monitor.server.entity.MonitorProjectVersion;
public interface MonitorProjectVersionService extends IService<MonitorProjectVersion>{


    /**
     * 开始
     *
     * @param entity 实体
     * @return {@link Boolean}
     */
    ErrorResult<Boolean> start(MonitorProjectVersion entity);

    /**
     * 停止
     *
     * @param entity 实体
     * @return {@link ErrorResult}<{@link Boolean}>
     */
    ErrorResult<Boolean> stop(MonitorProjectVersion entity);

    /**
     * 日志
     *
     * @param entity 实体
     * @return {@link ErrorResult}<{@link Boolean}>
     */
    ErrorResult<Boolean> log(MonitorProjectVersion entity);
}
