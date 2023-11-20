package com.chua.starter.unified.server.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.protocol.server.ServerOption;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.NotifyService;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import lombok.Setter;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

/**
 * 通知服务
 *
 * @author CH
 * @since 2023/11/20
 */
public class NotifyServiceImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> implements NotifyService<T> {

    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;
    @Resource
    private UnifiedServerProperties unifiedServerProperties;
    @Setter
    private Function<T, ? extends Serializable> getUnifiedId;
    @Setter
    private Function<T, String> getAppName;
    @Setter
    private Function<T, String> getProfile;

    @Setter
    private Consumer<BootResponse> responseConsumer;

    @Setter
    private ModuleType moduleType;

    @Override
    public Boolean notifyConfig(T t) {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            try {
                notifyClient(t);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    @Override
    public ResultData<Boolean> saveOrUpdateConfig(T t) {
        if(null != getUnifiedId.apply(t)) {
            baseMapper.updateById(t);
            notifyConfig(t);
            return ResultData.success(true);
        }
        baseMapper.insert(t);
        notifyConfig(t);
        return ResultData.success(true);
    }

    private void notifyClient(T t) {
        List<UnifiedExecuterItem> list = unifiedExecuterItemService.findItem(getProfile.apply(t));

        for (UnifiedExecuterItem unifiedExecuterItem : list) {
            String unifiedExecuterItemSubscribe = unifiedExecuterItem.getUnifiedExecuterItemSubscribe();
            if(StringUtils.isEmpty(unifiedExecuterItemSubscribe)) {
                continue;
            }

            JSONObject jsonObject = JSON.parseObject(unifiedExecuterItemSubscribe);
            JSONObject config = jsonObject.getJSONObject(moduleType.name());
            if(null == config) {
                continue;
            }

            JSONArray jsonArray = config.getJSONArray(SUBSCRIBE);
            if(null == jsonArray || !jsonArray.contains(getAppName.apply(t))) {
                continue;
            }
            notifyClient(unifiedExecuterItem, t);
        }
    }

    private void notifyClient(UnifiedExecuterItem unifiedExecuterItem, T t) {
        BootOption bootOption = BootOption.builder()
                .encryptionSchema(unifiedServerProperties.getEncryptionSchema())
                .encryptionKey(unifiedServerProperties.getEncryptionKey())
                .address(unifiedExecuterItem.getUnifiedExecuterItemHost() + ":" + unifiedExecuterItem.getUnifiedExecuterItemPort())
                .appName(getAppName.apply(t))
                .keepAlive(false)
                .serverOption(ServerOption.builder()
                        .port(Integer.parseInt(unifiedExecuterItem.getUnifiedExecuterItemPort())).host(unifiedExecuterItem.getUnifiedExecuterItemHost()).build())
                .build();
        Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(unifiedExecuterItem.getUnifiedExecuterItemProtocol(), bootOption);
        ProtocolClient protocolClient = protocol.createClient();
        BootRequest bootRequest = BootRequest.builder()
                .commandType(CommandType.REGISTER)
                .moduleType(moduleType)
                .content(Json.toJson(t))
                .address(bootOption.getAddress() + "/" + moduleType.name())
                .profile(getProfile.apply(t))
                .appName(getAppName.apply(t))
                .build();
        BootResponse bootResponse = protocolClient.send(bootRequest);
        responseConsumer.accept(bootResponse);
    }
}
