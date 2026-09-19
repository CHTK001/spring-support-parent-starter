package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.bot.BotInboundMessage;
import com.chua.common.support.ai.bot.BotMessageListener;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.bot.BotSendResult;
import com.chua.ilink.support.bot.ILinkBotClient;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.entity.SysAiBotMessage;
import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.mapper.SysAiBotBindingMapper;
import com.chua.starter.ai.support.mapper.SysAiBotMessageMapper;
import com.chua.starter.ai.support.model.BotConversation;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.websocket.SysAiWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
     * 用量记录拦截器（记录每次 机器人 AI 调用的 令牌/费用）
    */
    private final UsageRecorderInterceptor usageRecorderInterceptor;

    /**
     * 运行中的 机器人：机器人标识 -> i链接 客户端
    */
    private final Map<String, ILinkBotClient> runningBots = new ConcurrentHashMap<>();

    /**
     * 应用上下文刷新后加载所有已绑定的微信 机器人 并启动消息轮询。
     *
     * <p>使用 {@link ContextRefreshedEvent} 而非 {@code @PostConstruct}，
     * 确保 对话客户端registry 已初始化完成。</p>
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
     * 启动单个 机器人 的消息轮询与 AI 回复。
     *
     * <p>若该 Bot 已在运行则先停止，避免重复轮询。</p>
     *
     * @param bot 机器人 绑定记录
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
     * 查询 机器人 运行状态（i链接 会话是否存活）。
     *
     * <p>ILink 会话超时（errcode=-14）后客户端停止轮询并从 runningBots 移除，
     * 前端据此提示「会话已过期，请重新扫码绑定」。</p>
     *
     * @param botId 机器人 唯一标识
     * @return running=会话存活；stopped=未在运行（超时/停止），需重新扫码
     */
    public String runtimeStatus(String botId) {
        ILinkBotClient client = runningBots.get(botId);
 // 只用对象存在判断不够：会话超时（-14）后 客户端 仍留在 映射 中，
        // 但内部轮询已停止（isRunning=false）。必须看真实轮询状态。
        if (client != null && client.isRunning()) {
            return "running";
        }
 // 会话已停止：从 映射 移除，避免悬挂引用，并提示需要重新扫码
        if (client != null) {
            runningBots.remove(botId);
        }
        return "stopped";
    }

    /**
     * 停止指定 机器人 的消息轮询。
     *
     * @param botId 机器人 唯一标识
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
     * @param bot 机器人
     * @param client 客户端
     * @param message 消息
     */
    private void handleMessage(SysAiBotBinding bot, ILinkBotClient client, BotInboundMessage message) {
        String content = message.getContent();
        String fromUser = message.getFromUser();
        if (content == null || content.isBlank() || fromUser == null) {
            return;
        }
        log.info("[BotChat] Bot {} 收到消息 from={}: {}", bot.getBotId(), fromUser, content);
        recordMessage(bot.getBotId(), "IN", fromUser, content);
        try {
            String reply = aiReply(bot, content);
            if (reply != null && !reply.isBlank()) {
                BotSendResult result = client.sendText(fromUser, reply);
                if (result.isSuccess()) {
                    log.info("[BotChat] Bot {} 已回复 from={} text={}", bot.getBotId(), fromUser, reply);
                    recordMessage(bot.getBotId(), "OUT", fromUser, reply);
                } else {
                    log.warn("[BotChat] Bot {} 回复发送失败 from={} code={} msg={} text={}",
                            bot.getBotId(), fromUser, result.getErrorCode(), result.getErrorMessage(), reply);
                }
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
     * 主动向指定微信用户发送一条消息（聊天框手动发送 / 主动触达）。
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 目标微信用户 标识
     * @param content  消息内容
     * @return 发送结果
     */
    public BotSendResult sendMessage(String botId, String fromUser, String content) {
        ILinkBotClient client = runningBots.get(botId);
        if (client == null) {
            return BotSendResult.fail(-1, "Bot 未在运行，请先在 Bot 管理页启用");
        }
        if (content == null || content.isBlank()) {
            return BotSendResult.fail(-1, "消息内容不能为空");
        }
        BotSendResult result = client.sendText(fromUser, content);
        if (result.isSuccess()) {
            recordMessage(botId, "OUT", fromUser, content);
        } else {
            log.warn("[BotChat] 主动发送失败 bot={} to={} code={} msg={}",
                    botId, fromUser, result.getErrorCode(), result.getErrorMessage());
        }
        return result;
    }

    /**
     * 查询指定 机器人 与某用户的聊天记录（按时间升序）。
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 目标微信用户 标识（可空，空则查询该 机器人 全部消息）
     * @return 聊天记录列表
     */
    public List<SysAiBotMessage> listMessages(String botId, String fromUser) {
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
     * @return 分页结果：messages（升序）、hasMore、page、pageSize
     */
    public Map<String, Object> listMessagesPaged(String botId, String fromUser, int page, int pageSize) {
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
     * @return 润色结果，模型未配置/调用失败时返回空字符串
     */
    public String polishText(String botId, String text) {
        if (botId == null || botId.isBlank() || text == null || text.isBlank()) {
            return "";
        }
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
     * @return 会话摘要，无消息/模型未配置/调用失败时返回空字符串
     */
    public String summarizeConversation(String botId, String fromUser) {
        if (botId == null || botId.isBlank()) {
            return "";
        }
        List<SysAiBotMessage> messages = listMessages(botId, fromUser);
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
     * 解析 机器人 使用的模型名：优先 机器人 单独配置，其次全局默认模型。
     *
     * @param bot 机器人 绑定记录（可为 空）
     * @return 模型名，无法解析时返回 空
     */
    private String resolveModelName(SysAiBotBinding bot) {
        if (bot != null && bot.getModel() != null && !bot.getModel().isBlank()) {
            return bot.getModel();
        }
        return globalSettingReader.getStr("bot", "default_model");
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
            return ChatClient.create(ChatClientSetting.builder()
                    .provider(provider.getPlatform())
                    .appKey(apiKey)
                    .baseUrl(provider.getBaseUrl())
                    .extraHeaders(extraHeaders)
                    .proxy(provider.getProxy())
                    .build())
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
     * @param botId 机器人 唯一标识
     * @return 联系人列表
     */
    public List<String> listContacts(String botId) {
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
     * @return 会话列表（按最后消息时间降序）
     */
    public List<BotConversation> listConversations() {
        List<SysAiBotMessage> all = botMessageMapper.selectList(
                Wrappers.<SysAiBotMessage>lambdaQuery()
                        .orderByAsc(SysAiBotMessage::getCreateTime));
        Map<String, BotConversation> byKey = new LinkedHashMap<>(64); // 会话数量不可预估
        for (SysAiBotMessage m : all) {
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
        List<SysAiBotBinding> bindings = botBindingMapper.selectList(Wrappers.lambdaQuery());
        Map<String, SysAiBotBinding> byBotId = bindings.stream()
                .filter(b -> b.getBotId() != null)
                .collect(Collectors.toMap(SysAiBotBinding::getBotId, b -> b, (a, b) -> a));
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
     * @return 删除的消息条数
     */
    public int deleteConversation(String botId, String fromUser) {
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
        webSocketHandler.broadcast("bot.conversation-deleted", push);
        return deleted;
    }

    /**
     * 落库一条 机器人 聊天消息（失败不阻断主流程），并广播到 IM 页。
     *
     * @param botId    机器人 唯一标识
     * @param direction 入（用户→机器人）/ 出（机器人→用户）
     * @param fromUser  微信用户 标识
     * @param content   消息内容
     */
    private void recordMessage(String botId, String direction, String fromUser, String content) {
        try {
            SysAiBotMessage msg = new SysAiBotMessage();
            msg.setBotId(botId);
            msg.setDirection(direction);
            msg.setFromUser(fromUser);
            msg.setContent(content);
            msg.setCreateTime(LocalDateTime.now());
            botMessageMapper.insert(msg);
            // 广播到 IM 页（新消息 / 新会话实时刷新）
        Map<String, Object> push = new HashMap<>(4);
            push.put("type", "bot.message");
            push.put("botId", botId);
            push.put("direction", direction);
            push.put("fromUser", fromUser);
            push.put("content", content);
            push.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
            webSocketHandler.broadcast("bot.message", push);
        } catch (Exception e) {
            log.warn("[BotChat] 消息落库失败 bot={} dir={}: {}", botId, direction, e.getMessage());
        }
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
 // 优先用 机器人 单独配置的模型，未配置则回退全局默认模型（机器人.默认_模型）
        String modelName = bot.getModel();
        if (modelName == null || modelName.isBlank()) {
            modelName = globalSettingReader.getStr("bot", "default_model");
        }
        if (modelName == null || modelName.isBlank()) {
            return "请先为该 Bot 设置 AI 模型，或在设置中配置 Bot 默认模型。";
        }
        try {
 // 1. 按模型名查 提供者_模型，确定所属 提供者
            SysAiProviderModel pm = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, modelName)
                            .eq(SysAiProviderModel::getEnabled, true));
            if (pm == null) {
                log.warn("[BotChat] 模型 {} 未在模型管理中配置", modelName);
                return "模型 " + modelName + " 未配置，请在模型管理中检查。";
            }
 // 2. 查 提供者，取 platform / baseurl / 凭证
            SysAiProvider provider = providerMapper.selectById(pm.getProviderId());
            if (provider == null || provider.getBaseUrl() == null) {
                return "模型 " + modelName + " 的供应商不存在。";
            }
            String apiKey = extractApiKey(provider.getCredentials());
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
 // 3. 构建 对话客户端 并回复（默认非流式；机器人 开启深度思考时透传）
            ChatClient chatClient = ChatClient.create(ChatClientSetting.builder()
                    .provider(provider.getPlatform())
                    .appKey(apiKey)
                    .baseUrl(provider.getBaseUrl())
                    .extraHeaders(extraHeaders)
                    .proxy(provider.getProxy())
                    .build())
                    .model(modelName);
 // 机器人 配置了 系统 提示符 时注入
            if (bot.getSystemPrompt() != null && !bot.getSystemPrompt().isBlank()) {
                chatClient.system(bot.getSystemPrompt());
            }
            if (Boolean.TRUE.equals(bot.getThinking())) {
                chatClient.thinking(true);
                if (bot.getThinkingEffort() != null && !bot.getThinkingEffort().isBlank()) {
                    chatClient.thinkingEffort(bot.getThinkingEffort());
                }
            }
 // 4. 调用模型并记录用量（令牌/费用入库，供 usage 页统计）。
 // 受 机器人 绑定上的「记录用量」专属开关控制：关闭时不记录。
            long start = System.currentTimeMillis();
            ChatSyncResponse response = chatClient.chatSyncWithResponse(userMsg);
            long end = System.currentTimeMillis();
            boolean recordUsage = !Boolean.FALSE.equals(bot.getRecordUsage());
            if (recordUsage && response != null && response.getUsage() != null) {
                long duration = Math.max(0L, end - start);
                response.getUsage().setDurationMillis(duration);
                if (response.getUsage().getFirstTokenLatencyMillis() == null) {
                    response.getUsage().setFirstTokenLatencyMillis(duration);
                }
                try {
                    usageRecorderInterceptor.record(
                            "bot",
                            provider.getPlatform(),
                            modelName,
                            "bot",
                            response.getUsage(),
                            UUID.randomUUID().toString(),
                            null
                    );
                } catch (Exception e) {
                    log.warn("[BotChat] 用量记录失败 bot={} err={}", bot.getBotId(), e.getMessage());
                }
            }
            return response != null && response.getText() != null ? response.getText() : "";
        } catch (Exception e) {
            log.error("[BotChat] AI 回复失败 bot={} model={}", bot.getBotId(), modelName, e);
            return "AI 回复失败：" + e.getMessage();
        }
    }

    /**
     * 从 提供者 凭证中提取 API 键。
     *
     * <p>凭证结构：{@code {"api_keys": ["sk-...", ...]}}，取第一个；
     * 兼容 {@code apiKey} 键。</p>
     * @param credentials 凭证
     * @return extractapi键的结果
     */
    private static String extractApiKey(Map<String, Object> credentials) {
        if (credentials == null) {
            return null;
        }
        Object keys = credentials.get("api_keys");
        if (keys instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
            return list.getFirst().toString();
        }
        Object key = credentials.get("apiKey");
        return key != null ? key.toString() : null;
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
}
