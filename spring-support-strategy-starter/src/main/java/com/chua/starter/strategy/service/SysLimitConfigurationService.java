package com.chua.starter.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.strategy.entity.SysLimitConfiguration;

import java.util.List;

/**
 * 限流配置服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
public interface SysLimitConfigurationService extends IService<SysLimitConfiguration> {

    /**
     * 分页查询限流配置
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SysLimitConfiguration> pageForStrategy(IPage<SysLimitConfiguration> page, SysLimitConfiguration entity);

    /**
     * 查询所有启用的限流配置
     *
     * @return 启用的限流配置列表
     */
    List<SysLimitConfiguration> listEnabledConfigurations();

    /**
     * 根据路径查询限流配置
     *
     * @param path 接口路径
     * @return 匹配的限流配置
     */
    SysLimitConfiguration getByPath(String path);

    /**
     * 刷新限流配置到 Resilience4j
     */
    void refreshRateLimiters();
}
