package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiProviderDTO;
import com.chua.starter.ai.support.service.settings.AiProviderService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型供应商控制器，提供供应商的增删改查与启停切换。
 *
 * <p>所有写接口均受 {@code @Permission("admin")} 保护，
 * 超级管理员 (SUPER_ADMIN) 自动放行，普通管理员受限。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@RestController
@RequestMapping("/v2/providers")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 模型供应商")
public class AiProviderController {

    /**
     * 模型供应商服务
     */
    private final AiProviderService providerService;

    /**
     * 查询所有供应商。
     *
     * @return 供应商 DTO 列表
     */
    @Permission("admin")
    @Operation(summary = "查询所有供应商")
    @GetMapping
    public List<AiProviderDTO> list() {
        return providerService.list();
    }

    /**
     * 新建供应商。
     *
     * @param provider 供应商 DTO
     * @return 创建后的供应商 DTO
     */
    @Permission("admin")
    @Operation(summary = "新建供应商")
    @PostMapping
    public AiProviderDTO create(@RequestBody AiProviderDTO provider) {
        return providerService.create(provider);
    }

    /**
     * 更新供应商。
     *
     * @param providerId 供应商主键
     * @param provider   供应商 DTO
     * @return 更新后的供应商 DTO
     */
    @Permission("admin")
    @Operation(summary = "更新供应商")
    @PutMapping("/{providerId}")
    public AiProviderDTO update(@Parameter(description = "供应商主键 ID") @PathVariable Long providerId,
                                @RequestBody AiProviderDTO provider) {
        return providerService.update(providerId, provider);
    }

    /**
     * 删除供应商（逻辑删除）。
     *
     * @param providerId 供应商主键
     */
    @Permission("admin")
    @Operation(summary = "删除供应商")
    @DeleteMapping("/{providerId}")
    public void delete(@Parameter(description = "供应商主键 ID") @PathVariable Long providerId) {
        providerService.delete(providerId);
    }

    /**
     * 切换供应商启用状态。
     *
     * @param providerId 供应商主键
     * @param enabled    是否启用
     * @return 更新后的供应商 DTO
     */
    @Permission("admin")
    @Operation(summary = "切换供应商启用状态")
    @PutMapping("/{providerId}/toggle")
    public AiProviderDTO toggle(@Parameter(description = "供应商主键 ID") @PathVariable Long providerId,
                                @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return providerService.toggle(providerId, enabled);
    }

    /**
     * 启用供应商为 OpenAI 兼容服务。
     *
     * <p>将供应商连接信息写入 runtime 全局配置并热重建全局 ChatClient，
     * 使 {@code /v2/v1/chat/completions} 等 OpenAI 兼容端点立即可用。</p>
     *
     * @param providerId 供应商主键
     * @return 更新后的供应商 DTO
     */
    @Permission("admin")
    @Operation(summary = "启用 OpenAI 兼容服务")
    @PostMapping("/{providerId}/activate")
    public AiProviderDTO activate(@Parameter(description = "供应商主键 ID") @PathVariable Long providerId) {
        return providerService.activate(providerId);
    }
}
