package com.chua.starter.proxy.support.service.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;

import java.util.List;
import java.util.Map;

/**
 * 系统服务器配置项详情表服务接口
 *
 * @author CH
 * @since 2025/01/07
 */
public interface SystemServerSettingItemService extends IService<SystemServerSettingItem> {

    /**
     * 分页查询配置项列表
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SystemServerSettingItem> pageFor(Page<SystemServerSettingItem> page, SystemServerSettingItem entity);

    /**
     * 根据配置ID查询配置项列表
     *
     * @param settingId 配置ID
     * @return 配置项列表
     */
    ReturnResult<List<SystemServerSettingItem>> getBySettingId(Integer settingId);

    /**
     * 批量保存配置项
     *
     * @param settingId 配置ID
     * @param items     配置项列表
     * @return 保存结果
     */
    ReturnResult<Boolean> batchSaveItems(Integer settingId, List<SystemServerSettingItem> items);

    /**
     * 更新配置项值
     *
     * @param itemId 配置项ID
     * @param value  新值
     * @return 更新结果
     */
    ReturnResult<Boolean> updateItemValue(Integer itemId, String value);

    /**
     * 批量更新配置项值
     *
     * @param updates 更新列表，格式：[{itemId: 1, value: "newValue"}, ...]
     * @return 更新结果
     */
    ReturnResult<Boolean> batchUpdateItemValues(List<Map<String, Object>> updates);

    /**
     * 根据配置ID删除所有配置项
     *
     * @param settingId 配置ID
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteBySettingId(Integer settingId);

    /**
     * 验证配置项值
     *
     * @param item 配置项
     * @return 验证结果
     */
    ReturnResult<Boolean> validateItemValue(SystemServerSettingItem item);

    /**
     * 获取配置项的默认值
     *
     * @param settingId 配置ID
     * @param itemName  配置项名称
     * @return 默认值
     */
    ReturnResult<String> getItemDefaultValue(Integer settingId, String itemName);

    /**
     * 重置配置项为默认值
     *
     * @param itemId 配置项ID
     * @return 重置结果
     */
    ReturnResult<Boolean> resetItemToDefault(Integer itemId);

    /**
     * 批量重置配置项为默认值
     *
     * @param itemIds 配置项ID列表
     * @return 重置结果
     */
    ReturnResult<Boolean> batchResetItemsToDefault(List<Integer> itemIds);

    /**
     * 根据配置ID和必填状态查询配置项列表
     *
     * @param settingId 配置ID
     * @param required  是否必填
     * @return 配置项列表
     */
    ReturnResult<List<SystemServerSettingItem>> getBySettingIdAndRequired(Integer settingId, Boolean required);

    /**
     * 检查必填配置项是否都已配置
     *
     * @param settingId 配置ID
     * @return 检查结果
     */
    ReturnResult<Boolean> checkRequiredItemsConfigured(Integer settingId);

    /**
     * 将配置项转换为Map格式
     *
     * @param settingId 配置ID
     * @return 配置项Map
     */
    ReturnResult<Map<String, String>> getItemsAsMap(Integer settingId);
}




