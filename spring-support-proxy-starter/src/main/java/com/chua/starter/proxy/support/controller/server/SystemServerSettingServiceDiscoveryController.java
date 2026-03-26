package com.chua.starter.proxy.support.controller.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscovery;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscoveryMapping;
import com.chua.starter.proxy.support.service.server.SystemServerSettingServiceDiscoveryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ServiceDiscovery 配置管理
 */
@RestController
@RequestMapping("/proxy/server/setting/service-discovery")
@Api(tags = "服务发现配置管理")
@Tag(name = "服务发现配置管理")
@RequiredArgsConstructor
public class SystemServerSettingServiceDiscoveryController {

    private final SystemServerSettingServiceDiscoveryService serviceDiscoveryService;

    @GetMapping("/{serverId}")
    @ApiOperation("获取服务器的ServiceDiscovery配置")
    public ReturnResult<List<SystemServerSettingServiceDiscovery>> listByServerId(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(serviceDiscoveryService.listByServerId(serverId));
    }

    @PostMapping("/save")
    @ApiOperation("保存或更新ServiceDiscovery配置(保存即热应用)")
    public ReturnResult<SystemServerSettingServiceDiscovery> save(@RequestBody SystemServerSettingServiceDiscovery config) {
        return serviceDiscoveryService.saveOrUpdateConfig(config);
    }

    @DeleteMapping("/{serverId}")
    @ApiOperation("删除服务器的ServiceDiscovery配置(删除即热应用)")
    public ReturnResult<Boolean> remove(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return serviceDiscoveryService.removeByServerId(serverId);
    }

    @GetMapping("/{serverId}/mappings")
    @ApiOperation("获取服务器的ServiceDiscovery映射明细")
    public ReturnResult<List<SystemServerSettingServiceDiscoveryMapping>> listMappings(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return ReturnResult.ok(serviceDiscoveryService.listMappingsByServerId(serverId));
    }

    @PostMapping("/{serverId}/mappings/save")
    @ApiOperation("保存服务器的ServiceDiscovery映射明细(保存即热应用)")
    public ReturnResult<Boolean> saveMappings(@ApiParam("服务器ID") @PathVariable Integer serverId,
                                              @RequestBody List<SystemServerSettingServiceDiscoveryMapping> mappings) {
        return serviceDiscoveryService.saveOrUpdateMappings(serverId, mappings);
    }
}





