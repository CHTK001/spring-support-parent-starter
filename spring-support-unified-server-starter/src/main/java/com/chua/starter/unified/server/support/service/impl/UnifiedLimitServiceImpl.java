package com.chua.starter.unified.server.support.service.impl;

import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.starter.unified.server.support.entity.UnifiedLimit;
import com.chua.starter.unified.server.support.mapper.UnifiedLimitMapper;
import com.chua.starter.unified.server.support.service.UnifiedLimitService;
import org.springframework.stereotype.Service;
/**
 *    
 * @author CH
 */     
@Service
public class UnifiedLimitServiceImpl extends NotifyServiceImpl<UnifiedLimitMapper, UnifiedLimit> implements UnifiedLimitService{

    public UnifiedLimitServiceImpl() {
        setGetUnifiedId(UnifiedLimit::getUnifiedLimitId);
        setGetProfile(UnifiedLimit::getUnifiedLimitProfile);
        setGetAppName(UnifiedLimit::getUnifiedAppname);
        setResponseConsumer(it -> {});
        setModuleType(ModuleType.LIMIT);
    }
}
