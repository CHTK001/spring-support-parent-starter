package com.chua.starter.ai.support.manage.updater;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.route.RouteTarget;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.entity.SysAiManufacturer;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import com.chua.starter.ai.support.manage.event.SettingChangedEvent;
import com.chua.starter.ai.support.mapper.SysAiGlobalSettingMapper;
import com.chua.starter.ai.support.mapper.SysAiManufacturerKeyMapper;
import com.chua.starter.ai.support.mapper.SysAiManufacturerMapper;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
    private final ModelClientResolver modelClientResolver;

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

            // 3. 从数据库读取厂商配置。
            //
            //    供应商 ID 不一定都是数字：新式供应商走 sys_ai_provider，ID 可以是
            //    pollinations-local 这类字符串。此前这里无条件 Long.parseLong，遇到
            //    非数字 ID 直接抛 NumberFormatException，整个「刷新默认 chat ChatClient」
            //    在启动期就失败（日志里只有 For input string: "xxx"），默认客户端始终
            //    建不出来。解析失败时不再抛异常，转交 ModelClientResolver 走统一路径。
            Long providerId = parseProviderIdOrNull(providerIdStr);
            SysAiManufacturer manufacturer = providerId == null ? null : manufacturerMapper.selectById(providerId);
            if (manufacturer == null) {
                registerFromProviderTable(defaultModelId, providerIdStr, modelName, capability);
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
            ChatClientSetting.ChatClientSettingBuilder builder = ChatClientSetting.builder()
                    .provider(manufacturer.getCode())
                    .baseUrl(manufacturer.getBaseUrl())
                    .appKey(appKey);

            // 免费渠道：注入动态渠道路由（OAuth 渠道连接自动收集）
            if (ProviderRouteTargets.isFreeChannel(manufacturer.getCode())) {
                Map<String, RouteTarget> channelRoutes =
                        modelClientResolver.collectChannelRoutes("_free");
                if (channelRoutes != null && !channelRoutes.isEmpty()) {
                    builder.routes(channelRoutes);
                    log.info("[ChatClientHotUpdater] 免费渠道已注入 {} 条路由: provider={}",
                            channelRoutes.size(), manufacturer.getCode());
                }
            }

            ChatClientSetting setting = builder.build();

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
     * 解析供应商 ID 前缀。
     *
     * <p>只有纯数字才转 {@link Long}（旧式厂商表主键）；其余形态（如
     * {@code pollinations-local}）返回 {@code null}，由调用方改用新式供应商路径。</p>
     *
     * @param raw ID 前缀
     * @return 数字 ID；非数字或空时返回 {@code null}
     */
    private static Long parseProviderIdOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从新式供应商表（{@code sys_ai_provider}）注册默认客户端。
     *
     * <p>默认模型配成 {@code <providerId>:<model>} 且 providerId 不是数字时走这里：
     * 由 {@link ModelClientResolver} 统一完成供应商解析、密钥收集与客户端构建，
     * 与主对话链路同源，避免热更新器维护第二套构建逻辑而漂移。</p>
     *
     * @param qualifiedModel 完整限定名
     * @param providerId     供应商 ID
     * @param modelName      模型名
     * @param capability     能力标识
     */
    private void registerFromProviderTable(String qualifiedModel, String providerId,
                                           String modelName, String capability) {
        ChatClient chatClient = modelClientResolver.resolve(qualifiedModel);
        if (chatClient == null) {
            log.warn("[ChatClientHotUpdater] 默认 {} 模型 {} 未能构建客户端（供应商 {} 未启用或缺密钥）",
                    capability, qualifiedModel, providerId);
            return;
        }
        chatClientRegistry.register(providerId, modelName, chatClient, capability);
        if ("chat".equals(capability)) {
            chatClientRegistry.setDefault(chatClient);
        }
        log.info("[ChatClientHotUpdater] 默认 {} ChatClient 已更新（provider 表）: provider={}, model={}",
                capability, providerId, modelName);
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
