package com.chua.starter.unified.server.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.protocol.server.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.mapper.UnifiedMybatisMapper;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.service.UnifiedMybatisService;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

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
