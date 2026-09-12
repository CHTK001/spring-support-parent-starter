package com.chua.starter.ai.support.controller.settings;

import com.chua.common.support.lang.code.ReturnResult;
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
   * app钥匙 控制器，提供 app钥匙 的增删改查与启停切换。
 *
 * <p>所有写接口均受 {@code @Permission("admin")} 保护，统一返回 {@link ReturnResult}。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@RestController
@RequestMapping("/v2/app-keys")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - AppKey")
public class AiAppKeyController {

    /**
      * app钥匙 服务
     */
    private final AiAppKeyService appKeyService;

    /**
      * 查询所有 app钥匙。
     *
     * @return 操作结果
     */
    @Permission("admin")
    @Operation(summary = "查询所有 AppKey")
    @GetMapping
    public ReturnResult<List<AiAppKeyDTO>> list() {
        return appKeyService.list();
    }

    /**
      * 新建 app钥匙。
     *
     * @param appKey app钥匙 DTO
     * @return 操作结果
     */
    @Permission("admin")
    @Operation(summary = "新建 AppKey")
    @PostMapping
    public ReturnResult<AiAppKeyDTO> create(@RequestBody AiAppKeyDTO appKey) {
        return appKeyService.create(appKey);
    }

    /**
      * 更新 app钥匙。
     *
     * @param appKeyId app钥匙 主键
     * @param appKey   app钥匙 DTO
     * @return 操作结果
     * @param ID" 标识"
     */
    @Permission("admin")
    @Operation(summary = "更新 AppKey")
    @PutMapping("/{appKeyId}")
    public ReturnResult<AiAppKeyDTO> update(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
                                            @RequestBody AiAppKeyDTO appKey) {
        return appKeyService.update(appKeyId, appKey);
    }

    /**
      * 删除 app钥匙（逻辑删除）。
     *
     * @param appKeyId app钥匙 主键
     * @return 操作结果
     * @param ID" 标识"
     * @param ID" 标识"
     * @param ID" ID"
     */
    @Permission("admin")
    @Operation(summary = "删除 AppKey")
    @DeleteMapping("/{appKeyId}")
    public ReturnResult<Void> delete(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId) {
        return appKeyService.delete(appKeyId);
    }

    /**
      * 切换 app钥匙 启用状态。
     *
     * @param appKeyId app钥匙 主键
     * @param enabled  是否启用
     * @return 操作结果
     * @param ID" 标识"
     */
    @Permission("admin")
    @Operation(summary = "切换 AppKey 启用状态")
    @PutMapping("/{appKeyId}/toggle")
    public ReturnResult<AiAppKeyDTO> toggle(@Parameter(description = "AppKey 主键 ID") @PathVariable Long appKeyId,
                                            @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return appKeyService.toggle(appKeyId, enabled);
    }
}