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
import com.chua.report.client.starter.endpoint.ModuleType;
import com.chua.report.server.starter.entity.MonitorLog;
import com.chua.report.server.starter.service.MonitorLogService;
import com.chua.report.server.starter.service.MonitorSender;
import com.chua.starter.common.support.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 监控发送
 * @author CH
 * @since 2024/9/12
 */
@Service
@RequiredArgsConstructor
public class MonitorSenderImpl implements MonitorSender {

    final MonitorLogService monitorLogService;

    @Override
    public void upload(Object o, Discovery discovery, String params, ModuleType moduleType) {
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

        ProtocolClient protocolClient = protocol.createClient();
        Response responseCode = protocolClient.sendRequestAndReply(SenderRequest.builder()
                .url("/")
                .content(params)
                .profile(project.getApplicationActive())
                .appName(project.getApplicationName())
                .build());


        try {
            monitorLog.setLogCode(null == responseCode ? "-1" : String.valueOf(responseCode.code()));
            monitorLog.setLogMsg(null == responseCode ? null : responseCode.message());
            monitorLog.setLogHost(project.getApplicationHost());
            monitorLog.setLogPort(project.getApplicationPort());
            monitorLog.setLogModuleType(moduleType.name());
            monitorLog.setLogCommandType(CommandType.REQUEST);
            monitorLog.setLogProfile(project.getApplicationActive());
            monitorLog.setLogAppname(project.getApplicationName());
            monitorLog.setLogContent(params);

            monitorLogService.save(monitorLog);
        } catch (Exception ignored) {
        }
        if(!responseCode.isSuccessful()) {
            throw new RuntimeException(responseCode.message());
        }
    }

    @Override
    public Project getProject(Discovery discovery) {
        Map<String, String> metadata = discovery.getMetadata();
        return new Project(metadata);
    }
}
