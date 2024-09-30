package com.chua.report.server.starter.service.impl;

import com.chua.common.support.protocol.ClientSetting;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.service.MonitorSysGenService;
import com.chua.report.server.starter.service.TerminalService;
import com.chua.ssh.support.ssh.SshClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author CH
 * @since 2024/6/19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TerminalServiceImpl implements TerminalService {

    private static final Map<String, CacheClient> SERVER_MAP = new ConcurrentHashMap<>();
    final MonitorSysGenService monitorSysGenService;

    @Override
    public SshClient getClient(String genId) {
        MonitorSysGen monitorSysGen = monitorSysGenService.getById(genId);
        CacheClient cacheClient = SERVER_MAP.get(genId);
        if (null == cacheClient) {
            SshClient sshClient = createClient(monitorSysGen);
            SERVER_MAP.put(genId, new CacheClient(sshClient));
            return sshClient;
        }
        return cacheClient.getSshClient();
    }
    private SshClient createClient(MonitorSysGen monitorSysGen) {
        SshClient sshClient = new SshClient(ClientSetting.builder()
                .username(monitorSysGen.getGenUser())
                .password(monitorSysGen.getGenPassword())
                .host(monitorSysGen.getGenHost())
                .port(monitorSysGen.getGenPort())
                .build()
        );
        sshClient.connect();
        return sshClient;
    }

    @Data
    @AllArgsConstructor
    static class CacheClient {
        private SshClient sshClient;
    }
}
