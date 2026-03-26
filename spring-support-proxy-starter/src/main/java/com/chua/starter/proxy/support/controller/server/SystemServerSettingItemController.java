package com.chua.starter.proxy.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;
import com.chua.starter.proxy.support.service.server.SystemServerSettingItemService;
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
 * 系统服务器配置项详情表控制器
 *
 * @author CH
 * @since 2025/01/07
 */
@Slf4j
@RestController
@RequestMapping("/proxy/server/setting/item")
@RequiredArgsConstructor
@Api(tags = "系统服务器配置项管理")
@Tag(name = "系统服务器配置项管理", description = "系统服务器配置项的增删改查和参数配置")
public class SystemServerSettingItemController {

    private final SystemServerSettingItemService systemServerSettingItemService;

    /**
     * 分页查询配置项列表
     */
    @GetMapping("/page")
    @ApiOperation("分页查询配置项列表")
    public ReturnResult<IPage<SystemServerSettingItem>> page(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") Integer current,
            @ApiParam("页大小") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam("配置ID") @RequestParam(required = false) Integer settingId,
            @ApiParam("配置项名称") @RequestParam(required = false) String itemName,
            @ApiParam("配置项类型") @RequestParam(required = false) String itemType,
            @ApiParam("是否必填") @RequestParam(required = false) Boolean required) {

        try {
            Page<SystemServerSettingItem> page = new Page<>(current, size);
            SystemServerSettingItem entity = new SystemServerSettingItem();
            entity.setSystemServerSettingItemSettingId(settingId);
            entity.setSystemServerSettingItemName(itemName);
            entity.setSystemServerSettingItemType(itemType);
            entity.setSystemServerSettingItemRequired(required);

            IPage<SystemServerSettingItem> result = systemServerSettingItemService.pageFor(page, entity);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("分页查询配置项列表失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询配置项详情
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询配置项详情")
    public ReturnResult<SystemServerSettingItem> getById(@ApiParam("配置项ID") @PathVariable Integer id) {
        try {
            SystemServerSettingItem item = systemServerSettingItemService.getById(id);
            if (item == null) {
                return ReturnResult.error("配置项不存在");
            }
            return ReturnResult.ok(item);
        } catch (Exception e) {
            log.error("查询配置项详情失败: id={}", id, e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据配置ID查询配置项列表
     */
    @GetMapping("/setting/{settingId}")
    @ApiOperation("根据配置ID查询配置项列表")
    public ReturnResult<List<SystemServerSettingItem>> getBySettingId(
            @ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.getBySettingId(settingId);
    }

    /**
     * 新增配置项
     */
    @PostMapping
    @ApiOperation("新增配置项")
    public ReturnResult<SystemServerSettingItem> add(
            @ApiParam("配置项信息") @RequestBody @Validated(AddGroup.class) SystemServerSettingItem item) {
        try {
            // 验证配置项值
            ReturnResult<Boolean> validateResult = systemServerSettingItemService.validateItemValue(item);
            if (!validateResult.isOk()) {
                return ReturnResult.error(validateResult.getMsg());
            }

            boolean result = systemServerSettingItemService.save(item);
            if (result) {
                return ReturnResult.ok(item);
            } else {
                return ReturnResult.error("新增配置项失败");
            }
        } catch (Exception e) {
            log.error("新增配置项失败", e);
            return ReturnResult.error("新增失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置项
     */
    @PutMapping
    @ApiOperation("更新配置项")
    public ReturnResult<SystemServerSettingItem> update(
            @ApiParam("配置项信息") @RequestBody @Validated(UpdateGroup.class) SystemServerSettingItem item) {
        try {
            // 验证配置项值
            ReturnResult<Boolean> validateResult = systemServerSettingItemService.validateItemValue(item);
            if (!validateResult.isOk()) {
                return ReturnResult.error(validateResult.getMsg());
            }

            boolean result = systemServerSettingItemService.updateById(item);
            if (result) {
                return ReturnResult.ok(item);
            } else {
                return ReturnResult.error("更新配置项失败");
            }
        } catch (Exception e) {
            log.error("更新配置项失败", e);
            return ReturnResult.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置项
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除配置项")
    public ReturnResult<Boolean> delete(@ApiParam("配置项ID") @PathVariable Integer id) {
        try {
            boolean result = systemServerSettingItemService.removeById(id);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("删除配置项失败: id={}", id, e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存配置项
     */
    @PostMapping("/batch")
    @ApiOperation("批量保存配置项")
    public ReturnResult<Boolean> batchSaveItems(
            @ApiParam("配置ID") @RequestParam Integer settingId,
            @ApiParam("配置项列表") @RequestBody List<SystemServerSettingItem> items) {
        return systemServerSettingItemService.batchSaveItems(settingId, items);
    }

    /**
     * 更新配置项值
     */
    @PutMapping("/{id}/value")
    @ApiOperation("更新配置项值")
    public ReturnResult<Boolean> updateItemValue(
            @ApiParam("配置项ID") @PathVariable Integer id,
            @ApiParam("新值") @RequestParam String value) {
        return systemServerSettingItemService.updateItemValue(id, value);
    }

    /**
     * 批量更新配置项值
     */
    @PutMapping("/batch-values")
    @ApiOperation("批量更新配置项值")
    public ReturnResult<Boolean> batchUpdateItemValues(
            @ApiParam("更新列表") @RequestBody List<Map<String, Object>> updates) {
        return systemServerSettingItemService.batchUpdateItemValues(updates);
    }

    /**
     * 根据配置ID删除所有配置项
     */
    @DeleteMapping("/setting/{settingId}")
    @ApiOperation("根据配置ID删除所有配置项")
    public ReturnResult<Boolean> deleteBySettingId(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.deleteBySettingId(settingId);
    }

    /**
     * 验证配置项值
     */
    @PostMapping("/validate")
    @ApiOperation("验证配置项值")
    public ReturnResult<Boolean> validateItemValue(@ApiParam("配置项信息") @RequestBody SystemServerSettingItem item) {
        return systemServerSettingItemService.validateItemValue(item);
    }

    /**
     * 获取配置项的默认值
     */
    @GetMapping("/setting/{settingId}/item/{itemName}/default-value")
    @ApiOperation("获取配置项的默认值")
    public ReturnResult<String> getItemDefaultValue(
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @ApiParam("配置项名称") @PathVariable String itemName) {
        return systemServerSettingItemService.getItemDefaultValue(settingId, itemName);
    }

    /**
     * 重置配置项为默认值
     */
    @PostMapping("/{id}/reset")
    @ApiOperation("重置配置项为默认值")
    public ReturnResult<Boolean> resetItemToDefault(@ApiParam("配置项ID") @PathVariable Integer id) {
        return systemServerSettingItemService.resetItemToDefault(id);
    }

    /**
     * 批量重置配置项为默认值
     */
    @PostMapping("/batch-reset")
    @ApiOperation("批量重置配置项为默认值")
    public ReturnResult<Boolean> batchResetItemsToDefault(@ApiParam("配置项ID列表") @RequestBody List<Integer> itemIds) {
        return systemServerSettingItemService.batchResetItemsToDefault(itemIds);
    }

    /**
     * 根据配置ID和必填状态查询配置项列表
     */
    @GetMapping("/setting/{settingId}/required/{required}")
    @ApiOperation("根据配置ID和必填状态查询配置项列表")
    public ReturnResult<List<SystemServerSettingItem>> getBySettingIdAndRequired(
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @ApiParam("是否必填") @PathVariable Boolean required) {
        return systemServerSettingItemService.getBySettingIdAndRequired(settingId, required);
    }

    /**
     * 检查必填配置项是否都已配置
     */
    @GetMapping("/setting/{settingId}/check-required")
    @ApiOperation("检查必填配置项是否都已配置")
    public ReturnResult<Boolean> checkRequiredItemsConfigured(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.checkRequiredItemsConfigured(settingId);
    }

    /**
     * 将配置项转换为Map格式
     */
    @GetMapping("/setting/{settingId}/as-map")
    @ApiOperation("将配置项转换为Map格式")
    public ReturnResult<Map<String, String>> getItemsAsMap(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.getItemsAsMap(settingId);
    }
}




