package com.chua.starter.protocol.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.protocol.support.entity.SystemServerSettingServiceDiscoveryMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ServiceDiscovery 映射明细 Mapper
 */
@Mapper
public interface SystemServerSettingServiceDiscoveryMappingMapper extends BaseMapper<SystemServerSettingServiceDiscoveryMapping> {

    List<SystemServerSettingServiceDiscoveryMapping> selectByServerId(@Param("serverId") Integer serverId);
}

