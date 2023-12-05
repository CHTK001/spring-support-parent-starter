package com.chua.starter.unified.server.support.service.impl;

import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.mapper.UnifiedMybatisMapper;
import com.chua.starter.unified.server.support.service.UnifiedMybatisService;
import org.springframework.stereotype.Service;

/**
 * 统一mybatis服务impl
 *
 * @author CH
 * @since 2023/11/20
 */
@Service
public class UnifiedMybatisServiceImpl extends NotifyServiceImpl<UnifiedMybatisMapper, UnifiedMybatis> implements UnifiedMybatisService{


    public UnifiedMybatisServiceImpl() {
        setGetUnifiedId(UnifiedMybatis::getUnifiedMybatisId);
        setGetProfile(UnifiedMybatis::getUnifiedMybatisProfile);
        setGetAppName(UnifiedMybatis::getUnifiedAppname);
        setResponseConsumer(it -> {});
        setModuleType(ModuleType.MYBATIS);
    }
}
