package com.chua.starter.monitor.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.entity.SystemServerSettingAddressRateLimit;
import com.chua.starter.monitor.entity.SystemServerSettingIPRateLimit;
import com.chua.starter.monitor.service.SystemServerSettingRateLimitService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 限流规则管理控制器（IP/地址）
 */
@RestController
@RequestMapping("/system/server/setting/rate-limit")
@Api(tags = "限流规则管理")
@Tag(name = "限流规则管理")
@RequiredArgsConstructor
public class SystemServerSettingRateLimitController {

    private final SystemServerSettingRateLimitService rateLimitService;

    // IP 限流
    @GetMapping("/ip/{serverId}")
    @ApiOperation("获取服务器的IP限流规则")
    public ReturnResult<List<SystemServerSettingIPRateLimit>> listIp(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(rateLimitService.listIpRules(serverId));
    }

    @PostMapping("/ip/{serverId}/save")
    @ApiOperation("保存IP限流规则(保存即热应用)")
    public ReturnResult<Boolean> saveIp(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                        @RequestBody List<SystemServerSettingIPRateLimit> rules) {
        return rateLimitService.saveIpRules(serverId, rules);
    }

    @DeleteMapping("/ip/{serverId}")
    @ApiOperation("删除服务器的IP限流规则(删除即热应用)")
    public ReturnResult<Boolean> deleteIp(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return rateLimitService.deleteIpRulesByServer(serverId);
    }

    // 地址 限流
    @GetMapping("/address/{serverId}")
    @ApiOperation("获取服务器的地址限流规则")
    public ReturnResult<List<SystemServerSettingAddressRateLimit>> listAddress(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(rateLimitService.listAddressRules(serverId));
    }

    @PostMapping("/address/{serverId}/save")
    @ApiOperation("保存地址限流规则(保存即热应用)")
    public ReturnResult<Boolean> saveAddress(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                             @RequestBody List<SystemServerSettingAddressRateLimit> rules) {
        return rateLimitService.saveAddressRules(serverId, rules);
    }

    @DeleteMapping("/address/{serverId}")
    @ApiOperation("删除服务器的地址限流规则(删除即热应用)")
    public ReturnResult<Boolean> deleteAddress(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return rateLimitService.deleteAddressRulesByServer(serverId);
    }
}

