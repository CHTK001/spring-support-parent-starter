package com.chua.starter.strategy.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;

import java.util.List;

/**
 * 熔断配置服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
public interface SysCircuitBreakerConfigurationService extends IService<SysCircuitBreakerConfiguration> {

    /**
     * 分页查询熔断配置
     *
     * @param page   分页参数
     * @param entity 查询条件
     * @return 分页结果
     */
    IPage<SysCircuitBreakerConfiguration> pageForStrategy(IPage<SysCircuitBreakerConfiguration> page, 
                                                          SysCircuitBreakerConfiguration entity);

    /**
     * 查询所有启用的熔断配置
     *
     * @return 启用的熔断配置列表
     */
    List<SysCircuitBreakerConfiguration> listEnabledConfigurations();

    /**
     * 根据路径查询熔断配置
     *
     * @param path 接口路径
     * @return 匹配的熔断配置
     */
    SysCircuitBreakerConfiguration getByPath(String path);

    /**
     * 刷新熔断配置到 Resilience4j
     */
    void refreshCircuitBreakers();
}
