// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import com.chua.starter.ai.support.entity.SysAiServiceInstance;
import com.chua.starter.ai.support.entity.SysAiServiceToken;
import com.chua.starter.ai.support.entity.settings.SysAiModel;
import com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceInstanceMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceTokenMapper;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
import com.chua.starter.ai.support.service.RuntimeLogService;
import com.chua.starter.ai.support.service.ServiceInstanceManager;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对外服务管理控制器。
 *
 * <p>负责聚合 ChatClient 对外服务实例的管理（多端口、启停、协议开关、访问令牌、访问日志）。
 * 真正的 OpenAI / Claude / Gemini 协议面运行在每个实例独立绑定的端口上（见
 * {@link ServiceInstanceManager}），不在主端口重复暴露协议端点。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ServiceApiController {

    private final SysAiServiceInstanceMapper instanceMapper;
    private final SysAiServiceTokenMapper tokenMapper;
    private final SysAiServiceAccessLogMapper accessLogMapper;
    private final ServiceInstanceManager instanceManager;
    private final RuntimeLogService runtimeLogService;
    private final AiModelMapper aiModelMapper;

    /** 配额方式：按时间（到期失效） */
    private static final String QUOTA_TIME = "time";
    /** 配额方式：按次数（请求次数用尽失效） */
    private static final String QUOTA_COUNT = "count";
    /** 配额方式：按余额（配置总量，消耗完失效） */
    private static final String QUOTA_BALANCE = "balance";

    /**
     * 应用就绪后恢复启用的服务实例（默认低优先级，晚于注册中心初始化）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnReady() {
        try {
            instanceManager.recoverInstances();
        } catch (Exception e) {
            log.error("[ServiceApi] 启动恢复服务实例失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 实例管理（admin） ====================

    /**
     * 列出全部对外服务实例及其运行状态。
     *
     * @return 服务实例列表响应
     */
    @GetMapping("/v2/service/instances")
    public Map<String, Object> listInstances() {
        List<SysAiServiceInstance> list = instanceMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiServiceInstance i : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("instanceId", i.getInstanceId());
            m.put("instanceName", i.getInstanceName());
            m.put("port", i.getPort());
            m.put("enabled", i.getEnabled());
            m.put("running", instanceManager.isRunning(i.getInstanceId()));
            m.put("openaiEnabled", i.getOpenaiEnabled());
            m.put("ccEnabled", i.getCcEnabled());
            m.put("geminiEnabled", i.getGeminiEnabled());
            m.put("recordUsage", i.getRecordUsage());
            result.add(m);
        }
        return Map.of("instances", result);
    }

    /**
     * 创建对外服务实例。
     *
     * @param body 实例名称、端口、协议开关和用量记录配置
     * @return 新建实例信息
     */
    @PostMapping("/v2/service/instances")
    public Map<String, Object> createInstance(@RequestBody Map<String, Object> body) {
        SysAiServiceInstance inst = new SysAiServiceInstance();
        inst.setInstanceId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        inst.setInstanceName(String.valueOf(body.getOrDefault("name", "新实例")));
        inst.setPort(Integer.valueOf(String.valueOf(body.getOrDefault("port", 8790))));
        inst.setEnabled(0);
        inst.setOpenaiEnabled(Boolean.TRUE.equals(body.get("openai")) ? 1 : 0);
        inst.setCcEnabled(Boolean.TRUE.equals(body.get("cc")) ? 1 : 0);
        inst.setGeminiEnabled(Boolean.TRUE.equals(body.get("gemini")) ? 1 : 0);
        inst.setRecordUsage(Boolean.TRUE.equals(body.getOrDefault("recordUsage", true)) ? 1 : 0);
        inst.setUserId(AiUserContext.currentUserId());
        instanceMapper.insert(inst);
        return Map.of("ok", true, "instance", inst);
    }

    /**
     * 启动指定对外服务实例。
     *
     * @param id 服务实例标识
     * @return 启动结果
     */
    @PostMapping("/v2/service/instances/{id}/start")
    public Map<String, Object> startInstance(@PathVariable String id) {
        SysAiServiceInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            return Map.of("ok", false, "error", "实例不存在");
        }
        boolean ok = instanceManager.startInstance(inst);
        if (ok) {
            inst.setEnabled(1);
            instanceMapper.updateById(inst);
        }
        return Map.of("ok", ok);
    }

    /**
     * 停止指定对外服务实例。
     *
     * @param id 服务实例标识
     * @return 停止结果
     */
    @PostMapping("/v2/service/instances/{id}/stop")
    public Map<String, Object> stopInstance(@PathVariable String id) {
        SysAiServiceInstance inst = instanceMapper.selectById(id);
        instanceManager.stopInstance(id);
        if (inst != null) {
            inst.setEnabled(0);
            instanceMapper.updateById(inst);
        }
        return Map.of("ok", true);
    }

    /**
     * 删除已停止的对外服务实例及其关联数据。
     *
     * @param id 服务实例标识
     * @return 删除结果
     */
    @DeleteMapping("/v2/service/instances/{id}")
    public Map<String, Object> deleteInstance(@PathVariable String id) {
        SysAiServiceInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            return Map.of("ok", false, "error", "实例不存在");
        }
        // 必须先停止（运行中或仍处于启用态）才能删除，防止误删对外服务
        if (instanceManager.isRunning(id)
                || (inst.getEnabled() != null && inst.getEnabled() == 1)) {
            return Map.of("ok", false, "error", "请先停止该服务实例，再删除");
        }
        // 清理关联的访问令牌与访问日志，避免孤儿数据
        tokenMapper.delete(new LambdaQueryWrapper<SysAiServiceToken>()
                .eq(SysAiServiceToken::getInstanceId, id));
        accessLogMapper.delete(new LambdaQueryWrapper<SysAiServiceAccessLog>()
                .eq(SysAiServiceAccessLog::getInstanceId, id));
        instanceManager.stopInstance(id);
        instanceMapper.deleteById(id);
        return Map.of("ok", true);
    }

    // ==================== 令牌管理（admin） ====================

    /**
     * 列出访问令牌（台账数据源）。
     *
     * @param instanceId 服务实例标识，为空时返回全部实例的令牌
     * @param keyword    名称 / 令牌 / 模型编码的模糊过滤，为空不过滤
     * @return 访问令牌列表响应
     */
    @GetMapping("/v2/service/tokens")
    public Map<String, Object> listTokens(@RequestParam(required = false) String instanceId,
                                          @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysAiServiceToken> wrapper = new LambdaQueryWrapper<>();
        if (instanceId != null && !instanceId.isBlank()) {
            wrapper.eq(SysAiServiceToken::getInstanceId, instanceId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysAiServiceToken::getTokenName, kw)
                    .or().like(SysAiServiceToken::getToken, kw)
                    .or().like(SysAiServiceToken::getModelIds, kw));
        }
        wrapper.orderByDesc(SysAiServiceToken::getCreateTime);
        List<SysAiServiceToken> tokens = tokenMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokens", tokens);
        result.put("total", tokens.size());
        return result;
    }

    /**
     * 创建访问令牌（<b>已停止签发</b>）。
     *
     * <p>服务实例对外暴露的 {@code /v1/models}、{@code /v1/chat/completions}、
     * {@code /v1/messages}、Gemini 生成已统一按 appKey 鉴权：模型范围取 appKey 所属分组
     * 与 Key 上的限定模型，额度取 appKey 的次数 / Token / 余额，用量也归到 appKey 名下。
     * 若这里继续签发 {@code sys_ai_service_token}，就会出现「控制台发的 Key 与网关发的 Key
     * 两本账」，用量看板也对不上。</p>
     *
     * <p>存量令牌仍然有效（鉴权侧只读兼容），台账可查可停用可删除；新 Key 一律走
     * {@code POST /v2/app-keys}。</p>
     *
     * @param body 请求体（已不再使用）
     * @return 固定的拒绝说明
     */
    @Deprecated
    @PostMapping("/v2/service/tokens")
    public Map<String, Object> createToken(@RequestBody Map<String, Object> body) {
        return Map.of("ok", false, "error",
                "服务实例已统一按 AppKey 鉴权，不再签发实例令牌；请到「Key 台账」新建 AppKey"
                        + "（POST /v2/app-keys），模型范围与额度都在 AppKey 上配置");
    }

    /**
     * 更新访问令牌（名称 / 绑定模型 / 配额方式与额度 / 启停）。
     *
     * <p>仅用于存量令牌的只读兼容维护：调整名称、停用、续期仍然允许，但新的 Key
     * 不再由这里产生。</p>
     *
     * <p>只覆盖请求里出现的字段：前端编辑弹窗总是整份提交，缺失字段按「清空」处理，
     * 与表单语义一致。已用额度不会被这个接口重置，重置走 {@code /quota}。</p>
     *
     * @param id   访问令牌主键
     * @param body 待更新字段
     * @return 更新结果
     */
    @PutMapping("/v2/service/tokens/{id}")
    public Map<String, Object> updateToken(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysAiServiceToken token = tokenMapper.selectById(id);
        if (token == null) {
            return Map.of("ok", false, "error", "令牌不存在");
        }
        String quotaType = normalizeQuotaType(body.get("quotaType"));
        Long maxRequests = parseLongOrNull(body.get("maxRequests"));
        Long maxTokens = parseLongOrNull(body.get("maxTokens"));
        java.time.LocalDateTime expiresAt = parseDateTime(body.get("expiresAt"));
        String invalid = validateQuota(quotaType, expiresAt, maxRequests, maxTokens);
        if (invalid != null) {
            return Map.of("ok", false, "error", invalid);
        }
        String modelIds = expandModelAliases(normalizeModelIds(body.get("modelIds")));
        String tokenType = QUOTA_TIME.equals(quotaType) && expiresAt != null ? "temporary" : "permanent";

        // 这里刻意用显式 set 而不是 updateById：MyBatis-Plus 默认跳过 null 字段，
        // 「按时间 → 按次数」这类切换必须把不再适用的额度真正写成 NULL，
        // 否则旧的 expires_at / max_tokens 会在鉴权侧继续生效，用户改完却是删不掉的旧约束。
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysAiServiceToken> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(SysAiServiceToken::getTokenId, id);
        if (body.containsKey("name")) {
            String name = String.valueOf(body.get("name")).trim();
            uw.set(SysAiServiceToken::getTokenName, name);
            token.setTokenName(name);
        }
        if (body.containsKey("enabled")) {
            int enabled = Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0;
            uw.set(SysAiServiceToken::getEnabled, enabled);
            token.setEnabled(enabled);
        }
        uw.set(SysAiServiceToken::getQuotaType, quotaType);
        uw.set(SysAiServiceToken::getTokenType, tokenType);
        uw.set(SysAiServiceToken::getExpiresAt, QUOTA_TIME.equals(quotaType) ? expiresAt : null);
        uw.set(SysAiServiceToken::getMaxRequests, QUOTA_COUNT.equals(quotaType) ? maxRequests : null);
        uw.set(SysAiServiceToken::getMaxTokens, QUOTA_BALANCE.equals(quotaType) ? maxTokens : null);
        uw.set(SysAiServiceToken::getModelIds, modelIds);
        tokenMapper.update(null, uw);

        token.setQuotaType(quotaType);
        token.setTokenType(tokenType);
        token.setExpiresAt(QUOTA_TIME.equals(quotaType) ? expiresAt : null);
        token.setMaxRequests(QUOTA_COUNT.equals(quotaType) ? maxRequests : null);
        token.setMaxTokens(QUOTA_BALANCE.equals(quotaType) ? maxTokens : null);
        token.setModelIds(modelIds);
        return Map.of("ok", true, "token", token);
    }

    /**
     * 调整令牌已用额度：重置用量，或为「按余额」追加总量。
     *
     * <p>余额模式的余额 = 总量 - 已用。给运营留两条路：{@code reset} 把已用清零
     * （续期式重启），{@code add} 把总量加上一笔（充值式续量）。按次数模式同样
     * 支持 {@code reset}（清零已用次数）。</p>
     *
     * @param id   访问令牌主键
     * @param body {@code action}: reset / add；{@code amount}: add 时的增量（Token 或次数）
     * @return 调整后的额度快照
     */
    @PostMapping("/v2/service/tokens/{id}/quota")
    public Map<String, Object> adjustQuota(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysAiServiceToken token = tokenMapper.selectById(id);
        if (token == null) {
            return Map.of("ok", false, "error", "令牌不存在");
        }
        String action = String.valueOf(body.getOrDefault("action", "reset")).trim();
        long usedRequests = token.getUsedRequests() == null ? 0L : token.getUsedRequests();
        long usedTokens = token.getUsedTokens() == null ? 0L : token.getUsedTokens();
        // 按时间配额的续量语义是「改到期时间」，走 PUT 改 expiresAt；这里没有可加的额度，
        // 放行只会写出一条「上限被改大但鉴权仍按时间判」的假账。
        String quotaType = token.getQuotaType();
        if (!QUOTA_COUNT.equals(quotaType) && !QUOTA_BALANCE.equals(quotaType)) {
            // 老数据没有 quota_type：按已填的额度回推，别把按次 / 按余额的历史 Key 挡在门外
            if (token.getMaxRequests() != null) {
                quotaType = QUOTA_COUNT;
            } else if (token.getMaxTokens() != null) {
                quotaType = QUOTA_BALANCE;
            } else {
                return Map.of("ok", false, "error", "该 Key 按时间计费，请编辑到期时间");
            }
        }
        if ("add".equalsIgnoreCase(action)) {
            Long raw = parseLongOrNull(body.get("amount"));
            long amount = raw == null ? 0L : raw;
            if (amount <= 0) {
                return Map.of("ok", false, "error", "追加数量必须大于 0");
            }
            if (QUOTA_COUNT.equals(quotaType)) {
                // 按次：加的是允许次数，等价于把上限抬高（已用不清零，语义更诚实）
                token.setMaxRequests((token.getMaxRequests() == null ? 0L : token.getMaxRequests()) + amount);
            } else {
                // 按余额：加的是总量，余额随之增加
                token.setMaxTokens((token.getMaxTokens() == null ? 0L : token.getMaxTokens()) + amount);
            }
        } else if ("reset".equalsIgnoreCase(action)) {
            token.setUsedRequests(0L);
            token.setUsedTokens(0L);
        } else {
            return Map.of("ok", false, "error", "不支持的操作: " + action);
        }
        tokenMapper.updateById(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("maxRequests", token.getMaxRequests());
        result.put("usedRequests", token.getUsedRequests() == null ? usedRequests : token.getUsedRequests());
        result.put("maxTokens", token.getMaxTokens());
        result.put("usedTokens", token.getUsedTokens() == null ? usedTokens : token.getUsedTokens());
        return result;
    }

    /**
     * 校验配额方式与额度值是否自洽。
     *
     * @param quotaType   配额方式
     * @param expiresAt   到期时间
     * @param maxRequests 次数上限
     * @param maxTokens   余额总量
     * @return 错误提示；校验通过返回 {@code null}
     */
    private String validateQuota(String quotaType, java.time.LocalDateTime expiresAt,
                                 Long maxRequests, Long maxTokens) {
        switch (quotaType) {
            case QUOTA_TIME:
                return expiresAt == null ? "按时间配额需要设置到期时间" : null;
            case QUOTA_COUNT:
                return maxRequests == null || maxRequests <= 0 ? "按次配额需要填写大于 0 的可用次数" : null;
            case QUOTA_BALANCE:
                return maxTokens == null || maxTokens <= 0 ? "按余额配额需要填写大于 0 的总量" : null;
            default:
                return null;
        }
    }

    /**
     * 归一化配额方式：非法值按按时间处理（既有的「永久/临时」语义最近）。
     *
     * @param raw 前端提交的配额方式
     * @return time / count / balance 三者之一
     */
    private String normalizeQuotaType(Object raw) {
        String v = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();
        if (QUOTA_COUNT.equals(v) || QUOTA_BALANCE.equals(v) || QUOTA_TIME.equals(v)) {
            return v;
        }
        return QUOTA_TIME;
    }

    /**
     * 归一化绑定模型：接受 JSON 数组或逗号分隔字符串，去空去重保序。
     *
     * @param raw 前端提交的模型列表
     * @return 逗号分隔的模型编码；无有效值时返回 {@code null}（表示不限制）
     */
    private String normalizeModelIds(Object raw) {
        Collection<String> items = new ArrayList<>();
        if (raw instanceof Collection<?> list) {
            for (Object o : list) {
                if (o != null) {
                    items.add(String.valueOf(o));
                }
            }
        } else if (raw != null) {
            String s = String.valueOf(raw);
            for (String part : s.split("[,\\n]")) {
                items.add(part);
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String item : items) {
            String code = item == null ? "" : item.trim();
            if (!code.isEmpty()) {
                seen.add(code);
            }
        }
        return seen.isEmpty() ? null : String.join(",", seen);
    }

    /**
     * 展开模型别名：把绑定模型的别名一并写入绑定集合。
     *
     * <p>为什么必须展开：模型管理里同一个模型常配多个别名（如 {@code agnes-2.5-flash}
     * 别名 {@code gpt-4o-mini}），客户端拿别名调用是完全合法的；只记住主编码会让
     * 「已绑定该模型」的 Key 在别名请求上被判越权。别名取自 {@code sys_ai_model.aliases}
     * （JSON 数组），模型行缺失时按原值返回。</p>
     *
     * @param modelIds 归一化后的模型编码，可为 {@code null}
     * @return 含别名的逗号分隔编码串；入参为空时返回 {@code null}
     */
    private String expandModelAliases(String modelIds) {
        if (modelIds == null || modelIds.isBlank()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>(List.of(modelIds.split(",")));
        try {
            List<String> codes = new ArrayList<>(out);
            List<SysAiModel> models = aiModelMapper.selectList(
                    new LambdaQueryWrapper<SysAiModel>().in(SysAiModel::getModelCode, codes));
            for (SysAiModel m : models) {
                for (String alias : parseAliases(m.getModelAliases())) {
                    if (!alias.isBlank()) {
                        out.add(alias.trim());
                    }
                }
            }
        } catch (Exception e) {
            // 模型清单缺失不影响签发：至少主编码已绑定
            log.debug("[ServiceApi] 展开模型别名失败，已忽略: {}", e.getMessage());
        }
        return String.join(",", out);
    }

    /**
     * 解析模型别名（JSON 数组或逗号分隔），失败时返回空列表。
     *
     * @param raw 存储的别名字段
     * @return 别名列表
     */
    private List<String> parseAliases(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String s = raw.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        for (String part : s.split(",")) {
            String v = part.replace("\"", "").trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * parse Long Or Null
     * @param o 对象
     * @return 返回值
    */
    private Long parseLongOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty() || "0".equals(s)) {
            return null;
        }
        try {
            long v = Long.parseLong(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * parse Date Time
     * @param o 对象
     * @return 返回值
    */
    private java.time.LocalDateTime parseDateTime(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(s.substring(0, 10)).atStartOfDay();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * 切换访问令牌启用状态。
     *
     * @param id 访问令牌标识
     * @return 切换结果
     */
    @PostMapping("/v2/service/tokens/{id}/toggle")
    public Map<String, Object> toggleToken(@PathVariable Long id) {
        SysAiServiceToken token = tokenMapper.selectById(id);
        if (token != null) {
            // enabled 在历史数据里可能为 NULL（列有默认值但早期手写 SQL 未填），
            // 直接 Integer 比较会 NPE，这里按「未启用」处理。
            int current = token.getEnabled() == null ? 0 : token.getEnabled();
            token.setEnabled(current == 1 ? 0 : 1);
            tokenMapper.updateById(token);
        }
        return Map.of("ok", true);
    }

    /**
     * 删除访问令牌。
     *
     * @param id 访问令牌标识
     * @return 删除结果
     */
    @DeleteMapping("/v2/service/tokens/{id}")
    public Map<String, Object> deleteToken(@PathVariable Long id) {
        tokenMapper.deleteById(id);
        return Map.of("ok", true);
    }

    // ==================== 访问日志（admin） ====================

    /**
     * 查询服务访问日志。
     *
     * @param instanceId 服务实例标识，为空时不限定实例
     * @param limit 返回记录数上限
     * @return 访问日志列表响应
     */
    @GetMapping("/v2/service/logs")
    public Map<String, Object> listLogs(@RequestParam(required = false) String instanceId,
                                        @RequestParam(defaultValue = "20") int limit) {
        LambdaQueryWrapper<SysAiServiceAccessLog> wrapper = new LambdaQueryWrapper<>();
        if (instanceId != null) {
            wrapper.eq(SysAiServiceAccessLog::getInstanceId, instanceId);
        }
        wrapper.orderByDesc(SysAiServiceAccessLog::getCreateTime).last("LIMIT " + limit);
        return Map.of("logs", accessLogMapper.selectList(wrapper));
    }

    // ==================== 运行日志 / 系统日志（admin） ====================

    /**
     * 读取当前后端进程的运行日志（tail），用于在服务配置页排查卡住 / 报错。
     *
     * @param source out=标准输出（默认），err=标准错误
     * @param lines  返回的最大行数
     */
    @GetMapping("/v2/service/runtime-logs")
    public Map<String, Object> runtimeLogs(@RequestParam(defaultValue = "out") String source,
                                           @RequestParam(defaultValue = "300") int lines) {
        if (!AiUserContext.isAdmin()) {
            return Map.of("ok", false, "error", "无权限：仅管理员可查看运行日志");
        }
        RuntimeLogService.TailResult result = runtimeLogService.tail(source, lines);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", result.path());
        m.put("exists", result.exists());
        m.put("lastModified", result.lastModified());
        m.put("truncated", result.truncated());
        List<Map<String, Object>> lineList = new ArrayList<>();
        for (RuntimeLogService.LogLine l : result.lines()) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("raw", l.raw());
            lm.put("level", l.level());
            lineList.add(lm);
        }
        m.put("lines", lineList);
        return m;
    }
}
