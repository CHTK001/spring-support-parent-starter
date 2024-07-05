package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.collection.Option;
import com.chua.common.support.collection.Options;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.client.ProtocolClient;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.protocol.protocol.Protocol;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.DefaultRequest;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.protocol.request.SenderRequest;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.properties.MonitorProtocolProperties;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorApp;
import com.chua.starter.monitor.server.entity.MonitorConfig;
import com.chua.starter.monitor.server.entity.MonitorLog;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.mapper.MonitorAppMapper;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorLogService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MonitorAppServiceImpl extends ServiceImpl<MonitorAppMapper, MonitorApp> implements MonitorAppService{
    @Resource
    private MonitorServerFactory monitorServerFactory;
    @Resource
    private MonitorServerProperties monitorServerProperties;

    @Resource
    private MonitorLogService monitorLogService;

    @Override
    public Boolean upload(List<MonitorConfig> monitorConfig) {
        for (MonitorConfig config : monitorConfig) {
            upload(config);
        }
        return true;
    }

    private void upload(MonitorConfig config) {
        String configAppname = config.getConfigAppname();
        List<MonitorRequest> heart = monitorServerFactory.getHeart(configAppname);
        if(CollectionUtils.isEmpty(heart)) {
            return;
        }

        ThreadUtils.newStaticThreadPool().execute(() -> {
            for (MonitorRequest monitorRequest : heart) {
                try {
                    upload(config, monitorRequest, Json.toJSONString(config), "CONFIG", CommandType.REGISTER);
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * 上载
     *
     * @param config         配置
     * @param monitorRequest 监视器请求
     * @return {@linkResponse}
     */
    @Override
    public Response upload(MonitorConfig config, MonitorRequest monitorRequest, String content, String moduleType, CommandType commandType) {
        Object data = monitorRequest.getData();
        if(null == data) {
            return null;
        }

        data = MapUtils.get((Map)data, "config");
        MonitorProtocolProperties monitorProtocolProperties = BeanUtils.copyProperties(data, MonitorProtocolProperties.class);
        Codec codec = Codec.build(monitorProtocolProperties.getEncryptionSchema(), monitorProtocolProperties.getEncryptionKey());
        ProtocolSetting bootOption = ProtocolSetting.builder()
                .host(monitorProtocolProperties.getHost() )
                .port( monitorProtocolProperties.getPort())
                .heartbeat(false)
                .path(moduleType.toLowerCase())
                .codec(codec)
                .options(new Options()
                        .addOption("profileName", new Option(null == config ? "default" : config.getConfigProfile()))
                        .addOption("appName", new Option(null == config ? monitorRequest.getAppName() : config.getConfigAppname()))
                )
                .build();
        Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(monitorProtocolProperties.getProtocol(), bootOption);
        if(null == protocol) {
            return new BadResponse(new DefaultRequest(null, null, codec), "协议不存在");
        }

        ProtocolClient protocolClient = protocol.createClient();
        Response responseCode = protocolClient.sendRequestAndReply(SenderRequest.builder()
                .content(content)
                .moduleType(moduleType)
                .commandType(commandType)
                .build());

        try {
            MonitorLog monitorLog = new MonitorLog();
            monitorLog.setLogCode(null == responseCode ? "-1" : String.valueOf(responseCode.code()));
            monitorLog.setLogMsg(null == responseCode ? null : responseCode.message());
            monitorLog.setLogHost(monitorProtocolProperties.getHost());
            monitorLog.setLogPort(monitorProtocolProperties.getPort());
            monitorLog.setLogModuleType(moduleType);
            monitorLog.setLogCommandType(commandType);
            monitorLog.setLogProfile(null == config ? "default" : config.getConfigProfile());
            monitorLog.setLogAppname(null == config ? monitorRequest.getAppName() : config.getConfigAppname());
            monitorLog.setLogContent(content);

            monitorLogService.save(monitorLog);
        } catch (Exception ignored) {
        }

        return responseCode;
    }
}
