package com.chua.starter.protocol.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.protocol.support.entity.SystemServerSettingServiceDiscovery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ServiceDiscovery 主配置 Mapper
 */
@Mapper
public interface SystemServerSettingServiceDiscoveryMapper extends BaseMapper<SystemServerSettingServiceDiscovery> {

    List<SystemServerSettingServiceDiscovery> selectByServerId(@Param("serverId") Integer serverId);
}

