package com.chua.starter.proxy.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统服务器配置表 Mapper 接口
 *
 * @author CH
 * @since 2025/01/07
 */
@Mapper
public interface SystemServerSettingMapper extends BaseMapper<SystemServerSetting> {

    /**
     * 分页查询配置列表
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SystemServerSetting> pageFor(Page<SystemServerSetting> page, @Param("entity") SystemServerSetting entity);

    /**
     * 根据服务器ID查询配置列表
     *
     * @param serverId 服务器ID
     * @return 配置列表
     */
    List<SystemServerSetting> selectByServerId(@Param("serverId") Integer serverId);

    /**
     * 根据服务器ID和配置类型查询配置
     *
     * @param serverId    服务器ID
     * @param settingType 配置类型
     * @return 配置信息
     */
    SystemServerSetting selectByServerIdAndType(@Param("serverId") Integer serverId,
                                                @Param("settingType") String settingType);

    /**
     * 根据配置类型查询配置列表
     *
     * @param settingType 配置类型
     * @return 配置列表
     */
    List<SystemServerSetting> selectByType(@Param("settingType") String settingType);

    /**
     * 获取服务器配置的最大排序值
     *
     * @param serverId 服务器ID
     * @return 最大排序值
     */
    Integer getMaxOrderByServerId(@Param("serverId") Integer serverId);

    /**
     * 批量更新配置排序
     *
     * @param settingId 配置ID
     * @param order     新排序值
     * @return 更新结果
     */
    int updateOrder(@Param("settingId") Integer settingId, @Param("order") Integer order);

    /**
     * 根据服务器ID和启用状态查询配置列表
     *
     * @param serverId 服务器ID
     * @param enabled  启用状态
     * @return 配置列表
     */
    List<SystemServerSetting> selectByServerIdAndEnabled(@Param("serverId") Integer serverId,
                                                         @Param("enabled") Boolean enabled);

    /**
     * 更新配置启用状态
     *
     * @param settingId 配置ID
     * @param enabled   启用状态
     * @return 更新结果
     */
    int updateEnabled(@Param("settingId") Integer settingId, @Param("enabled") Boolean enabled);
}




