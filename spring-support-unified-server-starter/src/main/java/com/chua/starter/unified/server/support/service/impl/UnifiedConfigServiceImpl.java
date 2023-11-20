package com.chua.starter.unified.server.support.service.impl;

import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.mapper.UnifiedConfigMapper;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
import org.springframework.stereotype.Service;

/**
 * 统一配置服务impl
 *
 * @author CH
 * @since 2023/11/20
 */
@Service
public class UnifiedConfigServiceImpl extends NotifyServiceImpl<UnifiedConfigMapper, UnifiedConfig> implements UnifiedConfigService{
    public UnifiedConfigServiceImpl() {
        setGetUnifiedId(UnifiedConfig::getUnifiedConfigId);
        setGetProfile(UnifiedConfig::getUnifiedConfigProfile);
        setGetAppName(UnifiedConfig::getUnifiedAppname);
        setResponseConsumer(it -> {});
        setModuleType(ModuleType.CONFIG);
    }
}
