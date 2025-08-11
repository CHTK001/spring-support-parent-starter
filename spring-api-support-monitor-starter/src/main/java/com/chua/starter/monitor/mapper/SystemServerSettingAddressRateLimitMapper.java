package com.chua.starter.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.entity.SystemServerSettingAddressRateLimit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地址限流规则 Mapper
 */
@Mapper
public interface SystemServerSettingAddressRateLimitMapper extends BaseMapper<SystemServerSettingAddressRateLimit> {

    List<SystemServerSettingAddressRateLimit> selectByServerId(@Param("serverId") Integer serverId);
}

