package com.chua.starter.proxy.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统服务器配置项详情表 Mapper 接口
 *
 * @author CH
 * @since 2025/01/07
 */
@Mapper
public interface SystemServerSettingItemMapper extends BaseMapper<SystemServerSettingItem> {

    /**
     * 分页查询配置项列表
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SystemServerSettingItem> pageFor(Page<SystemServerSettingItem> page,
                                           @Param("entity") SystemServerSettingItem entity);

    /**
     * 根据配置ID查询配置项列表
     *
     * @param settingId 配置ID
     * @return 配置项列表
     */
    List<SystemServerSettingItem> selectBySettingId(@Param("settingId") Integer settingId);

    /**
     * 根据配置ID和配置项名称查询配置项
     *
     * @param settingId 配置ID
     * @param itemName  配置项名称
     * @return 配置项信息
     */
    SystemServerSettingItem selectBySettingIdAndName(@Param("settingId") Integer settingId,
                                                     @Param("itemName") String itemName);

    /**
     * 根据配置项名称查询配置项列表
     *
     * @param itemName 配置项名称
     * @return 配置项列表
     */
    List<SystemServerSettingItem> selectByName(@Param("itemName") String itemName);

    /**
     * 获取配置的最大排序值
     *
     * @param settingId 配置ID
     * @return 最大排序值
     */
    Integer getMaxOrderBySettingId(@Param("settingId") Integer settingId);

    /**
     * 批量更新配置项排序
     *
     * @param itemId 配置项ID
     * @param order  新排序值
     * @return 更新结果
     */
    int updateOrder(@Param("itemId") Integer itemId, @Param("order") Integer order);

    /**
     * 更新配置项值
     *
     * @param itemId 配置项ID
     * @param value  新值
     * @return 更新结果
     */
    int updateValue(@Param("itemId") Integer itemId, @Param("value") String value);

    /**
     * 根据配置ID删除所有配置项
     *
     * @param settingId 配置ID
     * @return 删除结果
     */
    int deleteBySettingId(@Param("settingId") Integer settingId);

    /**
     * 根据配置ID和必填状态查询配置项列表
     *
     * @param settingId 配置ID
     * @param required  是否必填
     * @return 配置项列表
     */
    List<SystemServerSettingItem> selectBySettingIdAndRequired(@Param("settingId") Integer settingId,
                                                               @Param("required") Boolean required);
}




