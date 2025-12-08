package com.chua.starter.common.support.api.feature;

import com.chua.starter.common.support.api.annotations.ApiFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 功能开关管理器
 * <p>
 * 管理所有 @ApiFeature 注解标记的功能开关状态，支持运行时动态切换。
 * </p>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Slf4j
@Component
public class ApiFeatureManager {

    /**
     * 功能开关状态缓存
     * key: 功能标识
     * value: 是否启用
     */
    private final Map<String, Boolean> featureStates = new ConcurrentHashMap<>();

    /**
     * 功能信息缓存
     * key: 功能标识
     * value: 功能信息
     */
    private final Map<String, ApiFeatureInfo> featureInfoMap = new ConcurrentHashMap<>();

    /**
     * 注册功能开关
     *
     * @param featureId      功能标识
     * @param apiFeature     注解信息
     * @param handlerMethod  处理方法
     * @param mappingInfo    映射信息
     */
    public void registerFeature(String featureId, ApiFeature apiFeature,
                                HandlerMethod handlerMethod, RequestMappingInfo mappingInfo) {
        // 如果已存在且状态被手动修改过，保留当前状态
        if (!featureStates.containsKey(featureId)) {
            featureStates.put(featureId, apiFeature.defaultEnabled());
        }

        // 构建功能信息
        var info = ApiFeatureInfo.builder()
                .featureId(featureId)
                .description(apiFeature.description())
                .group(apiFeature.group())
                .defaultEnabled(apiFeature.defaultEnabled())
                .enabled(featureStates.get(featureId))
                .disabledMessage(apiFeature.disabledMessage())
                .disabledStatus(apiFeature.disabledStatus())
                .className(handlerMethod.getBeanType().getName())
                .methodName(handlerMethod.getMethod().getName())
                .patterns(mappingInfo.getPatternValues())
                .build();

        featureInfoMap.put(featureId, info);
        log.debug("注册功能开关: {} -> {} (默认: {})", featureId, info.getPatterns(), apiFeature.defaultEnabled());
    }

    /**
     * 检查功能是否启用
     *
     * @param featureId 功能标识
     * @return 是否启用
     */
    public boolean isEnabled(String featureId) {
        return featureStates.getOrDefault(featureId, true);
    }

    /**
     * 设置功能开关状态
     *
     * @param featureId 功能标识
     * @param enabled   是否启用
     * @return 操作是否成功
     */
    public boolean setEnabled(String featureId, boolean enabled) {
        if (!featureInfoMap.containsKey(featureId)) {
            log.warn("功能开关不存在: {}", featureId);
            return false;
        }
        featureStates.put(featureId, enabled);
        // 更新信息缓存
        var info = featureInfoMap.get(featureId);
        if (info != null) {
            featureInfoMap.put(featureId, info.toBuilder().enabled(enabled).build());
        }
        log.info("功能开关状态已更新: {} -> {}", featureId, enabled);
        return true;
    }

    /**
     * 批量设置功能开关状态
     *
     * @param states 状态映射
     */
    public void setEnabledBatch(Map<String, Boolean> states) {
        states.forEach(this::setEnabled);
    }

    /**
     * 获取功能信息
     *
     * @param featureId 功能标识
     * @return 功能信息
     */
    public ApiFeatureInfo getFeatureInfo(String featureId) {
        return featureInfoMap.get(featureId);
    }

    /**
     * 获取所有功能信息
     *
     * @return 功能信息列表
     */
    public List<ApiFeatureInfo> getAllFeatures() {
        return new ArrayList<>(featureInfoMap.values());
    }

    /**
     * 按分组获取功能信息
     *
     * @param group 分组名称
     * @return 功能信息列表
     */
    public List<ApiFeatureInfo> getFeaturesByGroup(String group) {
        return featureInfoMap.values().stream()
                .filter(info -> group.equals(info.getGroup()))
                .toList();
    }

    /**
     * 获取所有分组
     *
     * @return 分组列表
     */
    public Set<String> getAllGroups() {
        var groups = new TreeSet<String>();
        featureInfoMap.values().forEach(info -> groups.add(info.getGroup()));
        return groups;
    }

    /**
     * 重置功能开关到默认状态
     *
     * @param featureId 功能标识
     */
    public void resetToDefault(String featureId) {
        var info = featureInfoMap.get(featureId);
        if (info != null) {
            setEnabled(featureId, info.isDefaultEnabled());
        }
    }

    /**
     * 重置所有功能开关到默认状态
     */
    public void resetAllToDefault() {
        featureInfoMap.keySet().forEach(this::resetToDefault);
    }

    /**
     * 获取功能开关数量
     *
     * @return 数量
     */
    public int getFeatureCount() {
        return featureInfoMap.size();
    }

    /**
     * 获取启用的功能开关数量
     *
     * @return 数量
     */
    public int getEnabledCount() {
        return (int) featureStates.values().stream().filter(Boolean::booleanValue).count();
    }
}
