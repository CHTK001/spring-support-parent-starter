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

import jakarta.annotation.Resource;
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
                    upload(config, monitorRequest, Json.toJSONString(config), CONFIG, CommandType.REGISTER);
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
     * @return {@link BootResponse}
     */
    @Override
    public BootResponse upload(MonitorConfig config, MonitorRequest monitorRequest, String content, ModuleType moduleType, CommandType commandType) {
        Object data = monitorRequest.getData();
        if(null == data) {
            return null;
        }

        MonitorProtocolProperties monitorProtocolProperties = BeanUtils.copyProperties(data, MonitorProtocolProperties.class);
        BootOption bootOption = BootOption.builder()
                .address(monitorProtocolProperties.getHost() + ":" + monitorProtocolProperties.getPort() + "/" + moduleType.name().toLowerCase())
                .heartbeat(false)
                .encryptionSchema(monitorProtocolProperties.getEncryptionSchema())
                .encryptionKey(monitorProtocolProperties.getEncryptionKey())
                .profile(null == config ? "default" : config.getConfigProfile())
                .appName(null == config ? monitorRequest.getAppName() : config.getConfigAppname()).build();
        Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(monitorProtocolProperties.getProtocol(), bootOption);
        if(null == protocol) {
            return BootResponse.empty();
        }

        ProtocolClient protocolClient = protocol.createClient();
        BootResponse bootResponse = protocolClient.get(BootRequest.builder()
                .profile(null == config ? "default" : config.getConfigProfile())
                .content(content)
                .appName(null == config ? monitorRequest.getAppName() : config.getConfigAppname())
                .moduleType(moduleType)
                .commandType(commandType)
                .build());

        try {
            MonitorLog monitorLog = new MonitorLog();
            monitorLog.setLogCode(null == bootResponse ? "-1" : bootResponse.getCode());
            monitorLog.setLogMsg(null == bootResponse ? null : bootResponse.getMsg());
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

        return bootResponse;
    }
}
