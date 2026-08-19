package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用量统计控制器，按模型维度查询用量数据。
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
     * 查询用量，可按模型过滤。
     *
     * @param model 模型名称（可选）
     * @return 用量 DTO 列表
     */
    @Permission("admin")
    @Operation(summary = "查询用量统计")
    @GetMapping
    public List<AiUsageDTO> query(@Parameter(description = "模型名称") @RequestParam(required = false) String model) {
        return usageService.query(model);
    }
}
