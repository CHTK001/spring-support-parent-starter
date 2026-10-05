package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import com.chua.starter.ai.support.util.AiUserContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量统计控制器，按 分组+服务商+模型 维度查询用量数据。
 *
 * <p>普通用户只能看到自己的用量，管理员看全量。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@RestController
@RequestMapping("/v2/usage")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 用量统计")
public class AiUsageController {

    /**
     * 用量服务
     */
    private final AiUsageService usageService;

    /**
     * 查询用量汇总，可按模型与时间窗口过滤。
     *
     * @param model     模型名称（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 用量 DTO 列表
     */
    @Operation(summary = "查询用量统计", description = "按分组+服务商+模型聚合；普通用户仅统计本人明细")
    @GetMapping
    public List<AiUsageDTO> query(
            @Parameter(description = "模型名称") @RequestParam(required = false) String model,
            @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        String userId = AiUserContext.canAccessOthersData() ? null : AiUserContext.requireUserId();
        return usageService.query(model, startTime, endTime, userId);
    }
}
