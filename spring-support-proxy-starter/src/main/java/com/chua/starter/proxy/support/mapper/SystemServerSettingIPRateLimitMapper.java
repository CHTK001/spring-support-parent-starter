package com.chua.starter.proxy.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.proxy.support.entity.SystemServerSettingIPRateLimit;
import org.apache.ibatis.annotations.Mapper;

/**
 * IP 限流规则 Mapper
 */
@Mapper
public interface SystemServerSettingIPRateLimitMapper extends BaseMapper<SystemServerSettingIPRateLimit> {
}






