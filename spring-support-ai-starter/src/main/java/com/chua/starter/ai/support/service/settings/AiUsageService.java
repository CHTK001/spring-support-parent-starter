package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiUsageDTO;

import java.util.List;

/**
* 用量统计服务接口
*
* @since 2026-08-03
* @author CH
 */
public interface AiUsageService {

    /**
    * 查询用量，可按模型过滤。
    *
    * @param model 模型名称（可选）
    * @return 用量 DTO 列表
     */
    List<AiUsageDTO> query(String model);
}
