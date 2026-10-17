package com.chua.starter.ai.support.service.impl.settings;

import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用量统计服务实现类——直接对 sys_ai_usage 明细做数据库端维度聚合。
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    /**
     * 用量 映射器
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 按 分组+服务商+模型 维度查询用量汇总。
     *
     * @param model     模型名称（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param userId    归属用户（可为空，为空时统计全部用户）
     * @return 用量 DTO 列表
     */
    @Override
    public List<AiUsageDTO> query(String model, LocalDateTime startTime, LocalDateTime endTime, String userId) {
        List<SysAiUsageMapper.UsageSummaryRow> rows =
                usageMapper.selectUsageSummary(model, startTime, endTime, userId,
                        // 无主数据（定时同步的外部工具用量）只对管理员可见，
                        // 口径与 SysAiUsageServiceImpl.includeOrphan() 一致。
                        com.chua.starter.ai.support.util.AiUserContext.isAdmin());
        List<AiUsageDTO> result = new ArrayList<>(rows.size());
        for (SysAiUsageMapper.UsageSummaryRow row : rows) {
            result.add(new AiUsageDTO(
                    row.groupName(),
                    row.provider(),
                    row.model(),
                    row.totalCost(),
                    row.totalCalls(),
                    row.totalPromptTokens(),
                    row.totalCompletionTokens(),
                    row.totalTokens(),
                    null));
        }
        return result;
    }
}
