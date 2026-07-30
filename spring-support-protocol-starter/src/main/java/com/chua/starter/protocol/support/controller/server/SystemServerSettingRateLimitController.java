package com.chua.starter.protocol.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerSettingAddressRateLimit;
import com.chua.starter.protocol.support.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.protocol.support.service.server.SystemServerSettingRateLimitService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 协议管理-限流配置控制器。
 */
@RestController
@RequestMapping("/system/server/setting/rate-limit")
@Api(tags = "协议管理-限流配置")
@Tag(name = "协议管理-限流配置")
@RequiredArgsConstructor
public class SystemServerSettingRateLimitController {

    private final SystemServerSettingRateLimitService rateLimitService;

    @GetMapping("/ip/{serverId}/{settingId}")
    @ApiOperation("查询IP限流规则")
    public ReturnResult<List<SystemServerSettingIPRateLimit>> listIp(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("配置ID") @PathVariable Integer settingId
    ) {
        return ReturnResult.ok(rateLimitService.listIpBySetting(serverId, settingId));
    }

    @PostMapping("/ip/{serverId}/{settingId}/save")
    @ApiOperation("保存IP限流规则")
    public ReturnResult<Boolean> saveIp(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @RequestBody List<SystemServerSettingIPRateLimit> rules
    ) {
        return rateLimitService.saveIpRules(serverId, settingId, rules);
    }

    @DeleteMapping("/ip/{serverId}/{settingId}")
    @ApiOperation("删除IP限流规则")
    public ReturnResult<Boolean> deleteIp(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("配置ID") @PathVariable Integer settingId
    ) {
        return rateLimitService.deleteIpRules(serverId, settingId);
    }

    @GetMapping("/address/{serverId}/{settingId}")
    @ApiOperation("查询地址限流规则")
    public ReturnResult<List<SystemServerSettingAddressRateLimit>> listAddress(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("配置ID") @PathVariable Integer settingId
    ) {
        return ReturnResult.ok(rateLimitService.listAddressBySetting(serverId, settingId));
    }

    @PostMapping("/address/{serverId}/{settingId}/save")
    @ApiOperation("保存地址限流规则")
    public ReturnResult<Boolean> saveAddress(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @RequestBody List<SystemServerSettingAddressRateLimit> rules
    ) {
        return rateLimitService.saveAddressRules(serverId, settingId, rules);
    }

    @DeleteMapping("/address/{serverId}/{settingId}")
    @ApiOperation("删除地址限流规则")
    public ReturnResult<Boolean> deleteAddress(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("配置ID") @PathVariable Integer settingId
    ) {
        return rateLimitService.deleteAddressRules(serverId, settingId);
    }
}

