package com.chua.starter.ai.support.interceptor;

import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.service.SysAiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用量记录拦截器
 * 用于异步记录 ChatClient 调用用量
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageRecorderInterceptor {

    /**
     * 用量服务
     */
    private final SysAiUsageService usageService;

    /**
     * 异步记录用量
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param callType 调用类型
     * @param usage 用量信息
     * @param requestId 请求ID
     * @param userId 用户ID
     */
    @Async
    public void record(String groupName, String provider, String model, String callType,
                       AiUsage usage, String requestId, String userId) {
        if (usage == null) {
            return;
        }
        try {
            usageService.record(groupName, provider, model, callType, usage, requestId, userId);
        } catch (Exception e) {
            log.error("[UsageRecorder] 记录失败: {}", e.getMessage(), e);
        }
    }
}
