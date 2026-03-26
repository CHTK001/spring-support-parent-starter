package com.chua.starter.proxy.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscovery;
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





