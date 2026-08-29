package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.BotInboundMessage;
import com.chua.common.support.ai.bot.BotMessageListener;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 iLink Bot 聊天引擎。
 *
 * <p>为每个已绑定的微信 Bot 启动 iLink 消息轮询（getupdates），
 * 收到微信消息后调用配置的 AI 模型生成回复，并通过 sendmessage 回发。</p>
 *
 * <p>每个 Bot 可配置独立的 AI 模型（{@link SysAiBotBinding#getModel()}），
 * AI 客户端基于模型管理配置的 provider（DB）即时构建。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBotChatService {

    /** Bot 绑定 Mapper */
    private final SysAiBotBindingMapper botBindingMapper;

    /** 模型表 Mapper（按模型名查所属 provider） */
    private final SysAiProviderModelMapper providerModelMapper;

    /** Provider 表 Mapper（获取 platform/baseUrl/凭证） */
    private final SysAiProviderMapper providerMapper;

    /** 运行中的 Bot：botId -> iLink 客户端 */
    private final Map<String, ILinkBotClient> runningBots = new ConcurrentHashMap<>();

    /**
     * 应用上下文刷新后加载所有已绑定的微信 Bot 并启动消息轮询。
     *
     * <p>使用 {@link ContextRefreshedEvent} 而非 {@code @PostConstruct}，
     * 确保 ChatClientRegistry 已初始化完成。</p>
     */
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        log.info("[BotChat] 启动微信 Bot 聊天引擎，加载已绑定 Bot...");
        List<SysAiBotBinding> bots = botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getPlatform, "wechat")
                        .eq(SysAiBotBinding::getStatus, "online")
                        .isNotNull(SysAiBotBinding::getBotToken));
        for (SysAiBotBinding bot : bots) {
            startBot(bot);
        }
        log.info("[BotChat] 聊天引擎加载完成，共启动 {} 个 Bot", runningBots.size());
    }

    /**
     * 启动单个 Bot 的消息轮询与 AI 回复。
     *
     * <p>若该 Bot 已在运行则先停止，避免重复轮询。</p>
     *
     * @param bot Bot 绑定记录
     */
    public void startBot(SysAiBotBinding bot) {
        if (bot == null || bot.getBotId() == null || bot.getBotToken() == null) {
            return;
        }
        stopBot(bot.getBotId());

        ILinkBotClient client = new ILinkBotClient();
        // token = iLink bot_token，登录后凭证；有 token 时 start() 直接轮询不扫码
        client.configure(bot.getBotToken(), null, null);
        client.addMessageListener(new BotMessageListener() {
            @Override
            public void onMessage(BotInboundMessage message) {
                handleMessage(bot, client, message);
            }
        });
        client.addErrorListener(e -> log.warn("[BotChat] Bot {} 消息轮询出错: {}", bot.getBotId(), e.getMessage()));

        runningBots.put(bot.getBotId(), client);
        // 已有 botToken 时直接轮询（getupdates 长轮询阻塞），避免 start() 强制重新扫码登录
        new Thread(() -> {
            try {
                client.startPolling();
            } catch (Exception e) {
                log.error("[BotChat] Bot {} 启动失败", bot.getBotId(), e);
            }
        }, "bot-chat-" + bot.getBotId()).start();
        log.info("[BotChat] Bot {} 已启动，模型={}", bot.getBotId(), bot.getModel());
    }

    /**
     * 停止指定 Bot 的消息轮询。
     *
     * @param botId Bot 唯一标识
     */
    public void stopBot(String botId) {
        ILinkBotClient client = runningBots.remove(botId);
        if (client != null) {
            client.stop();
            log.info("[BotChat] Bot {} 已停止", botId);
        }
    }

    /**
     * 处理微信入站消息：调用 AI 生成回复并回发。
     */
    private void handleMessage(SysAiBotBinding bot, ILinkBotClient client, BotInboundMessage message) {
        String content = message.getContent();
        String fromUser = message.getFromUser();
        if (content == null || content.isBlank() || fromUser == null) {
            return;
        }
        log.info("[BotChat] Bot {} 收到消息 from={}: {}", bot.getBotId(), fromUser, content);
        try {
            String reply = aiReply(bot, content);
            if (reply != null && !reply.isBlank()) {
                client.sendText(fromUser, reply);
                log.info("[BotChat] Bot {} 已回复 from={}", bot.getBotId(), fromUser);
                // 更新最后活跃时间
                bot.setLastActiveTime(LocalDateTime.now());
                botBindingMapper.updateById(bot);
            }
        } catch (Exception e) {
            log.error("[BotChat] Bot {} 回复失败 from={}", bot.getBotId(), fromUser, e);
            try {
                client.sendText(fromUser, "抱歉，我暂时无法回复，请稍后再试。");
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }

    /**
     * 用 Bot 配置的模型生成 AI 回复。
     *
     * <p>基于模型管理配置的 provider（DB）即时构建 ChatClient：
     * 根据模型名查 provider_model → provider（platform/baseUrl/凭证），
     * 用 {@link ChatClient#create(String, String, String)} 构建并调用。</p>
     */
    private String aiReply(SysAiBotBinding bot, String userMsg) {
        String modelName = bot.getModel();
        if (modelName == null || modelName.isBlank()) {
            return "请先为该 Bot 设置 AI 模型。";
        }
        try {
            // 1. 按模型名查 provider_model，确定所属 provider
            SysAiProviderModel pm = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, modelName)
                            .eq(SysAiProviderModel::getEnabled, true));
            if (pm == null) {
                log.warn("[BotChat] 模型 {} 未在模型管理中配置", modelName);
                return "模型 " + modelName + " 未配置，请在模型管理中检查。";
            }
            // 2. 查 provider，取 platform / baseUrl / 凭证
            SysAiProvider provider = providerMapper.selectById(pm.getProviderId());
            if (provider == null || provider.getBaseUrl() == null) {
                return "模型 " + modelName + " 的供应商不存在。";
            }
            String apiKey = extractApiKey(provider.getCredentials());
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
            // 3. 构建 ChatClient 并回复
            ChatClient chatClient = ChatClient.create(ChatClientSetting.builder()
                    .provider(provider.getPlatform())
                    .appKey(apiKey)
                    .baseUrl(provider.getBaseUrl())
                    .extraHeaders(extraHeaders)
                    .build())
                    .model(modelName);
            return chatClient.chatSync(userMsg);
        } catch (Exception e) {
            log.error("[BotChat] AI 回复失败 bot={} model={}", bot.getBotId(), modelName, e);
            return "AI 回复失败：" + e.getMessage();
        }
    }

    /**
     * 从 provider 凭证中提取 API Key。
     *
     * <p>凭证结构：{@code {"api_keys": ["sk-...", ...]}}，取第一个；
     * 兼容 {@code apiKey} 键。</p>
     */
    private static String extractApiKey(Map<String, Object> credentials) {
        if (credentials == null) {
            return null;
        }
        Object keys = credentials.get("api_keys");
        if (keys instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return list.get(0).toString();
        }
        Object key = credentials.get("apiKey");
        return key != null ? key.toString() : null;
    }

    /**
     * 从 provider extra 中提取 custom_headers。
     *
     * <p>extra 结构：{@code {"custom_headers": {"X-Key": "value", ...}}}</p>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object headers = extra.get("custom_headers");
        if (headers instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result.isEmpty() ? null : result;
        }
        return null;
    }
}
