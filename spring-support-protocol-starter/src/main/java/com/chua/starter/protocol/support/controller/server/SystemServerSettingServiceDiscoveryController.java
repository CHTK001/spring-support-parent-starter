package com.chua.starter.protocol.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerSettingServiceDiscovery;
import com.chua.starter.protocol.support.entity.SystemServerSettingServiceDiscoveryMapping;
import com.chua.starter.protocol.support.service.server.SystemServerSettingServiceDiscoveryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 协议管理-服务发现配置控制器。
 */
@RestController
@RequestMapping("/system/server/setting/service-discovery")
@Api(tags = "协议管理-服务发现配置")
@Tag(name = "协议管理-服务发现配置")
@RequiredArgsConstructor
public class SystemServerSettingServiceDiscoveryController {

    private final SystemServerSettingServiceDiscoveryService serviceDiscoveryService;

    @GetMapping("/{serverId}")
    @ApiOperation("查询服务发现配置")
    public ReturnResult<List<SystemServerSettingServiceDiscovery>> listByServerId(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(serviceDiscoveryService.listByServerId(serverId));
    }

    @PostMapping("/save")
    @ApiOperation("保存服务发现配置")
    public ReturnResult<SystemServerSettingServiceDiscovery> save(@RequestBody SystemServerSettingServiceDiscovery config) {
        return serviceDiscoveryService.saveOrUpdateConfig(config);
    }

    @DeleteMapping("/{serverId}")
    @ApiOperation("删除服务发现配置")
    public ReturnResult<Boolean> remove(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return serviceDiscoveryService.removeByServerId(serverId);
    }

    @GetMapping("/{serverId}/mappings")
    @ApiOperation("查询服务发现映射")
    public ReturnResult<List<SystemServerSettingServiceDiscoveryMapping>> listMappings(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(serviceDiscoveryService.listMappingsByServerId(serverId));
    }

    @PostMapping("/{serverId}/mappings/save")
    @ApiOperation("保存服务发现映射")
    public ReturnResult<Boolean> saveMappings(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @RequestBody List<SystemServerSettingServiceDiscoveryMapping> mappings
    ) {
        return serviceDiscoveryService.saveOrUpdateMappings(serverId, mappings);
    }
}

