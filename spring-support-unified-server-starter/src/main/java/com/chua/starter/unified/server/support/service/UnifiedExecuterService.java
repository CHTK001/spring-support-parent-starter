package com.chua.starter.unified.server.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;

/**
 * 统一执行器服务
 *
 * @author CH
 */
public interface UnifiedExecuterService extends IService<UnifiedExecuter>{

    /**
     * 创建执行器
     *
     * @param request 请求
     */
    void createExecutor(BootRequest request);
}
