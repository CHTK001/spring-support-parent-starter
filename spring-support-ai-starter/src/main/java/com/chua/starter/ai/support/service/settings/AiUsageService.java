package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiUsageDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量统计服务接口
 *
 * @since 2026-08-03
 * @author CH
 */
public interface AiUsageService {

    /**
     * 按 分组+服务商+模型 维度查询用量汇总。
     *
     * @param model     模型名称（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 用量 DTO 列表，按 令牌 消耗降序
     */
    List<AiUsageDTO> query(String model, LocalDateTime startTime, LocalDateTime endTime, String userId);
}
