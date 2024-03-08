package com.chua.starter.monitor.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorJob;
public interface MonitorJobService extends IService<MonitorJob>{


    /**
     * 停止
     *
     * @param jobId jobId
     * @return {@link ReturnResult}<{@link String}>
     */
    ReturnResult<String> stop(int jobId);

    /**
     * 开始
     *
     * @param jobId jobId
     * @return {@link ReturnResult}<{@link String}>
     */
    ReturnResult<String> start(int jobId);
}
