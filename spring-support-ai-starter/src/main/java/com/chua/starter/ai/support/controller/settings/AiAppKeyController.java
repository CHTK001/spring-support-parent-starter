package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiAppKeyDTO;
import com.chua.starter.ai.support.service.settings.AiAppKeyService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
@Api(tags = "AI 设置 - AppKey")
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
    @ApiOperation("查询所有 AppKey")
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
    @ApiOperation("新建 AppKey")
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
    @ApiOperation("更新 AppKey")
    @PutMapping("/{appKeyId}")
    public AiAppKeyDTO update(@ApiParam("AppKey 主键 ID") @PathVariable Long appKeyId,
                              @RequestBody AiAppKeyDTO appKey) {
        return appKeyService.update(appKeyId, appKey);
    }

    /**
     * 删除 AppKey（逻辑删除）。
     *
     * @param appKeyId AppKey 主键
     */
    @Permission("admin")
    @ApiOperation("删除 AppKey")
    @DeleteMapping("/{appKeyId}")
    public void delete(@ApiParam("AppKey 主键 ID") @PathVariable Long appKeyId) {
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
    @ApiOperation("切换 AppKey 启用状态")
    @PutMapping("/{appKeyId}/toggle")
    public AiAppKeyDTO toggle(@ApiParam("AppKey 主键 ID") @PathVariable Long appKeyId,
                              @ApiParam("是否启用") @RequestParam Boolean enabled) {
        return appKeyService.toggle(appKeyId, enabled);
    }
}
