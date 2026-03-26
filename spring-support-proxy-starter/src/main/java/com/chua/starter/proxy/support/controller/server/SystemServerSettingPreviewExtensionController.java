package com.chua.starter.proxy.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.pojo.PreviewExtensionConfigVo;
import com.chua.starter.proxy.support.service.server.SystemServerSettingPreviewExtensionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预览扩展名配置控制器
 * 用于管理 ViewServletFilter 的扩展名白名单/黑名单配置
 *
 * @author CH
 * @since 2024/12/08
 */
@RestController
@RequestMapping("/proxy/server/setting/preview-extension")
@Api(tags = "预览扩展名配置管理")
@Tag(name = "预览扩展名配置管理")
@RequiredArgsConstructor
public class SystemServerSettingPreviewExtensionController {

    private final SystemServerSettingPreviewExtensionService previewExtensionService;

    /**
     * 获取服务器的预览扩展名配置
     *
     * @param serverId 服务器ID
     * @return 配置信息
     */
    @GetMapping("/{serverId}")
    @ApiOperation("获取服务器的预览扩展名配置")
    public ReturnResult<PreviewExtensionConfigVo> getConfigForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId) {
        PreviewExtensionConfigVo config = previewExtensionService.getByServerIdForPreviewExtension(serverId);
        return ReturnResult.ok(config);
    }

    /**
     * 保存预览扩展名配置
     *
     * @param config 配置对象
     * @return 操作结果
     */
    @PostMapping("/save")
    @ApiOperation("保存预览扩展名配置")
    public ReturnResult<Boolean> saveConfigForPreviewExtension(
            @ApiParam("配置对象") @RequestBody PreviewExtensionConfigVo config) {
        return previewExtensionService.saveConfigForPreviewExtension(config);
    }

    /**
     * 添加禁用预览的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    @PostMapping("/{serverId}/disabled/add")
    @ApiOperation("添加禁用预览的扩展名")
    public ReturnResult<Boolean> addDisabledExtensionsForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions) {
        return previewExtensionService.addDisabledExtensionsForPreviewExtension(serverId, extensions);
    }

    /**
     * 移除禁用预览的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    @PostMapping("/{serverId}/disabled/remove")
    @ApiOperation("移除禁用预览的扩展名")
    public ReturnResult<Boolean> removeDisabledExtensionsForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions) {
        return previewExtensionService.removeDisabledExtensionsForPreviewExtension(serverId, extensions);
    }

    /**
     * 添加允许预览的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    @PostMapping("/{serverId}/allowed/add")
    @ApiOperation("添加允许预览的扩展名")
    public ReturnResult<Boolean> addAllowedExtensionsForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions) {
        return previewExtensionService.addAllowedExtensionsForPreviewExtension(serverId, extensions);
    }

    /**
     * 移除允许预览的扩展名
     *
     * @param serverId   服务器ID
     * @param extensions 扩展名列表
     * @return 操作结果
     */
    @PostMapping("/{serverId}/allowed/remove")
    @ApiOperation("移除允许预览的扩展名")
    public ReturnResult<Boolean> removeAllowedExtensionsForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions) {
        return previewExtensionService.removeAllowedExtensionsForPreviewExtension(serverId, extensions);
    }

    /**
     * 设置预览模式（白名单/黑名单）
     *
     * @param serverId      服务器ID
     * @param whitelistMode 是否启用白名单模式
     * @return 操作结果
     */
    @PutMapping("/{serverId}/mode")
    @ApiOperation("设置预览模式")
    public ReturnResult<Boolean> setPreviewModeForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId,
            @ApiParam("是否启用白名单模式") @RequestParam Boolean whitelistMode) {
        return previewExtensionService.setPreviewModeForPreviewExtension(serverId, whitelistMode);
    }

    /**
     * 清空预览扩展名配置
     *
     * @param serverId 服务器ID
     * @return 操作结果
     */
    @PostMapping("/{serverId}/clear")
    @ApiOperation("清空预览扩展名配置")
    public ReturnResult<Boolean> clearConfigForPreviewExtension(
            @ApiParam("服务器ID") @PathVariable Integer serverId) {
        return previewExtensionService.clearConfigForPreviewExtension(serverId);
    }
}




