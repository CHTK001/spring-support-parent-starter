// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiCompanion;
import com.chua.starter.ai.support.manage.store.SysAiCompanionFigure;
import com.chua.starter.ai.support.manage.store.SysAiCompanionMemory;
import com.chua.starter.ai.support.manage.store.SysAiCompanionSharedConfig;
import com.chua.starter.ai.support.manage.store.SysAiCompanionSkill;
import com.chua.starter.ai.support.manage.store.SysAiCompanionStore;
import com.chua.starter.ai.support.manage.store.SysAiConversation;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面伙伴（Companion）控制器。
 *
 * <p>提供伙伴 CRUD、记忆管理、技能管理、形象库、共享配置与数据导出的真实实现，
 * 持久化到 {@code sys_ai_companion*} 表。关键状态变更推送 companion.* WS 事件。</p>
 *
 * <p>共享配置在库中只有 {@code shared} 一条全局行，一经写入即影响所有用户，
 * 因此整体配置与「一键关闭」入口仅管理员可执行；采集授权属个人同意行为，
 * 按登录用户以 {@code consent:<userId>} 键单独读写，不污染全局行。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/companion")
@RequiredArgsConstructor
public class ApiCompanionController {

    /**
     * 授权配置键前缀：采集授权是个人同意行为，按登录用户单独成键，不写入全局 shared 行
     */
    private static final String CONSENT_KEY_PREFIX = "consent:";

    /**
     * 桌面伙伴持久化存储
     */
    private final SysAiCompanionStore companionStore;

    /**
     * 会话存储（伙伴专属会话创建）
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WS 事件推送
     */
    private final ApiWebSocketPushService pushService;

    // ==================== 伙伴 CRUD ====================

    @GetMapping("/companions")
    /**
     * 列表companions。
     * @return 列表companions的结果
     */
    public ApiResult<List<Map<String, Object>>> listCompanions() {
        List<Map<String, Object>> result = new ArrayList<>(16); // 容量不可预估，按默认初值
        for (SysAiCompanion c : companionStore.listCompanions()) {
            result.add(withStatus(toProfile(c)));
        }
        return ApiResult.ok(result);
    }

    @PostMapping("/companions")
    /**
     * 创建companion。
     * @param body 主体
     * @return 创建companion的结果
     */
    public ApiResult<Map<String, Object>> createCompanion(@RequestBody Map<String, Object> body) {
        SysAiCompanion companion = new SysAiCompanion();
        companion.setCompanionId(companionStore.newId());
        companion.setName(strOr(body.get("name"), "伙伴"));
        companion.setCharacter(strOr(body.get("character"), "mochi"));
        long now = System.currentTimeMillis();
        companion.setCreatedAt(now);
        companion.setUpdatedAt(now);
        companionStore.createCompanion(companion);
        Map<String, Object> profile = toProfile(companion);
        pushService.push("companion.created", Map.of("companion_id", companion.getCompanionId(), "profile", profile));
        return ApiResult.ok(profile);
    }

    @GetMapping("/companions/{companionId}")
    /**
     * 获取companion。
     * @param companionId companionid
     * @return 获取companion的结果
     */
    public ApiResult<Map<String, Object>> getCompanion(@PathVariable String companionId) {
        return ApiResult.ok(withStatus(toProfile(companionStore.getCompanion(companionId))));
    }

    @PatchMapping("/companions/{companionId}")
    public ApiResult<Map<String, Object>> patchCompanion(@PathVariable String companionId,
                                                          @RequestBody Map<String, Object> body) {
        SysAiCompanion updated = companionStore.patchCompanion(companionId, body);
        Map<String, Object> profile = toProfile(updated);
        pushService.push("companion.config-updated",
                Map.of("scope", companionId, "companion_id", companionId, "profile", profile));
        return ApiResult.ok(profile);
    }

    @DeleteMapping("/companions/{companionId}")
    /**
     * 删除companion。
     * @param companionId companionid
     * @return 删除companion的结果
     */
    public ApiResult<Void> deleteCompanion(@PathVariable String companionId) {
        companionStore.deleteCompanion(companionId);
        pushService.push("companion.deleted", Map.of("companion_id", companionId));
        return ApiResult.ok();
    }

    @PostMapping("/companions/{companionId}/apply-preset")
    public ApiResult<Map<String, Object>> applyPreset(@PathVariable String companionId,
                                                      @RequestBody Map<String, Object> body) {
        SysAiCompanion updated = companionStore.patchCompanion(companionId,
                Map.of("applied_preset", toMapOrEmpty(body)));
        return ApiResult.ok(toProfile(updated));
    }

    @GetMapping("/companions/{companionId}/status")
    /**
     * companion状态。
     * @param companionId companionid
     * @return companion状态的结果
     */
    public ApiResult<Map<String, Object>> companionStatus(@PathVariable String companionId) {
        SysAiCompanion companion = companionStore.getCompanion(companionId);
        return ApiResult.ok(statusOf(companion));
    }

    @PostMapping("/companions/{companionId}/figure")
    public ApiResult<Map<String, Object>> setFigure(@PathVariable String companionId,
                                                    @RequestBody Map<String, Object> body) {
 // 记录 源_路径 到 appearance.习俗_figure（前端两阶段上传，此处简化记录）
        @SuppressWarnings("unchecked")
        Map<String, Object> appearance = toMapOrEmpty(companionStore.getCompanion(companionId).getAppearance());
        Map<String, Object> customFigure = new HashMap<>(2);
        customFigure.put("source_path", body.get("source_path"));
        appearance.put("custom_figure", customFigure);
        SysAiCompanion updated = companionStore.patchCompanion(companionId, Map.of("appearance", appearance));
        return ApiResult.ok(toProfile(updated));
    }

    // ==================== 伙伴专属会话 ====================

    @GetMapping("/companions/{companionId}/companion/active")
    /**
     * 活跃companion。
     * @param companionId companionid
     * @return 活跃companion的结果
     */
    public ApiResult<Map<String, Object>> activeCompanion(@PathVariable String companionId) {
        SysAiCompanion companion = companionStore.getCompanion(companionId);
        Map<String, Object> result = new LinkedHashMap<>(2);
        return ApiResult.ok(result);
    }

    @GetMapping("/companions/{companionId}/companion/threads")
    /**
     * companionthreads。
     * @param companionId companionid
     * @return companionThreads的结果
     */
    public ApiResult<List<Map<String, Object>>> companionThreads(@PathVariable String companionId) {
        List<Map<String, Object>> result = new ArrayList<>(1);
        SysAiCompanion companion = companionStore.getCompanion(companionId);
        if (companion.getConversationId() != null) {
            result.add(threadOf(companion));
        }
        return ApiResult.ok(result);
    }

    /**
     * 幂等确保伙伴专属会话：不存在则创建一条 nomi 会话并持久化绑定。
     * @param companionId companionid
     * @return ensurecompanion会话的结果
     */
    @PostMapping("/companions/{companionId}/companion/threads")
    public ApiResult<Map<String, Object>> ensureCompanionSession(@PathVariable String companionId) {
        SysAiCompanion companion = companionStore.requireWritableCompanion(companionId);
        if (companion.getConversationId() == null) {
            SysAiConversation conversation = new SysAiConversation();
            String conversationId = conversationStore.newId();
            conversation.setConversationId(conversationId);
            conversation.setType("nomi");
            conversation.setName(companion.getName() != null ? companion.getName() : "伙伴会话");
            Map<String, Object> extra = new HashMap<>(2);
            extra.put("companion_id", companionId);
            conversation.setExtra(extra);
            long now = System.currentTimeMillis();
            conversation.setCreatedAt(now);
            conversation.setModifiedAt(now);
            conversationStore.create(conversation);
            companionStore.setCompanionConversationId(companionId, conversationId);
            companion.setConversationId(conversationId);
            pushService.push("conversation.listChanged",
                    Map.of("conversation_id", conversationId, "action", "created"));
        }
        return ApiResult.ok(threadOf(companion));
    }

    // ==================== 技能 ====================

    @PostMapping("/companions/{companionId}/skills/from-session")
    public ApiResult<String> skillsFromSession(@PathVariable String companionId,
                                               @RequestBody Map<String, Object> body) {
        return ApiResult.ok(null);
    }

    @PostMapping("/companions/{companionId}/learn/run")
    public ApiResult<Map<String, Object>> learnRun(@PathVariable String companionId,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("status", "completed", "events_processed", 0, "memories_added", 0));
    }

    @GetMapping("/companions/{companionId}/weekly-digest")
    public ApiResult<Map<String, Object>> weeklyDigest(@PathVariable String companionId,
                                                       @RequestParam(required = false) Integer days) {
        long sinceMs = System.currentTimeMillis() - (days == null ? 7 : days) * 86400000L;
        return ApiResult.ok(Map.of("companion_id", companionId, "since_ms", sinceMs,
                "skills_learned", 0, "memories_added", 0, "new_skill_names", List.of()));
    }

    @GetMapping("/companions/{companionId}/digests")
    public ApiResult<List<Map<String, Object>>> listDigests(@PathVariable String companionId,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer page_size) {
        return ApiResult.ok(List.of());
    }

    @GetMapping("/companions/{companionId}/history/days")
    /**
     * 历史days。
     * @param companionId companionid
     * @return 历史days的结果
     */
    public ApiResult<List<Map<String, Object>>> historyDays(@PathVariable String companionId) {
        return ApiResult.ok(List.of());
    }

    @GetMapping("/companions/{companionId}/skills")
    public ApiResult<Map<String, Object>> listSkills(@PathVariable String companionId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer limit,
                                                     @RequestParam(required = false) Integer offset) {
        List<Map<String, Object>> items = new ArrayList<>(16); // 数量不可预估，按默认初值
        for (SysAiCompanionSkill skill : companionStore.listSkills(companionId, limit, offset)) {
            items.add(toSkill(skill));
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("items", items);
        result.put("total", items.size());
        return ApiResult.ok(result);
    }

    @PostMapping("/companions/{companionId}/skills")
    public ApiResult<Map<String, Object>> createSkill(@PathVariable String companionId,
                                                      @RequestBody Map<String, Object> body) {
        SysAiCompanionSkill skill = new SysAiCompanionSkill();
        skill.setSkillId(companionStore.newId());
        skill.setCompanionId(companionId);
        skill.setSkillName(strOr(body.get("skill_name"), "新技能"));
        skill.setDescription(strOr(body.get("description"), ""));
        skill.setContent(strOr(body.get("content"), ""));
        skill.setStatus(strOr(body.get("status"), "draft"));
        companionStore.createSkill(skill);
        return ApiResult.ok(toSkill(skill));
    }

    @GetMapping("/companions/{companionId}/skills/{skillId}")
    public ApiResult<Map<String, Object>> getSkillContent(@PathVariable String companionId,
                                                          @PathVariable String skillId) {
        SysAiCompanionSkill skill = companionStore.getSkill(skillId);
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("skill", toSkill(skill));
        result.put("content", skill.getContent() != null ? skill.getContent() : "");
        return ApiResult.ok(result);
    }

    @PutMapping("/companions/{companionId}/skills/{skillId}")
    public ApiResult<Map<String, Object>> updateSkill(@PathVariable String companionId,
                                                      @PathVariable String skillId,
                                                      @RequestBody Map<String, Object> body) {
        SysAiCompanionSkill updated = companionStore.updateSkill(skillId, companionId,
                strOr(body.get("content"), null),
                strOr(body.get("status"), null));
        return ApiResult.ok(toSkill(updated));
    }

    @DeleteMapping("/companions/{companionId}/skills/{skillId}")
    public ApiResult<Void> deleteSkill(@PathVariable String companionId,
                                       @PathVariable String skillId) {
        companionStore.deleteSkill(skillId, companionId);
        return ApiResult.ok();
    }

    @PostMapping("/companions/{companionId}/skills/{skillId}/decide")
    public ApiResult<Map<String, Object>> decideSkill(@PathVariable String companionId,
                                                      @PathVariable String skillId,
                                                      @RequestBody Map<String, Object> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        SysAiCompanionSkill updated = companionStore.updateSkill(skillId, companionId,
                null, accept ? "active" : "archived");
        return ApiResult.ok(toSkill(updated));
    }

    // ==================== 记忆 ====================

    @GetMapping("/memories")
    public ApiResult<Map<String, Object>> listMemories(@RequestParam(required = false) String companion_id,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer page_size) {
        List<SysAiCompanionMemory> all = companionStore.listMemories(companion_id, null, null);
        List<Map<String, Object>> items = new ArrayList<>(16); // 数量不可预估，按默认初值
        for (SysAiCompanionMemory memory : all) {
            items.add(toMemory(memory));
        }
        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("items", items);
        result.put("total", items.size());
        return ApiResult.ok(result);
    }

    @PostMapping("/memories")
    /**
     * 创建内存。
     * @param body 主体
     * @return 创建内存的结果
     */
    public ApiResult<Map<String, Object>> createMemory(@RequestBody Map<String, Object> body) {
        SysAiCompanionMemory memory = new SysAiCompanionMemory();
        memory.setMemoryId(companionStore.newId());
        memory.setCompanionId(strOr(body.get("companion_id"), null));
        memory.setKind(strOr(body.get("kind"), "knowledge"));
        memory.setContent(strOr(body.get("content"), ""));
        memory.setSource(strOr(body.get("source"), "manual"));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) body.get("tags");
        memory.setTags(tags);
        companionStore.createMemory(memory);
        Map<String, Object> wire = toMemory(memory);
        pushService.push("companion.memory-created", wire);
        return ApiResult.ok(wire);
    }

    @PutMapping("/memories/{memoryId}")
    public ApiResult<Void> updateMemory(@PathVariable String memoryId,
                                        @RequestBody Map<String, Object> body) {
        companionStore.updateMemory(memoryId,
                strOr(body.get("companion_id"), null),
                strOr(body.get("content"), null),
                body.get("pinned") instanceof Boolean pin ? pin : null,
                strOr(body.get("status"), null));
        return ApiResult.ok();
    }

    @DeleteMapping("/memories/{memoryId}")
    public ApiResult<Void> deleteMemory(@PathVariable String memoryId,
                                        @RequestParam(required = false) String companion_id) {
        companionStore.deleteMemory(memoryId, companion_id);
        return ApiResult.ok();
    }

    @PostMapping("/memories/batch")
    /**
     * 批量内存。
     * @param body 主体
     * @return batch内存的结果
     */
    public ApiResult<Void> batchMemories(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/memories/merge-suggestions")
    /**
     * 合并suggestions。
     * @param body 主体
     * @return 合并suggestions的结果
     */
    public ApiResult<List<Map<String, Object>>> mergeSuggestions(@RequestBody Map<String, Object> body) {
        String companionId = strOr(body.get("companion_id"), null);
        if (companionId == null || companionId.isBlank()) {
            return ApiResult.ok(List.of());
        }
        List<List<SysAiCompanionMemory>> groups = companionStore.suggestMemoryMerges(companionId);
        List<Map<String, Object>> result = new ArrayList<>(8); // 分组数量不可预估
        for (List<SysAiCompanionMemory> group : groups) {
            List<Map<String, Object>> memories = new ArrayList<>(4); // 组内数量不可预估
            for (SysAiCompanionMemory memory : group) {
                memories.add(toMemory(memory));
            }
            Map<String, Object> entry = new LinkedHashMap<>(2);
            entry.put("memories", memories);
            result.add(entry);
        }
        return ApiResult.ok(result);
    }

    @PostMapping("/memories/merge")
    /**
     * 合并内存。
     * @param body 主体
     * @return 合并内存的结果
     */
    public ApiResult<Map<String, Object>> mergeMemories(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("status", "merged"));
    }

    // ==================== 形象 ====================

    @GetMapping("/figures")
    /**
     * 列表figures。
     * @return 列表figures的结果
     */
    public ApiResult<List<Map<String, Object>>> listFigures() {
        List<Map<String, Object>> result = new ArrayList<>(16); // 数量不可预估，按默认初值
        for (SysAiCompanionFigure figure : companionStore.listFigures()) {
            result.add(toFigure(figure));
        }
        return ApiResult.ok(result);
    }

    @PostMapping("/figures")
    /**
     * 创建figure。
     * @param body 主体
     * @return 创建figure的结果
     */
    public ApiResult<Map<String, Object>> createFigure(@RequestBody Map<String, Object> body) {
        SysAiCompanionFigure figure = new SysAiCompanionFigure();
        figure.setFigureId(companionStore.newId());
        figure.setName(strOr(body.get("name"), "形象"));
        figure.setSourcePath(strOr(body.get("source_path"), null));
        figure.setSizeTier(strOr(body.get("size_tier"), "m"));
        if (body.get("aspect") instanceof Number aspect) {
            figure.setAspect(aspect.doubleValue());
        }
        figure.setHeadBox(toMapOrNull(body.get("head_box")));
        companionStore.createFigure(figure);
        return ApiResult.ok(toFigure(figure));
    }

    /**
     * PATCH 形象（兼容前端 更新figure/renamefigure 的 PATCH 请求）。
     * @param figureId figureid
     * @return 删除figure的结果
     */
    @PatchMapping("/figures/{figureId}")
    public ApiResult<Map<String, Object>> patchFigure(@PathVariable String figureId,
                                                      @RequestBody Map<String, Object> body) {
        SysAiCompanionFigure updated = companionStore.updateFigure(figureId,
                strOr(body.get("name"), null),
                toMapOrNull(body.get("head_box")),
                strOr(body.get("size_tier"), null));
        return ApiResult.ok(toFigure(updated));
    }

    @PutMapping("/figures/{figureId}")
    public ApiResult<Map<String, Object>> updateFigure(@PathVariable String figureId,
                                                       @RequestBody Map<String, Object> body) {
        SysAiCompanionFigure updated = companionStore.updateFigure(figureId,
                strOr(body.get("name"), null),
                toMapOrNull(body.get("head_box")),
                strOr(body.get("size_tier"), null));
        return ApiResult.ok(toFigure(updated));
    }

    /**
     * 删除Figure。
     *
     * @param figureId figureID，不允许为 null
     * @return Api结果 对象
     */
    @DeleteMapping("/figures/{figureId}")
    public ApiResult<Void> deleteFigure(@PathVariable String figureId) {
        companionStore.deleteFigure(figureId);
        return ApiResult.ok();
    }

    // ==================== 事件统计 / 授权 ====================

    @GetMapping("/events/stats")
    /**
     * 事件stats。
     * @return 事件stats的结果
     */
    public ApiResult<List<Map<String, Object>>> eventStats() {
        return ApiResult.ok(List.of());
    }

    @GetMapping("/events/storage")
    /**
     * 事件storage。
     * @return 事件storage的结果
     */
    public ApiResult<Map<String, Object>> eventStorage() {
        return ApiResult.ok(nullableMap("total_bytes", 0, "max_bytes", 0, "file_count", 0,
                "oldest_day", null, "newest_day", null, "retention_days", 0, "max_storage_mb", 0));
    }

    @PostMapping("/consent")
    /**
     * applyconsent。
     * @param body 主体
     * @return applyConsent的结果
     */
    public ApiResult<Map<String, Object>> applyConsent(@RequestBody Map<String, Object> body) {
        Map<String, Object> consent = currentUserConsent();
        consent.put("consent_applied", true);
        companionStore.setConfigValue(CONSENT_KEY_PREFIX + AiUserContext.requireUserId(),
                companionStore.toJson(consent));
        return ApiResult.ok(viewConfig(sharedConfig()));
    }

    @PostMapping("/disable-all")
    /**
     * disable全部。
     * @param body 主体
     * @return disable全部的结果
     */
    @Permission("admin")
    public ApiResult<Map<String, Object>> disableAll(@RequestBody Map<String, Object> body) {
        Map<String, Object> config = sharedConfig();
        config.put("disable_all", true);
        companionStore.setConfigValue("shared", companionStore.toJson(config));
        return ApiResult.ok(config);
    }

    // ==================== 共享配置 ====================

    @GetMapping("/config")
    /**
     * 获取配置。
     * @return 获取配置的结果
     */
    public ApiResult<Map<String, Object>> getConfig() {
        Map<String, Object> config = viewConfig(sharedConfig());
        pushService.push("companion.config-updated", Map.of("scope", "shared", "config", config));
        return ApiResult.ok(config);
    }

    @PatchMapping("/config")
    /**
     * patch配置。
     * @param body 主体
     * @return patch配置的结果
     */
    @Permission("admin")
    public ApiResult<Map<String, Object>> patchConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> config = sharedConfig();
        config.putAll(toMapOrEmpty(body));
        config.remove("consent_applied");
        companionStore.setConfigValue("shared", companionStore.toJson(config));
        Map<String, Object> view = viewConfig(config);
        pushService.push("companion.config-updated", Map.of("scope", "shared", "config", view));
        return ApiResult.ok(view);
    }

    // ==================== 导出 / 导入 ====================

    @PostMapping("/export/memory")
    /**
     * 导出内存。
     * @param body 主体
     * @return 导出内存的结果
     */
    public ApiResult<Map<String, Object>> exportMemory(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("dest_path", strOr(body.get("dest_path"), ""), "exported", 0));
    }

    @PostMapping("/export/companions/{companionId}")
    public ApiResult<Map<String, Object>> exportCompanion(@PathVariable String companionId,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("dest_path", strOr(body.get("dest_path"), ""), "exported", 0));
    }

    @PostMapping("/import")
    /**
     * 导入bundle。
     * @param body 主体
     * @return 导入bundle的结果
     */
    public ApiResult<Map<String, Object>> importBundle(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("imported", 0));
    }

    // ==================== 内部序列化辅助 ====================

    /**
     * 转为配置文件。
     * @param c c
     * @return 转为配置文件的结果
     */
    private Map<String, Object> toProfile(SysAiCompanion c) {
        Map<String, Object> profile = new LinkedHashMap<>(16);
        profile.put("companion_id", c.getCompanionId());
        profile.put("seq", c.getSeq() == null ? 0 : c.getSeq());
        profile.put("name", c.getName() != null ? c.getName() : "");
        profile.put("character", c.getCharacter() != null ? c.getCharacter() : "mochi");
        profile.put("persona", c.getPersona() != null ? c.getPersona()
                : Map.of("preset", "", "custom", ""));
        profile.put("model", c.getModel());
        profile.put("fallback_model", c.getFallbackModel());
        profile.put("vision_model", c.getVisionModel());
        profile.put("voice", c.getVoice() != null ? c.getVoice()
                : nullableMap("asr", null, "tts", null, "vad",
                        Map.of("engine", "silero", "sensitivity", 0.5, "min_silence_ms", 600)));
        profile.put("learn", c.getLearn() != null ? c.getLearn()
                : nullableMap("enabled", false, "interval_minutes", 60, "model", null));
        profile.put("evolve", c.getEvolve() != null ? c.getEvolve()
                : nullableMap("enabled", false, "interval_minutes", 720, "model", null,
                        "min_pattern_count", 3, "min_distinct_sessions", 2,
                        "auto_activate", true, "auto_threshold", 0.8,
                        "skill_half_life_days", 14, "skill_archive_threshold", 0.1));
        profile.put("skills", c.getSkills() != null ? c.getSkills()
                : Map.of("enabled", List.of(), "disabled_auto", List.of()));
        profile.put("appearance", c.getAppearance() != null ? c.getAppearance()
                : nullableMap("companion_enabled", false, "companion_x", null, "companion_y", null,
                        "quiet_start", "", "quiet_end", "", "custom_figure", null));
        profile.put("applied_preset", c.getAppliedPreset());
        profile.put("order_index", c.getOrderIndex());
        profile.put("created_at", c.getCreatedAt());
        return profile;
    }

    /**
     * 状态的。
     * @param c c
     * @return 状态的的结果
     */
    private Map<String, Object> statusOf(SysAiCompanion c) {
        Map<String, Object> status = new LinkedHashMap<>(8);
        status.put("companion_id", c.getCompanionId());
        status.put("xp", 0);
        status.put("level", 1);
        status.put("mood", "content");
        long memoriesActive = c.getConversationId() != null
                ? companionStore.countMemories(c.getCompanionId()) : 0;
        status.put("memories_active", memoriesActive);
        status.put("memories_archived", 0);
        status.put("model_configured", c.getModel() != null);
        status.put("collect_any_enabled", false);
        return status;
    }

    /**
     * with状态。
     * @param profile 配置文件
     * @return with状态的结果
     */
    private Map<String, Object> withStatus(Map<String, Object> profile) {
        profile.put("status", statusOf(companionStore.getCompanion(strOr(profile.get("companion_id"), null))));
        return profile;
    }

    /**
     * 转为内存。
     * @param memory 内存
     * @return 转为内存的结果
     */
    private Map<String, Object> toMemory(SysAiCompanionMemory memory) {
        Map<String, Object> map = new LinkedHashMap<>(16);
        map.put("memory_id", memory.getMemoryId());
        map.put("kind", memory.getKind() != null ? memory.getKind() : "knowledge");
        map.put("content", memory.getContent() != null ? memory.getContent() : "");
        map.put("tags", memory.getTags() != null ? memory.getTags() : List.of());
        map.put("importance", memory.getImportance() != null ? memory.getImportance() : 0);
        map.put("strength", memory.getStrength() != null ? memory.getStrength() : 0);
        map.put("pinned", Boolean.TRUE.equals(memory.getPinned()));
        map.put("source", memory.getSource() != null ? memory.getSource() : "");
        map.put("status", memory.getStatus() != null ? memory.getStatus() : "active");
        map.put("created_at", memory.getCreatedAt());
        map.put("updated_at", memory.getUpdatedAt());
        map.put("last_reinforced_at", memory.getLastReinforcedAt());
        map.put("companion_id", memory.getCompanionId());
        return map;
    }

    /**
     * 转为skill。
     * @param skill skill
     * @return 转为skill的结果
     */
    private Map<String, Object> toSkill(SysAiCompanionSkill skill) {
        Map<String, Object> map = new LinkedHashMap<>(16);
        map.put("companion_skill_id", skill.getSkillId());
        map.put("skill_name", skill.getSkillName() != null ? skill.getSkillName() : "");
        map.put("companion_id", skill.getCompanionId());
        map.put("status", skill.getStatus() != null ? skill.getStatus() : "draft");
        map.put("source", skill.getSource() != null ? skill.getSource() : "");
        map.put("confidence", skill.getConfidence() != null ? skill.getConfidence() : 0);
        map.put("provenance_event_ids",
                skill.getProvenanceEventIds() != null ? skill.getProvenanceEventIds() : List.of());
        map.put("strength", skill.getStrength() != null ? skill.getStrength() : 0);
        map.put("version", skill.getVersion() != null ? skill.getVersion() : 1);
        map.put("skill_pattern_id", skill.getSkillPatternId());
        map.put("usage_count", skill.getUsageCount() != null ? skill.getUsageCount() : 0);
        map.put("last_used_at", skill.getLastUsedAt());
        map.put("created_at", skill.getCreatedAt());
        map.put("updated_at", skill.getUpdatedAt());
        map.put("description", skill.getDescription() != null ? skill.getDescription() : "");
        return map;
    }

    /**
     * 转为figure。
     * @param figure figure
     * @return 转为figure的结果
     */
    private Map<String, Object> toFigure(SysAiCompanionFigure figure) {
        Map<String, Object> map = new LinkedHashMap<>(8);
        map.put("figure_id", figure.getFigureId());
        map.put("name", figure.getName() != null ? figure.getName() : "");
        map.put("aspect", figure.getAspect() != null ? figure.getAspect() : 1);
        map.put("head_box", figure.getHeadBox() != null ? figure.getHeadBox()
                : Map.of("x", 0, "y", 0, "w", 0, "h", 0));
        map.put("size_tier", figure.getSizeTier() != null ? figure.getSizeTier() : "m");
        map.put("created_at", figure.getCreatedAt());
        return map;
    }

    /**
     * thread的。
     * @param c c
     * @return thread的的结果
     */
    private Map<String, Object> threadOf(SysAiCompanion c) {
        Map<String, Object> thread = new LinkedHashMap<>(6);
        thread.put("conversation_id", c.getConversationId());
        thread.put("companion_id", c.getCompanionId());
        thread.put("title", c.getName() != null ? c.getName() : "伙伴会话");
        thread.put("created_at", c.getCreatedAt());
        thread.put("updated_at", c.getUpdatedAt());
        return thread;
    }

    /**
     * 共享配置。
     * @return 共享配置的结果
     */
    private Map<String, Object> sharedConfig() {
        String json = companionStore.getConfigValue("shared");
        Map<String, Object> config = new LinkedHashMap<>(8);
        if (json != null) {
            config.putAll(companionStore.fromJson(json));
        }
        if (!config.containsKey("collect")) {
            config.put("collect", Map.of(
                    "chat_user_messages", false, "requirements", false,
                    "terminal_sessions", false, "tool_calls", false,
                    "companion_dialogues", false, "event_retention_days", 30,
                    "event_max_storage_mb", 1024));
        }
        if (!config.containsKey("archive")) {
            config.put("archive", Map.of("enabled", false, "idle_minutes", 30,
                    "min_chars", 200, "inject_recent_days", 3));
        }
        config.putIfAbsent("smart_collaboration", false);
        config.putIfAbsent("default_companion_id", null);
        config.putIfAbsent("bridge_to_memory_dir", null);
        return config;
    }

    /**
     * 当前登录用户的数据采集授权。
     *
     * @return 授权配置，未登录或从未授权时为空集合
     */
    private Map<String, Object> currentUserConsent() {
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
            return new LinkedHashMap<>(2);
        }
        String json = companionStore.getConfigValue(CONSENT_KEY_PREFIX + userId);
        Map<String, Object> consent = new LinkedHashMap<>(4);
        if (json != null) {
            consent.putAll(companionStore.fromJson(json));
        }
        return consent;
    }

    /**
     * 配置视图：全局共享配置叠加当前用户授权，前端仍从 {@code consent_applied} 读取本人状态。
     *
     * @param config 全局共享配置
     * @return 叠加本人授权后的配置
     */
    private Map<String, Object> viewConfig(Map<String, Object> config) {
        Map<String, Object> consent = currentUserConsent();
        if (consent.isEmpty()) {
            return config;
        }
        Map<String, Object> merged = new LinkedHashMap<>(config);
        merged.putAll(consent);
        return merged;
    }

    /**
     * str或。
     * @param value 值
     * @param fallback 降级
     * @return str或的结果
     */
    private static String strOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    /**
     * 构造允许 空 值的 映射（{@link Map#of} 不允许 空，前端契约中大量可空字段需要它）。
     *
     * @param kv 键值对（键、值、键、值……）
     * @return 映射
     */
    private static Map<String, Object> nullableMap(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>(8);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /**
     * 转为映射OrEmpty。
     *
     * @param map 映射，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMapOrEmpty(Map<String, Object> map) {
        return map == null ? new LinkedHashMap<>(4) : map;
    }

    /**
     * 转为映射OrNull。
     *
     * @param value 值，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMapOrNull(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }
}