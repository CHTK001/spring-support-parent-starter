package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.model.ServerAiStructuredAdvice;
import com.chua.starter.ai.support.model.ServerProcessAnalyzeRequest;
import com.chua.starter.ai.support.service.ServerAiAnalyzeService;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerProcessAiAdvisor {

    private final ServerAiAnalyzeService serverAiAnalyzeService;

    public ServerProcessAiAdvice analyze(
            ServerHost host,
            ServerProcessView process,
            List<ServerProcessView> topProcesses
    ) {
        if (host == null || process == null) {
            return null;
        }
        ServerAiStructuredAdvice advice = serverAiAnalyzeService.analyzeProcess(
                ServerProcessAnalyzeRequest.builder()
                        .serverName(host.getServerName())
                        .serverCode(host.getServerCode())
                        .serverType(host.getServerType())
                        .osType(host.getOsType())
                        .host(host.getHost())
                        .pid(process.getPid())
                        .name(process.getName())
                        .user(process.getUser())
                        .state(process.getState())
                        .cpuPercent(process.getCpuPercent())
                        .memoryPercent(process.getMemoryPercent())
                        .memoryBytes(process.getMemoryBytes())
                        .threadCount(process.getThreadCount())
                        .elapsed(process.getElapsed())
                        .startTime(process.getStartTime())
                        .commandLine(process.getCommandLine())
                        .topProcesses(buildProcessLines(topProcesses))
                        .build());
        if (advice == null || !StringUtils.hasText(advice.getSummary())) {
            return null;
        }
        return ServerProcessAiAdvice.builder()
                .pid(process.getPid())
                .summary(advice.getSummary())
                .riskLevel(advice.getRiskLevel())
                .suggestion(advice.getSuggestion())
                .provider(advice.getProvider())
                .model(advice.getModel())
                .build();
    }

    private List<String> buildProcessLines(List<ServerProcessView> processes) {
        if (processes == null || processes.isEmpty()) {
            return List.of();
        }
        return processes.stream()
                .limit(8)
                .map(item -> "- PID %s / %s / CPU %s / MEM %s / %s".formatted(
                        safe(item.getPid()),
                        safe(item.getName()),
                        safe(item.getCpuPercent()),
                        safe(item.getMemoryPercent()),
                        safe(item.getCommandLine())))
                .toList();
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value).trim();
    }
}
