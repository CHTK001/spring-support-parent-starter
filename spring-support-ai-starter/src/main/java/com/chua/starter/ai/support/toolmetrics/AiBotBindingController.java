package com.chua.starter.ai.support.toolmetrics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.model.BotBindingCreateRequest;
import com.chua.starter.ai.support.model.BotBindingListResponse;
import com.chua.starter.ai.support.model.BotConversation;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.BotUserSummary;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.service.AiBotBindingService;
import com.chua.starter.ai.support.service.AiBotChatService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.common.support.ai.bot.BotSendResult;
import com.chua.starter.ai.support.manage.LocalTrustAuthFilter;
import com.chua.starter.ai.support.util.AiBotCaller;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 微信 i链接 机器人 多绑定管理控制器。
 *
 * <p>支持多 Bot 绑定的增删改查。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/bot-binding")
@RequiredArgsConstructor
public class AiBotBindingController {

    /**
     * 配置中心 Bot 分组
     */
    private static final String GROUP_BOT = "bot";

    /**
     * Bot 默认模型字段名
     */
    private static final String FIELD_DEFAULT_MODEL = "default_model";

    /**
     * 机器人 绑定服务
     */
    private final AiBotBindingService botBindingService;

    /**
     * 机器人 聊天引擎（主动发消息 / 聊天记录）
     */
    private final AiBotChatService aiBotChatService;

    /**
     * 全局设置服务（机器人 默认模型等键值配置）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 用户级设置服务（Bot 默认模型按用户隔离，用户覆盖优先于全局）
     */
    private final SysAiUserSettingService sysAiUserSettingService;

    /**
     * 模型表 映射器（机器人 可选的对话模型来自模型管理配置的 提供者_模型）
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 获取可用于 机器人 的对话模型列表（供前端设置 机器人 的聊天模型）。
     *
     * <p>数据源为模型管理配置的 provider_model 表（capability 含 chat 的模型），
     * 与前端模型管理页面的数据一致。</p>
     *
     * @return 对话模型名列表
     */
    @GetMapping("/models")
    public ReturnResult<List<String>> models() {
        List<SysAiProviderModel> all = providerModelMapper.selectList(
                Wrappers.<SysAiProviderModel>lambdaQuery()
                        .eq(SysAiProviderModel::getEnabled, true));
        List<String> chatModels = new ArrayList<>();
        for (SysAiProviderModel m : all) {
            if (hasCapability(m, "chat")) {
                chatModels.add(m.getModel());
            }
        }
        return ReturnResult.ok(chatModels);
    }

    /**
     * 判断模型是否具备指定任务能力（capabilities 为 [{任务:...}]）。
     * @param m m
     * @param task 任务
     * @return 是否包含capability的结果
     */
    private static boolean hasCapability(SysAiProviderModel m, String task) {
        if (m.getCapabilities() == null) {
            return false;
        }
        return m.getCapabilities().stream()
                .anyMatch(c -> task.equals(c.get("task")));
    }

    /**
     * 获取绑定列表（按当前身份做越权过滤）。
     *
     * <p>可见范围：</p>
     * <ul>
     *   <li>管理员 / 本地信任：返回全部；显式传 {@code userId} 时按用户过滤</li>
     *   <li>已登录普通用户：仅返回归属为 {@code u:<userId>} 的绑定</li>
     *   <li>未登录：以浏览器指纹（请求头 {@code x-req-fingerprint}）归属 {@code fp:<指纹>} 过滤</li>
     *   <li>无任何身份标识：仅返回未归属（历史遗留）记录</li>
     * </ul>
     *
     * @param userId      可选，管理员按用户 标识 过滤绑定列表
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 绑定列表（按创建时间降序）
     * @param false false
     */
    @GetMapping("/list")
    public ReturnResult<List<BotBindingListResponse>> list(@RequestParam(required = false) Long userId,
                                                           HttpServletRequest httpRequest) {
        Identity identity = resolveIdentity(httpRequest);
        List<SysAiBotBinding> entities;
        if (identity.admin()) {
            entities = (userId != null)
                    ? botBindingService.listByUser(userId)
                    : botBindingService.listAll();
        } else {
            // 非管理员：仅返回本人归属；无身份标识时只返回未归属记录（默认最小可见）
            entities = botBindingService.listByOwnerKey(identity.ownerKey());
        }
        List<BotBindingListResponse> result = entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ReturnResult.ok(result);
    }

    /**
     * 管理员视角的用户聚合列表（每个用户绑定了多少 机器人、多少在线）。
     *
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 用户摘要列表
     */
    @GetMapping("/users")
    public ReturnResult<List<BotUserSummary>> users(HttpServletRequest httpRequest) {
        AiBotCaller caller = caller(httpRequest);
        if (!caller.admin()) {
            // 用户维度统计属管理员视角数据，普通归属者只能看本人绑定
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人数据");
        }
        return ReturnResult.ok(botBindingService.listUserSummaries());
    }

    /**
     * 主动向指定微信用户发送一条消息（聊天框输入发送 / 主动触达）。
     *
     * @param body 请求体：机器人标识 / 从用户 / 内容
     * @return 发送结果（含 消息_标识）
     */
    @PostMapping("/send")
    public ReturnResult<Map<String, Object>> send(@RequestBody Map<String, String> body,
                                                  HttpServletRequest httpRequest) {
        String botId = body.get("botId");
        String fromUser = body.get("fromUser");
        String content = body.get("content");
        if (StringUtils.isBlank(botId) || StringUtils.isBlank(fromUser) || StringUtils.isBlank(content)) {
            return sendFailure("botId / fromUser / content 不能为空");
        }
        BotSendResult result = aiBotChatService.sendMessage(botId, fromUser, content, caller(httpRequest));
        if (!result.isSuccess()) {
            return sendFailure(result.getErrorMessage() != null ? result.getErrorMessage() : "发送失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", result.getMsgId());
        data.put("ok", true);
        return ReturnResult.ok(data);
    }

    /**
     * 构造发送失败响应。
     *
     * <p>与成功响应保持同一信封（{@code ReturnResult.ok}），仅以 {@code ok=false} + {@code msg}
     * 表达业务失败，避免前端在“业务失败”与“请求失败”两分支间产生歧义。</p>
     *
     * @param message 失败原因
     * @return 统一的发送结果响应
     */
    private ReturnResult<Map<String, Object>> sendFailure(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", false);
        data.put("msg", message);
        data.put("messageId", null);
        return ReturnResult.ok(data);
    }

    /**
     * 查询 机器人 与某用户的聊天记录（按时间升序，支持滚动分页）。
     *
     * <p>第 0 页为最近 pageSize 条；页码越大越早。响应体：
     * {@code {messages: [...], hasMore, page, pageSize}}。</p>
     *
     * @param botId    机器人 标识
     * @param fromUser 微信用户 标识（可空，空则查全部）
     * @param page     页码（从 0 开始，默认 0）
     * @param pageSize 每页条数（默认 50）
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 分页聊天记录
     */
    @GetMapping("/messages")
    public ReturnResult<Map<String, Object>> messages(@RequestParam String botId,
                                                      @RequestParam(required = false) String fromUser,
                                                      @RequestParam(required = false, defaultValue = "0") Integer page,
                                                      @RequestParam(required = false, defaultValue = "50") Integer pageSize,
                                                      HttpServletRequest httpRequest) {
        return ReturnResult.ok(aiBotChatService.listMessagesPaged(botId, fromUser,
                page != null ? page : 0, pageSize != null ? pageSize : 50, caller(httpRequest)));
    }

    /**
     * 润色一段文本（IM 输入框「润色」按钮）。
     *
     * @param body 请求体：机器人标识 / 文本
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 润色结果（{@code result} 字段）
     */
    @PostMapping("/polish")
    public ReturnResult<Map<String, Object>> polish(@RequestBody Map<String, String> body,
                                                    HttpServletRequest httpRequest) {
        String botId = body.get("botId");
        String text = body.get("text");
        if (StringUtils.isBlank(botId) || StringUtils.isBlank(text)) {
            return ReturnResult.illegal("botId / text 不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", aiBotChatService.polishText(botId.trim(), text, caller(httpRequest)));
        return ReturnResult.ok(data);
    }

    /**
     * 总结指定会话的聊天记录（IM 页「上下文总结」按钮）。
     *
     * @param body 请求体：机器人标识 / 从用户
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 会话摘要（{@code summary} 字段）
     */
    @PostMapping("/summarize")
    public ReturnResult<Map<String, Object>> summarize(@RequestBody Map<String, String> body,
                                                       HttpServletRequest httpRequest) {
        String botId = body.get("botId");
        String fromUser = body.get("fromUser");
        if (StringUtils.isBlank(botId)) {
            return ReturnResult.illegal("botId 不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", aiBotChatService.summarizeConversation(botId.trim(), fromUser, caller(httpRequest)));
        return ReturnResult.ok(data);
    }

    /**
     * 会话列表（IM 左侧）：按 机器人 + 微信用户聚合，取最后一条消息。
     *
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 会话列表（按最后消息时间降序）
     */
    @GetMapping("/conversations")
    public ReturnResult<List<BotConversation>> conversations(HttpServletRequest httpRequest) {
        return ReturnResult.ok(aiBotChatService.listConversations(caller(httpRequest)));
    }

    /**
     * 机器人 内置命令清单（供 IM 输入框斜杠命令面板）。
     *
     * @return 命令列表（prefix / 名称 / description）
     */
    @GetMapping("/commands")
    public ReturnResult<List<Map<String, Object>>> commands() {
        return ReturnResult.ok(aiBotChatService.listCommands());
    }

    /**
     * 指定 机器人 的最近联系人（按 机器人标识 去重 从用户）。
     *
     * @param botId 机器人 唯一标识
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 联系人列表
     */
    @GetMapping("/contacts")
    public ReturnResult<List<String>> contacts(@RequestParam String botId, HttpServletRequest httpRequest) {
        if (StringUtils.isBlank(botId)) {
            return ReturnResult.illegal("botId 不能为空");
        }
        return ReturnResult.ok(aiBotChatService.listContacts(botId.trim(), caller(httpRequest)));
    }

    /**
     * 查询 机器人 运行状态（i链接 会话是否存活）。
     *
     * <p>ILink 会话超时后（errcode=-14）客户端停止轮询，前端据此展示
     * 「会话已过期，请重新扫码绑定」提示与重新扫码入口。</p>
     *
     * @param botId 机器人 唯一标识
     * @return status: running=会话存活 / stopped=已停止需重新扫码
     */
    @GetMapping("/runtime-status")
    public ReturnResult<Map<String, Object>> runtimeStatus(@RequestParam String botId) {
        if (StringUtils.isBlank(botId)) {
            return ReturnResult.illegal("botId 不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", aiBotChatService.runtimeStatus(botId.trim()));
        return ReturnResult.ok(data);
    }

    /**
     * 删除单个会话的聊天记录（清理僵尸会话）。
     *
     * <p>绑定记录删除后消息表仍会残留历史，此接口按 botId + fromUser 定向清除。</p>
     *
     * @param botId    机器人 唯一标识
     * @param fromUser 微信用户 标识（可空，空则删除该 机器人 全部会话）
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 删除的消息条数
     */
    @DeleteMapping("/conversations")
    public ReturnResult<Integer> deleteConversation(@RequestParam String botId,
                                                    @RequestParam(required = false) String fromUser,
                                                    HttpServletRequest httpRequest) {
        return ReturnResult.ok(aiBotChatService.deleteConversation(botId, fromUser, caller(httpRequest)));
    }

    /**
     * 查询当前用户生效的 机器人 默认 AI 模型。
     *
     * <p>Bot 默认模型按用户隔离：优先返回该用户自己的覆盖，未设置时回落全局默认。</p>
     *
     * @return 默认模型名（可能为空）与来源（user/global）
     */
    @GetMapping("/default-model")
    public ReturnResult<Map<String, Object>> getDefaultModel(HttpServletRequest httpRequest) {
        String userId = currentUserIdFromRequest(httpRequest);
        Optional<String> userValue = userId == null
                ? Optional.empty()
                : sysAiUserSettingService.getUserValue(userId, GROUP_BOT, FIELD_DEFAULT_MODEL);
        // 直读库：getValue 走 Redis 缓存，缓存不可用时会 500，模型解析不应被缓存层拖垮
        String model = userValue.orElseGet(() ->
                sysAiGlobalSettingService.getValueDirect(GROUP_BOT, FIELD_DEFAULT_MODEL).orElse(null));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model);
        data.put("source", userValue.isPresent() ? "user" : "global");
        data.put("overridden", userValue.isPresent());
        return ReturnResult.ok(data);
    }

    /**
     * 设置当前用户的 机器人 默认 AI 模型（用户级，不再要求管理员、不再改全局）。
     *
     * <p>与设置中心「默认模型」一致：写入 {@code sys_ai_user_setting} 的
     * {@code bot/default_model} 覆盖行，仅影响本人；全局默认由管理员在设置中心维护。</p>
     *
     * @param body        请求体：模型（空串表示撤销覆盖，回落全局）
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 更新后的默认模型名与来源
     */
    @PutMapping("/default-model")
    public ReturnResult<Map<String, Object>> setDefaultModel(@RequestBody Map<String, String> body,
                                                             HttpServletRequest httpRequest) {
        // 本控制器在鉴权白名单内，AiUserContext 为空，必须按令牌解析身份
        String userId = currentUserIdFromRequest(httpRequest);
        if (userId == null) {
            return ReturnResult.illegal("无法识别当前用户，请先登录");
        }
        String model = body.get("model");
        if (StringUtils.isBlank(model)) {
            // 空值即撤销覆盖 → 回落全局默认（与设置中心 override 语义一致）
            sysAiUserSettingService.deleteValue(userId, GROUP_BOT, FIELD_DEFAULT_MODEL);
            return getDefaultModel(httpRequest);
        }
        sysAiUserSettingService.upsertValue(userId, GROUP_BOT, FIELD_DEFAULT_MODEL, model.trim(),
                "STRING", "Bot 默认 AI 模型（按用户隔离，未配置时回落全局）");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model.trim());
        data.put("source", "user");
        data.put("overridden", true);
        return ReturnResult.ok(data);
    }

    /**
     * 当前登录用户标识（按请求身份解析）。
     *
     * <p>本控制器在鉴权白名单内，{@code AiUserContext} 不含用户；用
     * {@link AiBotCaller#userId()} 从令牌归属中取用户标识，设备指纹/匿名返回 空。</p>
     *
     * @param request HTTP 请求
     * @return 用户标识，取不到返回 空
     */
    private String currentUserIdFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        // AiBotCaller.userId()：登录态返回用户标识；设备指纹归属与匿名返回 空
        return caller(request).userId();
    }

    /**
     * 查询 机器人 全局行为配置（上下文thinking / 上下文大小 / 历史大小 / 命令已启用 / compression阈值）。
     *
     * <p>统一存储在全局设置表 bot 分组下，未配置时返回内置默认值。</p>
     *
     * @return 全局配置键值对
     */
    @GetMapping("/global-config")
    public ReturnResult<Map<String, Object>> getGlobalConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contextThinking", readBool("context_thinking", false));
        data.put("contextSize", readInt("context_size", 10));
        data.put("historySize", readInt("history_size", 50));
        data.put("commandEnabled", readBool("command_enabled", false));
        data.put("compressionThreshold", readInt("compression_threshold", 0));
        return ReturnResult.ok(data);
    }

    /**
     * 更新 机器人 全局行为配置（部分更新，仅写入请求体中出现的字段）。
     *
     * <p>配置落在全局设置表，对所有用户的 机器人 生效，因此仅管理员（或本地信任的桌面端）可改；
     * 读取保持开放以便前端展示默认值。</p>
     *
     * @param body        配置键值对（支持 上下文thinking / 上下文大小 / 历史大小 / 命令已启用 / compression阈值）
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 更新后的完整配置
     */
    @PutMapping("/global-config")
    public ReturnResult<Map<String, Object>> updateGlobalConfig(@RequestBody Map<String, Object> body,
                                                                HttpServletRequest httpRequest) {
        requireGlobalAdmin(httpRequest);
        if (body == null || body.isEmpty()) {
            return ReturnResult.illegal("请求体不能为空");
        }
        Object contextThinking = body.get("contextThinking");
        if (contextThinking instanceof Boolean flag) {
            sysAiGlobalSettingService.upsertValue("bot", "context_thinking", flag.toString(),
                    "Bot 深度思考开关（全局）");
        }
        Object contextSize = body.get("contextSize");
        if (contextSize instanceof Number num) {
            sysAiGlobalSettingService.upsertValue("bot", "context_size", String.valueOf(num.intValue()),
                    "Bot 上下文携带轮数（全局）");
        }
        Object historySize = body.get("historySize");
        if (historySize instanceof Number num) {
            sysAiGlobalSettingService.upsertValue("bot", "history_size", String.valueOf(num.intValue()),
                    "Bot 历史消息加载条数（全局）");
        }
        Object commandEnabled = body.get("commandEnabled");
        if (commandEnabled instanceof Boolean flag) {
            sysAiGlobalSettingService.upsertValue("bot", "command_enabled", flag.toString(),
                    "Bot 指令功能开关（全局）");
        }
        Object compressionThreshold = body.get("compressionThreshold");
        if (compressionThreshold instanceof Number num) {
            sysAiGlobalSettingService.upsertValue("bot", "compression_threshold", String.valueOf(num.intValue()),
                    "Bot 上下文压缩阈值（全局，0 表示关闭）");
        }
        return getGlobalConfig();
    }

    /**
     * 读取布尔型全局配置，缺失或解析失败时返回默认值。
     *
     * @param field        设置字段名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private boolean readBool(String field, boolean defaultValue) {
        return sysAiGlobalSettingService.getValue("bot", field)
                .map(v -> Boolean.parseBoolean(v))
                .orElse(defaultValue);
    }

    /**
     * 读取整型全局配置，缺失或解析失败时返回默认值。
     *
     * @param field        设置字段名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private int readInt(String field, int defaultValue) {
        return sysAiGlobalSettingService.getValue("bot", field)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    /**
     * 当前用户的 机器人 管理元信息：是否为管理员、当前用户 标识。
     *
     * <p>前端据此决定是否展示「全部用户」入口：仅管理员可见。
     * 由后端统一判定，前端不依赖具体权限框架。</p>
     *
     * @param request HTTP 请求
     * @return hasAdmin / 角色 / 用户标识
     */
    @GetMapping("/meta")
    public ReturnResult<Map<String, Object>> meta(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String> roles = new ArrayList<>();
        String userId = null;

        String token = resolveToken(request);
        if (StringUtils.isNotBlank(token)) {
            try {
                UserResult user = AuthClientExecute.getInstance().getUserResult(token);
                if (user != null) {
                    userId = user.getUserId();
                    if (user.getRoles() != null) {
                        roles.addAll(user.getRoles());
                    }
                }
            } catch (Exception e) {
                log.warn("[Bot] 解析当前用户失败: {}", e.getMessage());
            }
        }
        // 角色解析失败/未登录时按普通用户兜底（不开放管理员能力）
        boolean hasAdmin = roles.stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r) || "superadmin".equalsIgnoreCase(r));

        data.put("hasAdmin", hasAdmin);
        data.put("roles", roles);
        data.put("userId", userId);
        return ReturnResult.ok(data);
    }

    /**
     * 从请求中解析 Bearer 令牌。
     *
     * @param request HTTP 请求
     * @return Token 字符串，无则返回 空
     */
    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(auth) && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            if (StringUtils.isNotBlank(token)) {
                return token;
            }
        }
        return request.getParameter("token");
    }

    /**
     * 按 标识 获取绑定详情。
     *
     * @param id 绑定 标识
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 绑定信息
     */
    @GetMapping("/{id}")
    public ReturnResult<BotBindingListResponse> get(@PathVariable Long id, HttpServletRequest httpRequest) {
        SysAiBotBinding entity = botBindingService.getById(id);
        if (entity == null) {
            return ReturnResult.ok(null);
        }
        // 绑定含模型与提示词配置，读出口按归属拦越权
        caller(httpRequest).requireReadable(entity.getOwnerKey());
        return ReturnResult.ok(toResponse(entity));
    }

    /**
     * 创建绑定。
     *
     * @param request 绑定请求
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 数据库 标识
     */
    @PostMapping("/create")
    public ReturnResult<Long> create(@RequestBody BotBindingCreateRequest body,
                                     HttpServletRequest httpRequest) {
        SysAiBotBinding entity = new SysAiBotBinding();
        BeanUtils.copyProperties(body, entity);
        AiBotCaller caller = caller(httpRequest);
        // 保存按 botId upsert：命中他人已绑定记录等同于改写他人资源，先按原归属拦越权
        SysAiBotBinding existing = findExisting(entity.getBotId());
        requireSaveAllowed(caller, existing);
        // 归属标识由服务端根据当前身份写入，不接受前端指定（重绑时保留原归属）
        entity.setOwnerKey(existing == null ? caller.ownerKey() : existing.getOwnerKey());
        return ReturnResult.ok(botBindingService.saveOrUpdate(entity));
    }

    /**
     * 更新绑定。
     *
     * @param request 绑定请求（含 标识）
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 数据库 标识
     */
    @PutMapping("/update")
    public ReturnResult<Long> update(@RequestBody BotBindingCreateRequest request,
                                     HttpServletRequest httpRequest) {
        SysAiBotBinding entity = new SysAiBotBinding();
        BeanUtils.copyProperties(request, entity);
        AiBotCaller caller = caller(httpRequest);
        // 写出口只认归属：管理员对他人绑定同样不可改，归属不接受前端传值
        SysAiBotBinding existing = findExisting(entity.getBotId());
        requireSaveAllowed(caller, existing);
        entity.setOwnerKey(existing == null ? caller.ownerKey() : existing.getOwnerKey());
        return ReturnResult.ok(botBindingService.saveOrUpdate(entity));
    }

    /**
     * 保存（按 botId upsert）前的越权闸门。
     *
     * <p>命中既有绑定时按<b>原归属</b>校验写权限，客户端改归属无效；未命中则是新建，
     * 没有他人资源可改写，只拦只读账号。</p>
     *
     * @param caller   当前调用方归属
     * @param existing 既有绑定记录，新建时为 {@code null}
     */
    private void requireSaveAllowed(AiBotCaller caller, SysAiBotBinding existing) {
        if (existing == null) {
            caller.requireCreatable();
            return;
        }
        caller.requireWritable(existing.getOwnerKey());
    }

    /**
     * 按 机器人 唯一标识取既有绑定记录。
     *
     * @param botId 机器人 唯一标识，可为空
     * @return 既有绑定记录，不存在时返回 {@code null}
     */
    private SysAiBotBinding findExisting(String botId) {
        if (StringUtils.isBlank(botId)) {
            return null;
        }
        return botBindingService.findByBotId(botId.trim());
    }

    /**
     * 删除绑定。
     *
     * @param id 数据库 标识
     * @param httpRequest HTTP 请求（用于解析当前身份）
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        SysAiBotBinding entity = botBindingService.getById(id);
        if (entity != null) {
            caller(httpRequest).requireWritableOrAdminOrphan(entity.getOwnerKey());
        }
        botBindingService.delete(id);
        return ReturnResult.ok(null);
    }

    /**
     * 实体转响应 DTO。
     *
     * @param entity 数据库实体
     * @return 响应 DTO
     */
    private BotBindingListResponse toResponse(SysAiBotBinding entity) {
        BotBindingListResponse resp = new BotBindingListResponse();
        resp.setId(entity.getId());
        resp.setUserId(entity.getUserId());
        resp.setUserName(entity.getNickname());
        resp.setBotId(entity.getBotId());
        resp.setPlatform(entity.getPlatform());
        resp.setNickname(entity.getNickname());
        resp.setAvatarUrl(entity.getAvatarUrl());
        resp.setGatewayPort(entity.getGatewayPort());
        resp.setStatus(entity.getStatus());
        resp.setBindSource(entity.getBindSource());
        resp.setModel(entity.getModel());
        resp.setThinking(entity.getThinking());
        resp.setThinkingEffort(entity.getThinkingEffort());
        resp.setSystemPrompt(entity.getSystemPrompt());
        resp.setRecordUsage(entity.getRecordUsage());
        resp.setMentionOnly(entity.getMentionOnly());
        resp.setRandomReplyEnabled(entity.getRandomReplyEnabled());
        resp.setRandomContextCount(entity.getRandomContextCount());
        resp.setRandomProbability(entity.getRandomProbability());
        resp.setCreateTime(entity.getCreateTime());
        resp.setLastActiveTime(entity.getLastActiveTime());
        return resp;
    }

 // ==================== i链接 扫码绑定 ====================

    /**
     * 生成微信 i链接 扫码会话。
     *
     * <p>平台仅支持 wechat（iLink 扫码）；企业微信暂不支持，返回错误。
     * 重新扫码场景可传旧 机器人标识（机器人标识 参数），扫码确认后自动合并到旧绑定并迁移历史消息。</p>
     *
     * @param platform 平台类型，默认 wechat
     * @param botId    重新扫码场景下需合并的旧 机器人 唯一标识（可选）
     * @return key + qrcodeurl；qrcodeurl 即时为空，前端轮询 状态=就绪 后从 qr状态 取
     */
    @PostMapping("/qrcode")
    public ReturnResult<AiBotBindingService.QrResult> generateQr(
            @RequestParam(defaultValue = "wechat") String platform,
            @RequestParam(required = false) String botId,
            HttpServletRequest httpRequest) {
        if (!"wechat".equals(platform) && !"wecom".equals(platform)) {
            return ReturnResult.illegal("platform 仅支持 wechat / wecom");
        }
        // 归属标识随扫码会话下发，扫码确认时写入绑定记录
        return ReturnResult.ok(botBindingService.generateQrCode(platform, botId,
                resolveIdentity(httpRequest).ownerKey()));
    }

    /**
     * 轮询扫码会话状态。
     *
     * <p>前端每 3 秒调用一次；status 变为 confirmed 时表示绑定已完成，可刷新列表。</p>
     *
     * @param key 会话 Key
     * @param httpRequest HTTP 请求（用于解析当前身份，只允许查询本人会话）
     * @return status + 机器人标识（confirmed 时回填）
     */
    @GetMapping("/qrcode/status")
    public ReturnResult<AiBotBindingService.QrStatus> checkQrStatus(@RequestParam String key,
                                                                   HttpServletRequest httpRequest) {
        return ReturnResult.ok(botBindingService.checkQrCodeStatus(key, caller(httpRequest).ownerKey()));
    }

    // ==================== 飞书（凭据配置型） ====================

    /**
     * 绑定飞书 机器人。
     *
     * <p>凭据配置型：传入飞书开放平台的 appId / appSecret。</p>
     *
     * @param appId     飞书应用 标识
     * @param appSecret 飞书应用密钥
     * @return 数据库 标识
     */
    @PostMapping("/feishu")
    public ReturnResult<Long> bindFeishu(@RequestParam String appId,
                                         @RequestParam String appSecret,
                                         HttpServletRequest httpRequest) {
        AiBotCaller caller = caller(httpRequest);
        // 与 /create 同一道闸门：appId 即 botId，命中他人绑定等同于改写他人凭据
        requireSaveAllowed(caller, findExisting(appId));
        return ReturnResult.ok(botBindingService.bindFeishu(appId, appSecret, caller.ownerKey()));
    }

    // ==================== 身份解析 ====================

    /**
     * 解析当前请求的归属身份与角色，用于绑定资源的越权过滤。
     *
     * <p>优先级：本地信任（桌面 / 本地开发）→ 登录 Token → 浏览器指纹。</p>
     *
     * @param request HTTP 请求
     * @return 归属身份（ownerKey + 是否管理员）
     */
    private Identity resolveIdentity(HttpServletRequest request) {
        // 本地信任（Tauri / 本地开发）：视为管理员，保持原有全量可见行为
        Object trusted = request.getAttribute(LocalTrustAuthFilter.TRUSTED_ATTR);
        if (Boolean.TRUE.equals(trusted)) {
            /**
             * Identity。
             * @param null 空
             * @param true true
             * @return Identity的结果
             */
            return new Identity(null, true);
        }
        // 登录态：以 u:<userId> 归属，管理员放行全部
        String token = resolveToken(request);
        if (StringUtils.isNotBlank(token)) {
            try {
                UserResult user = AuthClientExecute.getInstance().getUserResult(token);
                if (user != null) {
                    boolean admin = user.getRoles() != null && user.getRoles().stream()
                            .anyMatch(r -> "admin".equalsIgnoreCase(r) || "superadmin".equalsIgnoreCase(r));
                    if (StringUtils.isNotBlank(user.getUserId())) {
                        /**
                         * Identity。
                         * @param user.getUserId( 用户.获取用户标识(
                         * @return Identity的结果
                         */
                        return new Identity("u:" + user.getUserId().trim(), admin);
                    }
                    if (admin) {
                        /**
                         * Identity。
                         * @param null 空
                         * @param true true
                         * @return Identity的结果
                         */
                        return new Identity(null, true);
                    }
                }
            } catch (Exception e) {
                log.warn("[Bot] 解析当前用户失败: {}", e.getMessage());
            }
        }
        // 未登录：以浏览器指纹作为设备归属标识
        String fingerprint = request.getHeader("x-req-fingerprint");
        if (StringUtils.isBlank(fingerprint)) {
            fingerprint = request.getHeader("X-Request-Fingerprint");
        }
        if (StringUtils.isNotBlank(fingerprint)) {
            /**
             * Identity。
             * @param fingerprint.trim( fingerprint.修剪(
             * @return Identity的结果
             */
            return new Identity("fp:" + fingerprint.trim(), false);
        }
        /**
         * Identity。
         * @param null 空
         * @param false false
         * @return Identity的结果
         */
        return new Identity(null, false);
    }

    /**
     * 解析当前请求的调用方归属，供 机器人 数据的越权拦截使用。
     *
     * <p>本控制器在鉴权白名单内，身份沿用 {@link #resolveIdentity(HttpServletRequest)} 的口径
     * （本地信任 → 登录 令牌 → 设备指纹），不能改读鉴权过滤器写入的请求属性。</p>
     *
     * @param request HTTP 请求
     * @return 调用方归属
     */
    private AiBotCaller caller(HttpServletRequest request) {
        Identity identity = resolveIdentity(request);
        return new AiBotCaller(identity.ownerKey(), identity.admin());
    }

    /**
     * 校验当前调用方可否写入影响全部用户的 机器人 全局设置。
     *
     * <p>本控制器在鉴权白名单内，必须自行解析身份；无身份与设备指纹归属者都不是管理员，
     * 因此匿名请求会被拒绝。异常用 {@link ApiErrorCode#FORBIDDEN} 而非会话失效码，
     * 避免前端拦截器把越权误判为未登录而清空令牌。</p>
     *
     * @param request HTTP 请求
     * @throws ApiException 非管理员且非本地信任时抛出
     */
    private void requireGlobalAdmin(HttpServletRequest request) {
        if (!caller(request).admin()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "机器人全局配置仅管理员可修改");
        }
    }

    /**
     * 请求归属身份。
     *
     * @param ownerKey 归属标识（{@code u:<userId>} / {@code fp:<fingerprint>}），无身份时为 空
     * @param admin    是否管理员（或本地信任）
     * @return Identity的结果
     */
    private record Identity(String ownerKey, boolean admin) {
    }
}
