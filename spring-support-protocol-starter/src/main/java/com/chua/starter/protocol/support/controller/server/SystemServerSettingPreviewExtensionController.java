package com.chua.starter.protocol.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.pojo.tool.PreviewExtensionConfigVo;
import com.chua.starter.protocol.support.service.server.SystemServerSettingPreviewExtensionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 协议管理-预览扩展名控制器。
 */
@RestController
@RequestMapping("/system/server/setting/preview-extension")
@Api(tags = "协议管理-预览扩展名配置")
@Tag(name = "协议管理-预览扩展名配置")
@RequiredArgsConstructor
public class SystemServerSettingPreviewExtensionController {

    private final SystemServerSettingPreviewExtensionService previewExtensionService;

    @GetMapping("/{serverId}")
    @ApiOperation("查询预览扩展名配置")
    public ReturnResult<PreviewExtensionConfigVo> getConfig(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(previewExtensionService.getByServerIdForPreviewExtension(serverId));
    }

    @PostMapping("/save")
    @ApiOperation("保存预览扩展名配置")
    public ReturnResult<Boolean> saveConfig(@ApiParam("配置") @RequestBody PreviewExtensionConfigVo config) {
        return previewExtensionService.saveConfigForPreviewExtension(config);
    }

    @PostMapping("/{serverId}/disabled/add")
    @ApiOperation("添加禁用扩展名")
    public ReturnResult<Boolean> addDisabledExtensions(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions
    ) {
        return previewExtensionService.addDisabledExtensionsForPreviewExtension(serverId, extensions);
    }

    @PostMapping("/{serverId}/disabled/remove")
    @ApiOperation("移除禁用扩展名")
    public ReturnResult<Boolean> removeDisabledExtensions(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions
    ) {
        return previewExtensionService.removeDisabledExtensionsForPreviewExtension(serverId, extensions);
    }

    @PostMapping("/{serverId}/allowed/add")
    @ApiOperation("添加允许扩展名")
    public ReturnResult<Boolean> addAllowedExtensions(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions
    ) {
        return previewExtensionService.addAllowedExtensionsForPreviewExtension(serverId, extensions);
    }

    @PostMapping("/{serverId}/allowed/remove")
    @ApiOperation("移除允许扩展名")
    public ReturnResult<Boolean> removeAllowedExtensions(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("扩展名列表") @RequestBody List<String> extensions
    ) {
        return previewExtensionService.removeAllowedExtensionsForPreviewExtension(serverId, extensions);
    }

    @PutMapping("/{serverId}/mode")
    @ApiOperation("切换白名单模式")
    public ReturnResult<Boolean> setPreviewMode(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("白名单模式") @RequestParam Boolean whitelistMode
    ) {
        return previewExtensionService.setPreviewModeForPreviewExtension(serverId, whitelistMode);
    }

    @PostMapping("/{serverId}/clear")
    @ApiOperation("清空扩展名配置")
    public ReturnResult<Boolean> clearConfig(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return previewExtensionService.clearConfigForPreviewExtension(serverId);
    }
}

