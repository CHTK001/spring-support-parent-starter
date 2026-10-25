package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.BotClient;
import com.chua.common.support.ai.bot.BotInboundMessage;
import com.chua.common.support.ai.bot.BotMessageListener;
import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.bot.BotSendResult;
import com.chua.common.support.probability.ProbabilityCollision;
import com.chua.common.support.probability.SimpleProbabilityCollision;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.whatsapp.support.bot.WhatsAppBotClient;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.entity.SysAiBotMessage;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.model.BotConversation;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.chat.AgentChatClient;
import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.manage.api.AgentMcpRuntimeService;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import com.chua.starter.ai.support.mapper.SysAiBotMessageMapper;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.manage.websocket.SysAiWebSocketHandler;
import com.chua.starter.ai.support.util.ProviderCredentials;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import com.chua.starter.ai.support.util.AiBotCaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 微信 i链接 机器人 聊天引擎。
 *
 * <p>为每个已绑定的微信 Bot 启动 iLink 消息轮询（getupdates），
 * 收到微信消息后调用配置的 AI 模型生成回复，并通过 sendmessage 回发。</p>
 *
 * <p>每个 Bot 可配置独立的 AI 模型（{@link SysAiBotBinding#getModel()}），
 * AI 客户端基于模型管理配置的 提供者（DB）即时构建。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBotChatService {

    /**
     * 机器人 绑定 映射器
    */
    private final SysAiBotBindingMapper botBindingMapper;

    /**
     * 模型表 映射器（按模型名查所属 提供者）
    */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 提供者 表 映射器（获取 platform/baseurl/凭证）
    */
    private final SysAiProviderMapper providerMapper;

    /**
     * 机器人 聊天消息 映射器（进度聊天历史落库）
    */
    private final SysAiBotMessageMapper botMessageMapper;

    /**
     * WS 推送（新消息广播给 IM 页）
    */
    private final SysAiWebSocketHandler webSocketHandler;

    /**
     * 全局设置读取（机器人 默认模型兜底）
    */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 生效对话模型解析器（连接器级 → Bot 默认 → 配置中心默认），全项目唯一解析口径
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    /**
     * 角色表映射器（连接器绑定角色或兜底通用角色）
     */
    private final SysAiRoleMapper sysAiRoleMapper;

    /**
     * 角色提示词解析器（分形态提示词 → 角色历史提示词）
     */
    private final SysAiRolePromptResolver sysAiRolePromptResolver;

    /**
     * 用量记录拦截器（记录每次 机器人 AI 调用的 令牌/费用）
    */
    private final UsageRecorderInterceptor usageRecorderInterceptor;

    /**
     * MCP 运行时服务（Bot 勾选 MCP 时实时连接）。
     */
    private final AgentMcpRuntimeService agentMcpRuntimeService;
    private final AgentProviderFactory agentProviderFactory;

    /**
     * 运行中的 机器人：机器人标识 -> 平台客户端（wechat=ILink，feishu/qq 等为对应 SPI 实现）。
    */
    private final Map<String, BotClient> runningBots = new ConcurrentHashMap<>();

    /**
     * 通用助手角色主键（id=1，系统内置）。
     */
    private static final Integer GENERAL_ASSISTANT_ID = 1;

    /**
     * 应用上下文刷新后加载所有已绑定的微信 机器人 并启动消息轮询。
     *
     * <p>使用 {@link ContextRefreshedEvent} 而非 {@code @PostConstruct}，
     * 确保 对话客户端registry 已初始化完成。</p>
     */
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        log.info("[BotChat] 启动 Bot 聊天引擎，加载所有平台已绑定 Bot...");
        List<SysAiBotBinding> bots = botBindingMapper.selectList(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getStatus, "online")
                        .isNotNull(SysAiBotBinding::getBotToken));
        for (SysAiBotBinding bot : bots) {
            // 逐条隔离：单个 Bot 配置异常不得影响其它平台启动
            try {
                startBot(bot);
            } catch (Exception e) {
                log.error("[BotChat] 启动 Bot 失败 platform={} botId={}，跳过该 Bot",
                        bot.getPlatform(), bot.getBotId(), e);
            }
        }
        log.info("[BotChat] 聊天引擎加载完成，共启动 {} 个 Bot", runningBots.size());
    }

    /**
     * 启动单个 机器人 的消息监听与 AI 回复（多平台）。
     *
     * <p>若该 Bot 已在运行则先停止，避免重复连接：</p>
     * <ul>
     *   <li>{@code wechat}：构建 {@link ILinkBotClient}，有 botToken 时直接 {@code startPolling}
     *       长轮询（避免 start() 强制重新扫码），轮询在独立线程阻塞；</li>
     *   <li>{@code feishu / qq} 等凭据型：经 {@link BotClient#auto(String)} 构建，
     *       用 appId/appSecret 配置后 {@code start()}（客户端内部自行管理网关线程、心跳与重连）。</li>
     * </ul>
     *
     * @param bot 机器人 绑定记录
     */
    public void startBot(SysAiBotBinding bot) {
        if (bot == null || bot.getBotId() == null || bot.getBotToken() == null) {
            return;
        }
        stopBot(bot.getBotId());

        String platform = bot.getPlatform();
        BotClient client;
        if ("wechat".equals(platform)) {
            // token = iLink bot_token，登录后凭证
            ILinkBotClient ilink = new ILinkBotClient();
            ilink.configure(bot.getBotToken(), null, null);
            // 补全机器人自身标识；重启后内存令牌丢失时回查持久化 context_token
            ilink.accountId(bot.getBotId());
            ilink.contextTokenLoader((bid, user) -> loadLatestContextToken(bid, user));
            client = ilink;
        } else if ("whatsapp".equals(platform)) {
            // WhatsApp Cloud API：SPI 取客户端，Webhook 入站由 AiBotChatService 转发
            BotClient auto = BotClient.auto(platform);
            if (!(auto instanceof WhatsAppBotClient whatsapp)) {
                throw new IllegalStateException("whatsapp SPI 未返回 WhatsAppBotClient");
            }
            whatsapp.configure(bot.getBotId(), bot.getBotToken(),
                    bot.getAppSecret(), bot.getVerifyToken());
            client = whatsapp;
        } else {
            // 凭据型：botId=appId，botToken=appSecret
            client = BotClient.auto(platform);
            client.configure(bot.getBotId(), bot.getBotToken(), null);
        }

        final BotClient managed = client;
        client.addMessageListener(new BotMessageListener() {
            @Override
            public void onMessage(BotInboundMessage message) {
                handleMessage(bot, managed, message);
            }
        });
        client.addErrorListener(e -> log.warn("[BotChat] Bot {} 消息监听出错: {}", bot.getBotId(), e.getMessage()));

        runningBots.put(bot.getBotId(), client);
        if (client instanceof ILinkBotClient ilink) {
            // iLink 长轮询阻塞，放独立线程
            new Thread(() -> {
                try {
                    ilink.startPolling();
                } catch (Exception e) {
                    log.error("[BotChat] Bot {} 启动失败", bot.getBotId(), e);
                }
            }, "bot-chat-" + bot.getBotId()).start();
        } else {
            // 凭据型客户端 start() 自带网关线程，非阻塞
            client.start();
        }
        log.info("[BotChat] Bot {}({}) 已启动，模型={}", bot.getBotId(), platform, bot.getModel());
    }

    /**
     * 查询 机器人 运行状态（客户端是否存活）。
     *
     * <p>wechat 会话超时（errcode=-14）后客户端停止轮询并从 runningBots 移除，
     * 前端据此提示「会话已过期，请重新绑定」。</p>
     *
     * @param botId 机器人 唯一标识
     * @return running=存活；stopped=未在运行（超时/停止），需重新绑定
     */
    public String runtimeStatus(String botId) {
        BotClient client = runningBots.get(botId);
 // 只用对象存在判断不够：会话超时后 客户端 仍留在 映射 中，
        // 但内部监听已停止（isRunning=false）。必须看真实运行状态。
        if (client != null && client.isRunning()) {
            return "running";
        }
 // 会话已停止：从 映射 移除，避免悬挂引用，并提示需要重新绑定
        if (client != null) {
            runningBots.remove(botId);
        }
        return "stopped";
    }

    /**
     * 停止指定 机器人 的消息监听。
     *
     * @param botId 机器人 唯一标识
     */
    public void stopBot(String botId) {
        BotClient client = runningBots.remove(botId);
        if (client != null) {
            client.stop();
            log.info("[BotChat] Bot {} 已停止", botId);
        }
    }

    /**
     * 校验 WhatsApp Webhook GET 握手（hub.mode / hub.verify_token / hub.challenge）。
     *
     * @param mode      hub.mode，须为 subscribe
     * @param token     hub.verify_token
     * @param challenge hub.challenge
     * @return 校验通过返回 challenge，否则返回 null
     */
    public String verifyWhatsAppWebhook(String mode, String token, String challenge) {
        if (!"subscribe".equalsIgnoreCase(mode) || token == null || token.isBlank()
                || challenge == null || challenge.isBlank()) {
            return null;
        }
        try {
            List<SysAiBotBinding> bindings = botBindingMapper.selectList(
                    Wrappers.<SysAiBotBinding>lambdaQuery()
                            .eq(SysAiBotBinding::getPlatform, "whatsapp")
                            .eq(SysAiBotBinding::getStatus, "online")
                            .eq(SysAiBotBinding::getVerifyToken, token)
                            .last("LIMIT 1"));
            return bindings == null || bindings.isEmpty() ? null : challenge;
        } catch (Exception e) {
            log.warn("[BotChat] WhatsApp Webhook 校验失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 处理 WhatsApp Webhook POST 回调，按 phone_number_id 转发给对应运行中的客户端。
     *
     * @param body    原始请求体
     * @param headers 请求头
     * @return 回调结果
     */
    public WhatsAppBotClient.WhatsAppCallbackResult handleWhatsAppWebhook(
            String body, Map<String, String> headers) {
        Set<String> phoneNumberIds = extractWhatsAppPhoneNumberIds(body);
        WhatsAppBotClient.WhatsAppCallbackResult last = null;
        for (String phoneNumberId : phoneNumberIds) {
            BotClient client = runningBots.get(phoneNumberId);
            if (client instanceof WhatsAppBotClient whatsapp) {
                last = whatsapp.handleCallback(body, headers);
            }
        }
        if (last == null) {
            // 无匹配的运行中 Bot：仍需回 200，否则 Meta 会持续重试并在多次失败后停用回调
            log.warn("[BotChat] WhatsApp 回调未匹配到运行中的 Bot，phoneNumberIds={}", phoneNumberIds);
            return new WhatsAppBotClient.WhatsAppCallbackResult(200,
                    "{\"status\":\"ignored\",\"msg\":\"no running whatsapp bot\"}");
        }
        return last;
    }

    /**
     * 从 Meta Webhook 回调体中提取所有 phone_number_id。
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractWhatsAppPhoneNumberIds(String body) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            Map<String, Object> root = Json.fromJson(body);
            Object entryObj = root.get("entry");
            if (!(entryObj instanceof List<?> entries)) {
                return ids;
            }
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> entryMap)) {
                    continue;
                }
                Object changesObj = entryMap.get("changes");
                if (!(changesObj instanceof List<?> changes)) {
                    continue;
                }
                for (Object change : changes) {
                    if (!(change instanceof Map<?, ?> changeMap)) {
                        continue;
                    }
                    Object valueObj = changeMap.get("value");
                    if (!(valueObj instanceof Map<?, ?> value)) {
                        continue;
                    }
                    Object metadataObj = value.get("metadata");
                    if (metadataObj instanceof Map<?, ?> metadata) {
                        Object id = metadata.get("phone_number_id");
                        if (id != null && !String.valueOf(id).isBlank()) {
                            ids.add(String.valueOf(id));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[BotChat] 解析 WhatsApp 回调 phone_number_id 失败: {}", e.getMessage());
        }
        return ids;
    }

    /**
     * 按平台与会话类型选择正确的回复目标并发送。
     *
     * <p>wechat（iLink）沿用 {@code sendText(发送者, ...)}；其余平台（qq/feishu）：
     * 群聊以会话目标 {@code chatId}（group/channel openid）走 {@code sendToGroup}，
     * 私聊以用户 openid 走 {@code sendText}。群回复所需的被动 msg_id 由客户端按
     * chatId 记忆的被动上下文自动附带。</p>
     *
     * @param bot     机器人绑定
     * @param client  平台客户端
     * @param message 触发回复的入站消息
     * @param reply   回复内容
     * @return 发送结果
     */
    private BotSendResult sendReply(SysAiBotBinding bot, BotClient client,
                                    BotInboundMessage message, String reply) {
        String fromUser = message.getFromUser();
        if (!"wechat".equals(bot.getPlatform()) && message.isFromGroup()) {
            String groupTarget = message.getChatId() != null ? message.getChatId() : fromUser;
            return client.sendToGroup(groupTarget, reply);
        }
        return client.sendText(fromUser, reply);
    }

    /**
     * 处理入站消息：调用 AI 生成回复并回发（多平台）。
     * @param bot 机器人
     * @param client 客户端
     * @param message 消息
     */
    private String handleMessage(SysAiBotBinding bot, BotClient client, BotInboundMessage message) {
        String content = message.getContent();
        String fromUser = message.getFromUser();
        if (content == null || content.isBlank() || fromUser == null) {
            return "SKIP_EMPTY";
        }
        // 每次入站重新读库：startBot 闭包里捕获的 bot 是启动时快照，
        // 管理员在连接器页改模型后不重启轮询，微信侧仍会一直用旧模型。
        SysAiBotBinding fresh = findBinding(bot.getBotId());
        if (fresh != null) {
            bot = fresh;
            log.info("[BotChat] Bot {} 入站重读绑定 model={} roleId={}",
                    bot.getBotId(), bot.getModel(), bot.getRoleId());
        }
        // 会话键：群聊用群 chatId，私聊用用户 openid，保证上下文按会话隔离
        boolean fromGroup = message.isFromGroup();
        String chatId = fromGroup
                ? (message.getChatId() != null ? message.getChatId() : fromUser)
                : fromUser;
        log.info("[BotChat] Bot {} 收到消息 chat={} from={} group={}: {}",
                bot.getBotId(), chatId, fromUser, fromGroup, content);
        // 幂等声明：跨实例同时收到同一条消息时，仅第一个落库成功的实例会处理并回复
        String dedupKey = inboundDedupKey(bot, message, chatId);
        boolean claimed = recordMessage(bot.getBotId(), "IN", fromUser, chatId, fromGroup, content,
                bot.getOwnerKey(), dedupKey, message.getContextToken());
        if (!claimed) {
            log.info("[BotChat] 入站消息已被其他实例处理，跳过回复 bot={} chat={} key={}",
                    bot.getBotId(), chatId, dedupKey);
            return "SKIP_DUPLICATE";
        }

        // ===== 路径 A（优先级最高）：被直接点名 → 常规直接回复 =====
        // 私聊总是回复；群聊：mentionOnly 关闭时回复，开启时仅被 @ 才回复。
        boolean addressed = !fromGroup
                || !Boolean.TRUE.equals(bot.getMentionOnly())
                || isMentioned(bot, message);
        boolean directReplied = false;
        if (!addressed) {
            log.info("[BotChat] 群消息未 @ Bot，不做直接回复 bot={} from={}", bot.getBotId(), fromUser);
        } else {
            try {
                // 直接回复也带多轮记忆：按绑定的「上下文条数」（random_context_count，默认 10）
                // 取本会话最近 N 条历史（排除当前这条刚落库的 IN），组装成 user/assistant 多轮。
                int memoryCount = bot.getRandomContextCount() == null ? 10 : bot.getRandomContextCount();
                List<ChatMessage> priorTurns = loadRecentTurns(bot.getBotId(), chatId, dedupKey, memoryCount);
                AiReplyAttribution attribution = new AiReplyAttribution();
                String reply = aiReply(bot, content, priorTurns, attribution);
                if (attribution.isFallback()) {
                    log.warn("[BotChat] Bot {} 回复已回退（指定 {} → 实际 {}）：{}",
                            bot.getBotId(), attribution.getRequestedModel(),
                            attribution.getUsedModel(), attribution.getFallbackReason());
                }
                if (reply != null && !reply.isBlank()) {
                    BotSendResult result = sendReply(bot, client, message, reply);
                    if (result.isSuccess()) {
                        log.info("[BotChat] Bot {} 直接回复 chat={} text={}", bot.getBotId(), chatId, reply);
                        recordMessage(bot.getBotId(), "OUT", fromUser, chatId, fromGroup, reply,
                                bot.getOwnerKey(), null, null);
                        directReplied = true;
                    } else {
                        log.warn("[BotChat] Bot {} 直接回复发送失败 chat={} code={} msg={} text={}",
                                bot.getBotId(), chatId, result.getErrorCode(), result.getErrorMessage(), reply);
                    }
                }
            } catch (Exception e) {
                log.error("[BotChat] Bot {} 直接回复失败 chat={}", bot.getBotId(), chatId, e);
                try {
                    sendReply(bot, client, message, "抱歉，我暂时无法回复，请稍后再试。");
                } catch (Exception ignored) {
                    // 忽略
                }
            }
        }

        // ===== 路径 B：随机冒泡（群聊/私聊每条都参与，独立于 @，在直接回复之后处理）=====
        boolean randomFired = false;
        if (Boolean.TRUE.equals(bot.getRandomReplyEnabled())) {
            double probability = bot.getRandomProbability() == null ? 0.0 : bot.getRandomProbability();
            if (!probabilityCollision().hitPercent(probability)) {
                log.info("[BotChat] 随机冒泡未命中（{}%）bot={} from={}", probability, bot.getBotId(), fromUser);
            } else {
                log.info("[BotChat] 随机冒泡命中（{}%），组装上下文 bot={} chat={}",
                        probability, bot.getBotId(), chatId);
                try {
                    int contextCount = bot.getRandomContextCount() == null ? 10 : bot.getRandomContextCount();
                    String proactive = aiReply(bot, buildContextPrompt(bot.getBotId(), chatId, contextCount));
                    if (proactive != null && !proactive.isBlank()) {
                        BotSendResult randomResult = sendReply(bot, client, message, proactive);
                        if (randomResult.isSuccess()) {
                            log.info("[BotChat] Bot {} 随机冒泡已发送 chat={} text={}",
                                    bot.getBotId(), chatId, proactive);
                            recordMessage(bot.getBotId(), "OUT", fromUser, chatId, fromGroup, proactive,
                                    bot.getOwnerKey(), null, null);
                            randomFired = true;
                        } else {
                            log.warn("[BotChat] Bot {} 随机冒泡发送失败 chat={} code={} msg={}",
                                    bot.getBotId(), chatId, randomResult.getErrorCode(),
                                    randomResult.getErrorMessage());
                        }
                    }
                } catch (Exception e) {
                    log.error("[BotChat] Bot {} 随机冒泡失败 chat={}", bot.getBotId(), chatId, e);
                }
            }
        }

        // 更新最后活跃时间
        if (directReplied || randomFired) {
            bot.setLastActiveTime(LocalDateTime.now());
            botBindingMapper.updateById(bot);
        }
        if (directReplied) {
            return "REPLIED";
        }
        if (randomFired) {
            return "RANDOM_FIRED";
        }
        return addressed ? "SEND_FAILED" : "SKIP_NOT_MENTIONED";
    }

    /**
     * 【仅用于业务验收】以捕获型客户端驱动真实消息处理链路，不真正对外发送。
     *
     * @param bindingId 绑定 id
     * @param message   模拟入站消息
     * @return 决策结果：outcome（决策码）、sentText（捕获到的回复，可空）
     */
    public Map<String, Object> simulateInbound(Long bindingId, BotInboundMessage message) {
        SysAiBotBinding bot = botBindingMapper.selectById(bindingId);
        Map<String, Object> result = new LinkedHashMap<>(4);
        if (bot == null) {
            result.put("outcome", "BINDING_NOT_FOUND");
            return result;
        }
        CapturingBotClient stub = new CapturingBotClient();
        String outcome = handleMessage(bot, stub, message);
        result.put("outcome", outcome);
        result.put("sentText", stub.captured);
        return result;
    }

    /**
     * 捕获型客户端：拦截 sendText / sendToGroup，不发起真实网络请求，仅记录待发送文本。
     */
    static final class CapturingBotClient extends ILinkBotClient {
        private String captured;

        @Override
        public BotSendResult sendText(String toUser, String content) {
            this.captured = content;
            return BotSendResult.ok("simulated");
        }

        @Override
        public BotSendResult sendToGroup(String groupId, String content) {
            this.captured = content;
            return BotSendResult.ok("simulated");
        }
    }

    /**
     * 判断入站消息是否 @ 了 Bot 自身。
     *
     * <p>判定优先级：平台显式标记 {@code mentionedBot} → @ 列表包含 Bot 标识/昵称
     * → 文本中出现「@昵称」。都不满足视为未 @。</p>
     *
     * @param bot     机器人绑定
     * @param message 入站消息
     * @return 是否 @ 了 Bot
     */
    private boolean isMentioned(SysAiBotBinding bot, BotInboundMessage message) {
        if (Boolean.TRUE.equals(message.getMentionedBot())) {
            return true;
        }
        List<String> mentioned = message.getMentionedList();
        if (mentioned != null) {
            for (String id : mentioned) {
                if (id == null) {
                    continue;
                }
                if (id.equals(bot.getBotId())
                        || (bot.getNickname() != null && id.contains(bot.getNickname()))) {
                    return true;
                }
            }
        }
        String text = message.getContent();
        return text != null
                && bot.getNickname() != null
                && text.contains("@" + bot.getNickname());
    }

    /**
     * 组装某会话最近 N 条上下文，构造交给 AI 的提示词（随机冒泡 / AI 发送复用）。
     *
     * <p>取该会话（bot + chatId）最近 {@code count} 条消息，按时间升序拼成聊天记录，
     * 并要求 AI 以参与者口吻自然接一句话。</p>
     *
     * @param botId  机器人标识
     * @param chatId 会话标识
     * @param count  上下文条数
     * @return 含上下文的提示词
     */
    private String buildContextPrompt(String botId, String chatId, int count) {
        int safeCount = Math.max(1, count);
        List<SysAiBotMessage> recent = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(SysAiBotMessage::getChatId, chatId)
                        .orderByDesc(SysAiBotMessage::getCreateTime)
                        .last("LIMIT " + safeCount));
        Collections.reverse(recent);

        StringBuilder transcript = new StringBuilder(512);
        for (SysAiBotMessage m : recent) {
            String speaker = "IN".equals(m.getDirection())
                    ? "对方"
                    : "你（Bot）";
            transcript.append(speaker).append("：").append(m.getContent()).append('\n');
        }
        return "以下是最近的聊天上下文：\n" + transcript
                + "\n请像其中的参与者一样，结合上下文自然地接一句话参与对话，"
                + "直接输出你要说的内容，不要解释、不要复述记录、不要加引号或任何前缀。";
    }

    /**
     * 取本会话最近 N 条历史，组装成多轮 role 消息（user/assistant），用于直接回复的上下文记忆。
     *
     * <p>排除当前这条刚落库的入站消息（按 dedupKey），避免与作为当前输入再次发送而重复；
     * OUT 行 dedupKey 为 NULL、历史 IN 行为各自不同的 dedupKey，故条件为
     * {@code dedup IS NULL OR dedup <> 当前key}。按 id 倒序取最近 N 条后再反转为时间正序。</p>
     *
     * @param botId           机器人标识
     * @param chatId          会话标识
     * @param currentDedupKey 当前入站消息的去重键
     * @param count           上下文条数
     * @return 时间正序的多轮消息（无历史时返回空列表）
     */
    private List<ChatMessage> loadRecentTurns(String botId, String chatId, String currentDedupKey, int count) {
        int safeCount = Math.max(0, count);
        if (safeCount == 0) {
            return List.of();
        }
        List<SysAiBotMessage> recent = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(SysAiBotMessage::getChatId, chatId)
                        .and(w -> w.isNull(SysAiBotMessage::getDedupKey)
                                .or().ne(SysAiBotMessage::getDedupKey, currentDedupKey))
                        .orderByDesc(SysAiBotMessage::getId)
                        .last("LIMIT " + safeCount));
        if (recent.isEmpty()) {
            return List.of();
        }
        Collections.reverse(recent);
        return toTurns(recent);
    }

    /**
     * 取本会话最近 N 条历史组装成多轮 role 消息（user/assistant），不做 dedup 排除。
     *
     * <p>用于「AI 发送」：操作者输入的 prompt 并未落库为 IN 消息，因此无需排除
     * 当前入站行，直接取最近上下文作为记忆即可。count=0 返回空列表。</p>
     *
     * @param botId  机器人标识
     * @param chatId 会话标识
     * @param count  上下文条数
     * @return 时间正序的多轮消息
     */
    private List<ChatMessage> loadHistoryTurns(String botId, String chatId, int count) {
        int safeCount = Math.max(0, count);
        if (safeCount == 0) {
            return List.of();
        }
        List<SysAiBotMessage> recent = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(SysAiBotMessage::getChatId, chatId)
                        .orderByDesc(SysAiBotMessage::getId)
                        .last("LIMIT " + safeCount));
        if (recent.isEmpty()) {
            return List.of();
        }
        Collections.reverse(recent);
        return toTurns(recent);
    }

    /**
     * 把按时间正序排列的消息行映射为多轮 role 消息（IN→user，OUT→assistant）。
     *
     * @param rows 时间正序的消息行
     * @return 多轮消息
     */
    private static List<ChatMessage> toTurns(List<SysAiBotMessage> rows) {
        List<ChatMessage> turns = new ArrayList<>(rows.size());
        for (SysAiBotMessage m : rows) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String role = "IN".equals(m.getDirection()) ? "user" : "assistant";
            turns.add(ChatMessage.builder().role(role).content(m.getContent()).build());
        }
        return turns;
    }

    /**
     * 主动向指定微信用户发送一条消息（聊天框手动发送 / 主动触达）。
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 目标微信用户 标识
     * @param content  消息内容
     * @param caller   调用方归属（用于越权拦截）
     * @return 发送结果
     */
    public BotSendResult sendMessage(String botId, String fromUser, String content, AiBotCaller caller) {
        SysAiBotBinding binding = findBinding(botId);
        // 以他人机器人对外发消息会消耗归属者的凭证与信任，先按绑定归属拦越权
        caller.requireReadable(binding == null ? null : binding.getOwnerKey());
        BotClient client = runningBots.get(botId);
        if (client == null) {
            return BotSendResult.fail(-1, "Bot 未在运行，请先在连接器管理页启用");
        }
        if (content == null || content.isBlank()) {
            return BotSendResult.fail(-1, "消息内容不能为空");
        }
        BotSendResult result = client.sendText(fromUser, content);
        if (result.isSuccess()) {
            recordMessage(botId, "OUT", fromUser, fromUser, false, content,
                    binding == null ? null : binding.getOwnerKey(), null, null);
        } else {
            log.warn("[BotChat] 主动发送失败 bot={} to={} code={} msg={}",
                    botId, fromUser, result.getErrorCode(), result.getErrorMessage());
        }
        return result;
    }

    // ==================== 连接器会话监控（按 chatId 隔离上下文） ====================

    /**
     * 列出某连接器下的会话（按 chatId 聚合，取每个会话最后一条消息）。
     *
     * @param bindingId 绑定 id
     * @param caller    调用方归属
     * @return 会话列表（按最后消息时间降序）
     */
    public List<Map<String, Object>> listBindingConversations(Long bindingId, AiBotCaller caller) {
        SysAiBotBinding binding = loadBinding(bindingId, caller);
        List<SysAiBotMessage> all = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, binding.getBotId())
                        .orderByAsc(SysAiBotMessage::getCreateTime));
        Map<String, Map<String, Object>> byChat = new LinkedHashMap<>();
        for (SysAiBotMessage m : all) {
            String key = m.getChatId() != null ? m.getChatId() : m.getFromUser();
            Map<String, Object> c = byChat.computeIfAbsent(key, k -> {
                Map<String, Object> x = new LinkedHashMap<>(8);
                x.put("chatId", key);
                x.put("fromGroup", Boolean.TRUE.equals(m.getFromGroup()));
                x.put("messageCount", 0);
                return x;
            });
            c.put("lastContent", m.getContent());
            c.put("lastTime", m.getCreateTime());
            c.put("messageCount", (Integer) c.get("messageCount") + 1);
            if (Boolean.TRUE.equals(m.getFromGroup())) {
                c.put("fromGroup", true);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(byChat.values());
        result.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("lastTime");
            LocalDateTime tb = (LocalDateTime) b.get("lastTime");
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        return result;
    }

    /**
     * 查询某会话的消息（按时间升序），构成该会话独立上下文。
     *
     * @param bindingId 绑定 id
     * @param chatId    会话标识
     * @param caller    调用方归属
     * @return 消息列表
     */
    public List<SysAiBotMessage> listConversationMessages(Long bindingId, String chatId, AiBotCaller caller) {
        SysAiBotBinding binding = loadBinding(bindingId, caller);
        return botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, binding.getBotId())
                        .and(w -> w.eq(SysAiBotMessage::getChatId, chatId)
                                .or().eq(SysAiBotMessage::getFromUser, chatId))
                        .orderByAsc(SysAiBotMessage::getCreateTime));
    }

    /**
     * 清除某会话的全部消息（重置该会话独立上下文）。
     *
     * <p>删除该绑定（botId）下指定 chatId 的全部消息；清除后该会话将从会话列表消失，
     * 直到外部平台再次发来消息。属写操作，仅归属本人可清除。</p>
     *
     * @param bindingId 绑定 id
     * @param chatId    会话标识
     * @param caller    调用方归属
     * @return 删除的消息条数
     */
    public int clearConversation(Long bindingId, String chatId, AiBotCaller caller) {
        SysAiBotBinding binding = loadBinding(bindingId, caller);
        caller.requireWritable(binding.getOwnerKey());
        int deleted = botMessageMapper.delete(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, binding.getBotId())
                        .and(w -> w.eq(SysAiBotMessage::getChatId, chatId)
                                .or().eq(SysAiBotMessage::getFromUser, chatId)));
        log.info("[Connector] 清除会话 id={} chat={} deleted={}", bindingId, chatId, deleted);
        return deleted;
    }

    /**
     * 以 Bot 身份向某会话手动发送一条消息。
     *
     * @param bindingId 绑定 id
     * @param chatId    会话标识
     * @param content   消息内容
     * @param caller    调用方归属
     * @return 发送结果
     */
    public BotSendResult sendToConversation(Long bindingId, String chatId, String content, AiBotCaller caller) {
        SysAiBotBinding binding = loadBinding(bindingId, caller);
        if (content == null || content.isBlank()) {
            return BotSendResult.fail(-1, "消息内容不能为空");
        }
        BotClient client = runningBots.get(binding.getBotId());
        if (client == null) {
            return BotSendResult.fail(-1, "连接器未在运行，请先在连接器管理页启用");
        }
        boolean fromGroup = isGroupConversation(binding.getBotId(), chatId);
        BotSendResult result = dispatch(client, binding, chatId, fromGroup, content);
        if (result.isSuccess()) {
            String author = "wechat".equals(binding.getPlatform()) && fromGroup
                    ? lastInboundAuthor(binding.getBotId(), chatId) : chatId;
            recordMessage(binding.getBotId(), "OUT", author != null ? author : chatId,
                    chatId, fromGroup, content, binding.getOwnerKey(), null, null);
        }
        return result;
    }

    /**
     * AI 发送：以操作者输入的 prompt 为当前问题，附带该会话最近 N 条上下文作为多轮记忆，
     * 交给 AI 生成回复并发送。
     *
     * @param bindingId    绑定 id
     * @param chatId       会话标识
     * @param prompt       操作者输入的问题/指令（必填，不落库为 IN）
     * @param contextCount 上下文条数（可空，空=绑定配置的条数）
     * @param caller       调用方归属
     * @return reply（AI 回复）、sent（是否已发送）、错误信息
     */
    public Map<String, Object> aiSendToConversation(Long bindingId, String chatId, String prompt,
                                                    Integer contextCount, AiBotCaller caller) {
        SysAiBotBinding binding = loadBinding(bindingId, caller);
        int count = contextCount != null && contextCount > 0
                ? contextCount
                : (binding.getRandomContextCount() != null ? binding.getRandomContextCount() : 10);
        // 当前问题用操作者输入的 prompt；最近上下文以多轮 role 消息注入（count=0 表示不带历史）
        List<ChatMessage> history = loadHistoryTurns(binding.getBotId(), chatId, count);
        AiReplyAttribution attribution = new AiReplyAttribution();
        String reply = aiReply(binding, prompt, history, attribution);
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("reply", reply);
        // 把"实际用哪个模型"带出去：目标模型失败会回退默认，若不显式说明，
        // 前端只会显示一条正常回复，造成"切了模型却没生效"的错觉。
        data.put("model", attribution.getUsedModel());
        data.put("requestedModel", attribution.getRequestedModel());
        String modelNotice = attribution.notice();
        if (modelNotice != null) {
            data.put("modelNotice", modelNotice);
            log.warn("[BotChat] Bot {} 回复已回退：{}", binding.getBotId(), modelNotice);
        }

        boolean usable = reply != null && !reply.isBlank()
                && !reply.startsWith("AI 回复失败") && !reply.contains("未配置") && !reply.contains("未在模型管理");
        BotClient client = runningBots.get(binding.getBotId());
        if (!usable) {
            data.put("sent", false);
            return data;
        }
        if (client == null) {
            data.put("sent", false);
            data.put("errorMessage", "连接器未在运行，请先在连接器管理页启用");
            return data;
        }
        boolean fromGroup = isGroupConversation(binding.getBotId(), chatId);
        BotSendResult result = dispatch(client, binding, chatId, fromGroup, reply);
        data.put("sent", result.isSuccess());
        data.put("errorCode", result.getErrorCode());
        data.put("errorMessage", result.getErrorMessage());
        if (result.isSuccess()) {
            String author = "wechat".equals(binding.getPlatform()) && fromGroup
                    ? lastInboundAuthor(binding.getBotId(), chatId) : chatId;
            recordMessage(binding.getBotId(), "OUT", author != null ? author : chatId,
                    chatId, fromGroup, reply, binding.getOwnerKey(), null, null);
        }
        return data;
    }

    // ---- 连接器监控辅助 ----

    /**
     * 加载绑定并校验可读。
     */
    private SysAiBotBinding loadBinding(Long bindingId, AiBotCaller caller) {
        SysAiBotBinding binding = botBindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new IllegalArgumentException("连接器不存在");
        }
        caller.requireReadable(binding.getOwnerKey());
        return binding;
    }

    /**
     * 该会话是否为群聊（看是否存在 fromGroup=1 的消息）。
     */
    private boolean isGroupConversation(String botId, String chatId) {
        Long cnt = botMessageMapper.selectCount(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(SysAiBotMessage::getChatId, chatId)
                        .eq(SysAiBotMessage::getFromGroup, true));
        return cnt != null && cnt > 0;
    }

    /**
     * 取该会话最近一条入站消息的作者（wechat 群被动回复需发给作者）。
     */
    private String lastInboundAuthor(String botId, String chatId) {
        List<SysAiBotMessage> ins = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(SysAiBotMessage::getChatId, chatId)
                        .eq(SysAiBotMessage::getDirection, "IN")
                        .orderByDesc(SysAiBotMessage::getCreateTime)
                        .last("LIMIT 1"));
        return ins.isEmpty() ? null : ins.getFirst().getFromUser();
    }

    /**
     * 按平台/会话类型选择正确目标发送。
     *
     * <p>wechat：私聊 sendText(用户)，群 sendText(最近作者)（沿用被动回复语义）；
     * qq/feishu：群 sendToGroup(chatId)，私聊 sendText(chatId)。</p>
     */
    private BotSendResult dispatch(BotClient client, SysAiBotBinding binding,
                                   String chatId, boolean fromGroup, String text) {
        if ("wechat".equals(binding.getPlatform())) {
            String target = fromGroup ? lastInboundAuthor(binding.getBotId(), chatId) : chatId;
            return client.sendText(target != null ? target : chatId, text);
        }
        if (fromGroup) {
            return client.sendToGroup(chatId, text);
        }
        return client.sendText(chatId, text);
    }

    /**
     * 查询指定 机器人 与某用户的聊天记录（按时间升序）。
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 目标微信用户 标识（可空，空则查询该 机器人 全部消息）
     * @param caller   调用方归属（用于越权拦截）
     * @return 聊天记录列表
     */
    public List<SysAiBotMessage> listMessages(String botId, String fromUser, AiBotCaller caller) {
        requireBotReadable(botId, caller);
        return botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(fromUser != null && !fromUser.isBlank(), SysAiBotMessage::getFromUser, fromUser)
                        .orderByAsc(SysAiBotMessage::getCreateTime));
    }

    /**
     * 分页查询指定 机器人 与某用户的聊天记录（供 IM 页滚动加载历史）。
     *
     * <p>第 0 页为最近 {@code pageSize} 条；页码越大越早（按时间倒序取窗口后反转升序），
     * 保证前端「先显示最新、向上滚动加载更早」的展示语义。</p>
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 目标微信用户 标识（可空，空则查询该 机器人 全部消息）
     * @param page     页码（从 0 开始，非法值按 0 处理）
     * @param pageSize 每页条数（非法值按 50 处理）
     * @param caller   调用方归属（用于越权拦截）
     * @return 分页结果：messages（升序）、hasMore、page、pageSize
     */
    public Map<String, Object> listMessagesPaged(String botId, String fromUser, int page, int pageSize,
                                                 AiBotCaller caller) {
        requireBotReadable(botId, caller);
        int safePage = Math.max(0, page);
        int safeSize = pageSize > 0 ? pageSize : 50;
        int offset = safePage * safeSize;
        // 倒序取窗口（最近的在最前），再反转回升序
        List<SysAiBotMessage> window = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(fromUser != null && !fromUser.isBlank(), SysAiBotMessage::getFromUser, fromUser)
                        .orderByDesc(SysAiBotMessage::getCreateTime)
                        .last("LIMIT " + offset + ", " + safeSize));
        Collections.reverse(window);
        Long total = botMessageMapper.selectCount(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(fromUser != null && !fromUser.isBlank(), SysAiBotMessage::getFromUser, fromUser));
        boolean hasMore = offset + window.size() < (total != null ? total : 0L);
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("messages", window);
        data.put("hasMore", hasMore);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        return data;
    }

    /**
     * 用 机器人 配置（或全局默认）的 AI 模型润色一段文本。
     *
     * <p>用于 IM 输入框的「润色」按钮：不落库、不回发，仅返回润色后的文本。</p>
     *
     * @param botId 机器人 唯一标识
     * @param text  待润色文本
     * @param caller 调用方归属（用于越权拦截）
     * @return 润色结果，模型未配置/调用失败时返回空字符串
     */
    public String polishText(String botId, String text, AiBotCaller caller) {
        if (botId == null || botId.isBlank() || text == null || text.isBlank()) {
            return "";
        }
        requireBotReadable(botId, caller);
        SysAiBotBinding bot = findBinding(botId);
        ChatClient client = buildModelChatClient(resolveModelName(bot));
        if (client == null) {
            log.warn("[BotChat] 润色失败：Bot {} 未解析到可用模型", botId);
            return "";
        }
        try {
            return client
                    .system("你是一名专业的文本润色助手。请在不改变原意的前提下，将用户输入润色为更得体、流畅、自然的中文表达，直接输出润色后的文本，不要添加任何解释或前后缀。")
                    .chatSync(text);
        } catch (Exception e) {
            log.error("[BotChat] 润色失败 bot={}", botId, e);
            return "";
        }
    }

    /**
     * 用 机器人 配置（或全局默认）的 AI 模型总结指定会话的聊天记录。
     *
     * <p>供 IM 页「上下文总结」按钮使用：将历史消息拼接后交给模型生成要点摘要，
     * 便于用户将摘要粘贴回输入框继续对话。最多取最近 200 条消息。</p>
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 目标微信用户 标识（可空，空则总结该 机器人 全部消息）
     * @param caller   调用方归属（用于越权拦截）
     * @return 会话摘要，无消息/模型未配置/调用失败时返回空字符串
     */
    public String summarizeConversation(String botId, String fromUser, AiBotCaller caller) {
        if (botId == null || botId.isBlank()) {
            return "";
        }
        List<SysAiBotMessage> messages = listMessages(botId, fromUser, caller);
        if (messages.isEmpty()) {
            return "";
        }
        // 只取最近 200 条，避免摘要输入过长
        int from = Math.max(0, messages.size() - 200);
        StringBuilder transcript = new StringBuilder(1024); // 摘要文本长度不可预估
        for (SysAiBotMessage m : messages.subList(from, messages.size())) {
            String role = "IN".equals(m.getDirection()) ? "用户" : "Bot";
            transcript.append("[").append(role).append("] ").append(m.getContent()).append("\n");
        }
        ChatClient client = buildModelChatClient(resolveModelName(findBinding(botId)));
        if (client == null) {
            log.warn("[BotChat] 总结失败：Bot {} 未解析到可用模型", botId);
            return "";
        }
        try {
            return client
                    .system("你是一名对话摘要助手。请将用户提供的聊天记录提炼为简明扼要的中文要点摘要，覆盖主要话题与结论，直接输出摘要内容，不要添加任何解释。")
                    .chatSync(transcript.toString());
        } catch (Exception e) {
            log.error("[BotChat] 总结失败 bot={}", botId, e);
            return "";
        }
    }

    /**
     * 按 机器人标识 查询 机器人 绑定记录。
     *
     * @param botId 机器人 唯一标识
     * @return 绑定记录，未找到返回 空
     */
    private SysAiBotBinding findBinding(String botId) {
        if (botId == null || botId.isBlank()) {
            return null;
        }
        return botBindingMapper.selectOne(
                Wrappers.<SysAiBotBinding>lambdaQuery()
                        .eq(SysAiBotBinding::getBotId, botId)
                        .last("LIMIT 1"));
    }

    /**
     * 校验调用方可否读取该 机器人 的聊天数据。
     *
     * <p>可见性随绑定归属：管理员（含本地信任）可跨归属查看，普通调用方仅能读取
     * 归属一致的 机器人；未绑定或历史无归属记录按未归属口径处理。</p>
     *
     * @param botId  机器人 唯一标识
     * @param caller 调用方归属
     */
    private void requireBotReadable(String botId, AiBotCaller caller) {
        SysAiBotBinding binding = findBinding(botId);
        caller.requireReadable(binding == null ? null : binding.getOwnerKey());
    }

    /**
     * 解析 机器人 使用的模型名。
     *
     * <p>解析口径统一收在 {@link EffectiveChatModelResolver}：
     * 机器人 自己配的模型 → Bot 全局默认 → 配置中心「对话路由」的默认对话模型；
     * 三级都不可用时返回 空，<b>不做自动兜底</b> —— 悄悄换一个模型会让用户
     * 以为用的是自己配的那个。</p>
     *
     * @param bot 机器人 绑定记录（可为 空）
     * @return 模型名，无法解析时返回 空
     */
    private String resolveModelName(SysAiBotBinding bot) {
        String resolved = effectiveChatModelResolver.resolve(bot == null ? null : bot.getModel());
        if (resolved == null) {
            log.warn("[BotChat] 未解析到可用对话模型（机器人未单独配、Bot 默认与配置中心默认均不可用）");
        }
        return resolved;
    }

    /**
     * 解析 机器人 使用的系统提示词：绑定角色提示词 > 通用助手角色提示词 > 全局默认提示词。
     *
     * <p>连接器原来的 {@code systemPrompt} 不再作为提示词来源，避免写死模型名或人设。
     *
     * @param bot 机器人 绑定记录（可为 空）
     * @return 生效的系统提示词，无则返回 {@code null}
     */
    private String resolveSystemPrompt(SysAiBotBinding bot) {
        Integer selectedRoleId = bot != null && bot.getRoleId() != null
                ? bot.getRoleId() : GENERAL_ASSISTANT_ID;
        String rolePrompt = resolveRolePrompt(selectedRoleId);
        if (rolePrompt != null && !rolePrompt.isBlank()) {
            return rolePrompt;
        }
        if (!GENERAL_ASSISTANT_ID.equals(selectedRoleId)) {
            String defaultPrompt = resolveRolePrompt(GENERAL_ASSISTANT_ID);
            if (defaultPrompt != null && !defaultPrompt.isBlank()) {
                return defaultPrompt;
            }
        }
        String global = globalSettingReader.getStr("bot", "system_prompt");
        return (global == null || global.isBlank()) ? null : global.trim();
    }

    /**
     * 按角色主键解析 build 形态提示词。
     *
     * @param roleId 角色主键
     * @return 角色提示词正文；角色不存在或未配置时返回 {@code null}
     */
    private String resolveRolePrompt(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        try {
            SysAiRole role = sysAiRoleMapper.selectById(roleId);
            if (role == null) {
                log.warn("[BotChat] 提示词角色不存在 roleId={}", roleId);
                return null;
            }
            String prompt = sysAiRolePromptResolver.resolve(role, SysAiRolePromptResolver.MODE_BUILD);
            log.info("[BotChat] 解析角色提示词 roleId={} role={} found={}",
                    roleId, role.getName(), !(prompt == null || prompt.isBlank()));
            return prompt;
        } catch (Exception e) {
            log.warn("[BotChat] 解析角色提示词失败 roleId={}: {}", roleId, e.getMessage());
            return null;
        }
    }

    /**
     * 按模型名从模型管理配置（提供者_模型 → 提供者）构建不带 系统 提示符 的 对话客户端。
     *
     * <p>供润色/摘要等辅助场景复用：只负责「模型→供应商→凭证」的解析与客户端构建，
     * 系统提示词由调用方按场景注入。</p>
     *
     * @param modelName 模型名称
     * @return ChatClient，模型未配置/供应商不存在时返回 空
     */
    private ChatClient buildModelChatClient(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        try {
            SysAiProviderModel pm = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, modelName)
                            .eq(SysAiProviderModel::getEnabled, true));
            if (pm == null) {
                return null;
            }
            SysAiProvider provider = providerMapper.selectById(pm.getProviderId());
            if (provider == null || provider.getBaseUrl() == null) {
                return null;
            }
            String apiKey = extractApiKey(provider.getCredentials());
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
            return ChatClient.create(buildSetting(provider, apiKey, extraHeaders))
                    .model(modelName);
        } catch (Exception e) {
            log.warn("[BotChat] 构建模型 ChatClient 失败 model={}: {}", modelName, e.getMessage());
            return null;
        }
    }

    /**
     * 查询 机器人 内置命令清单（供 IM 输入框斜杠命令面板）。
     *
     * @return 命令列表（prefix / 名称 / description）
     * @param commands 命令
     /**
      * 列表命令。
      * @return 列表命令的结果
      */
    public List<Map<String, Object>> listCommands() {
        List<Map<String, Object>> commands = new ArrayList<>(8); // 命令数量不可预估
        addCommand(commands, "/", "reset", "清空当前会话上下文");
        addCommand(commands, "/", "model", "切换对话模型");
        addCommand(commands, "/", "help", "查看可用命令");
        addCommand(commands, "@", "@用户", "主动给指定用户发送消息");
        addCommand(commands, "#", "#用户", "AI 回复指定用户");
        return commands;
    }

    /**
     * 添加Command。
     *
     * @param commands 方法入参 commands
     * @param prefix 前缀，不允许为 null
     * @param name 名称，不允许为 null
     * @param desc 描述，不允许为 null
     */
    private void addCommand(List<Map<String, Object>> commands, String prefix, String name, String desc) {
        Map<String, Object> cmd = new LinkedHashMap<>(4);
        cmd.put("prefix", prefix);
        cmd.put("name", name);
        cmd.put("description", desc);
        commands.add(cmd);
    }

    /**
     * 查询指定 机器人 的最近联系人（去重 by 从用户）。
     *
     * @param botId  机器人 唯一标识
     * @param caller 调用方归属（用于越权拦截）
     * @return 联系人列表
     */
    public List<String> listContacts(String botId, AiBotCaller caller) {
        requireBotReadable(botId, caller);
        List<SysAiBotMessage> messages = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .isNotNull(SysAiBotMessage::getFromUser)
                        .select(SysAiBotMessage::getFromUser));
        return messages.stream()
                .map(SysAiBotMessage::getFromUser)
                .filter(u -> u != null && !u.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 会话列表：按 机器人 + 微信用户聚合，取每个会话最后一条消息。
     *
     * <p>供 IM 页左侧会话列表使用；昵称与状态取自绑定记录（按 botId 匹配）。</p>
     *
     * @param caller 调用方归属（用于越权拦截）
     * @return 会话列表（按最后消息时间降序）
     */
    public List<BotConversation> listConversations(AiBotCaller caller) {
        // 先取绑定归属：会话列表按 机器人 归属过滤，他人往来内容不得出现在本人列表
        List<SysAiBotBinding> bindings = botBindingMapper.selectList(Wrappers.lambdaQuery());
        Map<String, SysAiBotBinding> byBotId = bindings.stream()
                .filter(b -> b.getBotId() != null)
                .collect(Collectors.toMap(SysAiBotBinding::getBotId, b -> b, (a, b) -> a));
        List<SysAiBotMessage> all = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .orderByAsc(SysAiBotMessage::getCreateTime));
        Map<String, BotConversation> byKey = new LinkedHashMap<>(64); // 会话数量不可预估
        for (SysAiBotMessage m : all) {
            SysAiBotBinding binding = byBotId.get(m.getBotId());
            if (!caller.canRead(binding == null ? null : binding.getOwnerKey())) {
                continue;
            }
            String key = m.getBotId() + "|" + (m.getFromUser() == null ? "" : m.getFromUser());
            BotConversation conv = byKey.computeIfAbsent(key, k -> {
                BotConversation c = new BotConversation();
                c.setBotId(m.getBotId());
                c.setFromUser(m.getFromUser());
                return c;
            });
            conv.setLastContent(m.getContent());
            conv.setLastTime(m.getCreateTime());
        }
        List<BotConversation> result = new ArrayList<>(byKey.values());
 // 补全在线状态（按绑定记录与 机器人标识 匹配）
        for (BotConversation c : result) {
            SysAiBotBinding binding = byBotId.get(c.getBotId());
            if (binding != null) {
                c.setNickname(binding.getNickname());
                c.setStatus(binding.getStatus());
                c.setPlatform(binding.getPlatform());
            }
            if (c.getNickname() == null || c.getNickname().isBlank()) {
                c.setNickname(c.getFromUser());
            }
        }
        // 注：不再为“无消息历史的绑定”伪造会话条目。
 // 旧实现用 nickname/机器人标识 冒充 从用户，与 WS 推送、/消息、/发送 使用的
 // 真实微信用户 标识 语义冲突（会导致重复会话、发送到错误目标）；
        // 现在只返回存在真实消息往来的会话，前端空状态文案已引导用户先扫码绑定。
        result.sort((a, b) -> {
            if (a.getLastTime() == null) {
                return 1;
            }
            if (b.getLastTime() == null) {
                return -1;
            }
            return b.getLastTime().compareTo(a.getLastTime());
        });
        return result;
    }

    /**
     * 将消息记录从旧 机器人 迁移到新 机器人（重新扫码后 机器人标识 变化时保留历史聊天）。
     *
     * <p>微信扫码会话过期重新绑定后，iLink 分配的 botId 可能变化；
     * 调用此方法把旧 机器人标识 下的聊天记录批量改写到新 机器人标识，保证前端历史不断裂。</p>
     *
     * @param oldBotId 原 机器人 唯一标识（允许为 空，为空时跳过）
     * @param newBotId 新 机器人 唯一标识（不能为空）
     * @return 迁移的消息条数
     */
    public int migrateMessages(String oldBotId, String newBotId) {
        if (newBotId == null || newBotId.isBlank() || oldBotId == null || oldBotId.isBlank()
                || oldBotId.equals(newBotId)) {
            return 0;
        }
        int moved = botMessageMapper.update(null,
                Wrappers.<SysAiBotMessage>lambdaUpdate()
                        .eq(SysAiBotMessage::getBotId, oldBotId)
                        .set(SysAiBotMessage::getBotId, newBotId));
        if (moved > 0) {
            log.info("[BotChat] 迁移消息 {} → {} 共 {} 条", oldBotId, newBotId, moved);
        }
        return moved;
    }

    /**
     * 删除单个会话的聊天记录（按 机器人 + 微信用户），并广播到 IM 页。
     *
     * <p>用于清理僵尸会话：绑定记录已删除后，消息表仍残留历史，
     * 通过此接口按 机器人标识 + 从用户 定向清除。</p>
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 微信用户 标识
     * @param caller   调用方归属（用于越权拦截）
     * @return 删除的消息条数
     */
    public int deleteConversation(String botId, String fromUser, AiBotCaller caller) {
        SysAiBotBinding binding = findBinding(botId);
        // 删除只认归属本身：管理员对他人聊天记录只读
        caller.requireWritable(binding == null ? null : binding.getOwnerKey());
        int deleted = botMessageMapper.delete(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .eq(SysAiBotMessage::getBotId, botId)
                        .eq(fromUser != null && !fromUser.isBlank(), SysAiBotMessage::getFromUser, fromUser));
        log.info("[BotChat] 删除会话 botId={} fromUser={} 共 {} 条消息", botId, fromUser, deleted);
        // 通知 IM 页刷新会话列表
            Map<String, Object> push = new HashMap<>(8);
        push.put("type", "bot.conversation-deleted");
        push.put("botId", botId);
        push.put("fromUser", fromUser);
        webSocketHandler.sendToUser(AiUserContext.currentUserId(), "bot.conversation-deleted", push);
        return deleted;
    }

    /**
     * 查询某 Bot 与目标用户/会话最近一条持久化的 context_token（重启后回填）。
     *
     * <p>按 chat_id（私聊即用户 openid）或 from_user 匹配目标，取 id 最大的非空令牌；
     * 无历史令牌（用户从未在重启后发过消息）时返回 {@code null}。</p>
     *
     * @param botId 机器人标识
     * @param user  目标用户/会话标识
     * @return 最近上下文令牌，无则 {@code null}
     */
    private String loadLatestContextToken(String botId, String user) {
        if (botId == null || user == null) {
            return null;
        }
        try {
            List<SysAiBotMessage> rows = botMessageMapper.selectList(
                    Wrappers.<SysAiBotMessage>lambdaQuery()
                            .eq(SysAiBotMessage::getBotId, botId)
                            .isNotNull(SysAiBotMessage::getContextToken)
                            .and(w -> w.eq(SysAiBotMessage::getChatId, user)
                                    .or().eq(SysAiBotMessage::getFromUser, user))
                            .orderByDesc(SysAiBotMessage::getId)
                            .last("LIMIT 1"));
            return rows.isEmpty() ? null : rows.getFirst().getContextToken();
        } catch (Throwable e) {
            log.error("[BotChat] loadLatestContextToken 查询异常 bot={} user={}", botId, user, e);
            return null;
        }
    }

    /**
     * 落库一条 机器人 聊天消息（失败不阻断主流程），并广播到 IM 页。
     *
     * @param botId     机器人 唯一标识
     * @param direction 入（用户→机器人）/ 出（机器人→用户）
     * @param fromUser  消息作者标识
     * @param chatId    会话标识（群=群 openid，私聊=用户 openid）
     * @param fromGroup 是否群聊
     * @param content   消息内容
     * @param ownerKey  所属 机器人 绑定的归属标识（{@code u:<userId>} / {@code fp:<指纹>}，可为空）
     * @param dedupKey  幂等去重键（仅入站 IN 消息传入，OUT 传 {@code null}）
     * @return {@code true}=落库成功（或非重复类异常，按尽力而为不阻断）；
     *         {@code false}=命中唯一键冲突，说明同一条消息已被本实例或其他实例处理，调用方应跳过
     */
    private boolean recordMessage(String botId, String direction, String fromUser, String chatId,
                               boolean fromGroup, String content, String ownerKey, String dedupKey,
                               String contextToken) {
        try {
            SysAiBotMessage msg = new SysAiBotMessage();
            msg.setBotId(botId);
            msg.setDirection(direction);
            msg.setFromUser(fromUser);
            msg.setChatId(chatId != null ? chatId : fromUser);
            msg.setFromGroup(fromGroup);
            msg.setContent(content);
            msg.setCreateTime(LocalDateTime.now());
            msg.setDedupKey(dedupKey);
            // 仅入站 IN 消息携带 context_token，随消息持久化，供重启后回填
            msg.setContextToken(contextToken);
            // 归属随 机器人 绑定：轮询线程取不到请求身份，且设备指纹流量不算到任何用户头上
            msg.setUserId(AiBotCaller.userOf(ownerKey));
            botMessageMapper.insert(msg);
            // 广播到 IM 页（新消息 / 新会话实时刷新）
        Map<String, Object> push = new HashMap<>(8);
            push.put("type", "bot.message");
            push.put("botId", botId);
            push.put("direction", direction);
            push.put("fromUser", fromUser);
            push.put("chatId", msg.getChatId());
            push.put("fromGroup", fromGroup);
            push.put("content", content);
            push.put("dedupKey", dedupKey);
            push.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            webSocketHandler.sendToUser(msg.getUserId(), "bot.message", push);
            return true;
        } catch (DuplicateKeyException e) {
            // 多实例并发处理同一条消息：唯一键冲突，仅一个实例能成功，其余在此被拦下
            log.info("[BotChat] 重复消息已拦截（幂等）bot={} dir={} chat={} key={}",
                    botId, direction, chatId, dedupKey);
            return false;
        } catch (Exception e) {
            log.warn("[BotChat] 消息落库失败 bot={} dir={}: {}", botId, direction, e.getMessage());
            // 非重复类异常按尽力而为处理，不阻断 IN 主链路（与改造前行为一致）
            return true;
        }
    }

    /**
     * 构造入站消息的幂等去重键。
     *
     * <p>平台提供稳定消息 ID（如 QQ 的消息 {@code id}）时，直接用
     * {@code ID:botId|chatId|msgId}，可精确去重、零误判；</p>
     *
     * <p>平台不提供稳定 ID（如 iLink 早期用随机值）时，退化为
     * {@code FP:botId|chatId|60s时间窗|发送者+内容指纹}：多实例对同一条消息的重复投递
     * 几乎同时发生、落在同一时间窗内从而被去重；窗口仅 60 秒，正常间隔的重复发言不受影响。</p>
     *
     * @param bot     机器人绑定
     * @param message 入站消息
     * @param chatId  会话标识
     * @return 幂等去重键
     */
    private String inboundDedupKey(SysAiBotBinding bot, BotInboundMessage message, String chatId) {
        String botId = bot.getBotId();
        String msgId = message.getMsgId();
        if (msgId != null && !msgId.isBlank()) {
            return "ID:" + botId + "|" + chatId + "|" + msgId;
        }
        long ts = message.getCreateTime() > 0L ? message.getCreateTime() : System.currentTimeMillis();
        long bucket = ts / 60000L;
        String fingerprint = DigestUtils.md5DigestAsHex(
                (message.getFromUser() + "\n" + message.getContent())
                        .getBytes(StandardCharsets.UTF_8));
        return "FP:" + botId + "|" + chatId + "|" + bucket + "|" + fingerprint;
    }

    /**
     * 获取概率碰撞实现（common-starter SPI）。
     *
     * <p>通过 {@link ServiceProvider} 加载默认的 {@link ProbabilityCollision}；
     * 类路径无注册实现时回退到 {@link SimpleProbabilityCollision}，保证不返回空。</p>
     *
     * @return 概率碰撞实现
     */
    private static ProbabilityCollision probabilityCollision() {
        // SPI 加载可能因实现类缺少 @Spi 注解等直接抛 IllegalStateException，
        // 必须失败安全：任何加载异常都回退到内置简单实现，绝不让概率组件拖垮整条消息链路。
        try {
            ProbabilityCollision collision = ServiceProvider
                    .of(ProbabilityCollision.class)
                    .getSpiService();
            if (collision != null) {
                return collision;
            }
        } catch (Throwable e) {
            log.warn("[BotChat] ProbabilityCollision SPI 加载失败，回退内置实现: {}", e.getMessage());
        }
        return new SimpleProbabilityCollision();
    }

    /**
     * 用 机器人 配置的模型生成 AI 回复。
     *
     * <p>基于模型管理配置的 provider（DB）即时构建 ChatClient：
     * 根据模型名查 提供者_模型 → 提供者（platform/baseurl/凭证），
     * 用 {@link ChatClient#create(String, String, String)} 构建并调用。</p>
     * @param bot 机器人
     * @param userMsg 用户msg
     * @return AIreply的结果
     */
    private String aiReply(SysAiBotBinding bot, String userMsg) {
        return aiReply(bot, userMsg, List.of());
    }

    /**
     * 用 机器人 配置的模型生成 AI 回复（可携带多轮历史）。
     *
     * @param bot     机器人绑定
     * @param userMsg 当前用户消息
     * @param history 多轮历史（user/assistant，时间正序，可空）
     * @return AI 回复
     */
    private String aiReply(SysAiBotBinding bot, String userMsg, List<ChatMessage> history) {
        return aiReply(bot, userMsg, history, null);
    }

    /**
     * 用 机器人 配置的模型生成 AI 回复，并（可选）回填模型归属。
     *
     * @param bot         机器人绑定
     * @param userMsg     当前用户消息
     * @param history     多轮历史（可空）
     * @param attribution 模型归属回填对象（可空）；非空时记录请求模型、实际模型与回退原因
     * @return AI 回复
     */
    private String aiReply(SysAiBotBinding bot, String userMsg, List<ChatMessage> history,
                           AiReplyAttribution attribution) {
        // 候选模型（按优先级、去重、仅保留「模型管理」里存在且启用的）：
        // ① 机器人/Bot 绑定自己配的 → ② Bot 全局默认 → ③ 配置中心「对话路由」默认对话模型。
        // 口径统一收在 EffectiveChatModelResolver；三级都不可用时返回空，
        // 由下面给出明确提示 —— 不自动挑模型。
        // （历史问题：种子默认值被硬编码成 gpt-4o，报错就永远指名那个用户从未配过的模型，
        //   真正的失败原因被完全盖住，用户根本无法判断到底该配哪个。）
        List<String> candidates = effectiveChatModelResolver.candidates(bot.getModel());
        log.info("[BotChat] Bot {} 候选模型解析 botModel={} candidates={}",
                bot.getBotId(), bot.getModel(), candidates);
        if (attribution != null) {
            attribution.requestedModel = candidates.isEmpty() ? bot.getModel() : candidates.get(0);
        }
        if (candidates.isEmpty()) {
            log.warn("[BotChat] Bot {} 没有可用的对话模型：连接器未单独配，Bot 默认与配置中心默认也不可用",
                    bot.getBotId());
            return "AI 回复失败：没有可用的对话模型。请给该连接器指定模型，"
                    + "或在「系统设置 → 对话路由」把默认对话模型设为已启用模型中的一个。";
        }
        long timeoutMs = resolveBotTimeout();
        Exception firstError = null;
        Exception lastError = null;
        for (String candidate : candidates) {
            try {
                String reply = attemptReply(bot, candidate, userMsg, timeoutMs, history);
                if (reply != null && !reply.isBlank()) {
                    if (attribution != null) {
                        attribution.usedModel = candidate;
                    }
                    return reply;
                }
                log.warn("[BotChat] 候选模型 {} 无有效回复，尝试下一个", candidate);
                if (attribution != null && attribution.fallbackReason == null) {
                    attribution.fallbackReason = "模型 " + candidate + " 未返回有效内容";
                }
            } catch (Exception e) {
                if (firstError == null) {
                    firstError = e;
                }
                lastError = e;
                log.warn("[BotChat] 候选模型 {} 调用失败（{}），尝试下一个", candidate, e.getMessage());
                if (attribution != null && attribution.fallbackReason == null) {
                    attribution.fallbackReason = "模型 " + candidate + " 调用失败："
                            + describeAiError(e, e.getMessage());
                }
            }
        }
        // 报第一个候选的失败原因：它是用户真正指定（或最优先）的那个模型；
        // 只报最后一个会把「连接器模型调用失败」误报成「默认模型没配置」。
        String reason = describeAiError(firstError != null ? firstError : lastError, "所有候选模型均无响应");
        return "AI 回复失败：" + reason;
    }

    /**
     * 规整 AI 调用失败原因：异常 message 为空、仅分隔符或为裸全限定类名时，
     * 回退为可读提示，避免出现「AI 回复失败： : com.xxx.Client」这类残缺文案。
     */
    private String describeAiError(Throwable e, String fallback) {
        if (e == null) {
            return fallback;
        }
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String trimmed = raw.trim().replaceAll("^[:：\\s]+", "").trim();
        boolean looksLikeClassName = !trimmed.isEmpty()
                && trimmed.matches("[A-Za-z_$][\\w$.]*")
                && trimmed.contains(".")
                && Character.isLowerCase(trimmed.charAt(0));
        if (trimmed.isEmpty() || looksLikeClassName) {
            return e.getClass().getSimpleName() + "：模型未返回有效内容，请检查模型配置、密钥或网络后重试";
        }
        return trimmed;
    }

    /**
     * 使用指定模型发起一次 AI 回复（含超时与用量记录）。
     *
     * <p>模型未配置、供应商缺失或调用超时时抛出异常，由 {@link #aiReply} 决定是否降级。</p>
     *
     * @param bot       机器人绑定
     * @param modelName 已归一化的模型名称
     * @param userMsg   用户消息
     * @param timeoutMs 请求超时（毫秒）
     * @param history   多轮历史（可空）
     * @return AI 回复文本
     */
    private String attemptReply(SysAiBotBinding bot, String modelName, String userMsg,
                                long timeoutMs, List<ChatMessage> history) {
        // 1. 按模型名查 提供者_模型，确定所属 提供者；支持 providerId:model 消除同名模型歧义
        String providerId = null;
        String plainModel = modelName;
        int colon = modelName == null ? -1 : modelName.indexOf(':');
        if (colon > 0) {
            String before = modelName.substring(0, colon).trim();
            String after = modelName.substring(colon + 1).trim();
            if (!before.isBlank() && !after.isBlank()) {
                providerId = before;
                plainModel = after;
            }
        }
        LambdaQueryWrapper<SysAiProviderModel> modelQuery = Wrappers.<SysAiProviderModel>lambdaQuery()
                .eq(SysAiProviderModel::getModel, plainModel)
                .eq(SysAiProviderModel::getEnabled, true);
        if (providerId != null) {
            modelQuery.eq(SysAiProviderModel::getProviderId, providerId);
        }
        SysAiProviderModel pm = providerModelMapper.selectOne(modelQuery);
        if (pm == null) {
            throw new IllegalStateException("模型 " + modelName + " 未在模型管理中配置");
        }
        log.info("[BotChat] Bot {} 命中模型 providerId={} model={} plainModel={} 原始模型名={}",
                bot.getBotId(), pm.getProviderId(), pm.getModel(), plainModel, modelName);
        // 2. 查 提供者，取 platform / baseurl / 凭证
        SysAiProvider provider = providerMapper.selectById(pm.getProviderId());
        if (provider == null || provider.getBaseUrl() == null) {
            throw new IllegalStateException("模型 " + modelName + " 的供应商不存在");
        }
        String apiKey = extractApiKey(provider.getCredentials());
        Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
        // 3. 构建 对话客户端（带请求超时）并回复
        ChatClient chatClient = ChatClient.create(buildSetting(provider, apiKey, extraHeaders, timeoutMs))
                .model(plainModel);
        log.info("[BotChat] Bot {} 实际调用 providerId={} platform={} baseUrl={} model={}",
                bot.getBotId(), provider.getProviderId(), provider.getPlatform(), provider.getBaseUrl(), plainModel);
        // 系统提示词：优先 Bot 自定义，未配置则回退全局默认（bot.system_prompt）
        String effectiveSystemPrompt = resolveSystemPrompt(bot);
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank()) {
            chatClient.system(effectiveSystemPrompt);
        }
        // 多轮历史：以 user/assistant role 消息注入（位于系统提示词之后、当前输入之前）
        if (history != null && !history.isEmpty()) {
            chatClient.history(history);
        }
        if (Boolean.TRUE.equals(bot.getThinking())) {
            chatClient.thinking(true);
            if (bot.getThinkingEffort() != null && !bot.getThinkingEffort().isBlank()) {
                chatClient.thinkingEffort(bot.getThinkingEffort());
            }
        }
        // 3.1 勾选了 MCP 服务器时，包装为 Agent 运行时真实连接并调用工具
        List<String> bindingMcpIds = new ArrayList<>();
        if (bot.getMcpServerIds() != null) {
            for (String piece : bot.getMcpServerIds().split(",")) {
                String trimmed = piece.trim();
                if (!trimmed.isEmpty()) {
                    bindingMcpIds.add(trimmed);
                }
            }
        }
        AgentMcpRuntimeService.McpRuntime mcpRuntime = null;
        if (!bindingMcpIds.isEmpty()) {
            mcpRuntime = agentMcpRuntimeService.build(bindingMcpIds);
            String agentId = "bot-" + Integer.toHexString(System.identityHashCode(chatClient));
            String instruction = effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank()
                    ? effectiveSystemPrompt : "You are a helpful assistant.";
            AgentDefinition definition = AgentDefinition.builder()
                    .id(agentId).name("Assistant").instruction(instruction).build();
            Agent agent = agentProviderFactory.createAgent()
                    .chatClient(chatClient)
                    .definition(definition)
                    .mode(AgentMode.SINGLE)
                    .mcp(true);
            agent.mcpManager(mcpRuntime.getManager());
            chatClient = new AgentChatClient(agent).withCloseHook(mcpRuntime::close);
        }
        // 4. 调用模型并记录用量（令牌/费用入库，供 usage 页统计）。
        // 受 机器人 绑定上的「记录用量」专属开关控制：关闭时不记录。
        // 采样温度：模型管理里给该模型配了 temperature 时按其要求发送（如 kimi 系只接受 1）。
        if (pm.getTemperature() != null) {
            chatClient.temperature(pm.getTemperature());
        }
        long start = System.currentTimeMillis();
        ChatSyncResponse response;
        try {
            response = chatClient.chatSyncWithResponse(userMsg);
        } catch (RuntimeException first) {
            Double required = requiredTemperature(first);
            if (required == null) {
                throw first;
            }
            // 模型只接受固定采样温度（如 kimi：只有 1 合法）时，按其要求改参数重试一次，
            // 避免因一个本可自适应的参数把模型判死、静默回退到默认模型。
            log.warn("[BotChat] Bot {} 模型 {} 采样温度不被接受，改用 temperature={} 重试：{}",
                    bot.getBotId(), plainModel, required, first.getMessage());
            chatClient.temperature(required);
            response = chatClient.chatSyncWithResponse(userMsg);
        }
        long end = System.currentTimeMillis();
        String replyText = response != null && response.getText() != null ? response.getText() : "";
        log.info("[BotChat] Bot {} 调用完成 providerId={} model={} reply={}",
                bot.getBotId(), provider.getProviderId(), plainModel,
                replyText.length() > 160 ? replyText.substring(0, 160) : replyText);
        boolean recordUsage = !Boolean.FALSE.equals(bot.getRecordUsage());
        if (recordUsage && response != null && response.getUsage() != null) {
            long duration = Math.max(0L, end - start);
            response.getUsage().setDurationMillis(duration);
            if (response.getUsage().getFirstTokenLatencyMillis() == null) {
                response.getUsage().setFirstTokenLatencyMillis(duration);
            }
            try {
                // 归属随 机器人 绑定：轮询线程取不到请求身份，设备指纹流量不计入任何用户
                usageRecorderInterceptor.record(
                        "bot",
                        provider.getPlatform(),
                        plainModel,
                        "bot",
                        response.getUsage(),
                        UUID.randomUUID().toString(),
                        AiBotCaller.userOf(bot.getOwnerKey())
                );
            } catch (Exception e) {
                log.warn("[BotChat] 用量记录失败 bot={} err={}", bot.getBotId(), e.getMessage());
            }
        }
        return response != null && response.getText() != null ? response.getText() : "";
    }

    /**
     * 从异常信息解析模型要求的采样温度。
     *
     * <p>部分模型只接受固定 temperature，否则返回
     * {@code 400: field Temperature invalid, only 1 is allowed for this model}。
     * 命中该形态时返回要求值（默认 {@code 1}），其余情况返回 {@code null}（不重试）。</p>
     *
     * @param e 调用异常
     * @return 模型要求的采样温度；非温度类错误返回 null
     */
    private static Double requiredTemperature(Throwable e) {
        String msg = (e == null || e.getMessage() == null) ? "" : e.getMessage();
        String lower = msg.toLowerCase(Locale.ROOT);
        if (!lower.contains("temperature")) {
            return null;
        }
        if (!(lower.contains("invalid") || lower.contains("only")
                || lower.contains("not support") || lower.contains("unsupported"))) {
            return null;
        }
        Matcher matcher = Pattern.compile("only\\s+([0-9]+(?:\\.[0-9]+)?)").matcher(lower);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // 解析失败时退回默认值
            }
        }
        return 1.0d;
    }

    /**
     * 解析 Bot 请求超时（毫秒），取自系统配置 {@code runtime.timeout_millis}，
     * 默认 30000，并收敛到 [5000, 120000]。
     *
     * @return 超时时间（毫秒）
     */
    private long resolveBotTimeout() {
        try {
            String value = globalSettingReader.getStr("runtime", "timeout_millis");
            if (value != null && !value.isBlank()) {
                long parsed = Long.parseLong(value.trim());
                if (parsed > 0L) {
                    return Math.max(5000L, Math.min(120000L, parsed));
                }
            }
        } catch (Exception ignored) {
            // 配置缺失或非法时使用默认值
        }
        return 30000L;
    }

    /**
     * 从 提供者 凭证中提取 API 键（形态兼容见 {@link ProviderCredentials}）。
     * @param credentials 凭证
     * @return extractapi键的结果
     */
    private static String extractApiKey(Map<String, Object> credentials) {
        return ProviderCredentials.firstApiKey(credentials);
    }

    /**
     * 从 提供者 extra 中提取 习俗_头部。
     *
     * <p>extra 结构：{@code {"custom_headers": {"X-Key": "value", ...}}}</p>
     * @param extra extra
     * @return extractextra头部的结果
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object headers = extra.get("custom_headers");
        if (headers instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>(8); // 自定义头部数量不可预估
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /**
     * 组装 提供者 的对话客户端配置，含认证方案。
     *
     * <p>{@code auth_scheme} 决定是否发送认证头，{@code extra.auth_header} /
     * {@code extra.auth_prefix} 决定头名与前缀。例如 Dots Studio 只认
     * {@code api-key: <key>}，若仍发标准的 {@code Authorization: Bearer} 会被 401。</p>
     *
     * @param provider     DB 提供者
     * @param apiKey       API Key，可为 null
     * @param extraHeaders 自定义请求头
     * @return 客户端配置
     */
    private static ChatClientSetting buildSetting(SysAiProvider provider, String apiKey, Map<String, String> extraHeaders) {
        return buildSetting(provider, apiKey, extraHeaders, null);
    }

    /**
     * 组装 提供者 的对话客户端配置（带超时），含认证方案。
     *
     * @param provider      DB 提供者
     * @param apiKey        API Key，可为 null
     * @param extraHeaders  自定义请求头
     * @param timeoutMillis 请求超时（毫秒），可为 null
     * @return 客户端配置
     */
    private static ChatClientSetting buildSetting(SysAiProvider provider, String apiKey,
                                                  Map<String, String> extraHeaders, Long timeoutMillis) {
        Map<String, Object> extra = provider.getExtra();
        return ChatClientSetting.builder()
                .provider(provider.getPlatform())
                .appKey(apiKey)
                .baseUrl(provider.getBaseUrl())
                .extraHeaders(extraHeaders)
                .authScheme(provider.getAuthScheme())
                .authHeader(extraString(extra, "auth_header"))
                .authPrefix(extraString(extra, "auth_prefix"))
                .proxy(provider.getProxy())
                .timeoutMillis(timeoutMillis)
                .routes(ProviderRouteTargets.fromExtra(extra))
                .build();
    }

    /**
     * 读取 extra 中的字符串值。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 字符串值；缺失返回 null
     */
    private static String extraString(Map<String, Object> extra, String key) {
        if (extra == null) {
            return null;
        }
        Object value = extra.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 一次回复的模型归属：向调用方表明"实际用哪个模型出的回复、是否发生过回退"。
     *
     * <p>此前回退是静默的：目标模型报错后会改用默认模型并照常回复，前端只看得到回复内容，
     * "切换了模型却没生效"的错觉由此而来。把归属显式带出来即可消除该歧义。</p>
     */
    public static final class AiReplyAttribution {

        /**
         * 最优先（通常即用户指定）的模型
         */
        private String requestedModel;

        /**
         * 实际产出回复的模型
         */
        private String usedModel;

        /**
         * 回退原因；为空表示未发生回退
         */
        private String fallbackReason;

        public String getRequestedModel() {
            return requestedModel;
        }

        public String getUsedModel() {
            return usedModel;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        /**
         * 是否发生了回退（请求的模型未产出回复，改用了其它模型）。
         *
         * @return true 表示发生了回退
         */
        public boolean isFallback() {
            return fallbackReason != null && !fallbackReason.isBlank();
        }

        /**
         * 面向用户的一句话说明；未回退时返回 null。
         *
         * @return 提示文案或 null
         */
        public String notice() {
            if (!isFallback()) {
                return null;
            }
            return "所选模型 " + requestedModel + " 未产出回复，本条实际由 " + usedModel
                    + " 生成（原因：" + fallbackReason + "）";
        }
    }
}
