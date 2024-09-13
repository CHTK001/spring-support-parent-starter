package com.chua.report.server.starter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.MonitorJob;

/**
 * 监控任务
 * @author Administrator
 */
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
