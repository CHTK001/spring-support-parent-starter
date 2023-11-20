package com.chua.starter.unified.server.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.protocol.server.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.mapper.UnifiedConfigMapper;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

@Service
public class UnifiedConfigServiceImpl extends ServiceImpl<UnifiedConfigMapper, UnifiedConfig> implements UnifiedConfigService{

    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;
    @Resource
    private UnifiedServerProperties unifiedServerProperties;

    @Override
    public Boolean notifyConfig(UnifiedConfig unifiedConfig) {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                notifyClient(unifiedConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    @Override
    public ResultData<Boolean> saveOrUpdateConfig(UnifiedConfig t) {
        t.setCreateTime(new Date());
        if(null != t.getUnifiedConfigId()) {
            baseMapper.updateById(t);
            if(t.getUnifiedConfigStatus().equals(1)) {
                notifyConfig(t);
            }
            return ResultData.success(true);
        }
        t.setUnifiedConfigStatus(1);
        baseMapper.insert(t);
        notifyConfig(t);
        return ResultData.success(true);
    }

    private void notifyClient(UnifiedConfig unifiedConfig) {
        List<UnifiedExecuterItem> list = unifiedExecuterItemService.findItem(unifiedConfig.getUnifiedConfigProfile());

        for (UnifiedExecuterItem unifiedExecuterItem : list) {
            String unifiedExecuterItemSubscribe = unifiedExecuterItem.getUnifiedExecuterItemSubscribe();
            if(StringUtils.isEmpty(unifiedExecuterItemSubscribe)) {
                continue;
            }

            JSONObject jsonObject = JSON.parseObject(unifiedExecuterItemSubscribe);
            JSONObject config = jsonObject.getJSONObject("CONFIG");
            if(null == config) {
                continue;
            }

            JSONArray jsonArray = config.getJSONArray(SUBSCRIBE);
            if(null == jsonArray || !jsonArray.contains(unifiedConfig.getUnifiedAppname())) {
                continue;
            }
            notifyClient(unifiedExecuterItem, unifiedConfig);
        }
    }

    private void notifyClient(UnifiedExecuterItem unifiedExecuterItem, UnifiedConfig unifiedConfig) {
        BootOption bootOption = BootOption.builder()
                .encryptionSchema(unifiedServerProperties.getEncryptionSchema())
                .encryptionKey(unifiedServerProperties.getEncryptionKey())
                .address(unifiedExecuterItem.getUnifiedExecuterItemHost() + ":" + unifiedExecuterItem.getUnifiedExecuterItemPort())
                .appName(unifiedConfig.getUnifiedAppname())
                .keepAlive(false)
                .serverOption(ServerOption.builder()
                        .port(Integer.parseInt(unifiedExecuterItem.getUnifiedExecuterItemPort())).host(unifiedExecuterItem.getUnifiedExecuterItemHost()).build())
                .build();
        Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(unifiedExecuterItem.getUnifiedExecuterItemProtocol(), bootOption);
        ProtocolClient protocolClient = protocol.createClient();
        BootRequest bootRequest = BootRequest.builder()
                .commandType(CommandType.REGISTER)
                .moduleType(ModuleType.CONFIG)
                .content(Json.toJson(unifiedConfig))
                .address(bootOption.getAddress() + "/" + ModuleType.CONFIG.name())
                .profile(unifiedConfig.getUnifiedConfigProfile())
                .appName(unifiedConfig.getUnifiedAppname())
                .build();
        BootResponse bootResponse = protocolClient.send(bootRequest);


    }
}
