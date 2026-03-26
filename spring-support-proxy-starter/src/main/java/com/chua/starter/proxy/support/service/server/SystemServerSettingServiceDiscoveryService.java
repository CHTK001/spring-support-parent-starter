package com.chua.starter.proxy.support.service.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscovery;
import com.chua.starter.proxy.support.entity.SystemServerSettingServiceDiscoveryMapping;

import java.util.List;

public interface SystemServerSettingServiceDiscoveryService extends IService<SystemServerSettingServiceDiscovery> {

    ReturnResult<SystemServerSettingServiceDiscovery> saveOrUpdateConfig(SystemServerSettingServiceDiscovery config);

    ReturnResult<Boolean> removeByServerId(Integer serverId);

    List<SystemServerSettingServiceDiscovery> listByServerId(Integer serverId);

    // 映射明细
    List<SystemServerSettingServiceDiscoveryMapping> listMappingsByServerId(Integer serverId);

    ReturnResult<Boolean> saveOrUpdateMappings(Integer serverId, List<SystemServerSettingServiceDiscoveryMapping> mappings);
}





