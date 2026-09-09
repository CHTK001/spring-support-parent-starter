package com.chua.starter.ai.support.toolmetrics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.model.BotBindingCreateRequest;
import com.chua.starter.ai.support.model.BotBindingListResponse;
import com.chua.starter.ai.support.model.BotConversation;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import com.chua.starter.ai.support.entity.SysAiBotMessage;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.BotUserSummary;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.service.AiBotBindingService;
import com.chua.starter.ai.support.service.AiBotChatService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.common.support.ai.bot.BotSendResult;
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
import java.util.stream.Collectors;

/**
 * 微信 iLink Bot 多绑定管理控制器。
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
     * Bot 绑定服务
     */
    private final AiBotBindingService botBindingService;

    /**
     * Bot 聊天引擎（主动发消息 / 聊天记录）
     */
    private final AiBotChatService aiBotChatService;

    /**
     * 全局设置服务（Bot 默认模型等键值配置）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 模型表 Mapper（Bot 可选的对话模型来自模型管理配置的 provider_model）
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 获取可用于 Bot 的对话模型列表（供前端设置 Bot 的聊天模型）。
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
     * 判断模型是否具备指定任务能力（capabilities 为 [{task:...}]）。
     */
    private static boolean hasCapability(SysAiProviderModel m, String task) {
        if (m.getCapabilities() == null) {
            return false;
        }
        return m.getCapabilities().stream()
                .anyMatch(c -> task.equals(c.get("task")));
    }

    /**
     * 获取全部绑定列表。
     *
     * <p>可选按用户过滤；普通用户调用时仅返回其本人绑定，
     * 管理员（{@code userId} 显式传值）可查看指定用户绑定。</p>
     *
     * @param userId 可选，按用户 ID 过滤绑定列表
     * @return 绑定列表（按创建时间降序）
     */
    @GetMapping("/list")
    public ReturnResult<List<BotBindingListResponse>> list(@RequestParam(required = false) Long userId) {
        List<SysAiBotBinding> entities = (userId != null)
                ? botBindingService.listByUser(userId)
                : botBindingService.listAll();
        List<BotBindingListResponse> result = entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ReturnResult.ok(result);
    }

    /**
     * 管理员视角的用户聚合列表（每个用户绑定了多少 Bot、多少在线）。
     *
     * @return 用户摘要列表
     */
    @GetMapping("/users")
    public ReturnResult<List<BotUserSummary>> users() {
        return ReturnResult.ok(botBindingService.listUserSummaries());
    }

    /**
     * 主动向指定微信用户发送一条消息（聊天框输入发送 / 主动触达）。
     *
     * @param body 请求体：botId / fromUser / content
     * @return 发送结果（含 message_id）
     */
    @PostMapping("/send")
    public ReturnResult<Map<String, Object>> send(@RequestBody Map<String, String> body) {
        String botId = body.get("botId");
        String fromUser = body.get("fromUser");
        String content = body.get("content");
        if (StringUtils.isBlank(botId) || StringUtils.isBlank(fromUser) || StringUtils.isBlank(content)) {
            return ReturnResult.illegal("botId / fromUser / content 不能为空");
        }
        BotSendResult result = aiBotChatService.sendMessage(botId, fromUser, content);
        if (!result.isSuccess()) {
            return ReturnResult.error(result.getErrorMessage() != null ? result.getErrorMessage() : "发送失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", result.getMsgId());
        data.put("ok", true);
        return ReturnResult.ok(data);
    }

    /**
     * 查询 Bot 与某用户的聊天记录（按时间升序）。
     *
     * @param botId    Bot ID
     * @param fromUser 微信用户 ID（可空，空则查全部）
     * @return 聊天记录列表
     */
    @GetMapping("/messages")
    public ReturnResult<List<SysAiBotMessage>> messages(@RequestParam String botId,
                                                        @RequestParam(required = false) String fromUser) {
        return ReturnResult.ok(aiBotChatService.listMessages(botId, fromUser));
    }

    /**
     * 会话列表（IM 左侧）：按 Bot + 微信用户聚合，取最后一条消息。
     *
     * @return 会话列表（按最后消息时间降序）
     */
    @GetMapping("/conversations")
    public ReturnResult<List<BotConversation>> conversations() {
        return ReturnResult.ok(aiBotChatService.listConversations());
    }

    /**
     * 删除单个会话的聊天记录（清理僵尸会话）。
     *
     * <p>绑定记录删除后消息表仍会残留历史，此接口按 botId + fromUser 定向清除。</p>
     *
     * @param botId    Bot 唯一标识
     * @param fromUser 微信用户 ID（可空，空则删除该 Bot 全部会话）
     * @return 删除的消息条数
     */
    @DeleteMapping("/conversations")
    public ReturnResult<Integer> deleteConversation(@RequestParam String botId,
                                                    @RequestParam(required = false) String fromUser) {
        return ReturnResult.ok(aiBotChatService.deleteConversation(botId, fromUser));
    }

    /**
     * 查询 Bot 默认 AI 模型（全局设置 bot.default_model，Bot 未单独配置时兜底）。
     *
     * @return 默认模型名（可能为空）
     */
    @GetMapping("/default-model")
    public ReturnResult<Map<String, Object>> getDefaultModel() {
        String model = sysAiGlobalSettingService.getValue("bot", "default_model").orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model);
        return ReturnResult.ok(data);
    }

    /**
     * 设置 Bot 默认 AI 模型（全局兜底，避免每个用户单独配置模型）。
     *
     * @param body 请求体：model
     * @return 更新后的默认模型名
     */
    @PutMapping("/default-model")
    public ReturnResult<Map<String, Object>> setDefaultModel(@RequestBody Map<String, String> body) {
        String model = body.get("model");
        if (StringUtils.isBlank(model)) {
            return ReturnResult.illegal("model 不能为空");
        }
        sysAiGlobalSettingService.upsertValue("bot", "default_model", model.trim(),
                "Bot 默认 AI 模型（未单独配置时的全局兜底）");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model.trim());
        return ReturnResult.ok(data);
    }

    /**
     * 查询 Bot 全局行为配置（contextThinking / contextSize / historySize / commandEnabled / compressionThreshold）。
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
     * 更新 Bot 全局行为配置（部分更新，仅写入请求体中出现的字段）。
     *
     * @param body 配置键值对（支持 contextThinking / contextSize / historySize / commandEnabled / compressionThreshold）
     * @return 更新后的完整配置
     */
    @PutMapping("/global-config")
    public ReturnResult<Map<String, Object>> updateGlobalConfig(@RequestBody Map<String, Object> body) {
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
     * 当前用户的 Bot 管理元信息：是否为管理员、当前用户 ID。
     *
     * <p>前端据此决定是否展示「全部用户」入口：仅管理员可见。
     * 由后端统一判定，前端不依赖具体权限框架。</p>
     *
     * @param request HTTP 请求
     * @return hasAdmin / roles / userId
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
     * 从请求中解析 Bearer Token。
     *
     * @param request HTTP 请求
     * @return Token 字符串，无则返回 null
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
     * 按 ID 获取绑定详情。
     *
     * @param id 绑定 ID
     * @return 绑定信息
     */
    @GetMapping("/{id}")
    public ReturnResult<BotBindingListResponse> get(@PathVariable Long id) {
        SysAiBotBinding entity = botBindingService.getById(id);
        return ReturnResult.ok(entity != null ? toResponse(entity) : null);
    }

    /**
     * 创建绑定。
     *
     * @param request 绑定请求
     * @return 数据库 ID
     */
    @PostMapping("/create")
    public ReturnResult<Long> create(@RequestBody BotBindingCreateRequest request) {
        SysAiBotBinding entity = new SysAiBotBinding();
        BeanUtils.copyProperties(request, entity);
        return ReturnResult.ok(botBindingService.saveOrUpdate(entity));
    }

    /**
     * 更新绑定。
     *
     * @param request 绑定请求（含 ID）
     * @return 数据库 ID
     */
    @PutMapping("/update")
    public ReturnResult<Long> update(@RequestBody BotBindingCreateRequest request) {
        SysAiBotBinding entity = new SysAiBotBinding();
        BeanUtils.copyProperties(request, entity);
        return ReturnResult.ok(botBindingService.saveOrUpdate(entity));
    }

    /**
     * 删除绑定。
     *
     * @param id 数据库 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
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
        resp.setCreateTime(entity.getCreateTime());
        resp.setLastActiveTime(entity.getLastActiveTime());
        return resp;
    }

    // ==================== iLink 扫码绑定 ====================

    /**
     * 生成微信 iLink 扫码会话。
     *
     * <p>平台仅支持 wechat（iLink 扫码）；企业微信暂不支持，返回错误。</p>
     *
     * @param platform 平台类型，默认 wechat
     * @return key + qrcodeUrl；qrcodeUrl 即时为空，前端轮询 status=ready 后从 QrStatus 取
     */
    @PostMapping("/qrcode")
    public ReturnResult<AiBotBindingService.QrResult> generateQr(
            @RequestParam(defaultValue = "wechat") String platform) {
        if (!"wechat".equals(platform) && !"wecom".equals(platform)) {
            return ReturnResult.illegal("platform 仅支持 wechat / wecom");
        }
        return ReturnResult.ok(botBindingService.generateQrCode(platform));
    }

    /**
     * 轮询扫码会话状态。
     *
     * <p>前端每 3 秒调用一次；status 变为 confirmed 时表示绑定已完成，可刷新列表。</p>
     *
     * @param key 会话 key
     * @return status + botId（confirmed 时回填）
     */
    @GetMapping("/qrcode/status")
    public ReturnResult<AiBotBindingService.QrStatus> checkQrStatus(@RequestParam String key) {
        return ReturnResult.ok(botBindingService.checkQrCodeStatus(key));
    }

    // ==================== 飞书（凭据配置型） ====================

    /**
     * 绑定飞书 Bot。
     *
     * <p>凭据配置型：传入飞书开放平台的 appId / appSecret。</p>
     *
     * @param appId     飞书应用 ID
     * @param appSecret 飞书应用密钥
     * @return 数据库 ID
     */
    @PostMapping("/feishu")
    public ReturnResult<Long> bindFeishu(@RequestParam String appId,
                                         @RequestParam String appSecret) {
        return ReturnResult.ok(botBindingService.bindFeishu(appId, appSecret));
    }
}
