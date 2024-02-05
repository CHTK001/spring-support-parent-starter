package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.chua.common.support.protocol.boot.ModuleType.CONFIG;

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
                    upload(config, monitorRequest);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void upload(MonitorConfig config, MonitorRequest monitorRequest) {
        Object data = monitorRequest.getData();
        if(null == data) {
            return;
        }

        MonitorProtocolProperties monitorProtocolProperties = BeanUtils.copyProperties(data, MonitorProtocolProperties.class);
        BootOption bootOption = BootOption.builder()
                .address(monitorProtocolProperties.getHost() + ":" + monitorProtocolProperties.getPort() + "/config")
                .heartbeat(false)
                .encryptionSchema(monitorProtocolProperties.getEncryptionSchema())
                .encryptionKey(monitorProtocolProperties.getEncryptionKey())
                .profile(config.getConfigProfile())
                .appName(config.getConfigAppname()).build();
        Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(monitorProtocolProperties.getProtocol(), bootOption);
        if(null == protocol) {
            return;
        }

        ProtocolClient protocolClient = protocol.createClient();
        BootResponse bootResponse = protocolClient.get(BootRequest.builder()
                .profile(config.getConfigProfile())
                .content(Json.toJSONString(config))
                .appName(config.getConfigAppname())
                .moduleType(CONFIG)
                .commandType(CommandType.REGISTER)
                .build());

        try {
            MonitorLog monitorLog = new MonitorLog();
            monitorLog.setLogCode(null == bootResponse ? "-1" : bootResponse.getCode());
            monitorLog.setLogMsg(null == bootResponse ? null : bootResponse.getMsg());
            monitorLog.setLogHost(monitorProtocolProperties.getHost());
            monitorLog.setLogPort(monitorProtocolProperties.getPort());
            monitorLog.setLogModuleType(CONFIG);
            monitorLog.setLogCommandType(CommandType.REGISTER);
            monitorLog.setLogProfile(config.getConfigProfile());
            monitorLog.setLogAppname(config.getConfigAppname());
            monitorLog.setLogContent(Json.toJSONString(config));

            monitorLogService.save(monitorLog);
        } catch (Exception ignored) {
        }
    }
}
