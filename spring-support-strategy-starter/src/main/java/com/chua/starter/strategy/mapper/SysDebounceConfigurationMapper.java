package com.chua.starter.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.strategy.entity.SysDebounceConfiguration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 防抖配置 Mapper
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Mapper
public interface SysDebounceConfigurationMapper extends BaseMapper<SysDebounceConfiguration> {
}
