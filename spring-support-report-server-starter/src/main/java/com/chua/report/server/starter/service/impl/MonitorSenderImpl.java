package com.chua.report.server.starter.service.impl;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.crypto.AesCodec;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.discovery.Discovery;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.client.ProtocolClient;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.protocol.protocol.Protocol;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.protocol.request.SenderRequest;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.report.client.starter.endpoint.ModuleType;
import com.chua.report.server.starter.entity.MonitorJobLog;
import com.chua.report.server.starter.entity.MonitorLog;
import com.chua.report.server.starter.service.MonitorLogService;
import com.chua.report.server.starter.service.MonitorSender;
import com.chua.starter.common.support.project.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 监控发送
 * @author CH
 * @since 2024/9/12
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MonitorSenderImpl implements MonitorSender, InitializingBean {

    final MonitorLogService monitorLogService;
    private ExecutorService executorService;

    @Override
    public void upload(Object o, Discovery discovery, String params, ModuleType moduleType) {
        executorService.execute(() -> {
            Project project = getProject(discovery);
            Codec codec = new AesCodec("1234567890123456".getBytes(StandardCharsets.UTF_8));
            ProtocolSetting bootOption = ProtocolSetting.builder()
                    .host(project.getApplicationHost())
                    .port(Converter.createInteger(ObjectUtils.defaultIfNull(project.getClientProtocolEndpointPort(), project.getApplicationPort() + "10000")))
                    .heartbeat(false)
                    .codec(codec)
                    .build();
            Protocol protocol = ServiceProvider.of(Protocol.class).getNewExtension(project.getClientProtocolEndpointProtocol(), bootOption);
            MonitorLog monitorLog = new MonitorLog();
            if(null == protocol) {
                monitorLog.setLogCode(ReturnCode.RESOURCE_NOT_FOUND.getCode());
                monitorLog.setLogMsg(ReturnCode.RESOURCE_NOT_FOUND.getMsg());
                throw new RuntimeException("未找到对应协议");
            }

            long startTime = System.currentTimeMillis();
            ProtocolClient protocolClient = protocol.createClient();
            Response responseCode = protocolClient.sendRequestAndReply(SenderRequest.builder()
                    .url("/" + moduleType.name().toLowerCase())
                    .content(params)
                    .profile(project.getApplicationActive())
                    .appName(project.getApplicationName())
                    .build());


            try {
                monitorLog.setLogCode(null == responseCode ? "-1" : String.valueOf(responseCode.code()));
                monitorLog.setLogMsg(null == responseCode ? null : responseCode.getSource().toString());
                monitorLog.setLogHost(project.getApplicationHost());
                monitorLog.setLogPort(project.getApplicationPort());
                monitorLog.setLogModuleType(moduleType.name());
                monitorLog.setLogCommandType(CommandType.REQUEST);
                monitorLog.setLogProfile(project.getApplicationActive());
                monitorLog.setLogAppname(project.getApplicationName());
                monitorLog.setLogContent(params);
                if(o instanceof MonitorJobLog monitorJobLog) {
                    monitorJobLog.setJobLogCost(BigDecimal.valueOf(System.currentTimeMillis() - startTime));
                }

                monitorLogService.save(monitorLog);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public Project getProject(Discovery discovery) {
        Map<String, String> metadata = discovery.getMetadata();
        return new Project(metadata);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.executorService = ThreadUtils.newVirtualThreadExecutor();
    }
}
