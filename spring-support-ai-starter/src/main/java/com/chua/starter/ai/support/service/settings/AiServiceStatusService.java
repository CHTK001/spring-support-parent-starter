package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiServiceStatusDTO;

import java.util.List;

/**
* 聚合服务运行状态服务接口
*
* @since 2026-08-03
* @author CH
 */
public interface AiServiceStatusService {

    /**
    * 查询所有服务运行状态。
    *
    * @return 服务状态 DTO 列表
    */
    List<AiServiceStatusDTO> list();

    /**
    * 启动指定服务。
    *
    * @param serviceName 服务名称
    */
    void start(String serviceName);

    /**
    * 停止指定服务。
    *
    * @param serviceName 服务名称
    */
    void stop(String serviceName);
}
