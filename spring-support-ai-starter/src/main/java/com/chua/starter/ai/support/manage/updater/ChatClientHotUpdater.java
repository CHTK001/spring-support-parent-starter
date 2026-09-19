package com.chua.starter.ai.support.manage.updater;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import com.chua.starter.ai.support.manage.event.SettingChangedEvent;
import com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper;
import com.chua.starter.ai.support.mapper.SysAiManufacturerKeyMapper;
import com.chua.starter.ai.support.mapper.SysAiManufacturerMapper;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ChatClient 热更新器
 * 监听设置变更事件，动态更新 ChatClientRegistry
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientHotUpdater {

    private final ChatClientRegistry chatClientRegistry;
    private final SysAiManufacturerMapper manufacturerMapper;
    private final SysAiManufacturerKeyMapper manufacturerKeyMapper;
    private final SysAiGlobalSettingMapper globalSettingMapper;

    /**
     * 监听设置变更事件，按分组触发热更新
     *
     * <p>当前仅处理 {@code models} 分组：刷新全部默认能力对应的 ChatClient。</p>
     *
     * @param event 设置变更事件
     */
    @EventListener
    public void onSettingChanged(SettingChangedEvent event) {
        String group = event.getGroup();
        log.info("[ChatClientHotUpdater] 收到设置变更事件: group={}", group);

        // 处理 models 分组的变更
        if ("models".equals(group)) {
            refreshAllDefaultChatClients();
        }
    }

    /**
     * 刷新所有默认类型的 ChatClient
     */
    public void refreshAllDefaultChatClients() {
        // 1. 对话模型
        refreshDefaultChatClient("defaultChatModelId", "chat");
        // 2. 标题总结模型
        refreshDefaultChatClient("defaultTitleSummaryModelId", "summary");
        // 3. 视觉辅助模型
        refreshDefaultChatClient("defaultVisualAssistantModelId", "vision");
        // 4. 历史压缩模型
        refreshDefaultChatClient("defaultHistoryCompressionModelId", "compression");
        // 5. 翻译模型
        refreshDefaultChatClient("defaultTranslationModelId", "translation");
    }

    /**
     * 刷新指定类型的默认 ChatClient
     *
     * @param settingField 设置字段名
     * @param capability   能力ID（chat/summary/vision/compression/translation）
     */
    public void refreshDefaultChatClient(String settingField, String capability) {
        try {
            log.info("[ChatClientHotUpdater] 开始刷新默认 {} ChatClient...", capability);

            // 1. 从全局设置读取默认模型ID
            String defaultModelId = getSettingValue(settingField);
            if (defaultModelId == null || defaultModelId.isEmpty()) {
                log.warn("[ChatClientHotUpdater] 未配置默认 {} 模型", capability);
                return;
            }

            // 2. 解析模型ID格式：providerId:modelName
            String[] parts = defaultModelId.split(":", 2);
            if (parts.length != 2) {
                log.warn("[ChatClientHotUpdater] {} 模型ID格式错误: {}", capability, defaultModelId);
                return;
            }

            String providerIdStr = parts[0];
            String modelName = parts[1];
            Long providerId = Long.parseLong(providerIdStr);

            // 3. 从数据库读取厂商配置
            SysAiManufacturer manufacturer = manufacturerMapper.selectById(providerId);
            if (manufacturer == null) {
                log.warn("[ChatClientHotUpdater] 厂商不存在: id={}", providerId);
                return;
            }

            if (manufacturer.getStatus() != 1) {
                log.warn("[ChatClientHotUpdater] 厂商已停用: {}", manufacturer.getName());
                return;
            }

            // 4. 获取厂商的API Key（支持多Key）
            List<SysAiManufacturerKey> keys = manufacturerKeyMapper.selectList(
                    new LambdaQueryWrapper<SysAiManufacturerKey>()
                            .eq(SysAiManufacturerKey::getSysAiManufacturerCode, manufacturer.getCode())
                            .eq(SysAiManufacturerKey::getStatus, 1)
            );

            String appKey = manufacturer.getAppKey();
            if (keys != null && !keys.isEmpty()) {
                appKey = keys.get(0).getValue();
            }

            // 5. 创建 ChatClient
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(manufacturer.getCode())
                    .baseUrl(manufacturer.getBaseUrl())
                    .appKey(appKey)
                    .build();

            ChatClient chatClient = ChatClient.create(setting);
            if (chatClient == null) {
                log.error("[ChatClientHotUpdater] 创建 {} ChatClient 失败: provider={}", capability, manufacturer.getCode());
                return;
            }

            // 6. 注册到 ChatClientRegistry
            chatClientRegistry.register(manufacturer.getCode(), modelName, chatClient, capability);

            // 7. 如果是对话模型，设置为默认
            if ("chat".equals(capability)) {
                chatClientRegistry.setDefault(chatClient);
            }

            log.info("[ChatClientHotUpdater] 默认 {} ChatClient 已更新: provider={}, model={}",
                    capability, manufacturer.getName(), modelName);

        } catch (Exception e) {
            log.error("[ChatClientHotUpdater] 刷新默认 {} ChatClient 失败: {}", capability, e.getMessage(), e);
        }
    }

    /**
     * 从全局设置读取指定字段的值
     *
     * @param field 字段名
     * @return 字段值，未配置时返回null
     */
    private String getSettingValue(String field) {
        SysAiGlobalSetting setting = globalSettingMapper.selectOne(
                new LambdaQueryWrapper<SysAiGlobalSetting>()
                        .eq(SysAiGlobalSetting::getSettingGroup, "models")
                        .eq(SysAiGlobalSetting::getField, field)
                        .eq(SysAiGlobalSetting::getStatus, 1)
        );
        return setting != null ? setting.getValue() : null;
    }
}
