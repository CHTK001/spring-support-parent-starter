package com.chua.starter.ai.support.interceptor;

import com.chua.common.support.ai.AiUsage;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.service.SysAiUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用量记录拦截器
 * 用于异步记录 对话客户端 调用用量
 *
 * @since 4.0.0.42
 * @author CH
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
     * 配置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 异步记录用量
     *
     * @param groupName 分组
     * @param provider 服务商
     * @param model 模型
     * @param callType 调用类型
     * @param usage 用量信息
     * @param requestId 请求标识
     * @param userId 用户标识
     */
    @Async
    public void record(String groupName, String provider, String model, String callType,
                       AiUsage usage, String requestId, String userId) {
        if (usage == null) {
            return;
        }
        // 检查会话用量写入开关
        boolean enabled = settingReader.getBool("global", "enable_usage_recorder", true);
        if (!enabled) {
            log.debug("[UsageRecorder] 用量记录已关闭，跳过: model={}", model);
            return;
        }
        try {
            usageService.record(groupName, provider, model, callType, usage, requestId, userId);
        } catch (Exception e) {
            log.error("[UsageRecorder] 记录失败: {}", e.getMessage(), e);
        }
    }
}
