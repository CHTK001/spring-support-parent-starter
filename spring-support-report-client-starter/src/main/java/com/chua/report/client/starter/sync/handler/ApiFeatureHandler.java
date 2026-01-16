package com.chua.report.client.starter.sync.handler;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.api.feature.ApiFeatureManager;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * API 功能开关消息处理器
 * <p>
 * 接收来自服务端的功能开关控制命令，执行对应的操作。
 * </p>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ApiFeatureHandler implements SyncMessageHandler {

    private final ApiFeatureManager featureManager;

    @Override
    public String getName() {
        return "api-feature";
    }

    @Override
    public boolean supports(String topic) {
        return "monitor/control/api-feature".equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        try {
            if (data == null) {
                log.warn("[ApiFeatureHandler] 消息数据为空");
                return null;
            }

            String action = MapUtils.getString(data, "action");

            log.info("[ApiFeatureHandler] 收到功能开关控制命令: action={}", action);

            return switch (action) {
                case "setEnabled" -> handleSetEnabled(data);
                case "setBatch" -> handleSetBatch(data);
                case "resetAll" -> handleResetAll();
                case "reset" -> handleReset(data);
                default -> {
                    log.warn("[ApiFeatureHandler] 未知的操作类型: {}", action);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("[ApiFeatureHandler] 处理消息失败", e);
            return null;
        }
    }

    /**
     * 处理设置单个功能开关状态
     */
    private Object handleSetEnabled(Map<String, Object> payload) {
        String featureId = MapUtils.getString(payload, "featureId");
        Boolean enabled = MapUtils.getBoolean(payload, "enabled");

        if (featureId == null || enabled == null) {
            log.warn("[ApiFeatureHandler] setEnabled 缺少必要参数: featureId={}, enabled={}", featureId, enabled);
            return false;
        }

        boolean success = featureManager.setEnabled(featureId, enabled);
        log.info("[ApiFeatureHandler] 设置功能开关: featureId={}, enabled={}, success={}", featureId, enabled, success);
        return success;
    }

    /**
     * 处理批量设置功能开关状态
     */
    @SuppressWarnings("unchecked")
    private Object handleSetBatch(Map<String, Object> payload) {
        Object statesObj = payload.get("states");
        if (!(statesObj instanceof Map)) {
            log.warn("[ApiFeatureHandler] setBatch 缺少 states 参数");
            return false;
        }

        Map<String, Boolean> states = (Map<String, Boolean>) statesObj;
        featureManager.setEnabledBatch(states);
        log.info("[ApiFeatureHandler] 批量设置功能开关: count={}", states.size());
        return true;
    }

    /**
     * 处理重置所有功能开关
     */
    private Object handleResetAll() {
        featureManager.resetAllToDefault();
        log.info("[ApiFeatureHandler] 已重置所有功能开关到默认状态");
        return true;
    }

    /**
     * 处理重置单个功能开关
     */
    private Object handleReset(Map<String, Object> payload) {
        String featureId = MapUtils.getString(payload, "featureId");
        if (featureId == null) {
            log.warn("[ApiFeatureHandler] reset 缺少 featureId 参数");
            return false;
        }

        featureManager.resetToDefault(featureId);
        log.info("[ApiFeatureHandler] 重置功能开关: featureId={}", featureId);
        return true;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
