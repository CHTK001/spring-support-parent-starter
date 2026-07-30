package com.chua.starter.protocol.support.controller.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.protocol.support.entity.SystemServerSettingItem;
import com.chua.starter.protocol.support.service.server.SystemServerSettingItemService;
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
 * 协议管理-过滤器配置项控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/server/setting/item")
@RequiredArgsConstructor
@Api(tags = "协议管理-过滤器配置项")
@Tag(name = "协议管理-过滤器配置项", description = "配置项增删改查与校验")
public class SystemServerSettingItemController {

    private final SystemServerSettingItemService systemServerSettingItemService;

    @GetMapping("/page")
    @ApiOperation("分页查询配置项")
    public ReturnResult<IPage<SystemServerSettingItem>> page(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") Integer current,
            @ApiParam("页大小") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam("配置ID") @RequestParam(required = false) Integer settingId,
            @ApiParam("配置项名称") @RequestParam(required = false) String itemName,
            @ApiParam("配置项类型") @RequestParam(required = false) String itemType,
            @ApiParam("是否必填") @RequestParam(required = false) Boolean required
    ) {
        try {
            Page<SystemServerSettingItem> page = new Page<>(current, size);
            SystemServerSettingItem entity = new SystemServerSettingItem();
            entity.setSystemServerSettingItemSettingId(settingId);
            entity.setSystemServerSettingItemName(itemName);
            entity.setSystemServerSettingItemType(itemType);
            entity.setSystemServerSettingItemRequired(required);
            return ReturnResult.ok(systemServerSettingItemService.pageFor(page, entity));
        } catch (Exception e) {
            log.error("分页查询配置项失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @ApiOperation("按ID查询配置项")
    public ReturnResult<SystemServerSettingItem> getById(@ApiParam("配置项ID") @PathVariable Integer id) {
        SystemServerSettingItem item = systemServerSettingItemService.getById(id);
        if (item == null) {
            return ReturnResult.error("配置项不存在");
        }
        return ReturnResult.ok(item);
    }

    @GetMapping("/setting/{settingId}")
    @ApiOperation("按配置ID查询配置项")
    public ReturnResult<List<SystemServerSettingItem>> getBySettingId(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.getBySettingId(settingId);
    }

    @PostMapping
    @ApiOperation("新增配置项")
    public ReturnResult<SystemServerSettingItem> add(
            @ApiParam("配置项") @RequestBody @Validated(AddGroup.class) SystemServerSettingItem item
    ) {
        ReturnResult<Boolean> validateResult = systemServerSettingItemService.validateItemValue(item);
        if (!validateResult.isOk()) {
            return ReturnResult.error(validateResult.getMsg());
        }
        boolean result = systemServerSettingItemService.save(item);
        return result ? ReturnResult.ok(item) : ReturnResult.error("新增配置项失败");
    }

    @PutMapping
    @ApiOperation("更新配置项")
    public ReturnResult<SystemServerSettingItem> update(
            @ApiParam("配置项") @RequestBody @Validated(UpdateGroup.class) SystemServerSettingItem item
    ) {
        ReturnResult<Boolean> validateResult = systemServerSettingItemService.validateItemValue(item);
        if (!validateResult.isOk()) {
            return ReturnResult.error(validateResult.getMsg());
        }
        boolean result = systemServerSettingItemService.updateById(item);
        return result ? ReturnResult.ok(item) : ReturnResult.error("更新配置项失败");
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除配置项")
    public ReturnResult<Boolean> delete(@ApiParam("配置项ID") @PathVariable Integer id) {
        return ReturnResult.ok(systemServerSettingItemService.removeById(id));
    }

    @PostMapping("/batch")
    @ApiOperation("批量保存配置项")
    public ReturnResult<Boolean> batchSaveItems(
            @ApiParam("配置ID") @RequestParam Integer settingId,
            @ApiParam("配置项列表") @RequestBody List<SystemServerSettingItem> items
    ) {
        return systemServerSettingItemService.batchSaveItems(settingId, items);
    }

    @PutMapping("/{id}/value")
    @ApiOperation("更新配置项值")
    public ReturnResult<Boolean> updateItemValue(
            @ApiParam("配置项ID") @PathVariable Integer id,
            @ApiParam("新值") @RequestParam String value
    ) {
        return systemServerSettingItemService.updateItemValue(id, value);
    }

    @PutMapping("/batch-values")
    @ApiOperation("批量更新配置项值")
    public ReturnResult<Boolean> batchUpdateItemValues(@ApiParam("更新列表") @RequestBody List<Map<String, Object>> updates) {
        return systemServerSettingItemService.batchUpdateItemValues(updates);
    }

    @DeleteMapping("/setting/{settingId}")
    @ApiOperation("按配置ID删除全部配置项")
    public ReturnResult<Boolean> deleteBySettingId(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.deleteBySettingId(settingId);
    }

    @PostMapping("/validate")
    @ApiOperation("校验配置项值")
    public ReturnResult<Boolean> validateItemValue(@ApiParam("配置项") @RequestBody SystemServerSettingItem item) {
        return systemServerSettingItemService.validateItemValue(item);
    }

    @GetMapping("/setting/{settingId}/item/{itemName}/default-value")
    @ApiOperation("获取配置项默认值")
    public ReturnResult<String> getItemDefaultValue(
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @ApiParam("配置项名") @PathVariable String itemName
    ) {
        return systemServerSettingItemService.getItemDefaultValue(settingId, itemName);
    }

    @PostMapping("/{id}/reset")
    @ApiOperation("重置配置项为默认值")
    public ReturnResult<Boolean> resetItemToDefault(@ApiParam("配置项ID") @PathVariable Integer id) {
        return systemServerSettingItemService.resetItemToDefault(id);
    }

    @PostMapping("/batch-reset")
    @ApiOperation("批量重置配置项为默认值")
    public ReturnResult<Boolean> batchResetItemsToDefault(@ApiParam("配置项ID列表") @RequestBody List<Integer> itemIds) {
        return systemServerSettingItemService.batchResetItemsToDefault(itemIds);
    }

    @GetMapping("/setting/{settingId}/required/{required}")
    @ApiOperation("按是否必填查询配置项")
    public ReturnResult<List<SystemServerSettingItem>> getBySettingIdAndRequired(
            @ApiParam("配置ID") @PathVariable Integer settingId,
            @ApiParam("是否必填") @PathVariable Boolean required
    ) {
        return systemServerSettingItemService.getBySettingIdAndRequired(settingId, required);
    }

    @GetMapping("/setting/{settingId}/check-required")
    @ApiOperation("检查必填项是否已配置")
    public ReturnResult<Boolean> checkRequiredItemsConfigured(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.checkRequiredItemsConfigured(settingId);
    }

    @GetMapping("/setting/{settingId}/as-map")
    @ApiOperation("将配置项转换为Map")
    public ReturnResult<Map<String, String>> getItemsAsMap(@ApiParam("配置ID") @PathVariable Integer settingId) {
        return systemServerSettingItemService.getItemsAsMap(settingId);
    }
}

