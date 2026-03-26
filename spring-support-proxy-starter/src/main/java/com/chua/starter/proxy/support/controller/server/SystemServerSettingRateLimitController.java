package com.chua.starter.proxy.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingAddressRateLimit;
import com.chua.starter.proxy.support.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.proxy.support.service.server.SystemServerSettingRateLimitService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/proxy/server/setting/rate-limit")
@Api(tags = "限流规则管理")
@Tag(name = "限流规则管理")
@RequiredArgsConstructor
public class SystemServerSettingRateLimitController {

    private final SystemServerSettingRateLimitService rateLimitService;

    // IP
    @GetMapping("/ip/{serverId}/{settingId}")
    @ApiOperation("获取IP限流规则")
    public ReturnResult<List<SystemServerSettingIPRateLimit>> listIp(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                                                     @ApiParam("SystemServerSettingId") @PathVariable Integer settingId) {
        return ReturnResult.ok(rateLimitService.listIpBySetting(serverId, settingId));
    }

    @PostMapping("/ip/{serverId}/{settingId}/save")
    @ApiOperation("保存IP限流规则(保存即热应用)")
    public ReturnResult<Boolean> saveIp(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                        @ApiParam("SystemServerSettingId") @PathVariable Integer settingId,
                                        @RequestBody List<SystemServerSettingIPRateLimit> rules) {
        return rateLimitService.saveIpRules(serverId, settingId, rules);
    }

    @DeleteMapping("/ip/{serverId}/{settingId}")
    @ApiOperation("删除IP限流规则(删除即热应用)")
    public ReturnResult<Boolean> deleteIp(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                          @ApiParam("SystemServerSettingId") @PathVariable Integer settingId) {
        return rateLimitService.deleteIpRules(serverId, settingId);
    }

    // 地址
    @GetMapping("/address/{serverId}/{settingId}")
    @ApiOperation("获取地址限流规则")
    public ReturnResult<List<SystemServerSettingAddressRateLimit>> listAddress(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                                                              @ApiParam("SystemServerSettingId") @PathVariable Integer settingId) {
        return ReturnResult.ok(rateLimitService.listAddressBySetting(serverId, settingId));
    }

    @PostMapping("/address/{serverId}/{settingId}/save")
    @ApiOperation("保存地址限流规则(保存即热应用)")
    public ReturnResult<Boolean> saveAddress(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                             @ApiParam("SystemServerSettingId") @PathVariable Integer settingId,
                                             @RequestBody List<SystemServerSettingAddressRateLimit> rules) {
        return rateLimitService.saveAddressRules(serverId, settingId, rules);
    }

    @DeleteMapping("/address/{serverId}/{settingId}")
    @ApiOperation("删除地址限流规则(删除即热应用)")
    public ReturnResult<Boolean> deleteAddress(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                               @ApiParam("SystemServerSettingId") @PathVariable Integer settingId) {
        return rateLimitService.deleteAddressRules(serverId, settingId);
    }
}






