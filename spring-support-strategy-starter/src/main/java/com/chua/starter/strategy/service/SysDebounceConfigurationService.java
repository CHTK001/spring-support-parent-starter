package com.chua.starter.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.strategy.entity.SysDebounceConfiguration;

import java.util.List;

/**
 * 防抖配置服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
public interface SysDebounceConfigurationService extends IService<SysDebounceConfiguration> {

    /**
     * 分页查询防抖配置
     *
     * @param page                      分页参数
     * @param sysDebounceConfiguration 查询条件
     * @return 分页结果
     */
    IPage<SysDebounceConfiguration> pageForDebounce(Page<SysDebounceConfiguration> page, 
                                                      SysDebounceConfiguration sysDebounceConfiguration);

    /**
     * 查询所有启用的防抖配置
     *
     * @return 启用的防抖配置列表
     */
    List<SysDebounceConfiguration> listEnabledForDebounce();

    /**
     * 刷新防抖配置到内存
     * 从数据库加载所有启用的配置到内存缓存
     *
     * @return 刷新的配置数量
     */
    int refreshDebounceConfigurations();

    /**
     * 根据路径匹配防抖配置
     *
     * @param path 请求路径
     * @return 匹配的防抖配置，如果没有匹配则返回null
     */
    SysDebounceConfiguration matchDebounceConfiguration(String path);
}
