package com.chua.starter.protocol.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.common.support.spi.SpiOption;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerSetting;
import com.chua.starter.protocol.support.pojo.server.ServerSettingOrderRequest;
import com.chua.starter.protocol.support.pojo.tool.ViewerConfigVO;
import com.chua.starter.protocol.support.pojo.tool.ViewerInfoVO;
import com.chua.starter.protocol.support.service.server.SystemServerSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 协议管理-服务配置控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/server/setting")
@RequiredArgsConstructor
@Api(tags = "协议管理-服务配置")
@Tag(name = "协议管理-服务配置", description = "服务过滤器配置管理")
public class SystemServerSettingController {

    private final SystemServerSettingService systemServerSettingService;

    @GetMapping("/page")
    @ApiOperation("分页查询配置列表")
    public ReturnResult<IPage<SystemServerSetting>> page(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") Integer current,
            @ApiParam("页大小") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam("服务ID") @RequestParam(required = false) Integer serverId,
            @ApiParam("配置名称") @RequestParam(required = false) String settingName,
            @ApiParam("配置类型") @RequestParam(required = false) String settingType,
            @ApiParam("启用状态") @RequestParam(required = false) Boolean enabled
    ) {
        try {
            Page<SystemServerSetting> page = new Page<>(current, size);
            SystemServerSetting entity = new SystemServerSetting();
            entity.setSystemServerSettingServerId(serverId);
            entity.setSystemServerSettingName(settingName);
            entity.setSystemServerSettingType(settingType);
            entity.setSystemServerSettingEnabled(enabled);
            return ReturnResult.ok(systemServerSettingService.pageFor(page, entity));
        } catch (Exception e) {
            log.error("分页查询配置失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @ApiOperation("查询配置详情")
    public ReturnResult<SystemServerSetting> getById(@ApiParam("配置ID") @PathVariable Integer id) {
        SystemServerSetting setting = systemServerSettingService.getById(id);
        if (setting == null) {
            return ReturnResult.error("配置不存在");
        }
        return ReturnResult.ok(setting);
    }

    @GetMapping("/server/{serverId}")
    @ApiOperation("查询服务已安装配置")
    public ReturnResult<List<SystemServerSetting>> getServerInstalledFilters(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return systemServerSettingService.getServerInstalledFilters(serverId);
    }

    @PostMapping("/server/{serverId}/order")
    @ApiOperation("按顺序保存已安装配置")
    public ReturnResult<Boolean> orderServerInstalledFilters(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("排序列表") @RequestBody List<ServerSettingOrderRequest> settingOrders
    ) {
        return systemServerSettingService.orderServerInstalledFilters(settingOrders);
    }

    @PostMapping
    @ApiOperation("新增配置")
    public ReturnResult<SystemServerSetting> add(
            @ApiParam("配置信息") @RequestBody @Validated(AddGroup.class) SystemServerSetting setting
    ) {
        try {
            return systemServerSettingService.save(setting) ? ReturnResult.ok(setting) : ReturnResult.error("新增配置失败");
        } catch (Exception e) {
            log.error("新增配置失败", e);
            return ReturnResult.error("新增失败: " + e.getMessage());
        }
    }

    @PutMapping
    @ApiOperation("更新配置")
    public ReturnResult<SystemServerSetting> update(
            @ApiParam("配置信息") @RequestBody @Validated(UpdateGroup.class) SystemServerSetting setting
    ) {
        try {
            boolean result = systemServerSettingService.updateById(setting);
            if (result) {
                systemServerSettingService.applyConfigToRunningServer(setting.getSystemServerSettingServerId());
            }
            return result ? ReturnResult.ok(setting) : ReturnResult.error("更新配置失败");
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return ReturnResult.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除配置")
    public ReturnResult<Boolean> delete(@ApiParam("配置ID") @PathVariable Integer id) {
        try {
            SystemServerSetting setting = systemServerSettingService.getById(id);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }
            boolean result = systemServerSettingService.removeById(id);
            if (result) {
                systemServerSettingService.applyConfigToRunningServer(setting.getSystemServerSettingServerId());
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("删除配置失败: id={}", id, e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/objects")
    @ApiOperation("获取可用过滤器对象")
    public ReturnResult<List<SpiOption>> getAvailableServletFilterObjects(
            @ApiParam("服务ID") @RequestParam(required = false) Integer serverId
    ) {
        return systemServerSettingService.getAvailableServletFilterObjects(serverId);
    }

    @PostMapping("/install")
    @ApiOperation("安装过滤器")
    public ReturnResult<SystemServerSetting> installServletFilter(
            @ApiParam("服务ID") @RequestParam Integer serverId,
            @ApiParam("过滤器类型") @RequestParam String filterType
    ) {
        return systemServerSettingService.installServletFilter(serverId, filterType);
    }

    @DeleteMapping("/{id}/uninstall")
    @ApiOperation("卸载过滤器")
    public ReturnResult<Boolean> uninstallServletFilter(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.uninstallServletFilter(id);
    }

    @PostMapping("/{id}/enable")
    @ApiOperation("启用过滤器")
    public ReturnResult<Boolean> enableServletFilter(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.enableServletFilter(id);
    }

    @PostMapping("/{id}/disable")
    @ApiOperation("禁用过滤器")
    public ReturnResult<Boolean> disableServletFilter(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.disableServletFilter(id);
    }

    @PutMapping("/order")
    @ApiOperation("更新过滤器排序")
    public ReturnResult<Boolean> updateServletFilterOrder(@ApiParam("排序更新列表") @RequestBody List<Map<String, Integer>> orderUpdates) {
        return systemServerSettingService.updateServletFilterOrder(orderUpdates);
    }

    @GetMapping("/servlet-filters/{filterType}/config-items")
    @ApiOperation("获取过滤器配置项定义")
    public ReturnResult<List<Map<String, Object>>> getServletFilterConfigItems(@ApiParam("过滤器类型") @PathVariable String filterType) {
        return systemServerSettingService.getServletFilterConfigItems(filterType);
    }

    @PutMapping("/{id}/config")
    @ApiOperation("更新过滤器配置")
    public ReturnResult<Boolean> updateServletFilterConfig(
            @ApiParam("配置ID") @PathVariable Integer id,
            @ApiParam("配置数据") @RequestBody Map<String, Object> config
    ) {
        return systemServerSettingService.updateServletFilterConfig(id, config);
    }

    @PostMapping("/{id}/config")
    @ApiOperation("更新过滤器配置(兼容POST)")
    public ReturnResult<Boolean> updateServletFilterConfigByPost(
            @ApiParam("配置ID") @PathVariable Integer id,
            @ApiParam("配置数据") @RequestBody Map<String, Object> config
    ) {
        return systemServerSettingService.updateServletFilterConfig(id, config);
    }

    @GetMapping("/{id}/config")
    @ApiOperation("获取过滤器配置")
    public ReturnResult<Map<String, Object>> getServletFilterConfig(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.getServletFilterConfig(id);
    }

    @PostMapping("/server/{serverId}/apply")
    @ApiOperation("热应用服务配置")
    public ReturnResult<Boolean> applyConfigToRunningServer(@ApiParam("服务ID") @PathVariable Integer serverId) {
        return systemServerSettingService.applyConfigToRunningServer(serverId);
    }

    @PostMapping("/clone")
    @ApiOperation("克隆服务配置")
    public ReturnResult<Boolean> cloneServerSettings(
            @ApiParam("源服务ID") @RequestParam Integer sourceServerId,
            @ApiParam("目标服务ID") @RequestParam Integer targetServerId
    ) {
        return systemServerSettingService.cloneServerSettings(sourceServerId, targetServerId);
    }

    @GetMapping("/server/{serverId}/enabled/{enabled}")
    @ApiOperation("按启用状态查询配置列表")
    public ReturnResult<List<SystemServerSetting>> getByServerIdAndEnabled(
            @ApiParam("服务ID") @PathVariable Integer serverId,
            @ApiParam("启用状态") @PathVariable Boolean enabled
    ) {
        return systemServerSettingService.getByServerIdAndEnabled(serverId, enabled);
    }

    @PutMapping("/batch-enabled")
    @ApiOperation("批量启用/禁用过滤器")
    public ReturnResult<Boolean> batchUpdateEnabled(
            @ApiParam("配置ID列表") @RequestParam List<Integer> settingIds,
            @ApiParam("启用状态") @RequestParam Boolean enabled
    ) {
        return systemServerSettingService.batchUpdateEnabled(settingIds, enabled);
    }

    @GetMapping("/{settingId}/viewers")
    @ApiOperation("获取可用查看器列表")
    public ReturnResult<List<ViewerInfoVO>> getViewerListForSetting(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingService.getViewerList(settingId);
    }

    @GetMapping("/{settingId}/viewer-config")
    @ApiOperation("获取查看器配置")
    public ReturnResult<ViewerConfigVO> getViewerConfigForSetting(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingService.getViewerConfig(settingId);
    }

    @PostMapping("/{settingId}/viewer-config")
    @ApiOperation("保存查看器配置")
    public ReturnResult<Boolean> saveViewerConfigForSetting(
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @ApiParam("查看器配置") @RequestBody ViewerConfigVO config
    ) {
        return systemServerSettingService.saveViewerConfig(settingId, config);
    }
}

