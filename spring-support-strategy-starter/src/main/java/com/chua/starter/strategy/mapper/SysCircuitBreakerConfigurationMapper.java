package com.chua.starter.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 熔断配置 Mapper 接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Mapper
public interface SysCircuitBreakerConfigurationMapper extends BaseMapper<SysCircuitBreakerConfiguration> {
}
