package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.BotInboundMessage;
import com.chua.common.support.ai.bot.BotMessageListener;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
 * 未配置时回退到默认 ChatClient。</p>
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

    /** AI 对话客户端注册中心（按模型获取 ChatClient） */
    private final ChatClientRegistry chatClientRegistry;

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
        // 后台线程启动消息轮询（getupdates 长轮询阻塞）
        new Thread(() -> {
            try {
                client.start();
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
     * 用 Bot 配置的模型（或默认）生成 AI 回复。
     */
    private String aiReply(SysAiBotBinding bot, String userMsg) {
        ChatClient chatClient = null;
        // 优先使用 Bot 配置的模型
        if (bot.getModel() != null && !bot.getModel().isBlank()) {
            chatClient = chatClientRegistry.getByModel(bot.getModel());
        }
        if (chatClient == null) {
            chatClient = chatClientRegistry.getDefault();
        }
        if (chatClient == null) {
            log.warn("[BotChat] 无可用 ChatClient（模型={}），无法回复", bot.getModel());
            return "当前未配置可用的 AI 模型。";
        }
        return chatClient.chatSync(userMsg);
    }
}
