package com.chua.starter.proxy.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.spi.SpiOption;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.pojo.ServerSettingOrderRequest;
import com.chua.starter.proxy.support.pojo.ViewerConfigVO;
import com.chua.starter.proxy.support.pojo.ViewerInfoVO;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;

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
 * 系统服务器配置表控制器
 *
 * @author CH
 * @since 2025/01/07
 */
@Slf4j
@RestController
@RequestMapping("/proxy/server/setting")
@RequiredArgsConstructor
@Api(tags = "系统服务器配置管理")
@Tag(name = "系统服务器配置管理", description = "系统服务器配置的增删改查和ServletFilter管理")
public class SystemServerSettingController {

    private final SystemServerSettingService systemServerSettingService;

    /**
     * 分页查询配置列表
     */
    @GetMapping("/page")
    @ApiOperation("分页查询配置列表")
    public ReturnResult<IPage<SystemServerSetting>> page(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") Integer current,
            @ApiParam("页大小") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam("服务器ID") @RequestParam(required = false) Integer serverId,
            @ApiParam("配置名称") @RequestParam(required = false) String settingName,
            @ApiParam("配置类型") @RequestParam(required = false) String settingType,
            @ApiParam("启用状态") @RequestParam(required = false) Boolean enabled) {

        try {
            Page<SystemServerSetting> page = new Page<>(current, size);
            SystemServerSetting entity = new SystemServerSetting();
            entity.setSystemServerSettingServerId(serverId);
            entity.setSystemServerSettingName(settingName);
            entity.setSystemServerSettingType(settingType);
            entity.setSystemServerSettingEnabled(enabled);

            IPage<SystemServerSetting> result = systemServerSettingService.pageFor(page, entity);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("分页查询配置列表失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询配置详情
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询配置详情")
    public ReturnResult<SystemServerSetting> getById(@ApiParam("配置ID") @PathVariable Integer id) {
        try {
            SystemServerSetting setting = systemServerSettingService.getById(id);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }
            return ReturnResult.ok(setting);
        } catch (Exception e) {
            log.error("查询配置详情失败: id={}", id, e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据服务器ID查询已安装的ServletFilter配置
     */
    @GetMapping("/server/{serverId}")
    @ApiOperation("根据服务器ID查询已安装的ServletFilter配置")
    public ReturnResult<List<SystemServerSetting>> getServerInstalledFilters(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return systemServerSettingService.getServerInstalledFilters(serverId);
    }
    /**
     * 根据服务器ID查询已安装的ServletFilter配置
     */
    @PostMapping("/server/{serverId}/order")
    @ApiOperation("根据服务器排序ServletFilter配置")
    public ReturnResult<Boolean> orderServerInstalledFilters(@RequestBody List<ServerSettingOrderRequest> settingOrders) {
        return systemServerSettingService.orderServerInstalledFilters(settingOrders);
    }

    /**
     * 新增配置
     */
    @PostMapping
    @ApiOperation("新增配置")
    public ReturnResult<SystemServerSetting> add(
            @ApiParam("配置信息") @RequestBody @Validated(AddGroup.class) SystemServerSetting setting) {
        try {
            boolean result = systemServerSettingService.save(setting);
            if (result) {
                return ReturnResult.ok(setting);
            } else {
                return ReturnResult.error("新增配置失败");
            }
        } catch (Exception e) {
            log.error("新增配置失败", e);
            return ReturnResult.error("新增失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置
     */
    @PutMapping
    @ApiOperation("更新配置")
    public ReturnResult<SystemServerSetting> update(
            @ApiParam("配置信息") @RequestBody @Validated(UpdateGroup.class) SystemServerSetting setting) {
        try {
            boolean result = systemServerSettingService.updateById(setting);
            if (result) {
                // 应用配置到运行中的服务器
                systemServerSettingService.applyConfigToRunningServer(setting.getSystemServerSettingServerId());
                return ReturnResult.ok(setting);
            } else {
                return ReturnResult.error("更新配置失败");
            }
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return ReturnResult.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置
     */
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
                // 应用配置到运行中的服务器
                systemServerSettingService.applyConfigToRunningServer(setting.getSystemServerSettingServerId());
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("删除配置失败: id={}", id, e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的ServletFilter对象列表
     */
    @GetMapping("/objects")
    @ApiOperation("获取可用的ServletFilter对象列表")
    public ReturnResult<List<SpiOption>> getAvailableServletFilterObjects(
            @ApiParam("服务器ID") @RequestParam(required = false) Integer serverId) {
        return systemServerSettingService.getAvailableServletFilterObjects(serverId);
    }

    /**
     * 为服务器安装ServletFilter
     */
    @PostMapping("/install")
    @ApiOperation("为服务器安装ServletFilter")
    public ReturnResult<SystemServerSetting> installServletFilter(
            @ApiParam("服务器ID") @RequestParam Integer serverId,
            @ApiParam("Filter类型") @RequestParam String filterType) {
        return systemServerSettingService.installServletFilter(serverId, filterType);
    }

    /**
     * 卸载ServletFilter
     */
    @DeleteMapping("/{id}/uninstall")
    @ApiOperation("卸载ServletFilter")
    public ReturnResult<Boolean> uninstallServletFilter(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.uninstallServletFilter(id);
    }

    /**
     * 启用ServletFilter
     */
    @PostMapping("/{id}/enable")
    @ApiOperation("启用ServletFilter")
    public ReturnResult<Boolean> enableServletFilter(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.enableServletFilter(id);
    }

    /**
     * 禁用ServletFilter
     */
    @PostMapping("/{id}/disable")
    @ApiOperation("禁用ServletFilter")
    public ReturnResult<Boolean> disableServletFilter(@ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.disableServletFilter(id);
    }

    /**
     * 更新ServletFilter排序
     */
    @PutMapping("/order")
    @ApiOperation("更新ServletFilter排序")
    public ReturnResult<Boolean> updateServletFilterOrder(
            @ApiParam("排序更新列表") @RequestBody List<Map<String, Integer>> orderUpdates) {
        return systemServerSettingService.updateServletFilterOrder(orderUpdates);
    }

    /**
     * 获取ServletFilter配置项定义
     */
    @GetMapping("/servlet-filters/{filterType}/config-items")
    @ApiOperation("获取ServletFilter配置项定义")
    public ReturnResult<List<Map<String, Object>>> getServletFilterConfigItems(
            @ApiParam("Filter类型") @PathVariable String filterType) {
        return systemServerSettingService.getServletFilterConfigItems(filterType);
    }

    /**
     * 更新ServletFilter配置
     */
    @PutMapping("/{id}/config")
    @ApiOperation("更新ServletFilter配置")
    public ReturnResult<Boolean> updateServletFilterConfig(
            @ApiParam("配置ID") @PathVariable Integer id,
            @ApiParam("配置数据") @RequestBody Map<String, Object> config) {
        return systemServerSettingService.updateServletFilterConfig(id, config);
    }

    /**
     * 获取ServletFilter配置
     */
    @GetMapping("/{id}/config")
    @ApiOperation("获取ServletFilter配置")
    public ReturnResult<Map<String, Object>>getServletFilterConfig(
            @ApiParam("配置ID") @PathVariable Integer id) {
        return systemServerSettingService.getServletFilterConfig(id);
    }

    /**
     * 应用配置到运行中的服务器
     */
    @PostMapping("/server/{serverId}/apply")
    @ApiOperation("应用配置到运行中的服务器")
    public ReturnResult<Boolean> applyConfigToRunningServer(@ApiParam("服务器ID") @PathVariable Integer serverId) {
        return systemServerSettingService.applyConfigToRunningServer(serverId);
    }

    /**
     * 克隆服务器配置
     */
    @PostMapping("/clone")
    @ApiOperation("克隆服务器配置")
    public ReturnResult<Boolean> cloneServerSettings(
            @ApiParam("源服务器ID") @RequestParam Integer sourceServerId,
            @ApiParam("目标服务器ID") @RequestParam Integer targetServerId) {
        return systemServerSettingService.cloneServerSettings(sourceServerId, targetServerId);
    }

    /**
     * 根据服务器ID和启用状态查询配置列表
     */
    @GetMapping("/server/{serverId}/enabled/{enabled}")
    @ApiOperation("根据服务器ID和启用状态查询配置列表")
    public ReturnResult<List<SystemServerSetting>> getByServerIdAndEnabled(
            @ApiParam("服务器ID") @PathVariable Integer serverId,
            @ApiParam("启用状态") @PathVariable Boolean enabled) {
        return systemServerSettingService.getByServerIdAndEnabled(serverId, enabled);
    }

    /**
     * 批量启用/禁用ServletFilter
     */
    @PutMapping("/batch-enabled")
    @ApiOperation("批量启用/禁用ServletFilter")
    public ReturnResult<Boolean> batchUpdateEnabled(
            @ApiParam("配置ID列表") @RequestParam List<Integer> settingIds,
            @ApiParam("启用状态") @RequestParam Boolean enabled) {
        return systemServerSettingService.batchUpdateEnabled(settingIds, enabled);
    }


    // ==================== Viewer 视图查看器配置 ====================

    /**
     * 获取可用的视图查看器列表（通过SPI发现）
     *
     * @param settingId 配置ID
     * @return 查看器信息列表
     */
    @GetMapping("/{settingId}/viewers")
    @ApiOperation("获取可用的视图查看器列表")
    public ReturnResult<List<ViewerInfoVO>> getViewerListForSetting(
            @ApiParam("配置ID") @PathVariable Integer settingId) {
        try {
            return systemServerSettingService.getViewerList(settingId);
        } catch (Exception e) {
            log.error("获取视图查看器列表失败", e);
            return ReturnResult.error("获取视图查看器列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取视图查看器配置
     *
     * @param settingId 配置ID
     * @return 查看器配置（包含禁用的查看器列表）
     */
    @GetMapping("/{settingId}/viewer-config")
    @ApiOperation("获取视图查看器配置")
    public ReturnResult<ViewerConfigVO> getViewerConfigForSetting(
            @ApiParam("配置ID") @PathVariable Integer settingId) {
        try {
            return systemServerSettingService.getViewerConfig(settingId);
        } catch (Exception e) {
            log.error("获取视图查看器配置失败", e);
            return ReturnResult.error("获取视图查看器配置失败: " + e.getMessage());
        }
    }

    /**
     * 保存视图查看器配置
     * <p>
     * 配置内容包括：
     * - disabledViewers: 禁用的查看器名称列表
     *
     * @param settingId 配置ID
     * @param config    查看器配置
     * @return 是否保存成功
     */
    @PostMapping("/{settingId}/viewer-config")
    @ApiOperation("保存视图查看器配置")
    public ReturnResult<Boolean> saveViewerConfigForSetting(
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @ApiParam("查看器配置") @RequestBody ViewerConfigVO config) {
        try {
            return systemServerSettingService.saveViewerConfig(settingId, config);
        } catch (Exception e) {
            log.error("保存视图查看器配置失败", e);
            return ReturnResult.error("保存视图查看器配置失败: " + e.getMessage());
        }
    }
}




