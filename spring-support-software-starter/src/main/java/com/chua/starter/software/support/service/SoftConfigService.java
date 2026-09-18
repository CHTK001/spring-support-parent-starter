package com.chua.starter.software.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.software.support.entity.SoftConfig;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* 软件配置表 服务 接口
*
* @since 2026/7/25
* @author CH
 */
public interface SoftConfigService extends IService<SoftConfig> {

    /**
    * 分页查询配置列表
    *
    * @param query 查询条件对象，用于分页和排序
    * @param request 软件配置查询请求对象
    * @return 返回分页查询结果
    */
    IPage<SoftConfig> pageFor(Query<SoftConfig> query, SoftConfig request);

    /**
    * 保存配置
    *
    * @param config 配置信息
    * @return 返回保存结果
    */
    ReturnResult<SoftConfig> saveConfig(SoftConfig config);

    /**
    * 更新配置
    *
    * @param config 配置信息
    * @return 返回更新结果
    */
    ReturnResult<SoftConfig> updateConfig(SoftConfig config);

    /**
    * 删除配置
    *
    * @param configId 配置标识
    * @return 返回删除结果
    */
    ReturnResult<Boolean> deleteConfig(String configId);

    /**
    * 根据软件标识和配置键获取配置
    *
    * @param softwareId 软件标识
    * @param key 配置键
    * @return 配置信息
    */
    SoftConfig getBySoftwareAndKey(String softwareId, String key);

    /**
    * 根据软件标识获取配置列表
    *
    * @param softwareId 软件标识
    * @return 配置列表
    */
    List<SoftConfig> listBySoftwareId(String softwareId);

    /**
    * 根据软件标识和用户标识获取配置列表
    *
    * @param softwareId 软件标识
    * @param userId 用户标识
    * @return 配置列表
    */
    List<SoftConfig> listBySoftwareIdAndUserId(String softwareId, String userId);

    /**
    * 根据软件标识和设备标识获取配置列表
    *
    * @param softwareId 软件标识
    * @param deviceId 设备标识
    * @return 配置列表
    */
    List<SoftConfig> listBySoftwareIdAndDeviceId(String softwareId, String deviceId);

    /**
    * 批量保存或更新配置
    *
    * @param configs 配置列表
    * @return 返回操作结果
    */
    ReturnResult<Boolean> batchSaveOrUpdate(List<SoftConfig> configs);
}
