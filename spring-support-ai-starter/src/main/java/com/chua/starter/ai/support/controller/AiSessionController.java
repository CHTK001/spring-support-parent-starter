// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDateTime;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionTraceMapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSessionGroup;
import com.chua.starter.ai.support.entity.SysAiSessionTrace;
import com.chua.starter.ai.support.service.AiSessionEventBroadcaster;
import com.chua.starter.ai.support.service.AiSessionTodoService;
import com.chua.starter.ai.support.service.SessionGoalService;
import com.chua.starter.ai.support.service.SessionNextTaskService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.ai.support.workspace.SessionWorkspaceResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.utils.CollectionUtils;

/**
   * 会话控制器
   *
   * @author CH
   * @since 4.0.0.42
   */
@Slf4j
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class AiSessionController {

    private final SysAiSessionMapper sessionMapper;
    private final SysAiMessageMapper messageMapper;
    private final com.chua.starter.ai.support.mapper.SysAiSessionGroupMapper sessionGroupMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

      /**
       * 对话门面（压缩时用它同步调用模型生成摘要）
       */
    private final com.chua.starter.ai.support.service.AiChatFacadeService aiChatFacadeService;

      /**
       * 会话压缩记录服务
       */
    private final com.chua.starter.ai.support.service.SysAiSessionCompressionService sessionCompressionService;

      /**
       * 生效对话模型解析器（压缩时选模型，不写死）
       */
    private final com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver effectiveChatModelResolver;

      /**
       * 全局设置服务（取"系统/工作目录基路径" system.workspaceBaseDir）
       */
    private final com.chua.starter.ai.support.service.SysAiGlobalSettingService globalSettingService;

      /**
       * 会话自定义工作目录（项目目录）解析器：校验范围、读会话绑定、算终端/Git 的执行目录
       */
    private final SessionWorkspaceResolver sessionWorkspaceResolver;

      /**
       * 工作目录路径解析器（统一 {@code <基路径>/<账号>} 口径与空配置兜底
       * {@code ~/ai-workspace}；此前本类与 {@code WorkspacePathResolver} 各算各的，
       * 空配置一处返回 null 一处兜底，导致工作区路径不一致）
       */
    private final com.chua.starter.ai.support.workspace.WorkspacePathResolver workspacePathResolver;

      /**
       * 会话「AI 联想下一个任务」服务
       *
       * <p>开关值钉在会话自身的 {@code settings.aiNextTask}（会话保留原始信息），
       * 联想走 {@code EffectiveChatModelResolver} 解析出的模型，不在此处写死模型名。</p>
       */
    private final SessionNextTaskService sessionNextTaskService;

      /**
       * 会话输入框预测服务（按 Tab 填入的「下一条消息」建议，不自动发送）
       */
    private final com.chua.starter.ai.support.service.SessionInputSuggestService sessionInputSuggestService;

      /**
       * 会话待办服务（opencode todowrite 镜像查询入口）
       */
    private final AiSessionTodoService sessionTodoService;

      /**
       * 会话目标服务（settings.goal 状态机：读取/保存/暂停/恢复/销毁）
       */
    private final SessionGoalService sessionGoalService;

      /**
       * 工作目录文件变化事件流（工作区侧栏实时刷新用）
       */
    private final com.chua.starter.ai.support.service.AiWorkspaceEventStreamService workspaceEventStream;

    /**
     * 会话事件广播器（会话增删改跨标签页同步）
     */
    private final AiSessionEventBroadcaster sessionEventBroadcaster;
    private final SysAiSessionTraceMapper sessionTraceMapper;

      /**
       * 模型注册表（查上下文窗口；窗口只认注册表，不认会话快照，见 {@link #registryContextWindow}）
       */
    private final com.chua.starter.account.channel.service.AccountChannelModelService channelModelService;

      /**
       * 将前端 settings 对象序列化为 JSON 字符串；入参为 null 或序列化失败时返回 null。
       * @param settingsObj 对象
       * @return 返回值
      */
    private String writeSettings(Object settingsObj) {
        if (settingsObj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(settingsObj);
        } catch (Exception e) {
            log.warn("[Session] 序列化 settings 失败: {}", e.getMessage());
            return null;
        }
    }

      /**
      /**
       * 将持久化的 settings JSON 解析为 Map；为空或解析失败时返回空 Map。
       * @param json 字符串
       * @return 返回值
      */
    private Map<String, Object> readSettings(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? parsed : new HashMap<>();
        } catch (Exception e) {
            log.warn("[Session] 解析 settings 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

      /**
       * 合并会话 settings，并统一托管控件「会话工作目录」。
       *
       * <p>工作目录虽是客户端提交的，但只有落在 {@code <基路径>/<账号>} 或
       * {@code <基路径>/public} 下、且真实存在的目录才会被接受（校验在
       * {@link SessionWorkspaceResolver#normalize(String)}）。因此先把客户端塞进 settings 的
       * 同名键剔掉，再按入参校验后写回，避免绕过范围校验。</p>
       *
       * @param previousJson 库里已有的 settings JSON（新建会话传 null）
       * @param incomingSettings 本次提交的 settings；null 表示保持原值
       * @param touchWorkspace 本次是否修改工作目录
       * @param rawWorkspacePath 本次提交的工作目录；空值表示清空
       * @return 合并后的 JSON；内容为空时返回 null
       */
    private String mergeSettings(String previousJson, Object incomingSettings,
                                 boolean touchWorkspace, Object rawWorkspacePath) {
        Map<String, Object> settings = new LinkedHashMap<>(readSettings(previousJson));
        Map<String, Object> incoming = asSettingsMap(incomingSettings);
        if (incoming != null) {
            settings = new LinkedHashMap<>(incoming);
              // 工作目录是服务端托管键：客户端不能借 settings 绕过下面的范围校验
            settings.remove(SessionWorkspaceResolver.WORKSPACE_PATH_KEY);
        }
        String current = Objects.toString(settings.get(SessionWorkspaceResolver.WORKSPACE_PATH_KEY), null);
        if (touchWorkspace) {
            current = Objects.toString(rawWorkspacePath, null);
        }
        String normalized = sessionWorkspaceResolver.normalize(current);
        if (normalized == null) {
            settings.remove(SessionWorkspaceResolver.WORKSPACE_PATH_KEY);
        } else {
            settings.put(SessionWorkspaceResolver.WORKSPACE_PATH_KEY, normalized);
        }
        return settings.isEmpty() ? null : writeSettings(settings);
    }

      /**
       * 把前端提交的 settings 统一成键值对。
       *
       * @param rawSettings 前端提交的 settings
       * @return 键值对；null 表示本次没有提交 settings
       */
    private Map<String, Object> asSettingsMap(Object rawSettings) {
        if (rawSettings == null) {
            return null;
        }
        if (rawSettings instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (rawSettings instanceof CharSequence text) {
              // 双重编码的 settings：直接按 JSON 文本解析，避免被当成字符串丢掉
            return readSettings(text.toString());
        }
        return readSettings(writeSettings(rawSettings));
    }

      /**
       * 解析助手消息上持久化的确认请求；仅返回 status=pending 的请求，结构 {replyId, items}。
       *
       * @param json 确认请求 JSON
       * @return 待确认请求 Map；非 pending / 解析失败返回 null
       */
      /**
       * 解析随助手消息落库的**工具调用轨迹**（{@code confirmation} 列里的 {@code {toolCalls:[...]}}）。
       *
       * <p>为什么需要：工具调用原先只在流式 SSE 事件里出现、从不落库，刷新/重载后
       * 「逐行工具行」就没有数据。落库后由本方法回传为 {@code metadata.toolCalls}。</p>
       *
       * @param json confirmation 原文（可能是待确认请求、也可能没有）
       * @return 含 {@code toolCalls} 的 Map；无轨迹返回 null
       */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolTrace(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
              // 两种形态都认：toolCalls（MCP 路径）/ toolTraces（AgentScope trace 路径）
            return (data.get("toolCalls") != null || data.get("toolTraces") != null) ? data : null;
        } catch (Exception e) {
            return null;
        }
    }

      /**
       * 把 trace 行自带的耗时搬到「工具行」上，供历史渲染动态计时。
       *
       * <p>为什么需要：AgentScope 的 trace 是分阶段的（tool_call / tool_args / tool_result），
       * 历史接口会把它们合成「一次调用一行」。合成过程一旦丢掉时序字段，
       * 刷新后工具行就只剩名字与摘要，看不到「26.1s」这类耗时。</p>
       *
       * <p>取值优先级：durationMs（落库时由 endTimestamp - timestamp 算好）&gt;
       * endTimestamp - timestamp（老数据兜底）。同时写顶层与 metadata 两个位置：
       * 前端 {@code durationFor()} 读的是 {@code tool.metadata.durationMs}，
       * 顶层键便于其它消费方直接读。</p>
       *
       * @param row    正在合成的工具行
       * @param trace  该行对应的原始 trace
       */
    private static void carryTraceDuration(Map<String, Object> row, Map<?, ?> trace) {
        if (row == null || trace == null || row.containsKey("durationMs")) {
            return;
        }
        Long duration = null;
        Object raw = trace.get("durationMs");
        if (raw instanceof Number number) {
            duration = number.longValue();
        } else {
            Object startObj = trace.get("timestamp");
            Object endObj = trace.get("endTimestamp");
            if (startObj instanceof Number start && endObj instanceof Number end) {
                long diff = end.longValue() - start.longValue();
                if (diff > 0) {
                    duration = diff;
                }
            }
        }
        if (duration == null || duration <= 0) {
            return;
        }
        row.put("durationMs", duration);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("durationMs", duration);
        row.put("metadata", metadata);
    }

      /**
      /**
       * 解析待确认请求
       * @param json 字符串
       * @return 待确认请求 Map；非 pending / 解析失败返回 null
      */
    private Map<String, Object> parsePendingConfirmation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            if (!"pending".equals(data.get("status"))) {
                return null;
            }
            Object replyId = data.get("replyId");
            if (replyId == null || String.valueOf(replyId).isBlank()) {
                return null;
            }
            Map<String, Object> req = new HashMap<>();
            req.put("replyId", String.valueOf(replyId));
            req.put("items", data.getOrDefault("items", new ArrayList<>()));
            return req;
        } catch (Exception e) {
            log.warn("[Session] 解析确认请求失败: {}", e.getMessage());
            return null;
        }
    }

      /**
       * 解析失败轮的错误态。 <p>失败助手消息把 {@code finishReason}/{@code errorDetail} 落在 confirmation 里 （见 {@code ChatStreamOrchestrator
          .persistErroredAssistantMessage}）。SSE 期间前端直接 吃 turn_end 事件所以能显示红色错误块；刷新后改走本方法还原历史，必须把这两个字段 一并透出，否则错误提示在重载后整块消失。</p> <p>只认非空的 
          {@code finishReason}，且 {@code errorDetail} 单独判空后回落到 finishReason， 避免上游只给状态码不给文案时渲染成空白错误框。</p>
       * @param json 字符串
       * @return 含 finishReason（及 errorDetail）的 Map；非失败轮或解析失败返回 null
      */
    private Map<String, Object> parseErrorState(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            Object finishReason = data.get("finishReason");
            if (finishReason == null || String.valueOf(finishReason).isBlank()) {
                return null;
            }
            Map<String, Object> state = new HashMap<>();
            state.put("finishReason", String.valueOf(finishReason));
            Object errorDetail = data.get("errorDetail");
            state.put("errorDetail", errorDetail == null ? String.valueOf(finishReason) : String.valueOf(errorDetail));
            return state;
        } catch (Exception e) {
            log.warn("[Session] 解析错误态失败: {}", e.getMessage());
            return null;
        }
    }

      /**
       * 组装一条助手消息的 usage（历史回读口径）。
       *
       * <p>三列 {@code prompt/completion/total_tokens} 是既有列，直接取；缓存命中量与
       * 首字耗时没有独立列，写方把它们并进了 {@code confirmation} JSON
       * （见 {@code ChatStreamOrchestrator#attachUsageExtra}），这里解析出来补进 usage，
       * 否则刷新后「会话底部统计条」的缓存命中率与 TTFT 会归零。</p>
       *
       * @param msg 助手消息实体
       * @return usage Map（缺值字段不放键，前端显示「—」）
       */
    private Map<String, Object> buildUsage(SysAiMessage msg) {
        Map<String, Object> usage = new HashMap<>();
        usage.put("promptTokens", msg.getPromptTokens());
        usage.put("completionTokens", msg.getCompletionTokens());
        usage.put("totalTokens", msg.getTotalTokens());
        Map<String, Object> extra = parseUsageExtra(msg.getConfirmation());
        if (extra != null) {
            usage.putAll(extra);
        }
        return usage;
    }

      /**
       * 从 confirmation JSON 里取用量补充字段（{@code cachedTokens} / {@code firstTokenLatencyMs}）。
       *
       * @param json confirmation 原文
       * @return 含补充字段的 Map；都没有时返回 {@code null}
       */
    private Map<String, Object> parseUsageExtra(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            Map<String, Object> extra = new HashMap<>(2);
            Object cached = data.get("cachedTokens");
            if (cached instanceof Number && ((Number) cached).longValue() > 0) {
                extra.put("cachedTokens", ((Number) cached).longValue());
            }
            Object firstToken = data.get("firstTokenLatencyMs");
            if (firstToken instanceof Number && ((Number) firstToken).longValue() > 0) {
                extra.put("firstTokenLatencyMs", ((Number) firstToken).longValue());
            }
            return extra.isEmpty() ? null : extra;
        } catch (Exception e) {
            return null;
        }
    }

      /**
       * 解析本轮实际应答的供应商信息。 <p>写入方在 {@code ChatStreamOrchestrator
          }：路由一旦出问题（选了 A 家却发到 B 家接口、 B 家返回配额报错），最需要的就是「当时到底是哪家供应商、哪个 baseUrl 应答的」这条信息。 SSE 期间前端从 create 事件拿；刷新后改走本方法还原，必须也从 confirmation 透出，
          否则提示只在流式的那几秒存在，刷新即丢。</p>
       * @param json 字符串
       * @return 路由信息 Map；无路由信息或解析失败返回 {@code null}
      */
    private Map<String, Object> parseRoute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            Object route = data.get("route");
            if (route instanceof Map<?, ?> map && !map.isEmpty()) {
                Map<String, Object> result = new HashMap<>(map.size());
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            return null;
        } catch (Exception e) {
            log.warn("[Session] 解析路由信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析思考正文。
     *
     * <p>写入侧 {@code ChatStreamOrchestrator#attachReasoning} 把它并入助手消息 confirmation 的
     * {@code reasoning} 键；这里取出供 GET messages 透出为 {@code content.reasoningContent}。</p>
     *
     * @param json 助手消息的 confirmation JSON（可为空）
     * @return 思考正文；无则返回 {@code null}
     */
    private String parseReasoning(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            Object reasoning = data.get("reasoning");
            return reasoning instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.warn("[Session] 解析思考正文失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从工具轨迹里聚合思考正文（老数据兜底）。
     *
     * <p>AgentScope 路径曾把思考写成 {@code toolTraces} 里 {@code kind=thinking} 的条目，
     * 这些条目没有 {@code toolName}：工具行聚合会跳过它们（不当作工具调用），
     * 但思考内容本身不该丢——按出现顺序拼接后透出为 {@code reasoningContent}。</p>
     *
     * @param toolTrace {@link #parseToolTrace(String)} 的结果（可为空）
     * @return 拼接后的思考正文；无 thinking 轨迹时返回 {@code null}
     */
    private String aggregateThinkingTrace(Map<String, Object> toolTrace) {
        if (toolTrace == null) {
            return null;
        }
        Object traces = toolTrace.get("toolTraces");
        if (!(traces instanceof List<?> list)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            String kind = mm.get("kind") == null ? "" : String.valueOf(mm.get("kind"));
            if (!"thinking".equals(kind)) {
                continue;
            }
            Object text = mm.get("text");
            if (text == null) {
                continue;
            }
            String s = String.valueOf(text);
            if (!s.isBlank()) {
                sb.append(s).append('\n');
            }
        }
        return sb.length() == 0 ? null : sb.toString().trim();
    }

      /**
     * 获取会话列表。
     *
     * @param skip            跳过的条数（可选）
     * @param limit           每页条数（可选）
     * @param groupId         分组标识（可选；当前后端无分组概念，非空返回空页）
     * @param includeArchived 是否包含已归档会话（可选）
     * @return 会话列表与 hasMore 标记
     */
    @GetMapping
    public ReturnResult<Map<String, Object>> list(
            @RequestParam(required = false) Integer skip,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) Boolean includeArchived) {

        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
   // 一律按账号隔离（含管理员）：管理员在业务数据侧与普通账号同权
        if (!AiUserContext.canAccessOthersData()) {
            wrapper.eq(SysAiSession::getSysUserId, AiUserContext.requireUserId());
        }
          // 分组过滤：后端当前没有会话归属分组字段，全部视为未分组。
          // groupId 非空（真实分组）时返回空页并 hasMore=false，避免把别组数据混进来；
          // 未分组时按 skip/limit 分页并多取一条判断 hasMore。
          // 之前这里直接忽略 skip/limit/groupId，侧边栏「加载更多」永远拿不到下一页。
        boolean ungrouped = groupId == null || groupId.isBlank() || "null".equalsIgnoreCase(groupId)
                || "undefined".equalsIgnoreCase(groupId);

        int offset = skip != null && skip > 0 ? skip : 0;
        int pageSize = limit != null && limit > 0 ? Math.min(limit, 100) : 15;

        List<SysAiSession> sessions;
        boolean hasMore;
        if (!ungrouped) {
            sessions = java.util.Collections.emptyList();
            hasMore = false;
        } else {
              // 多取一条用于判断 hasMore
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiSession> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(offset / pageSize + 1, pageSize + 1);
            List<SysAiSession> fetched = sessionMapper.selectPage(page, wrapper).getRecords();
            hasMore = fetched.size() > pageSize;
            sessions = hasMore ? fetched.subList(0, pageSize) : fetched;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreateTime());
            item.put("updatedAt", session.getUpdateTime());

            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", offset + items.size() + (hasMore ? 1 : 0));
        result.put("hasMore", hasMore);
        return ReturnResult.ok(result);
    }

      /**
       * 获取侧边栏会话列表
       *
       * @param limit 数量限制
       * @return 会话列表
       */
    @GetMapping("/sidebar")
    public ReturnResult<Map<String, Object>> sidebar(@RequestParam(required = false) Integer limit) {
        int pageSize = limit != null ? limit : 15;
          // 一律按账号隔离（含管理员）
        boolean crossAccount = AiUserContext.canAccessOthersData();
        String userId = AiUserContext.requireUserId();

          // 查询分组：仅本人分组
        LambdaQueryWrapper<SysAiSessionGroup> groupWrapper = new LambdaQueryWrapper<>();
        if (!crossAccount) {
            groupWrapper.eq(SysAiSessionGroup::getUserId, userId);
        }
        groupWrapper.orderByAsc(SysAiSessionGroup::getSortOrder);
        List<SysAiSessionGroup> groups = sessionGroupMapper.selectList(groupWrapper);

          // 构造 groups 数组
        List<Map<String, Object>> groupList = new ArrayList<>();
        for (SysAiSessionGroup group : groups) {
            Map<String, Object> groupMap = new HashMap<>();
            groupMap.put("id", String.valueOf(group.getId()));
            groupMap.put("name", group.getName());
            groupMap.put("sortOrder", group.getSortOrder());
            groupList.add(groupMap);
        }

          // 查询未归档会话：仅本人会话
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiSession::getIsArchived, false);  // 只查询未归档的会话
        if (!crossAccount) {
            wrapper.eq(SysAiSession::getSysUserId, userId);
        }
          // 未分组会话按创建时间倒序取 pageSize+1 条：多取的一条只用来判断 hasMore，
          // 之前这里 hasMore 硬编码 false，且未分组会话超过一页时永远翻不到下一页。
        wrapper.orderByDesc(SysAiSession::getCreateTime);
        List<SysAiSession> sessions = sessionMapper.selectList(
                wrapper.last("LIMIT " + (pageSize + 1)));
        boolean ungroupedHasMore = sessions.size() > pageSize;
        if (ungroupedHasMore) {
            sessions = sessions.subList(0, pageSize);
        }

          // 构造 groupSessions（未分组）
        List<Map<String, Object>> groupSessions = new ArrayList<>();
        Map<String, Object> ungroupedGroup = new HashMap<>();
        ungroupedGroup.put("groupId", null);

        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreateTime());
            item.put("updatedAt", session.getUpdateTime());
            items.add(item);
        }
        ungroupedGroup.put("items", items);
        ungroupedGroup.put("hasMore", ungroupedHasMore);
        groupSessions.add(ungroupedGroup);

        Map<String, Object> result = new HashMap<>();
        result.put("groups", groupList);
        result.put("groupSessions", groupSessions);

        return ReturnResult.ok(result);
    }

      /**
       * 创建新会话
       *
       * @param body 会话参数
       * @return 创建的会话
       */

      /**
       * 获取会话消息列表
       */
      // 前端 UUID sessionId -> 后端 Long sessionId 的映射（与 ChatStreamController 共享）
    public static final java.util.Map<String, Long> SESSION_DIALOG_MAP = new java.util.concurrent.ConcurrentHashMap<>();

      /**
       * 工作目录树最大展开深度（0 表示只列一层）
       */
    private static final int WORKSPACE_MAX_DEPTH = 2;

      /**
       * 工作目录树每个目录最多列出的条目数
       */
    private static final int WORKSPACE_MAX_ENTRIES = 200;

      /**
       * 初始窗口允许的最大用户轮次数。
       *
       * <p>与前端下拉「初始化加载最新的记录数量」的上限（50）对齐：这个参数是查询入参，
       * 不设上限时一个超大 N 会退化成「把整段历史一次查出来」，正是要避免的情形。</p>
       */
    private static final int MAX_INITIAL_TURNS = 50;

    /**
     * 获取会话消息列表。
     *
     * @param id              会话标识（数字 id 或前端 UUID）
     * @param limit           消息条数（可选）
     * @param beforeMessageId 起始消息 id（向前翻页，可选）
     * @param initialTurns    初始加载的最大用户轮次数（可选，上限 {@link #MAX_INITIAL_TURNS}）
     * @return 会话消息列表
     */
    @GetMapping("/{id}/messages")
    public ReturnResult<Map<String, Object>> messages(
            @PathVariable("id") String id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String beforeMessageId,
            @RequestParam(required = false) Integer initialTurns) {

        log.info("[Messages] Loading messages for sessionId: {}", id);
        Long sessionId;
          // 如果是数字，直接解析
        if (id.matches("\\d+")) {
            sessionId = Long.parseLong(id);
        } else {
              // 从映射中查找
            sessionId = SESSION_DIALOG_MAP.get(id);
            if (sessionId == null) {
                  // 通过会话标题查找（前端UUID前8位对应会话标题后8位）
                String titlePrefix = id != null && id.length() > 8 ? "guada-" + id.substring(0, 8) : null;
                if (titlePrefix != null) {
                    LambdaQueryWrapper<SysAiSession> titleWrapper = new LambdaQueryWrapper<>();
                    titleWrapper.likeRight(SysAiSession::getTitle, titlePrefix);
                    titleWrapper.orderByDesc(SysAiSession::getId);
                    titleWrapper.last("LIMIT 1");
                    SysAiSession existingSession = sessionMapper.selectOne(titleWrapper);
                    if (existingSession != null) {
                        sessionId = existingSession.getId();
                        SESSION_DIALOG_MAP.put(id, sessionId);
                    }
                }
                  // 还是找不到，创建新会话
                if (sessionId == null) {
                    SysAiSession newSession = new SysAiSession();
                    newSession.setTitle("guada-" + (id != null && id.length() > 8 ? id.substring(0, 8) : "chat"));
                    newSession.setSysUserId(AiUserContext.requireUserId());
                    newSession.setStatus(1);
                    newSession.setIsArchived(false);
                    sessionMapper.insert(newSession);
                    sessionId = newSession.getId();
                    SESSION_DIALOG_MAP.put(id, sessionId);
                }
            }
        }

   // 归属校验：他人会话的消息不可读（管理员只读放行）
        requireReadableSession(sessionId);

        log.info("[Messages] Querying messages for sessionId (Long): {}", sessionId);
        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        
          // 如果有 beforeMessageId，查询该消息之前的消息
        if (beforeMessageId != null && beforeMessageId.matches("\\d+")) {
            Long beforeId = Long.parseLong(beforeMessageId);
            wrapper.lt(SysAiMessage::getId, beforeId);
        }

          /*
           * 初始窗口：按「用户轮次」取最新的 N 轮，供会话页首次打开时使用
           * （字段见 SessionGlobalSettingProvider.initial_load_turns）。
           *
           * 为什么必须落在 user 消息上做下界：前端按 user 消息把消息分组成轮次，
           * 窗口若从某条 assistant 开始，那条回复找不到所属提问就会被判成「孤儿」而不渲染，
           * 表现为明明有历史却少一条回复。取第 N 条最新的 user 消息作为下界（含自身），
           * 窗口内就一定是完整的若干轮。
           *
           * 只在没有 beforeMessageId（即首屏，不是向上翻页）时生效；initialTurns 出现时
           * 优先于 limit —— limit 是「按条数」的旧上限，两者叠加会把 N 轮又截断一次。
           * 不传 initialTurns 时行为与历史版本完全一致。
           */
        boolean turnWindowApplied = false;
        if (initialTurns != null && initialTurns > 0 && (beforeMessageId == null || beforeMessageId.isBlank())) {
            LambdaQueryWrapper<SysAiMessage> boundaryWrapper = new LambdaQueryWrapper<>();
            boundaryWrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
            boundaryWrapper.eq(SysAiMessage::getRole, "user");
              // id 兜底排序：同一毫秒内落库的两条消息仅按 createTime 排序结果不确定，
              // 取错边界会让窗口多一轮或少一轮
            boundaryWrapper.orderByDesc(SysAiMessage::getCreateTime);
            boundaryWrapper.orderByDesc(SysAiMessage::getId);
            boundaryWrapper.last("LIMIT " + Math.min(initialTurns, MAX_INITIAL_TURNS));
            List<SysAiMessage> boundaryRows = messageMapper.selectList(boundaryWrapper);
            if (!boundaryRows.isEmpty()) {
                Long oldestTurnUserId = boundaryRows.get(boundaryRows.size() - 1).getId();
                wrapper.ge(SysAiMessage::getId, oldestTurnUserId);
                turnWindowApplied = true;
            } else {
                  // 一条 user 消息都没有（只剩系统/助手消息的异常会话）：回落到 limit 口径，
                  // 不因为窗口算不出来就把整段历史一次性铺出去
                log.info("[Messages] 会话 {} 没有 user 消息，initialTurns={} 回落到 limit 口径", sessionId, initialTurns);
            }
        }
        
        // 同一毫秒落库的 user/assistant 仅按 createTime 排序结果不确定，会导致下面的
        // 倒序输出里 assistant 跑到所属 user 前面；id 单调递增，作为稳定兜底排序
        wrapper.orderByDesc(SysAiMessage::getCreateTime);
        wrapper.orderByDesc(SysAiMessage::getId);
        if (limit != null && !turnWindowApplied) {
            wrapper.last("LIMIT " + limit);
        }

        List<SysAiMessage> messages = messageMapper.selectList(wrapper);

          // 会话级思考强度档位（settings.thinkingEffort）：随消息 metadata 回显，
          // 便于历史消息与 UI 知道该会话当前生效的档位；'default'/缺省 = 不传（交由模型默认）
        String sessionEffort = null;
        try {
            SysAiSession effortSession = sessionMapper.selectById(sessionId);
            if (effortSession != null) {
                Map<String, Object> settingsMap = readSettings(effortSession.getSettings());
                Object effortRaw = settingsMap == null ? null : settingsMap.get("thinkingEffort");
                if (effortRaw != null) {
                    String effortValue = String.valueOf(effortRaw).trim();
                    if (!effortValue.isEmpty() && !"default".equalsIgnoreCase(effortValue)) {
                        sessionEffort = effortValue;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[Messages] 读取会话思考强度失败: {}", ex.getMessage());
        }

          // 反转，按时间升序返回
        List<Map<String, Object>> items = new ArrayList<>();
        String lastUserMessageId = null;
        LocalDateTime lastUserMessageTime = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            SysAiMessage msg = messages.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(msg.getId()));
            item.put("role", msg.getRole());
            
              // 如果是 user 消息，记录 ID 和时间；如果是 assistant，设置 parentId 和耗时
            if ("user".equals(msg.getRole())) {
                lastUserMessageId = String.valueOf(msg.getId());
                lastUserMessageTime = msg.getCreateTime();
            } else if ("assistant".equals(msg.getRole())
                    && (msg.getParentId() != null || lastUserMessageId != null)) {
                /*
                 * 优先用落库的 parent_id：插入顺序与创建时间一旦不等（同一毫秒落库、
                 * 按 createTime 排序结果不确定、或 LIMIT / beforeMessageId 截窗把所属
                 * 提问切掉），下面按「上一条 user」推断就会把回复挂到错误的轮次，
                 * 甚至整条挂不上，前端按 parentId 分组时该回复直接消失。
                 * 推断只作为老数据（parent_id 为空）的兜底。
                 */
                Long storedParentId = msg.getParentId();
                String parentIdValue = storedParentId != null
                        ? String.valueOf(storedParentId) : lastUserMessageId;
                item.put("parentId", parentIdValue);
                  // 计算耗时（毫秒）
                if (lastUserMessageTime != null) {
                    long durationMs = java.time.Duration.between(lastUserMessageTime, msg.getCreateTime()).toMillis();
                    item.put("totalDurationMs", durationMs);
                }
            }
            
              // 构造 contents 数组
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            content.put("id", String.valueOf(msg.getId()));
            content.put("content", msg.getContent());
            content.put("state", Map.of("isStreaming", false));
              // 用户消息附件回填：web 浏览器推理链路的消息经 /messages/import 落库，
              // 附件元数据存在 attachment（JSON 数组，TurnItem 渲染形状）。
              // 刷新/重载后从这里还原 contents[0].files，否则附件只在发送当场可见。
            if ("user".equals(msg.getRole())) {
                List<Map<String, Object>> attachmentFiles = parseAttachmentFiles(msg.getAttachment());
                if (!attachmentFiles.isEmpty()) {
                    content.put("files", attachmentFiles);
                }
            }
              // 在 content 级别也添加 metadata，包含 usage
            Map<String, Object> contentMetadata = new HashMap<>();
            if ("assistant".equals(msg.getRole())) {
                Map<String, Object> usage = buildUsage(msg);
                contentMetadata.put("usage", usage);
                  // 工具调用轨迹：随助手消息落库，刷新/重载后仍能渲染「逐行工具行」
                Map<String, Object> toolTrace = parseToolTrace(msg.getConfirmation());
                if (toolTrace != null) {
                    Object tcRaw = toolTrace.get("toolCalls");
                    if (tcRaw != null) {
                        contentMetadata.put("toolCalls", tcRaw);
                        contentMetadata.put("toolCallsResponse", toolTrace.get("toolCallsResponse"));
                    } else {
                          // 智能体路径只有 trace 形态：聚合出「每次工具调用一行」。
                          //
                          // AgentScope 的 trace 是**分阶段**的，同一次调用会产生多条：
                          //   tool_call    调用工具: write_file
                          //   tool_args    {"path": "...", ...}
                          //   tool_result  Error: Parameter validation failed ...
                          //
                          // 早先实现逐条 trace 各建一行，并且把 outcome 硬编码成 "success"，
                          // 于是有两个后果：同一次 write_file 渲染成三行同名工具；
                          // **工具报错却显示为成功** —— 参数校验失败在界面上完全看不出来，
                          // 用户只知道「模型没干活」，查不到真实原因。
                          //
                          // 现在按出现顺序聚合：tool_call 开新行，后续 tool_args / tool_result
                          // 归到该行；outcome 由 tool_result 的真实内容判定。
                        List<Map<String, Object>> synth = new ArrayList<>();
                        Object traces = toolTrace.get("toolTraces");
                        if (traces instanceof List<?> list) {
                            Map<String, Object> current = null;
                            String currentName = null;
                            for (Object o : list) {
                                if (!(o instanceof Map<?, ?> mm)) {
                                    continue;
                                }
                                String toolName = mm.get("toolName") == null
                                        ? "" : String.valueOf(mm.get("toolName"));
                                  // 没有工具名的 trace（agent_end / turn / usage 之类）不属于工具行
                                if (toolName.isBlank() || "null".equals(toolName)) {
                                    continue;
                                }
                                String kind = mm.get("kind") == null ? "" : String.valueOf(mm.get("kind"));
                                String text = mm.get("text") == null ? "" : String.valueOf(mm.get("text"));

                                boolean startNew = current == null
                                        || "tool_call".equals(kind)
                                        || !toolName.equals(currentName);
                                if (startNew) {
                                    current = new LinkedHashMap<>();
                                    current.put("name", toolName);
                                    current.put("arguments", "tool_call".equals(kind) ? "" : text);
                                    current.put("outcome", "success");
                                    synth.add(current);
                                    currentName = toolName;
                                      // 打开新行时先把该 trace 自带的耗时任进来（有些实现的
                                      // tool_call 行本身就是完整 span），后面 tool_result 再覆盖。
                                    carryTraceDuration(current, mm);
                                    if ("tool_call".equals(kind)) {
                                        continue;
                                    }
                                }
                                if ("tool_args".equals(kind)) {
                                    current.put("arguments", text);
                                } else if ("tool_result".equals(kind)) {
                                    current.put("result", text);
                                      // 失败判定看工具返回的真实内容：以 Error 开头即失败。
                                      // 绝不硬编码 success —— 参数校验失败被显示成成功，
                                      // 正是「工具出错但界面无迹可寻」的直接原因。
                                    boolean failed = text.startsWith("Error")
                                            || text.startsWith("error")
                                            || text.contains("validation failed");
                                    current.put("outcome", failed ? "failed" : "success");
                                      // 工具耗时：合成时必须一并带上，否则刷新后工具行只剩
                                      // 名字与摘要、没有「26.1s」这类计时（用户 2026-10-24 要求）。
                                    carryTraceDuration(current, mm);
                                }
                            }
                        }
                        if (!synth.isEmpty()) {
                            contentMetadata.put("toolCalls", synth);
                        }
                    }
                }
                  // 待人工确认（plan_exit）：刷新后仍渲染批准卡片
                Map<String, Object> pendingConfirm = parsePendingConfirmation(msg.getConfirmation());
                if (pendingConfirm != null) {
                    contentMetadata.put("confirmationRequest", pendingConfirm);
                }
                  // 失败轮的错误态：ChatStreamOrchestrator.persistErroredAssistantMessage 把
                  // finishReason/errorDetail 写进 confirmation，而上面的解析只取了工具调用与待确认，
                  // 于是刷新后 TurnItem 拿不到 finishReason，红色错误块整块消失——
                  // 表现为「会话里报错是红的，一刷新就恢复成普通消息」。这里补齐透出。
                Map<String, Object> errorState = parseErrorState(msg.getConfirmation());
                if (errorState != null) {
                    contentMetadata.putAll(errorState);
                }
                  // 本轮实际应答的供应商信息（写入方见 ChatStreamOrchestrator#describeRoute）
                Map<String, Object> route = parseRoute(msg.getConfirmation());
                if (route != null) {
                    contentMetadata.put("route", route);
                }
                  // 思考正文还原：写入侧把它并入 confirmation.reasoning，这里透出为
                  // content.reasoningContent —— 前端 flattenSingleContent 靠它生成 think 项，
                  // 否则刷新后思考行整段消失（用户报障：思考没有保存）。
                String reasoning = parseReasoning(msg.getConfirmation());
                if (reasoning == null) {
                    // 老数据兜底：AgentScope 路径曾把思考落在 toolTraces（kind=thinking），
                    // 这些条目没有 toolName，此前在工具行聚合时被跳过，刷新后同样看不到。
                    reasoning = aggregateThinkingTrace(toolTrace);
                }
                if (reasoning != null) {
                    content.put("reasoningContent", reasoning);
                }
            }
            content.put("metadata", contentMetadata);
            contents.add(content);
            item.put("contents", contents);
            
            item.put("state", Map.of("isStreaming", false));
            item.put("createdAt", msg.getCreateTime());
            
              // 添加 metadata，包含模型信息和 token 使用量
            Map<String, Object> metadata = new HashMap<>();
            if ("assistant".equals(msg.getRole()) && msg.getModelName() != null) {
                metadata.put("modelName", msg.getModelName());
            }
            if ("assistant".equals(msg.getRole())) {
                Map<String, Object> usage = buildUsage(msg);
                metadata.put("usage", usage);
            }
              // 会话生效的思考强度档位（low/medium/high/max）；未设置则不返回该键
            if ("assistant".equals(msg.getRole()) && sessionEffort != null) {
                metadata.put("thinkingEffort", sessionEffort);
            }
              // 失败轮错误态同时提到消息级：TurnItem.assistantMetadata 以 messageMeta 覆盖
              // contentMeta，只在 content 级补的话会被消息级空对象覆盖掉，错误块仍不显示。
            if ("assistant".equals(msg.getRole())) {
                Map<String, Object> errorState = parseErrorState(msg.getConfirmation());
                if (errorState != null) {
                    metadata.putAll(errorState);
                }
            }
            item.put("metadata", metadata);
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
          /*
           * 是否还有更早的记录。前端原先只能拿「本次返回条数 >= 请求条数」猜，
           * 一旦初始窗口改成「按轮次」（条数不再等于请求条数），这个猜测必然失效：
           * 明明还有历史却提示「没有更多消息」，向上翻页的触发条件也一起失效。
           * 这里给出确定答案：本次返回的最早一条之前还有没有消息。
           */
        result.put("hasMore", hasOlderMessages(sessionId, items));
        result.put("initialTurnsApplied", turnWindowApplied);
        return ReturnResult.ok(result);
    }

      /**
       * 本次返回的最早一条消息之前，是否还存在更早的消息。
       *
       * <p>用一次 COUNT 换取确定的「还有没有更早记录」，比前端按条数猜更可靠；
       * 返回空列表时直接判定为没有更多。</p>
       *
       * @param sessionId 会话主键
       * @param items     本次返回的消息条目（时间升序，元素含字符串型 id）
       * @return true 表示还能继续向上加载
       */
    private boolean hasOlderMessages(Long sessionId, List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            return false;
        }
        Object earliest = items.get(0).get("id");
        if (earliest == null || !String.valueOf(earliest).matches("\\d+")) {
            return false;
        }
        LambdaQueryWrapper<SysAiMessage> olderWrapper = new LambdaQueryWrapper<>();
        olderWrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        olderWrapper.lt(SysAiMessage::getId, Long.parseLong(String.valueOf(earliest)));
        Long older = messageMapper.selectCount(olderWrapper);
        return older != null && older > 0;
    }

      /**
       * 搜索会话（标题 + 消息正文，游标分页）。
       *
       * <p>前端搜索框（{@code SessionSearchDialog}）调的是 {@code GET /sessions/search}，
       * 而后端此前没有这个映射：请求落进 {@code GET /sessions/{id}} 的兜底，
       * {@code Long.parseLong("search")} 抛 NumberFormatException → 400。
       * 补上独立映射后，Spring 会优先精确路径，不再被 {@code {id}} 截走。</p>
       *
       * <p>返回结构对齐前端 {@code SearchSessionResponse}：{@code items / total / hasMore / nextCursor}；
       * 每项带 {@code matchType}（title/content）与 {@code matchSnippet}（正文命中片段）。
       * 游标是不透明字符串（此处为偏移量），前端只回传不解析。</p>
       *
       * @param keyword         搜索关键字（空白时返回空结果）
       * @param cursor          游标（可选）
       * @param limit           每页条数（可选，默认 20，上限 50）
       * @param includeArchived 是否包含已归档会话（可选）
       * @return 搜索结果
       */
    @GetMapping("/search")
    public ReturnResult<Map<String, Object>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "includeArchived", required = false) Boolean includeArchived) {
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", 0);
        result.put("hasMore", false);
        result.put("nextCursor", null);
        if (kw.isEmpty()) {
            return ReturnResult.ok(result);
        }

        int pageSize = limit != null && limit > 0 ? Math.min(limit, 50) : 20;
        int offset = parseCursor(cursor);
        boolean crossAccount = AiUserContext.canAccessOthersData();
        String userId = AiUserContext.requireUserId();
        boolean withArchived = Boolean.TRUE.equals(includeArchived);

          // 命中会话按 id 去重：同一会话可能既命中标题又命中正文，保留标题命中优先
        Map<Long, SysAiSession> matched = new LinkedHashMap<>();
        Map<Long, String> matchType = new HashMap<>();
        Map<Long, String> snippets = new HashMap<>();

          // 1) 标题命中
        LambdaQueryWrapper<SysAiSession> titleWrapper = new LambdaQueryWrapper<>();
        titleWrapper.like(SysAiSession::getTitle, kw);
        if (!crossAccount) {
            titleWrapper.eq(SysAiSession::getSysUserId, userId);
        }
        applyArchiveFilter(titleWrapper, withArchived);
        titleWrapper.orderByDesc(SysAiSession::getUpdateTime).last("LIMIT 200");
        for (SysAiSession session : sessionMapper.selectList(titleWrapper)) {
            matched.put(session.getId(), session);
            matchType.put(session.getId(), "title");
        }

          // 2) 正文命中：先按内容捞最近的匹配消息，再反查会话（归属与归档在后一步过滤）
        LambdaQueryWrapper<SysAiMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.select(SysAiMessage::getSysAiSessionId, SysAiMessage::getContent)
                .like(SysAiMessage::getContent, kw)
                .orderByDesc(SysAiMessage::getId)
                .last("LIMIT 300");
        java.util.Set<Long> contentSessionIds = new java.util.LinkedHashSet<>();
        for (SysAiMessage message : messageMapper.selectList(messageWrapper)) {
            Long sid = message.getSysAiSessionId();
            if (sid == null) {
                continue;
            }
              // 片段取最近一条命中消息：同一会话的多条命中只留一条，避免列表里重复
            snippets.putIfAbsent(sid, snippet(message.getContent(), kw));
            contentSessionIds.add(sid);
        }
        if (!contentSessionIds.isEmpty()) {
            for (SysAiSession session : sessionMapper.selectBatchIds(contentSessionIds)) {
                if (!crossAccount && !userId.equals(session.getSysUserId())) {
                    continue;
                }
                if (!withArchived && Boolean.TRUE.equals(session.getIsArchived())) {
                    continue;
                }
                if (matched.containsKey(session.getId())) {
                    continue;
                }
                matched.put(session.getId(), session);
                matchType.put(session.getId(), "content");
            }
        }

          // 3) 统一按最近活跃倒序，再按偏移切片
        List<SysAiSession> ordered = new ArrayList<>(matched.values());
        ordered.sort(Comparator.comparing(SysAiSession::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int total = ordered.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + pageSize, total);
        for (SysAiSession session : ordered.subList(from, to)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("groupId", null);
            item.put("matchType", matchType.get(session.getId()));
            String snippet = snippets.get(session.getId());
            if (snippet != null) {
                item.put("matchSnippet", snippet);
            }
            item.put("createdAt", epochMillis(session.getCreateTime()));
            item.put("updatedAt", epochMillis(session.getUpdateTime()));
            item.put("lastActiveAt", epochMillis(session.getUpdateTime()));
            items.add(item);
        }

        boolean hasMore = to < total;
        result.put("items", items);
        result.put("total", total);
        result.put("hasMore", hasMore);
        result.put("nextCursor", hasMore ? String.valueOf(to) : null);
        return ReturnResult.ok(result);
    }

      /**
       * 归档过滤：默认只看未归档（历史行的 is_archived 可能为 NULL，一并视为未归档）。
       *
       * @param wrapper       会话查询条件
       * @param withArchived  是否包含已归档
       */
    private static void applyArchiveFilter(LambdaQueryWrapper<SysAiSession> wrapper, boolean withArchived) {
        if (withArchived) {
            return;
        }
        wrapper.and(w -> w.eq(SysAiSession::getIsArchived, false).or().isNull(SysAiSession::getIsArchived));
    }

      /**
       * 解析游标：只接受本接口下发的数字偏移，其它形态一律从头开始。
       *
       * @param cursor 游标
       * @return 偏移量（>= 0）
       */
    private static int parseCursor(String cursor) {
        if (cursor == null || !cursor.matches("\\d+")) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

      /**
       * 把 LocalDateTime 转成前端统一用的毫秒时间戳。
       *
       * @param time 时间，可为 null
       * @return 毫秒时间戳；null 时返回 0
       */
    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

      /**
       * 截取正文命中片段（关键字前后各留一段），供搜索列表展示。
       *
       * @param content 消息正文
       * @param keyword 关键字
       * @return 单行片段；内容为空时返回 null
       */
    private static String snippet(String content, String keyword) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String text = content.replace('\n', ' ').replace('\r', ' ').trim();
        int index = text.toLowerCase().indexOf(keyword.toLowerCase());
        int start = index < 0 ? 0 : Math.max(index - 30, 0);
        int end = Math.min(start + 120, text.length());
        String piece = text.substring(start, end);
        return (start > 0 ? "…" : "") + piece + (end < text.length() ? "…" : "");
    }

      /**
       * 获取单个会话详情（前端 fetchSession）
       * @param id ID，不允许为 null
       * @return Return结果 对象
       */
    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable("id") String id) {
        Long sessionId = Long.parseLong(id);
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
   // 归属校验：他人会话详情不可读（管理员只读放行）
            requireReadableSession(sessionId);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(sessionId));
        
        if (session != null) {
            result.put("title", session.getTitle());
            result.put("modelId", session.getModelId());
            result.put("characterId", session.getCharacterId());
            result.put("mcpServerIds", parseIdList(session.getMcpServerIds()));
            result.put("groupId", session.getSysAiModuleCode());
            result.put("createdAt", session.getCreateTime() != null ? session.getCreateTime().atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli() : System.currentTimeMillis());
            result.put("updatedAt", session.getUpdateTime() != null ? session.getUpdateTime().atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli() : System.currentTimeMillis());
        } else {
            result.put("title", "会话 " + id);
            result.put("modelId", null);
            result.put("characterId", null);
            result.put("mcpServerIds", null);
            result.put("groupId", null);
            result.put("createdAt", System.currentTimeMillis());
            result.put("updatedAt", System.currentTimeMillis());
        }
        
        Map<String, Object> detailSettings = readSettings(session == null ? null : session.getSettings());
        result.put("settings", detailSettings);
          // 会话绑定的项目目录：前端据此回显工作目录，未设置时为 null
        result.put("workspacePath", detailSettings.get(SessionWorkspaceResolver.WORKSPACE_PATH_KEY));
        return ReturnResult.ok(result);
    }

      /**
       * 更新会话（移动到分组、修改标题等）。
       *
       * @param id 会话 id（字符串形式的路径参数）
       * @param body 更新内容请求体，键值对形式
       * @return 更新结果，含 success 与 message 字段
       */
    @PutMapping("/{id}")
    public ReturnResult<Map<String, Object>> update(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
        Long sessionId = Long.parseLong(id);
        
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "会话不存在");
            return ReturnResult.ok(result);
        }
   // 写出口校验：仅归属人可改，管理员对他人数据只读
        requireWritableSession(sessionId);
        
          // 更新标题
        if (body.containsKey("title")) {
            session.setTitle(body.get("title").toString());
        }
        
          // 更新分组
        if (body.containsKey("groupId")) {
            Object groupId = body.get("groupId");
            if (groupId != null && !"null".equals(groupId.toString())) {
                session.setSysAiModuleCode(groupId.toString());
            } else {
                session.setSysAiModuleCode(null);
            }
        }
        
          // 更新模型ID
        if (body.containsKey("modelId")) {
            Object modelId = body.get("modelId");
            if (modelId != null && !"null".equals(modelId.toString())) {
                session.setModelId(modelId.toString());
            } else {
                session.setModelId(null);
            }
        }

          // 更新绑定角色
        if (body.containsKey("characterId")) {
            session.setCharacterId(parseLongOrNull(String.valueOf(body.get("characterId"))));
        }

          // 更新会话级 MCP 服务器列表（仅管理员可写，非管理员忽略）
        if (body.containsKey("mcpServerIds")) {
              // 会话级 MCP 服务器仍限管理员配置：这是**服务端能力开关**，
              // 不是账号数据隔离的一部分（见 canAccessOthersData 的口径说明）。
            String mcpCsvOnUpdate = normalizeIdList(body.get("mcpServerIds"));
            if (!AiUserContext.isAdmin()) {
                mcpCsvOnUpdate = null;
            }
            session.setMcpServerIds(mcpCsvOnUpdate);
        }

          // 更新会话级设置（runMode、温度、topP、知识库引用等）与会话工作目录。
          // 两者落在同一份 settings JSON：工作目录由 mergeSettings 统一做范围校验。
        if (body.containsKey("settings") || body.containsKey("workspacePath")) {
            session.setSettings(mergeSettings(session.getSettings(), body.get("settings"),
                    body.containsKey("workspacePath"), body.get("workspacePath")));
        }

        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        Map<String, Object> updatedSettings = readSettings(session.getSettings());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", String.valueOf(session.getId()));
        result.put("title", session.getTitle());
        result.put("groupId", session.getSysAiModuleCode());
        result.put("modelId", session.getModelId());
        result.put("characterId", session.getCharacterId());
        result.put("mcpServerIds", parseIdList(session.getMcpServerIds()));
        result.put("settings", updatedSettings);
        result.put("workspacePath", updatedSettings.get(SessionWorkspaceResolver.WORKSPACE_PATH_KEY));

        // 广播会话更新事件：其他标签页同步标题/分组/模型/设置（发起方按 source 自忽略）
        Map<String, Object> eventSession = new HashMap<>(result);
        eventSession.remove("success");
        sessionEventBroadcaster.publish(AiSessionEventBroadcaster.TYPE_SESSION_UPDATED,
                session.getSysUserId(), String.valueOf(session.getId()), Map.of("session", eventSession));
        return ReturnResult.ok(result);
    }

      /**
       * 创建。
       *
       * @param body 请求体，不允许为 null
       * @return Return结果 对象
       */
    @PostMapping
    public ReturnResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        // null 防御：getOrDefault 对「键存在但值为 null」会返回 null，直接 .toString() 会 NPE
        // （前端模型列表未就绪时 payload 会带 modelId:null，2026-11-24 实测踩雷）
        Object titleRaw = body.get("title");
        String title = titleRaw == null ? "新会话" : titleRaw.toString();
          // 前端字段为 characterId，兼容旧字段 roleId
        Object characterRaw = body.get("characterId");
        if (characterRaw == null) {
            characterRaw = body.get("roleId");
        }
        String characterIdRaw = characterRaw == null ? "" : characterRaw.toString();
        Object modelIdRaw = body.get("modelId");
        String modelId = modelIdRaw == null ? "" : modelIdRaw.toString();
        Object mcpServerIdsRaw = body.get("mcpServerIds");

          // 真正保存到数据库
        SysAiSession session = new SysAiSession();
        session.setTitle(title);
        session.setSysAiModuleCode(null);
        session.setModelId(modelId);
        session.setCharacterId(parseLongOrNull(characterIdRaw));
        String mcpCsvOnCreate = normalizeIdList(mcpServerIdsRaw);
          // MCP 仅管理员可配置：非管理员提交一律忽略
        if (!AiUserContext.isAdmin()) {
            mcpCsvOnCreate = null;
        }
        session.setMcpServerIds(mcpCsvOnCreate);
          // 先确认身份再碰文件系统：normalize 会按当前账号解析工作区根，
          // 未登录时不该有机会落到公共目录
        session.setSysUserId(AiUserContext.requireUserId());
        String createdSettings = mergeSettings(null, body.get("settings"),
                body.containsKey("workspacePath"), body.get("workspacePath"));
        session.setSettings(createdSettings);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setStatus(1);
        session.setIsArchived(false);
        sessionMapper.insert(session);

        Map<String, Object> createdSettingsView = readSettings(createdSettings);
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(session.getId()));
        result.put("title", session.getTitle());
        result.put("groupId", session.getSysAiModuleCode());
        result.put("modelId", session.getModelId());
        result.put("characterId", session.getCharacterId());
        result.put("mcpServerIds", parseIdList(session.getMcpServerIds()));
        result.put("settings", createdSettingsView);
        result.put("workspacePath", createdSettingsView.get(SessionWorkspaceResolver.WORKSPACE_PATH_KEY));
        result.put("createdAt", session.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        result.put("updatedAt", session.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

        log.info("[Session] 创建会话: id={}, title={}, characterId={}", session.getId(), title, session.getCharacterId());

        // 广播会话创建事件：其他标签页同步侧边栏（发起方按 source 自忽略）
        sessionEventBroadcaster.publish(AiSessionEventBroadcaster.TYPE_SESSION_CREATED,
                session.getSysUserId(), String.valueOf(session.getId()), Map.of("session", result));
        return ReturnResult.ok(result);
    }

      /**
       * 手动压缩会话上下文。
       *
       * <p>把「除最近保留条数外」的历史交给模型生成摘要并落库到
       * {@code sys_ai_session_compression}；后续对话会把这段历史替换成该摘要
       * （见 {@code ChatStreamOrchestrator} 组装 messages 的分支）。
       * 会话内置命令「压缩上下文」调用的就是本接口 —— 此前这个接口不存在，
       * 前端一点就只能收到 404。</p>
       *
       * <p>失败不抛异常，统一返回 {@code success=false} + 可读 message，
       * 让前端按业务提示展示而不是报「请求错误」。</p>
       *
       * @param id 会话标识（数字主键，或前端会话 UUID —— 与 /{id}/messages 取值一致）
       * @return success / removedMessages / summary
       */
    @PostMapping("/{id}/compress")
    public ReturnResult<Map<String, Object>> compress(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        wrapper.orderByAsc(SysAiMessage::getId);
        List<SysAiMessage> all = messageMapper.selectList(wrapper);

          // 保留最近 4 条原文，更早的才值得压成摘要
        final int keep = 4;
        Map<String, Object> result = new HashMap<>();
        if (all.size() < keep + 2) {
            result.put("success", false);
            result.put("message", "消息较少，无需压缩");
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }

        List<SysAiMessage> older = all.subList(0, all.size() - keep);
        StringBuilder transcript = new StringBuilder();
        for (SysAiMessage m : older) {
            transcript.append("user".equals(m.getRole()) ? "[用户] " : "[助手] ")
                    .append(m.getContent() == null ? "" : m.getContent())
                    .append('\n');
        }

        String model = effectiveChatModelResolver.resolve(session.getModelId());
        if (model == null || model.isBlank()) {
            result.put("success", false);
            result.put("message", "压缩失败：未解析到可用模型，请先在设置里指定默认对话模型");
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }

        String summary;
          // response 提到 try 外：调用未成功时 text 为空，真实原因落在 error.message 里，
          // 收尾阶段必须把它带出来，否则 429/无路由都会被报成「模型未返回摘要」。
        Map<String, Object> response = null;
        try {
            com.chua.starter.ai.support.model.AiChatRequest request =
                    new com.chua.starter.ai.support.model.AiChatRequest();
            request.setModel(model);
            request.setStream(false);
            request.setSystemPrompt("你是一名对话摘要助手。请把用户提供的对话压缩成简洁摘要，"
                      + "保留事实、结论、待办与关键约束，直接输出摘要，不要添加解释。");
            // 关键：非流式 call() 下发给模型的是 promptOf(request)（即 getPrompt()），
            // 只设 messages 会让模型收到空提示词，表现为「200 + 摘要为空」。
            // 与 generateTitle 同一口径，两处都设，避免再踩同一个坑。
            String instruction = "请把以下对话压缩成摘要：\n" + transcript;
            request.setPrompt(instruction);
            request.setMessages(List.of(Map.of("role", "user", "content", instruction)));
            response = aiChatFacadeService.call(request);
            Object text = response == null ? null : response.get("text");
            summary = text == null ? "" : String.valueOf(text).trim();
        } catch (Exception e) {
            log.warn("[Session] 压缩会话失败: sessionId={}, {}", sessionId, e.getMessage());
            result.put("success", false);
            result.put("message", "压缩失败：" + e.getMessage());
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }
        if (summary.isEmpty()) {
              // 模型调用未成功时 text 为空：把 error.message 原样带出来，
              // 不要把 429/限流/无路由一律报成「模型未返回摘要」。
            Object failure = response == null ? null : response.get("error");
            if (failure != null) {
                String reason = failure instanceof Map<?, ?> map && map.get("message") != null
                        ? String.valueOf(map.get("message")) : String.valueOf(failure);
                log.warn("[Session] 压缩会话模型调用未成功: sessionId={}, model={}, error={}", sessionId, model, reason);
                result.put("success", false);
                result.put("message", "压缩失败：" + reason);
                result.put("removedMessages", 0);
                return ReturnResult.ok(result);
            }
            result.put("success", false);
            result.put("message", "压缩失败：模型未返回摘要");
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }

        com.chua.starter.ai.support.entity.SysAiSessionCompression record =
                new com.chua.starter.ai.support.entity.SysAiSessionCompression();
        record.setSysAiSessionId(sessionId);
        record.setSysAiCompressionFromMessageId(older.get(0).getId().intValue());
        record.setSysAiCompressionMessageCount((long) older.size());
        record.setSysAiCompressionContext(summary);
        record.setUserId(AiUserContext.requireUserId());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        sessionCompressionService.saveOrUpdate(record);

        result.put("success", true);
        result.put("removedMessages", older.size());
        result.put("summary", summary);
        log.info("[Session] 压缩会话: sessionId={}, 压缩 {} 条, 摘要 {} 字",
                sessionId, older.size(), summary.length());
        return ReturnResult.ok(result);
    }

      /**
       * 解析会话标识为数字主键：数字直接用，前端 UUID 查会话映射表（不创建新会话）。
       *
       * @param id 会话标识
       * @return 数字主键；解析不到时返回 {@code null}
       */
    private static Long resolveSessionId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (id.matches("\\d+")) {
            return Long.parseLong(id);
        }
        return SESSION_DIALOG_MAP.get(id);
    }

      /**
       * 按会话上的模型标识查「模型注册表」里的上下文窗口。
       *
       * <p><b>为什么读取时不再用会话快照里的窗口。</b>窗口原先由前端在流式结束后回写进
       * 会话 {@code settings.contextStats}，看似省事，实则把分母交给了客户端：模型未登记
       * 窗口时前端会拿「用量」兜底（{@code totalTokens}，或首轮还没有消息 token 时的
       * {@code usedTokens}），实测在 {@code agnes-3.0-flash} 上落成过 307 与 12308 两个值。
       * 一旦落库就被后续每轮继承，占用率永久钉在 100%，而链路上再没有任何环节能纠正它。</p>
       *
       * <p>窗口是模型的静态属性，只认注册表：查到就用，查不到就是未知（0），
       * 由界面显示「—」，而不是编一个分母出来。</p>
       *
       * @param modelId 会话上的模型标识，形如 {@code provider:model}；可为空
       * @return 上下文窗口（Token）；未登记或非法时为 0
       */
    private long registryContextWindow(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return 0L;
        }
        int sep = modelId.indexOf(':');
        String providerId = sep > 0 ? modelId.substring(0, sep) : null;
        String model = sep > 0 ? modelId.substring(sep + 1) : modelId;
        if (model.isBlank()) {
            return 0L;
        }
        try {
            com.chua.starter.account.channel.store.SysAccountChannelModel row = providerId == null
                    ? channelModelService.findAnyByModel(model)
                    : channelModelService.find(providerId, model);
            Integer window = row == null ? null : row.getContextWindow();
            return window == null || window <= 0 ? 0L : window;
        } catch (Exception e) {
            log.warn("[Session] 读取模型上下文窗口失败 modelId={}: {}", modelId, e.getMessage());
            return 0L;
        }
    }

      /**
       * 取会话的 token 统计（上下文占用）。
       *
       * <p>两段来源：① 消息表累计的真实 token（prompt / completion）；
       * ② 会话 settings 的 {@code contextStats} —— 前端在流式 finish 事件里拿到
       * 「已用」后回写，刷新页面时靠它恢复已用量（窗口不取快照，见
       * {@link #registryContextWindow}）。</p>
       *
       * @param id 会话标识
       * @return usedTokens / effectiveContextWindow / promptTokens / completionTokens / totalTokens / breakdown
       */
    @GetMapping("/{id}/token-stats")
    public ReturnResult<Map<String, Object>> tokenStats(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        SysAiSession session = sessionMapper.selectById(sessionId);

        long usedTokens = 0L;
          // 窗口从注册表现查，忽略快照里回写的窗口值 —— 那是可以被客户端污染的字段。
        long effectiveContextWindow = registryContextWindow(session == null ? null : session.getModelId());
        Map<String, Object> breakdown = new LinkedHashMap<>();
        Object contextStats = readSettings(session == null ? null : session.getSettings()).get("contextStats");
        if (contextStats instanceof Map<?, ?> stats) {
            usedTokens = longOf(stats.get("usedTokens"));
              // token 构成与已用同源（都由流式 turn_end 落库），缺了它
              // 「上下文已用」卡片刷新后就只剩占用条，明细整块消失。
            breakdown = mapOf(stats.get("breakdown"));
        }

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        long promptTokens = 0L;
        long completionTokens = 0L;
        for (SysAiMessage m : messageMapper.selectList(wrapper)) {
            promptTokens += m.getPromptTokens() == null ? 0 : m.getPromptTokens();
            completionTokens += m.getCompletionTokens() == null ? 0 : m.getCompletionTokens();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("usedTokens", usedTokens);
        result.put("effectiveContextWindow", effectiveContextWindow);
        result.put("promptTokens", promptTokens);
        result.put("completionTokens", completionTokens);
        result.put("totalTokens", Math.max(usedTokens, promptTokens + completionTokens));
        result.put("breakdown", breakdown);
        return ReturnResult.ok(result);
    }

      /**
       * 回写会话的上下文占用（前端在流式 finish 之后调用）。
       *
       * <p>落到会话 settings 的 {@code contextStats}，刷新后由
       * {@link #tokenStats} 读回，避免占用率归零；不建新表。</p>
       *
       * @param id   会话标识
       * @param body 含 usedTokens / effectiveContextWindow / breakdown
       * @return 是否写入成功
       */
    @PutMapping("/{id}/token-stats")
    public ReturnResult<Map<String, Object>> saveTokenStats(@PathVariable("id") String id,
                                                            @RequestBody Map<String, Object> body) {
        Long sessionId = resolveSessionId(id);
        SysAiSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);

        Map<String, Object> settings = new HashMap<>(readSettings(session.getSettings()));
        Map<String, Object> contextStats = new HashMap<>();
        contextStats.put("usedTokens", longOf(body.get("usedTokens")));
        contextStats.put("effectiveContextWindow", longOf(body.get("effectiveContextWindow")));
          // token 构成（系统提示词/摘要/用户输入/历史消息/工具定义）一并落库：
          // 它由流式 turn_end 的 contextStats 带下来，不存的话刷新后「上下文已用」卡片
          // 只剩一根占用条，明细整块消失。原样透传，键名与前端一致。
        Map<String, Object> breakdown = mapOf(body.get("breakdown"));
        if (!breakdown.isEmpty()) {
            contextStats.put("breakdown", breakdown);
        }
        settings.put("contextStats", contextStats);
        session.setSettings(writeSettings(settings));
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

      /**
       * AI 生成会话标题（前端「AI 重命名」）。
       *
       * <p><b>为什么补这个接口。</b>前端 {@code handleAIRenameSession} 一直在 POST
       * {@code /sessions/{id}/generate-title}，代码注释还写着「后端已有该接口」——
       * 但后端从来没有实现过它。请求落到 Spring 的静态资源兜底，日志里是
       * {@code 资源不存在（未映射路径）: No static resource v2/sessions/135/generate-title}，
       * 前端只能弹「AI 重命名失败」。属于「注释描述了一个不存在的实现」。</p>
       *
       * <p>模型走 {@link com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver}
       * 的既有回退链（会话模型 → bot 默认 → 用户默认 → 全局默认），与「AI 联想下一步任务」
       * 同一口径，不在此处写死模型名。</p>
       *
       * <p>不可生成时返回 {@code skipped=true} 与原因，而不是抛错：会话还没消息、
       * 没解析到模型都属于正常状态，前端据此显示「已跳过」而非「失败」。</p>
       *
       * @param id 会话标识
       * @return title 新标题；skipped=true 时附 reason
       */
    @PostMapping("/{id}/generate-title")
    public ReturnResult<Map<String, Object>> generateTitle(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
          // 取最近若干条：标题只需要「这段对话在聊什么」，全量喂进去既慢又没必要
        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId)
                .orderByDesc(SysAiMessage::getId)
                .last("LIMIT 12");
        List<SysAiMessage> recent = messageMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(recent)) {
            result.put("skipped", true);
            result.put("reason", "会话还没有消息，无法生成标题");
            return ReturnResult.ok(result);
        }
          // 上面按 id 倒序取最近 N 条，这里翻回时间顺序，否则模型看到的对话是倒着的
        List<SysAiMessage> ordered = new ArrayList<>(recent);
        java.util.Collections.reverse(ordered);

        String model = effectiveChatModelResolver.resolve(session.getModelId());
        if (model == null || model.isBlank()) {
            result.put("skipped", true);
            result.put("reason", "未解析到可用模型，请先在设置里指定默认对话模型");
            return ReturnResult.ok(result);
        }

        StringBuilder transcript = new StringBuilder();
        for (SysAiMessage m : ordered) {
            String content = m.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
              // 单条截断：一条超长回复（比如整段代码）会把标题生成的上下文挤爆，
              // 而对判断「在聊什么」没有任何额外帮助
            String trimmed = content.length() > 600 ? content.substring(0, 600) + "…" : content;
            transcript.append("user".equals(m.getRole()) ? "用户：" : "助手：")
                    .append(trimmed.replace('\n', ' ')).append('\n');
        }
        if (transcript.length() == 0) {
            result.put("skipped", true);
            result.put("reason", "会话内容为空，无法生成标题");
            return ReturnResult.ok(result);
        }

        String title;
          // response 需要在 try 之外可见：调用失败时 text 是空的，真正的原因在
          // response.error.message 里（例如压根没路由到 ChatClient），收尾阶段要把它带出来。
        Map<String, Object> response = null;
        try {
            com.chua.starter.ai.support.model.AiChatRequest request =
                    new com.chua.starter.ai.support.model.AiChatRequest();
            request.setModel(model);
            request.setStream(false);
            request.setSystemPrompt("你是会话命名助手。根据用户提供的对话内容，生成一个能概括主题的中文标题。"
                      + "要求：不超过 16 个字；不要标点符号、不要引号、不要「关于」「讨论」这类空词；"
                      + "不要解释，不要输出任何多余内容。只输出标题本身。");
              // 关键：非流式 call() 下发给模型的是 promptOf(request)，也就是
              // request.getPrompt()，**不是** messages。只设 messages 会让模型收到
              // 空提示词，于是返回空字符串 —— 表现为接口 200、无异常、raw 为空、
              // 提示「模型没有给出可用标题」，排查时极易误判成模型不配合。
            String instruction = "以下是本次会话的最近对话：\n\n" + transcript + "\n\n请给出标题。";
            request.setPrompt(instruction);
              // messages 一并设置：本方法其他调用方与流式路径按 messages 组装，
              // 两处口径保持一致，避免以后再踩同一个坑。
            request.setMessages(List.of(Map.of("role", "user", "content", instruction)));
            response = aiChatFacadeService.call(request);
            Object text = response == null ? null : response.get("text");
            title = text == null ? "" : String.valueOf(text).trim();
        } catch (Exception e) {
            log.warn("[SessionTitle] 生成标题失败 sessionId={} model={}: {}", sessionId, model, e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "生成标题失败: " + e.getMessage(), e);
        }

        String rawTitle = title;
        title = sanitizeTitle(title);
        if (title.isEmpty()) {
              // 失败原因必须原样带出来。call() 在「路由不到 ChatClient」或模型调用失败时
              // text 为空、真实原因落在 error.message 里，而这里原先只回一句
              // 「模型没有给出可用标题」—— 前端和排查的人都会被这句话误导，分不清是
              // 模型返回了空，还是请求压根没走到模型。实测该接口 56ms 就返回
              // （真实模型调用不可能这么快），正是这条把原因吞掉的路径。
            Object failure = response == null ? null : response.get("error");
            if (failure != null) {
                log.warn("[SessionTitle] 模型调用未成功 sessionId={} model={} error={}", sessionId, model, failure);
                result.put("skipped", true);
                result.put("reason", "模型调用未成功: " + failure);
                return ReturnResult.ok(result);
            }
            log.warn("[SessionTitle] 模型输出清洗后为空 sessionId={} model={} raw={}", sessionId, model, rawTitle);
            result.put("skipped", true);
            result.put("reason", "模型没有给出可用标题，可重试");
            return ReturnResult.ok(result);
        }

        result.put("title", title);
          // 落库：此前只把标题回给前端、不写会话行，页面刷新或侧栏重排后仍是旧标题
          // （表现为「接口返回新标题，但 before=after」）。写库与写出口校验统一走
          // requireWritableSession，不在这里另开旁路。
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return ReturnResult.ok(result);
    }

    /**
     * 会话摘要列表（前端「备忘 / 上下文摘要」面板）。
     *
     * <p>前端 {@code MemoPanel} 一直调 {@code GET /sessions/{id}/summaries}，
     * 但后端没有该映射；请求既不是 {@code /{id}}（段数不同），也不是静态资源，
     * 最终以 404 收场，面板永远显示「没有摘要」。这里按
     * {@code sys_ai_session_compression} 的单条会话摘要作答：语义上是
     * 「当前生效的压缩上下文」，不是历史流水，因此 total 与 items 长度一致。</p>
     *
     * @param id    会话标识
     * @param limit 条数上限（可选；当前实现每会话至多一条）
     * @return items + total
     */
    @GetMapping("/{id}/summaries")
    public ReturnResult<Map<String, Object>> summaries(@PathVariable("id") String id,
                                                       @RequestParam(value = "limit", required = false) Integer limit) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);

        List<Map<String, Object>> items = new ArrayList<>();
        com.chua.starter.ai.support.entity.SysAiSessionCompression compression =
                sessionCompressionService.getBySessionId(sessionId);
        if (compression != null) {
            items.add(summaryItem(compression));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 修改某条会话摘要的正文（前端「编辑摘要」）。
     *
     * @param summaryId 摘要标识（压缩上下文主键）
     * @param body      请求体，含 summaryContent
     * @return 是否成功
     */
    @PutMapping("/summaries/{summaryId}")
    public ReturnResult<Map<String, Object>> updateSummary(@PathVariable("summaryId") String summaryId,
                                                           @RequestBody Map<String, Object> body) {
        Integer compressionId = parseCompressionId(summaryId);
        if (compressionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "摘要不存在");
        }
        Object content = body == null ? null : body.get("summaryContent");
        com.chua.starter.ai.support.service.SysAiSessionCompressionService.WriteResult write =
                sessionCompressionService.updateContext(compressionId, content == null ? "" : String.valueOf(content));
        requireSummaryWritable(write, "无权修改他人会话的摘要");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 删除某条会话摘要（前端「删除摘要」）。
     *
     * @param summaryId 摘要标识（压缩上下文主键）
     * @return 是否成功
     */
    @DeleteMapping("/summaries/{summaryId}")
    public ReturnResult<Map<String, Object>> deleteSummary(@PathVariable("summaryId") String summaryId) {
        Integer compressionId = parseCompressionId(summaryId);
        if (compressionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "摘要不存在");
        }
        com.chua.starter.ai.support.service.SysAiSessionCompressionService.WriteResult write =
                sessionCompressionService.deleteById(compressionId);
        requireSummaryWritable(write, "无权删除他人会话的摘要");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 把摘要写操作的三态结果翻译成接口响应。
     *
     * @param write       写操作结果
     * @param forbiddenMsg 无权时的提示语
     */
    private static void requireSummaryWritable(
            com.chua.starter.ai.support.service.SysAiSessionCompressionService.WriteResult write, String forbiddenMsg) {
        if (write == com.chua.starter.ai.support.service.SysAiSessionCompressionService.WriteResult.NOT_FOUND) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "摘要不存在");
        }
        if (write == com.chua.starter.ai.support.service.SysAiSessionCompressionService.WriteResult.FORBIDDEN) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, forbiddenMsg);
        }
    }

    /**
     * 解析摘要标识：只接受数字主键。
     *
     * @param summaryId 路径参数
     * @return 主键；非法形态返回 null
     */
    private static Integer parseCompressionId(String summaryId) {
        if (summaryId == null || !summaryId.matches("\\d+")) {
            return null;
        }
        try {
            return Integer.parseInt(summaryId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 把压缩上下文实体映射成前端摘要项。
     *
     * <p>字段口径对齐 {@code MemoPanel}：{@code summaryContent} 是摘要正文，
     * {@code createdAt} 用毫秒时间戳（dayjs 可直接解析），
     * {@code cleaningStrategy/compressionStats} 只在有数据时给出，前端已做缺省兼容。</p>
     *
     * @param compression 压缩上下文实体
     * @return 摘要项
     */
    private static Map<String, Object> summaryItem(
            com.chua.starter.ai.support.entity.SysAiSessionCompression compression) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", String.valueOf(compression.getId()));
        item.put("sessionId", compression.getSysAiSessionId() == null
                ? null : String.valueOf(compression.getSysAiSessionId()));
        item.put("summaryContent", compression.getSysAiCompressionContext());
        item.put("cleaningStrategy", "summarized");
        Map<String, Object> stats = new LinkedHashMap<>();
        if (compression.getSysAiCompressionMessageCount() != null) {
            stats.put("beforeMessageCount", compression.getSysAiCompressionMessageCount());
        }
        if (!stats.isEmpty()) {
            item.put("compressionStats", stats);
        }
        item.put("createdAt", epochMillis(compression.getCreateTime()));
        item.put("updatedAt", epochMillis(compression.getUpdateTime()));
        return item;
    }

      /**
       * 清洗模型返回的标题：去掉引号、换行、markdown 强调符与常见前缀。
       *
       * <p>模型即使被要求「只输出标题」，也常带上「标题：」「《》」「**」这类包装。
       * 直接落库会让侧边栏出现 {@code **项目重构**} 这种脏数据，所以在服务端统一收口，
       * 而不是指望前端每处调用都记得清一遍。</p>
       *
       * @param raw 模型原始输出
       * @return 规整后的标题；不可用时为空串
       */
    private static String sanitizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
          // 只取第一行：多行输出说明模型没守约束，第一行通常才是标题
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl);
        }
        t = t.replaceAll("^[#>*\\-\\s]+", "")
                .replaceAll("^(标题|会话标题|title)\\s*[:：]\\s*", "")
                .replaceAll("[*`\"'《》【】\\[\\]]", "")
                .trim();
          // 兜底截断：约束是 16 字，留一点余量，避免超级长标题撑坏侧边栏
        if (t.length() > 24) {
            t = t.substring(0, 24);
        }
        return t;
    }

      /**
       * 取一个可能是 Map 的字段；不是 Map（或为空）时返回空 Map。
       *
       * <p>请求体来自前端 JSON，{@code breakdown} 可能是 null / 非对象，
       * 直接强转会在运行时抛 ClassCastException，故统一在这里兜住。</p>
       *
       * @param value 原始值
       * @return 键值均为字符串化后的 Map；无有效内容时为空 Map
       */
    private Map<String, Object> mapOf(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> src) {
            src.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(String.valueOf(k), longOf(v));
                }
            });
        }
        return out;
    }

      /**
       * 取会话待办列表（opencode todowrite 的服务端镜像）。
       *
       * <p>Agent 运行时每次 {@code todowrite} 都会把全量待办列表镜像到
       * {@code sys_ai_session_todo} 并广播 {@code todo_updated}；本接口按
       * Agent 下发顺序返回镜像列表，供前端「待办」面板在刷新 / 重开标签页后恢复。
       * 写入只发生在后端 Agent 链路，前端无写入口。</p>
       *
       * @param id 会话标识
       * @return {items:[{id,todoId,content,status,priority,sortNo}], total}
       */
    @GetMapping("/{id}/todo")
    public ReturnResult<Map<String, Object>> todo(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        return ReturnResult.ok(sessionTodoService.listBySession(
                String.valueOf(sessionId), AiUserContext.currentUserId()));
    }

      /**
       * 读取会话目标。
       *
       * @param id 会话标识
       * @return 目标；未设置时 data 为 null
       */
    @GetMapping("/{id}/goal")
    public ReturnResult<SessionGoalService.GoalView> goal(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        return ReturnResult.ok(sessionGoalService.getGoal(sessionId));
    }

      /**
       * 新建或覆盖会话目标（状态置 RUNNING；标题不变保留计数，标题变更计数清零）。
       *
       * @param id   会话标识
       * @param body {title, detail?}
       * @return 落库后的目标
       */
    @PutMapping("/{id}/goal")
    public ReturnResult<SessionGoalService.GoalView> saveGoal(@PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        String title = body == null || body.get("title") == null ? null : String.valueOf(body.get("title"));
        String detail = body == null || body.get("detail") == null ? "" : String.valueOf(body.get("detail"));
        return ReturnResult.ok(sessionGoalService.saveGoal(sessionId, title, detail));
    }

      /**
       * 暂停会话目标（RUNNING/PENDING → PAUSED；终态 409）。
       *
       * @param id 会话标识
       * @return 暂停后的目标
       */
    @PostMapping("/{id}/goal/pause")
    public ReturnResult<SessionGoalService.GoalView> pauseGoal(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        return ReturnResult.ok(sessionGoalService.pauseGoal(sessionId));
    }

      /**
       * 恢复会话目标（PAUSED → RUNNING；终态 409）。
       *
       * @param id 会话标识
       * @return 恢复后的目标
       */
    @PostMapping("/{id}/goal/resume")
    public ReturnResult<SessionGoalService.GoalView> resumeGoal(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        return ReturnResult.ok(sessionGoalService.resumeGoal(sessionId));
    }

      /**
       * 取消并彻底销毁会话目标（settings.goal 节点移除，前后端都不保留；幂等）。
       *
       * @param id   会话标识
       * @param body {reason?} 取消原因（仅记日志）
       * @return 被销毁的目标（state=CANCELED）；本就无目标时 data 为 null
       */
    @PostMapping("/{id}/goal/cancel")
    public ReturnResult<SessionGoalService.GoalView> cancelGoal(@PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        return ReturnResult.ok(sessionGoalService.cancelGoal(sessionId, reason));
    }

      /**
       * 取会话的「AI 联想下一个任务」状态。
       *
       * <p>会话尚无自己的取值时，服务端会把**当前默认值钉进该会话**（会话保留原始信息），
       * 此后管理员改设置中心的默认值不再影响这个已有会话；返回的 {@code defaultValue}
       * 供前端展示「跟随默认：开/关」。</p>
       *
       * @param id 会话标识
       * @return 开关、是否已钉住、默认值、已联想出的任务列表与生成时间
       */
    @GetMapping("/{id}/ai-next-task")
    public ReturnResult<SessionNextTaskService.NextTaskState> aiNextTask(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        requireReadableSession(sessionId);
        return ReturnResult.ok(sessionNextTaskService.state(sessionId));
    }

      /**
       * 设置该会话的「AI 联想下一个任务」开关。
       *
       * <p>{@code enabled} 传 true/false 写入会话自身；传 {@code null} 表示
       * <b>解除钉住、回到跟随默认值</b>（同时清掉旧开关语义下生成的联想结果）。</p>
       *
       * @param id   会话标识
       * @param body 含 enabled 字段的请求体
       * @return 设置后的状态
       */
    @PutMapping("/{id}/ai-next-task")
    public ReturnResult<SessionNextTaskService.NextTaskState> setAiNextTask(
            @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
        Long sessionId = resolveSessionId(id);
        requireWritableSession(sessionId);
        Object enabled = body.get("enabled");
        Boolean value = null;
        if (enabled != null && !"null".equals(String.valueOf(enabled))) {
            value = Boolean.parseBoolean(String.valueOf(enabled));
        }
        return ReturnResult.ok(sessionNextTaskService.setEnabled(sessionId, value));
    }

      /**
       * 重新联想该会话的下一步任务（服务端同步调用一次模型）。
       *
       * <p>模型不可用、对话为空或模型没给出可执行任务时返回空列表 + 原因文案，
       * 并保留上一次的结果，不把面板打成错误态。</p>
       *
       * @param id 会话标识
       * @return 联想后的状态
       */
    @PostMapping("/{id}/ai-next-task/refresh")
    public ReturnResult<SessionNextTaskService.NextTaskState> refreshAiNextTask(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        requireReadableSession(sessionId);
        return ReturnResult.ok(sessionNextTaskService.refresh(sessionId));
    }

      /**
       * 预测该会话输入框里「用户下一条最可能发送的消息」。
       *
       * <p><b>这条建议绝不会被自动发送</b>：前端只把它当灰字提示，用户按 Tab 才填入输入框，
       * 填入后仍是可编辑草稿，发不发由用户决定。</p>
       *
       * <p>模型口径：配置 {@code input_suggest.suggest_model} 优先，否则用请求体带来的
       * 当前会话模型，再兜底生效对话模型链。会话没有消息、模型不可用或调用失败时
       * 返回空建议 + 原因文案（HTTP 仍是 200），预测失败不该打断用户输入。</p>
       *
       * @param id   会话标识
       * @param body 含 model（当前会话模型，可空）的请求体，可为空
       * @return 预测文本、实际模型、耗时与不可用原因
       */
    @PostMapping("/{id}/suggest-input")
    public ReturnResult<com.chua.starter.ai.support.model.InputSuggestResponse> suggestInput(
            @PathVariable("id") String id,
            @RequestBody(required = false) com.chua.starter.ai.support.model.InputSuggestRequest body) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        String requestedModel = body == null ? null : body.model();
        return ReturnResult.ok(sessionInputSuggestService.suggest(sessionId, requestedModel));
    }

      /**
       * 取会话工作目录的文件树。
       *
       * <p>目录口径与对话写入侧一致：{@code <system.workspaceBaseDir>/<当前账号>}
       * （按账号隔离，不按会话）；基路径未配置时返回空树。</p>
       *
       * @param id 会话标识
       * @return tree（目录/文件节点数组，结构与前端 WorkspaceSidebar 一致）
       */
    @GetMapping("/{id}/workspace/tree")
    public ReturnResult<Map<String, Object>> workspaceTree(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        java.nio.file.Path root = resolveSessionRoot(String.valueOf(sessionId));
        Map<String, Object> result = new HashMap<>();
        result.put("tree", root == null ? new ArrayList<>() : listWorkspace(root, 0));
        return ReturnResult.ok(result);
    }

      /**
       * 取会话工作目录路径。
       *
       * <p>前端打开工作区面板时会问一次目录路径；此前该端点不存在（404）。
       * 口径与 {@link #workspaceTree} 完全一致。同时给出 {@code path} 与
       * {@code workspacePath} 两个键名，兼容前端不同版本的读取方式。</p>
       *
       * @param id 会话标识
       * @return 目录路径（未配置基路径时为空串）
       */
    @GetMapping("/{id}/workspace-path")
    public ReturnResult<Map<String, Object>> workspacePath(@PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        java.nio.file.Path root = resolveSessionRoot(String.valueOf(sessionId));
        String path = root == null ? "" : root.toString();
        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("workspacePath", path);
        return ReturnResult.ok(result);
    }

      /**
       * 会话工作目录的文件变化事件流（SSE）。
       *
       * <p>前端打开工作区侧栏时按会话连上来，智能体往工作目录写文件时就地推送，
       * 文件树与预览自动刷新，不用手动刷新。归属与目录口径都复用上面两个出口：
       * 目录按「会话绑定的项目目录 / 账号工作目录」解析（{@link #resolveSessionRoot}），会话归属走
       * {@link #requireReadableSession}。</p>
       *
       * <p>基路径未配置时回 404 空响应：前端把 404/405/410 当作「接口不存在」永久停止
       * 重连；若回一条立刻断开的 200 空流，客户端会按正常断开每 3 秒无限重连。</p>
       *
       * @param id 会话标识（原样回填进事件的 {@code sessionId}，前端按它过滤）
       * @return SSE 事件流（data 为 JSON：{@code {type,path,sessionId,timestamp}}）
       */
    @GetMapping("/{id}/workspace/events")
    public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> workspaceEvents(
            @PathVariable("id") String id) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireReadableSession(sessionId);
        java.nio.file.Path root = resolveSessionRoot(String.valueOf(sessionId));
        if (root == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .body(workspaceEventStream.subscribe(id, root));
    }

      /**
       * 解析当前账号的工作目录（{@code <基路径>/<账号标识>}）。
       *
       * <p>账号标识做安全清洗，并在解析后校验仍落在基路径内，防 {@code ../} 越界。</p>
       *
       * @return 工作目录；基路径未配置或越界时返回 {@code null}
       */
    private java.nio.file.Path resolveAgentWorkspace() {
        // 统一口径：直接复用 WorkspacePathResolver（<基路径>/<账号>，
        // 空配置兜底 ~/ai-workspace，越界/建目录失败抛 ApiException 而非静默返回 null）。
        return workspacePathResolver.agentWorkspace();
    }

      /**
       * 解析会话的工作区根：会话绑定了项目目录就用项目，否则回退账号工作目录。
       *
       * <p>侧栏文件树、{@code workspace-path}、SSE 监视都以这个根为准，口径与智能体的执行目录一致
       * （见 {@link SessionWorkspaceResolver#projectRoot(String)}）。</p>
       *
       * @param sessionId 会话标识（数字主键字符串）
       * @return 工作区根；基路径未配置时返回 {@code null}
       */
    private java.nio.file.Path resolveSessionRoot(String sessionId) {
        java.nio.file.Path project = sessionWorkspaceResolver.projectRoot(sessionId);
        return project != null ? project : resolveAgentWorkspace();
    }

      /**
       * 递归列目录为前端可渲染的节点数组。
       *
       * <p>深度与同级条数都有上限：工作目录可能很大，全量递归会把响应与前端
       * 渲染一起拖垮，所以只展开到 {@value #WORKSPACE_MAX_DEPTH} 层、每层最多
       * {@value #WORKSPACE_MAX_ENTRIES} 条。</p>
       *
       * @param dir   目录
       * @param depth 当前深度（0 起）
       * @return 节点数组；目录不可读时返回空数组
       */
    private List<Map<String, Object>> listWorkspace(java.nio.file.Path dir, int depth) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (depth > WORKSPACE_MAX_DEPTH || !java.nio.file.Files.isDirectory(dir)) {
            return nodes;
        }
        try (java.util.stream.Stream<java.nio.file.Path> children = java.nio.file.Files.list(dir)) {
            List<java.nio.file.Path> sorted = children
                    .sorted(Comparator.comparing((java.nio.file.Path p) -> !java.nio.file.Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))
                    .limit(WORKSPACE_MAX_ENTRIES)
                    .toList();
            for (java.nio.file.Path child : sorted) {
                Map<String, Object> node = new HashMap<>();
                node.put("name", child.getFileName().toString());
                node.put("path", child.toString());
                boolean directory = java.nio.file.Files.isDirectory(child);
                node.put("isDirectory", directory);
                if (!directory) {
                    try {
                        node.put("size", java.nio.file.Files.size(child));
                    } catch (Exception ignored) {
                        node.put("size", 0L);
                    }
                }
                if (directory) {
                    node.put("children", listWorkspace(child, depth + 1));
                }
                nodes.add(node);
            }
        } catch (Exception e) {
            log.warn("[Session] 列目录失败 dir={}: {}", dir, e.getMessage());
        }
        return nodes;
    }

      /**
       * 把对象转成 long，null / 非法值按 0。
       *
       * @param value 原值
       * @return long 值
       */
    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

      /**
       * 从此处分叉：把源会话中「分叉点及其之前」的消息复制到一个新会话。
       *
       * <p>前端会话里「从此处分叉」按钮调用本接口（此前它调的就是这个路径，但后端没有实现，
       * 点一次必然 404 并提示「分叉失败」）。复制按消息主键升序进行，并把助手消息的
       * {@code parentId} 重新指向**复制出来的**那条用户消息，这样新会话里的
       * 「多版本」归属关系依然正确；不直接沿用源消息 id，避免跨会话串父。</p>
       *
       * @param id   源会话标识
       * @param body 请求体，含分叉点消息 id（字段名 messageId）
       * @return 新会话 id、标题与复制条数
       */
    @PostMapping("/{id}/fork")
    public ReturnResult<Map<String, Object>> fork(@PathVariable("id") String id,
                                                  @RequestBody Map<String, Object> body) {
        Long sourceSessionId = parseLongOrNull(id);
        SysAiSession source = sourceSessionId == null ? null : sessionMapper.selectById(sourceSessionId);
        if (source == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
          // 读出口校验：他人会话不可分叉（管理员只读放行在外层统一处理）
        requireReadableSession(sourceSessionId);

        Long anchorId = parseLongOrNull(String.valueOf(body.get("messageId")));
        if (anchorId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "缺少分叉点消息 id");
        }

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sourceSessionId);
        wrapper.orderByAsc(SysAiMessage::getId);
        List<SysAiMessage> all = messageMapper.selectList(wrapper);

          // 截取到分叉点（含）为止；没截到说明分叉点不属于本会话
        List<SysAiMessage> picked = new ArrayList<>();
        for (SysAiMessage m : all) {
            picked.add(m);
            if (anchorId.equals(m.getId())) {
                break;
            }
        }
        if (picked.isEmpty() || !anchorId.equals(picked.get(picked.size() - 1).getId())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "分叉点消息不存在");
        }

        SysAiSession target = new SysAiSession();
        String sourceTitle = source.getTitle() == null ? "" : source.getTitle();
        target.setTitle(sourceTitle + "-分叉");
        target.setSysAiModuleCode(source.getSysAiModuleCode());
        target.setModelId(source.getModelId());
        target.setCharacterId(source.getCharacterId());
        target.setMcpServerIds(source.getMcpServerIds());
        target.setSettings(source.getSettings());
        target.setSysUserId(AiUserContext.requireUserId());
        target.setCreateTime(LocalDateTime.now());
        target.setUpdateTime(LocalDateTime.now());
        target.setStatus(1);
        target.setIsArchived(false);
        sessionMapper.insert(target);

        Long lastUserId = null;
        int copied = 0;
        for (SysAiMessage src : picked) {
            SysAiMessage copy = new SysAiMessage();
            copy.setSysAiSessionId(target.getId());
            copy.setRole(src.getRole());
            copy.setType(src.getType());
            copy.setContent(src.getContent());
            copy.setAttachment(src.getAttachment());
            copy.setModelName(src.getModelName());
            copy.setPromptTokens(src.getPromptTokens());
            copy.setCompletionTokens(src.getCompletionTokens());
            copy.setTotalTokens(src.getTotalTokens());
            copy.setUserId(AiUserContext.requireUserId());
            copy.setCreateTime(src.getCreateTime());
            copy.setUpdateTime(LocalDateTime.now());
              // 用户消息不带父；助手消息挂到本次复制出来的那条用户消息上
            copy.setParentId("user".equals(src.getRole()) ? null : lastUserId);
            messageMapper.insert(copy);
            if ("user".equals(src.getRole())) {
                lastUserId = copy.getId();
            }
            copied++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", String.valueOf(target.getId()));
        result.put("title", target.getTitle());
        result.put("copiedMessages", copied);
        log.info("[Session] 分叉会话: 源={}, 新={}, 复制消息={}", sourceSessionId, target.getId(), copied);
        return ReturnResult.ok(result);
    }

      /**
       * 解析角色 id 字符串为 Long，非数字 / 空值返回 null。
       * @param raw 原始字符串
       * @return Long 或 null
       */
    private static Long parseLongOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

      /**
       * 将逗号分隔的 MCP 服务器ID字符串解析为列表。
       * @param raw 原始字符串
       * @return ID 列表，永不为 null
       */
    private static List<String> parseIdList(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

      /**
       * 将前端传入的 MCP 服务器ID（列表或逗号字符串）归一化为逗号分隔字符串；空集合归一化为 null。
       * @param raw 原始值
       * @return 逗号分隔字符串或 null
       */
    private static String normalizeIdList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                addId(values, o == null ? null : String.valueOf(o));
            }
        } else if (raw != null) {
            for (String part : String.valueOf(raw).split(",")) {
                addId(values, part);
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

      /**
       * 添加一个去空白、去重的 ID。
       * @param values 目标列表
       * @param part 原始片段
       */
    private static void addId(List<String> values, String part) {
        if (part == null) {
            return;
        }
        String id = part.trim();
        if (!id.isEmpty() && !"null".equalsIgnoreCase(id) && !"undefined".equalsIgnoreCase(id)
                && !values.contains(id)) {
            values.add(id);
        }
    }

      /**
       * 从某一轮起回退：删除该轮及其之后的所有消息。
       *
       * <p>前端「回退到此处」一直调用 {@code DELETE /v2/sessions/{id}/messages?fromMessageId=}，
       * 但后端只有单条删除端点 —— 请求落到路径变量 {@code /{id}} 上再拼 {@code /messages}
       * 没有映射，Spring 回 405「不支持DELETE请求」，前端只能逐条退化成 N 次删除。</p>
       *
       * <p>本端点按「轮」归并而不是按「条」：锚点是助手消息时，必须连同该轮的用户提问
       * 一起删，否则会留下没有回复的孤儿提问。只回退对话，不回滚工作目录里已被工具
       * 写入的文件 —— 文件是执行轨迹，删了会让用户丢失工作成果。</p>
       *
       * @param id            会话标识
       * @param fromMessageId 回退锚点消息 ID（该条及其之后全部删除）
       * @return success / removedTurns / removedMessages
       */
    @DeleteMapping("/{id}/messages")
    public ReturnResult<Map<String, Object>> rewind(
            @PathVariable("id") String id,
            @RequestParam(value = "fromMessageId", required = false) String fromMessageId) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        requireWritableSession(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (fromMessageId == null || fromMessageId.isBlank()) {
            result.put("success", false);
            result.put("message", "fromMessageId 不能为空");
            result.put("removedTurns", 0);
            result.put("removedMessages", 0);
            return ReturnResult.ok(result);
        }

        SysAiMessage anchor = null;
        if (fromMessageId.matches("\\d+")) {
            anchor = messageMapper.selectById(Long.parseLong(fromMessageId));
        }
        if (anchor == null || !sessionId.equals(anchor.getSysAiSessionId())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "回退点消息不存在");
        }

          // 锚点若是助手回复，回退点应落到它对应的用户提问上（整轮一起删）
        Long boundary = anchor.getId();
        if ("assistant".equals(anchor.getRole()) && anchor.getParentId() != null) {
            SysAiMessage question = messageMapper.selectById(anchor.getParentId());
            if (question != null && sessionId.equals(question.getSysAiSessionId())) {
                boundary = question.getId();
            }
        }

          // 统计影响轮数：边界及之后的消息里有多少条用户提问
        LambdaQueryWrapper<SysAiMessage> affected = new LambdaQueryWrapper<>();
        affected.eq(SysAiMessage::getSysAiSessionId, sessionId)
                .ge(SysAiMessage::getId, boundary);
        List<SysAiMessage> toRemove = messageMapper.selectList(affected);
        long turns = toRemove.stream().filter((m) -> "user".equals(m.getRole())).count();

        LambdaQueryWrapper<SysAiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiMessage::getSysAiSessionId, sessionId)
                .ge(SysAiMessage::getId, boundary);
        int removed = messageMapper.delete(wrapper);

        result.put("success", true);
        result.put("removedTurns", turns);
        result.put("removedMessages", removed);
        log.info("[Session] 回退会话: sessionId={}, 回退点={}, 删除 {} 条 ({} 轮)",
                sessionId, boundary, removed, turns);
        return ReturnResult.ok(result);
    }

      /**
       * 批量导入消息（web 浏览器推理等前端执行链路的落库通道）。
       *
       * <p>web 推理模型在前端动态加载脚本并在浏览器里推理，不经过后端 SSE 编排，
       * 消息不会自动落库。前端在推理成功后把本轮「用户消息 + 助手回复」经这里补记，
       * 重启/刷新后历史不丢。仅做持久化，不触发任何模型调用；assistant 行自动挂到
       * 本批上一条 user 行，user 行的 files 数组原样存入 attachment（JSON）。</p>
       *
       * @param id       会话标识
       * @param messages 消息列表：[{role, content, files?, modelName?}]
       * @return 创建的消息行（id / role / parentId，均为字符串主键）
       */
    @PostMapping("/{id}/messages/import")
    public ReturnResult<Map<String, Object>> importMessages(
            @PathVariable("id") String id,
            @RequestBody(required = false) List<Map<String, Object>> messages) {
        Long sessionId = resolveSessionId(id);
        if (sessionId == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        SysAiSession session = requireWritableSession(sessionId);

        List<Map<String, Object>> rows = new ArrayList<>();
        int imported = 0;
        Long lastUserId = null;
        if (messages != null) {
            for (Map<String, Object> input : messages) {
                if (input == null) {
                    continue;
                }
                String role = String.valueOf(input.get("role"));
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                Object contentRaw = input.get("content");
                String content = contentRaw == null ? "" : String.valueOf(contentRaw);
                Object modelRaw = input.get("modelName");
                String modelName = modelRaw == null ? null : String.valueOf(modelRaw);

                SysAiMessage row = new SysAiMessage();
                row.setSysAiSessionId(sessionId);
                row.setRole(role);
                row.setType("TEXT");
                row.setContent(content);
                row.setModelName(modelName);
                row.setUserId(session.getSysUserId());
                row.setCreateTime(LocalDateTime.now());
                row.setUpdateTime(LocalDateTime.now());
                if ("user".equals(role)) {
                    Object filesRaw = input.get("files");
                    if (filesRaw != null) {
                        try {
                            row.setAttachment(objectMapper.writeValueAsString(filesRaw));
                        } catch (Exception ex) {
                            log.warn("[Messages] 导入消息附件序列化失败: {}", ex.getMessage());
                        }
                    }
                    row.setParentId(null);
                } else {
                    row.setParentId(lastUserId);
                }
                messageMapper.insert(row);
                imported++;
                if ("user".equals(role)) {
                    lastUserId = row.getId();
                }
                Map<String, Object> rowView = new LinkedHashMap<>();
                rowView.put("id", String.valueOf(row.getId()));
                rowView.put("role", role);
                rowView.put("parentId", row.getParentId() == null ? null : String.valueOf(row.getParentId()));
                rows.add(rowView);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("imported", imported);
        log.info("[Messages] 导入会话消息: sessionId={}, 导入 {} 条", sessionId, imported);
        return ReturnResult.ok(result);
    }

      /**
       * 解析消息 attachment 里的附件元数据（JSON 数组）。
       *
       * <p>元素为前端 TurnItem 渲染所需形状（id/displayName/fileType/fileExtension/
       * fileSize/previewUrl），原样透传；解析失败只告警并返回空列表，不影响历史加载。</p>
       *
       * @param attachment attachment 原始字符串
       * @return 附件元数据列表
       */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseAttachmentFiles(String attachment) {
        if (attachment == null || attachment.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(attachment, Object.class);
            if (!(parsed instanceof List)) {
                return List.of();
            }
            List<Map<String, Object>> files = new ArrayList<>();
            for (Object element : (List<Object>) parsed) {
                if (element instanceof Map) {
                    files.add((Map<String, Object>) element);
                }
            }
            return files;
        } catch (Exception ex) {
            log.warn("[Messages] 解析消息附件失败: {}", ex.getMessage());
            return List.of();
        }
    }

      /**
       * 删除单条消息
       * @param messageId 消息ID，不允许为 null
       * @return Return结果 对象
       */
    @DeleteMapping("/messages/{messageId}")
    public ReturnResult<Map<String, Object>> deleteMessage(@PathVariable("messageId") String messageId) {
        Long mid = Long.parseLong(messageId);
        
          // 先查询这条消息
        SysAiMessage message = messageMapper.selectById(mid);
        if (message != null) {
   // 写出口校验：消息随所属会话归属
            requireWritableSession(message.getSysAiSessionId());
              // 如果是用户消息，同时删除对应的 AI 回复（通过 parent_id 关联）
            if ("user".equals(message.getRole())) {
                LambdaQueryWrapper<SysAiMessage> assistantWrapper = new LambdaQueryWrapper<>();
                assistantWrapper.eq(SysAiMessage::getParentId, mid);
                messageMapper.delete(assistantWrapper);
            }
              // 删除消息本身
            messageMapper.deleteById(mid);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

      /**
       * 更新消息内容
       * @param messageId 消息ID，不允许为 null
       * @param body 请求体，包含 content 字段
       * @return ReturnResult 对象
       */
    @PutMapping("/messages/{messageId}")
    public ReturnResult<Map<String, Object>> updateMessage(
            @PathVariable("messageId") String messageId,
            @RequestBody Map<String, Object> body) {
        Long mid = Long.parseLong(messageId);
        String content = (String) body.get("content");
        SysAiMessage message = messageMapper.selectById(mid);
        if (message != null) {
   // 写出口校验：消息随所属会话归属
            requireWritableSession(message.getSysAiSessionId());
            message.setContent(content);
            messageMapper.updateById(message);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

      /**
       * 删除会话
       * @param id ID，不允许为 null
       * @return Return结果 对象
       */
    @DeleteMapping("/{id}")
    public ReturnResult<Map<String, Object>> delete(@PathVariable("id") String id,
            @RequestParam(value = "deleteWorkspace", defaultValue = "false") boolean deleteWorkspace) {
        Long sessionId = Long.parseLong(id);
   // 写出口校验：仅归属人可删，管理员对他人数据只读
        requireWritableSession(sessionId);
        SysAiSession deleting = sessionMapper.selectById(sessionId);

        // 仅清理系统自动创建的会话子目录，不能把项目根或账号根作为删除目标。
        if (deleteWorkspace) {
            workspacePathResolver.deleteDefaultSessionWorkspace(id);
        }
        
          // 删除会话的所有消息
        LambdaQueryWrapper<SysAiMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
        messageMapper.delete(messageWrapper);

          // 删除会话的执行轨迹（轨迹表不随会话外键级联，须显式清理，否则留孤儿行）
        LambdaQueryWrapper<SysAiSessionTrace> traceWrapper = new LambdaQueryWrapper<>();
        traceWrapper.eq(SysAiSessionTrace::getSessionId, sessionId);
        sessionTraceMapper.delete(traceWrapper);

          // 删除会话
        sessionMapper.deleteById(sessionId);

        // 广播会话删除事件：发给会话归属人（管理员代删时，归属人的其他标签页同步摘除）
        if (deleting != null) {
            sessionEventBroadcaster.publish(AiSessionEventBroadcaster.TYPE_SESSION_DELETED,
                    deleting.getSysUserId(), String.valueOf(sessionId), null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

      /**
       * 批量删除会话。
       *
       * @param body 请求体，须包含 ids 列表（字符串形式的会话 id）
       * @return 删除结果，含 success 与 message 字段
       */
    @DeleteMapping("/batch")
    public ReturnResult<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        
        if (CollectionUtils.isEmpty(ids)) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "ids is required");
            return ReturnResult.ok(result);
        }
        
   // 先整批校验归属，再执行删除：避免中途拒绝导致部分删除
        List<Long> sessionIds = new ArrayList<>();
        for (String id : ids) {
            Long sessionId = Long.parseLong(id);
   // 写出口校验：仅归属人可删，管理员对他人数据只读
            requireWritableSession(sessionId);
            sessionIds.add(sessionId);
        }

        int deletedCount = 0;
        for (Long sessionId : sessionIds) {
              // 删除会话的所有消息
            LambdaQueryWrapper<SysAiMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper.eq(SysAiMessage::getSysAiSessionId, sessionId);
            messageMapper.delete(messageWrapper);

              // 删除会话的执行轨迹（同上，显式清理避免孤儿行）
            LambdaQueryWrapper<SysAiSessionTrace> traceWrapper = new LambdaQueryWrapper<>();
            traceWrapper.eq(SysAiSessionTrace::getSessionId, sessionId);
            sessionTraceMapper.delete(traceWrapper);

              // 删除会话
            sessionMapper.deleteById(sessionId);
            deletedCount++;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deletedCount", deletedCount);
        return ReturnResult.ok(result);
    }

      /**
       * 读出口归属校验：**仅归属人本人**（管理员在业务数据侧与普通账号同权）。
       *
       * @param sessionId 会话 标识
       * @return 校验通过的会话
       */
    private SysAiSession requireReadableSession(Long sessionId) {
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人会话");
        }
        return session;
    }

      /**
       * 写出口归属校验：只认归属人本人（含管理员）。
       *
       * @param sessionId 会话 标识
       * @return 校验通过的会话
       */
    private SysAiSession requireWritableSession(Long sessionId) {
        SysAiSession session = requireReadableSession(sessionId);
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "不可修改或删除他人的会话");
        }
        return session;
    }
}
