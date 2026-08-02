package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiAppKeyDTO;
import com.chua.starter.ai.support.service.settings.AiAppKeyService;
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
 * AppKey 控制器，提供 AppKey 的增删改查与启停切换。
 *
 * <p>所有写接口均受 {@code @Permission("admin")} 保护。</p>
 *
 * @author CH
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/v2/ai/app-keys")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - AppKey")
public class AiAppKeyController {

    /**
     * AppKey 服务
     */
    private final AiAppKeyService appKeyService;

    /**
     * 查询所有 AppKey。
     *
     * @return AppKey DTO 列表
     */
    @Permission("admin")
    @Operation(summary = "查询所有 AppKey")
    @GetMapping
    public List<AiAppKeyDTO> list() {
        return appKeyService.list();
    }

    /**
     * 新建 AppKey。
     *
     * @param appKey AppKey DTO
     * @return 创建后的 AppKey DTO
     */
    @Permission("admin")
    @Operation(summary = "新建 AppKey")
    @PostMapping
    public AiAppKeyDTO create(@RequestBody AiAppKeyDTO appKey) {
        return appKeyService.create(appKey);
    }

    /**
     * 更新 AppKey。
     *
     * @param appKeyId AppKey 主键
     * @param appKey   AppKey DTO
     * @return 更新后的 AppKey DTO
     */
    @Permission("admin")
    @Operation(summary = "更新 AppKey")
    @PutMapping("/{appKeyId}")
    public AiAppKeyDTO update(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
                              @RequestBody AiAppKeyDTO appKey) {
        return appKeyService.update(appKeyId, appKey);
    }

    /**
     * 删除 AppKey（逻辑删除）。
     *
     * @param appKeyId AppKey 主键
     */
    @Permission("admin")
    @Operation(summary = "删除 AppKey")
    @DeleteMapping("/{appKeyId}")
    public void delete(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId) {
        appKeyService.delete(appKeyId);
    }

    /**
     * 切换 AppKey 启用状态。
     *
     * @param appKeyId AppKey 主键
     * @param enabled  是否启用
     * @return 更新后的 AppKey DTO
     */
    @Permission("admin")
    @Operation(summary = "切换 AppKey 启用状态")
    @PutMapping("/{appKeyId}/toggle")
    public AiAppKeyDTO toggle(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
                              @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return appKeyService.toggle(appKeyId, enabled);
    }
}
