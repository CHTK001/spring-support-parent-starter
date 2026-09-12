package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiCompanionFigureMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiCompanionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiCompanionMemoryMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiCompanionSharedConfigMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiCompanionSkillMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;

/**
* 桌面伙伴（Companion）持久化存储服务。
*
* <p>基于 MyBatis-Plus 提供伙伴、记忆、技能、形象、共享配置的 CRUD 与 JSON 序列化，
* 数据持久化到 MySQL（companions/内存/skills/figures/共享_配置 五张表）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Service
public class SysAiCompanionStore {

    /**
    * 伙伴表 映射器
     */
    private final SysAiCompanionMapper companionMapper;

    /**
    * 记忆表 映射器
     */
    private final SysAiCompanionMemoryMapper memoryMapper;

    /**
    * 技能表 映射器
     */
    private final SysAiCompanionSkillMapper skillMapper;

    /**
    * 形象表 映射器
     */
    private final SysAiCompanionFigureMapper figureMapper;

    /**
    * 共享配置表 映射器
     */
    private final SysAiCompanionSharedConfigMapper configMapper;

    /**
    * JSON 序列化器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
    * 构造注入；启动时自动补列。
     */
    public SysAiCompanionStore(SysAiCompanionMapper companionMapper,
                               SysAiCompanionMemoryMapper memoryMapper,
                               SysAiCompanionSkillMapper skillMapper,
                               SysAiCompanionFigureMapper figureMapper,
                               SysAiCompanionSharedConfigMapper configMapper,
                               DataSource dataSource) {
        this.companionMapper = companionMapper;
        this.memoryMapper = memoryMapper;
        this.skillMapper = skillMapper;
        this.figureMapper = figureMapper;
        this.configMapper = configMapper;
        ensureTables(dataSource);
    }

    /**
    * 启动时确保五张表存在（幂等 创建 TABLE IF NOT EXISTS）。
    * @param dataSource 数据源
     */
    private void ensureTables(DataSource dataSource) {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS `sys_ai_companion` ("
                + "`sys_ai_companion_id` VARCHAR(36) NOT NULL,"
                + "`sys_ai_companion_seq` INT NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_name` VARCHAR(128) NULL,"
                + "`sys_ai_companion_character` VARCHAR(64) NULL,"
                + "`sys_ai_companion_persona_text` TEXT NULL,"
                + "`sys_ai_companion_model_text` TEXT NULL,"
                + "`sys_ai_companion_voice_text` TEXT NULL,"
                + "`sys_ai_companion_learn_text` TEXT NULL,"
                + "`sys_ai_companion_evolve_text` TEXT NULL,"
                + "`sys_ai_companion_skills_text` TEXT NULL,"
                + "`sys_ai_companion_appearance_text` TEXT NULL,"
                + "`sys_ai_companion_applied_preset_text` TEXT NULL,"
                + "`sys_ai_companion_fallback_model_text` TEXT NULL,"
                + "`sys_ai_companion_vision_model_text` TEXT NULL,"
                + "`sys_ai_companion_order_index` INT NULL,"
                + "`sys_ai_companion_conversation_id` VARCHAR(36) NULL,"
                + "`sys_ai_companion_created_at` BIGINT NOT NULL,"
                + "`sys_ai_companion_updated_at` BIGINT NOT NULL,"
                + "PRIMARY KEY (`sys_ai_companion_id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS `sys_ai_companion_memory` ("
                + "`sys_ai_companion_memory_id` VARCHAR(36) NOT NULL,"
                + "`sys_ai_companion_memory_companion_id` VARCHAR(36) NULL,"
                + "`sys_ai_companion_memory_kind` VARCHAR(32) NOT NULL DEFAULT 'knowledge',"
                + "`sys_ai_companion_memory_content` TEXT NULL,"
                + "`sys_ai_companion_memory_tags` TEXT NULL,"
                + "`sys_ai_companion_memory_importance` DOUBLE NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_memory_strength` DOUBLE NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_memory_pinned` TINYINT NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_memory_source` VARCHAR(64) NULL,"
                + "`sys_ai_companion_memory_status` VARCHAR(16) NOT NULL DEFAULT 'active',"
                + "`sys_ai_companion_memory_created_at` BIGINT NOT NULL,"
                + "`sys_ai_companion_memory_updated_at` BIGINT NOT NULL,"
                + "`sys_ai_companion_memory_last_reinforced_at` BIGINT NULL,"
                + "PRIMARY KEY (`sys_ai_companion_memory_id`),"
                + "KEY `idx_ai_companion_memory_owner` (`sys_ai_companion_memory_companion_id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS `sys_ai_companion_skill` ("
                + "`sys_ai_companion_skill_id` VARCHAR(36) NOT NULL,"
                + "`sys_ai_companion_skill_companion_id` VARCHAR(36) NULL,"
                + "`sys_ai_companion_skill_name` VARCHAR(128) NULL,"
                + "`sys_ai_companion_skill_status` VARCHAR(16) NOT NULL DEFAULT 'draft',"
                + "`sys_ai_companion_skill_source` VARCHAR(64) NULL,"
                + "`sys_ai_companion_skill_confidence` DOUBLE NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_skill_provenance_event_ids` TEXT NULL,"
                + "`sys_ai_companion_skill_strength` DOUBLE NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_skill_version` INT NOT NULL DEFAULT 1,"
                + "`sys_ai_companion_skill_skill_pattern_id` VARCHAR(36) NULL,"
                + "`sys_ai_companion_skill_usage_count` INT NOT NULL DEFAULT 0,"
                + "`sys_ai_companion_skill_last_used_at` BIGINT NULL,"
                + "`sys_ai_companion_skill_created_at` BIGINT NOT NULL,"
                + "`sys_ai_companion_skill_updated_at` BIGINT NOT NULL,"
                + "`sys_ai_companion_skill_description` TEXT NULL,"
                + "`sys_ai_companion_skill_content` TEXT NULL,"
                + "PRIMARY KEY (`sys_ai_companion_skill_id`),"
                + "KEY `idx_ai_companion_skill_owner` (`sys_ai_companion_skill_companion_id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS `sys_ai_companion_figure` ("
                + "`sys_ai_companion_figure_id` VARCHAR(36) NOT NULL,"
                + "`sys_ai_companion_figure_name` VARCHAR(128) NULL,"
                + "`sys_ai_companion_figure_aspect` DOUBLE NOT NULL DEFAULT 1,"
                + "`sys_ai_companion_figure_head_box` TEXT NULL,"
                + "`sys_ai_companion_figure_size_tier` VARCHAR(4) NOT NULL DEFAULT 'm',"
                + "`sys_ai_companion_figure_source_path` VARCHAR(512) NULL,"
                + "`sys_ai_companion_figure_created_at` BIGINT NOT NULL,"
                + "PRIMARY KEY (`sys_ai_companion_figure_id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            "CREATE TABLE IF NOT EXISTS `sys_ai_companion_shared_config` ("
                + "`sys_ai_companion_shared_config_key` VARCHAR(64) NOT NULL,"
                + "`sys_ai_companion_shared_config_value` TEXT NULL,"
                + "`sys_ai_companion_shared_config_updated_at` BIGINT NOT NULL,"
                + "PRIMARY KEY (`sys_ai_companion_shared_config_key`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        };
        try (Connection conn = dataSource.getConnection()) {
            for (String sql : ddl) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
            log.info("[CompanionStore] companion 相关表已就绪");
        } catch (Exception e) {
            log.warn("[CompanionStore] 建表检查失败（非致命）: {}", e.getMessage());
        }
    }

    // ==================== JSON 序列化辅助 ====================

    /**
    * 生成 uuidv7 字符串（36 位）。
    *
    * @return UUIDv7
     */
    public String newId() {
        return SysAiConversationStore.generateUuidV7();
    }

    /**
    * 将对象序列化为 JSON 字符串（空 返回 空）。
    * @param value 值
    * @return 转为json的结果
     */
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + e.getMessage());
        }
    }

    /**
    * 将 JSON 字符串解析为 映射（空/空返回空 映射）。
    * @param json json
    * @return 从json的结果
     */
    public Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    // ==================== 伙伴 CRUD ====================

    /**
    * 创建companion。
    * @param companion companion
    * @return 创建companion的结果
     */
    public SysAiCompanion createCompanion(SysAiCompanion companion) {
        if (companion.getSeq() == null) {
            Long maxSeq = companionMapper.selectCount(new LambdaQueryWrapper<>());
            companion.setSeq(maxSeq == null ? 1 : maxSeq.intValue() + 1);
        }
        long now = System.currentTimeMillis();
        if (companion.getCreatedAt() == null) {
            companion.setCreatedAt(now);
        }
        companion.setUpdatedAt(now);
        companionMapper.insert(companion);
        return companion;
    }

    /**
    * 获取companion。
    * @param companionId companionid
    * @return 获取companion的结果
     */
    public SysAiCompanion getCompanion(String companionId) {
        SysAiCompanion companion = companionMapper.selectById(companionId);
        if (companion == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "伙伴不存在: " + companionId);
        }
        return companion;
    }

    /**
    * 列表companions。
    * @return 列表companions的结果
     */
    public List<SysAiCompanion> listCompanions() {
        return companionMapper.selectList(
                new LambdaQueryWrapper<SysAiCompanion>()
                        .orderByAsc(SysAiCompanion::getSeq));
    }

    /**
    * PATCH 伙伴（RFC 7396 简化：非 空 顶层字段覆盖，映射 字段合并）。
    *
    * @param companionId 伙伴 ID
    * @param patch       补丁
     */
    public SysAiCompanion patchCompanion(String companionId, Map<String, Object> patch) {
        SysAiCompanion current = getCompanion(companionId);
        applyPatch(current, patch);
        current.setUpdatedAt(System.currentTimeMillis());
        companionMapper.updateById(current);
        return current;
    }

    public void deleteCompanion(String companionId) {
        getCompanion(companionId);
        companionMapper.deleteById(companionId);
        // 级联清理记忆与技能
        memoryMapper.delete(new LambdaQueryWrapper<SysAiCompanionMemory>()
                .eq(SysAiCompanionMemory::getCompanionId, companionId));
        skillMapper.delete(new LambdaQueryWrapper<SysAiCompanionSkill>()
                .eq(SysAiCompanionSkill::getCompanionId, companionId));
    }

    /**
    * 将 RFC 7396 patch 的顶层键应用到实体（含 映射 字段的 JSON 字段合并语义）。
    * @author CH
    * @since 4.0.0
    * @param target Target
    * @param patch patch
     */
    private void applyPatch(SysAiCompanion target, Map<String, Object> patch) {
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            Object value = e.getValue();
            switch (e.getKey()) {
                case "name" -> target.setName(str(value));
                case "character" -> target.setCharacter(str(value));
                case "seq" -> target.setSeq(num(value));
                case "order_index" -> target.setOrderIndex(num(value));
                case "persona" -> mergeMap(target::getPersona, target::setPersona, value);
                case "model" -> mergeMap(target::getModel, target::setModel, value);
                case "fallback_model" -> mergeMap(target::getFallbackModel, target::setFallbackModel, value);
                case "vision_model" -> mergeMap(target::getVisionModel, target::setVisionModel, value);
                case "voice" -> mergeMap(target::getVoice, target::setVoice, value);
                case "learn" -> mergeMap(target::getLearn, target::setLearn, value);
                case "evolve" -> mergeMap(target::getEvolve, target::setEvolve, value);
                case "skills" -> mergeMap(target::getSkills, target::setSkills, value);
                case "appearance" -> mergeMap(target::getAppearance, target::setAppearance, value);
                case "applied_preset" -> mergeMap(target::getAppliedPreset, target::setAppliedPreset, value);
                case "companion_id", "created_at", "updated_at" -> { /* 只读字段忽略 */ }
                default -> { /* 未知字段忽略 */ }
            }
        }
    }

    @FunctionalInterface
    private interface Getter { Map<String, Object> get(); }

    @FunctionalInterface
    private interface Setter { void set(Map<String, Object> v); }

    /**
    * 映射 字段合并：空 patch 值清除；否则在现有值上合并 patch 键。
    *
    * @param getter     现有值 getter
    * @param setter     写回 setter
    * @param patchValue 补丁值
     */
    private void mergeMap(Getter getter, Setter setter, Object patchValue) {
        if (patchValue == null) {
            setter.set(null);
            return;
        }
        Map<String, Object> patch = toMap(patchValue);
        Map<String, Object> current = getter.get();
        if (current == null) {
            current = new HashMap<>();
        }
        current.putAll(patch);
        setter.set(current);
    /**
    * str。
    * @param value 值
    * @return str的结果
     */
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    /**
    * num。
    * @param value 值
    * @return num的结果
     */
    }

    private static Integer num(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new HashMap<>();
    }

    // ==================== 记忆 CRUD ====================

    /**
    * 列表内存。
    * @param companionId companionid
    * @param limit 限制
    * @param offset 偏移量
    * @return 列表内存的结果
     */
    public List<SysAiCompanionMemory> listMemories(String companionId, Integer limit, Integer offset) {
        LambdaQueryWrapper<SysAiCompanionMemory> wrapper = new LambdaQueryWrapper<SysAiCompanionMemory>()
                .eq(companionId != null && !companionId.isBlank(), SysAiCompanionMemory::getCompanionId, companionId)
                .orderByDesc(SysAiCompanionMemory::getCreatedAt);
        if (offset != null && offset > 0) {
            wrapper.last("LIMIT " + (limit == null || limit <= 0 ? 200 : limit) + " OFFSET " + offset);
        }
        return memoryMapper.selectList(wrapper);
    }

    /**
    * 数量内存。
    * @param companionId companionid
    * @return 数量内存的结果
     */
    public long countMemories(String companionId) {
        return memoryMapper.selectCount(
                new LambdaQueryWrapper<SysAiCompanionMemory>()
                        .eq(SysAiCompanionMemory::getCompanionId, companionId)
                        .eq(SysAiCompanionMemory::getStatus, "active"));
    }

    /**
    * 设置companionconversationid。
    * @param companionId companionid
    * @param conversationId conversationid
     */
    public void setCompanionConversationId(String companionId, String conversationId) {
        SysAiCompanion companion = getCompanion(companionId);
        companion.setConversationId(conversationId);
        companion.setUpdatedAt(System.currentTimeMillis());
        companionMapper.updateById(companion);
    }

    /**
    * 创建内存。
    * @param memory 内存
    * @return 创建内存的结果
     */
    public SysAiCompanionMemory createMemory(SysAiCompanionMemory memory) {
        long now = System.currentTimeMillis();
        if (memory.getCreatedAt() == null) {
            memory.setCreatedAt(now);
        }
        memory.setUpdatedAt(now);
        if (memory.getLastReinforcedAt() == null) {
            memory.setLastReinforcedAt(now);
        }
        if (memory.getImportance() == null) {
            memory.setImportance(0D);
        }
        if (memory.getStrength() == null) {
            memory.setStrength(0D);
        }
        if (memory.getPinned() == null) {
            memory.setPinned(false);
        }
        if (memory.getStatus() == null) {
            memory.setStatus("active");
        }
        memoryMapper.insert(memory);
        return memory;
    }

    /**
    * 获取内存。
    * @param memoryId 内存标识
    * @return 获取内存的结果
     */
    public SysAiCompanionMemory getMemory(String memoryId) {
        SysAiCompanionMemory memory = memoryMapper.selectById(memoryId);
        if (memory == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "记忆不存在: " + memoryId);
        }
        return memory;
    }

    public SysAiCompanionMemory updateMemory(String memoryId, String companionId,
                                             String content, Boolean pinned, String status) {
        SysAiCompanionMemory memory = getMemory(memoryId);
        // owner 不匹配拒绝（强约束：记忆严格按伙伴归属）
        if (companionId != null && !companionId.isBlank()
                && memory.getCompanionId() != null && !memory.getCompanionId().equals(companionId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "记忆不存在: " + memoryId);
        }
        if (content != null) {
            memory.setContent(content);
        }
        if (pinned != null) {
            memory.setPinned(pinned);
        }
        if (status != null) {
            memory.setStatus(status);
        }
        memory.setUpdatedAt(System.currentTimeMillis());
        memoryMapper.updateById(memory);
        return memory;
    }

    /**
    * 删除内存。
    * @param memoryId 内存标识
    * @param companionId companionid
     */
    public void deleteMemory(String memoryId, String companionId) {
        SysAiCompanionMemory memory = getMemory(memoryId);
        if (companionId != null && !companionId.isBlank()
                && memory.getCompanionId() != null && !memory.getCompanionId().equals(companionId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "记忆不存在: " + memoryId);
        }
        memoryMapper.deleteById(memoryId);
    }

    /**
    * 查重合并建议：扫描该伙伴的 活跃 记忆，按归一化内容分组，
    * 返回包含两条以上疑似重复记忆的分组。
    *
    * <p>归一化规则：去除首尾空白、折叠内部空白（含换行/全角空格）、去除
    * 常见标点后做全文比对；完全一致或仅标点/空白差异视为疑似重复。</p>
    *
    * @param companionId 伙伴 标识
    * @return 疑似重复分组（每组 memories ≥ 2）
     */
    public List<List<SysAiCompanionMemory>> suggestMemoryMerges(String companionId) {
        LambdaQueryWrapper<SysAiCompanionMemory> wrapper = new LambdaQueryWrapper<SysAiCompanionMemory>()
                .eq(SysAiCompanionMemory::getCompanionId, companionId)
                .eq(SysAiCompanionMemory::getStatus, "active");
        List<SysAiCompanionMemory> active = memoryMapper.selectList(wrapper);
        // key(归一化内容) → 记忆列表
        Map<String, List<SysAiCompanionMemory>> byNormal = new java.util.LinkedHashMap<>();
        for (SysAiCompanionMemory memory : active) {
            String content = memory.getContent() != null ? memory.getContent() : "";
            if (content.isBlank()) {
                continue;
            }
            String norm = normalizeMemoryText(content);
            if (norm.isEmpty()) {
                continue;
            }
            byNormal.computeIfAbsent(norm, k -> new ArrayList<>()).add(memory);
        }
        List<List<SysAiCompanionMemory>> result = new ArrayList<>();
        for (List<SysAiCompanionMemory> group : byNormal.values()) {
            if (group.size() >= 2) {
                result.add(group);
            }
        }
        // 按组内最早时间升序，保证输出稳定
        result.sort((a, b) -> {
            long at = a.get(0).getCreatedAt() == null ? 0 : a.get(0).getCreatedAt();
            long bt = b.get(0).getCreatedAt() == null ? 0 : b.get(0).getCreatedAt();
            return Long.compare(at, bt);
        });
        return result;
    }

    /**
    * 归一化记忆文本：折叠空白（含全角空格/换行）→ 转小写 → 去常见标点。
    * @param text 文本
    * @return normalize内存文本的结果
     */
    private String normalizeMemoryText(String text) {
        String collapsed = text.replaceAll("\\s+", "").replace("\u3000", "");
        String lower = collapsed.toLowerCase();
        // 去常见中英文标点（保留 CJK 与字母数字）
        return lower.replaceAll("[\\p{Punct}\\p{P}\\p{S}，。！？；：、“”‘’（）《》【】…—·]", "");
    }

    /**
    * 创建skill。
    * @param skill skill
    * @return 创建skill的结果
     */
    public SysAiCompanionSkill createSkill(SysAiCompanionSkill skill) {
        long now = System.currentTimeMillis();
        if (skill.getCreatedAt() == null) {
            skill.setCreatedAt(now);
        }
        skill.setUpdatedAt(now);
        if (skill.getConfidence() == null) {
            skill.setConfidence(0D);
        }
        if (skill.getStrength() == null) {
            skill.setStrength(0D);
        }
        if (skill.getVersion() == null) {
            skill.setVersion(1);
        }
        if (skill.getUsageCount() == null) {
            skill.setUsageCount(0);
        }
        if (skill.getStatus() == null) {
            skill.setStatus("draft");
        }
        skillMapper.insert(skill);
        return skill;
    }

    /**
    * 获取skill。
    * @param skillId skillid
    * @return 获取skill的结果
     */
    public SysAiCompanionSkill getSkill(String skillId) {
        SysAiCompanionSkill skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillId);
        }
        return skill;
    }

    /**
    * 列表skills。
    * @param companionId companionid
    * @param limit 限制
    * @param offset 偏移量
    * @return 列表skills的结果
     */
    public List<SysAiCompanionSkill> listSkills(String companionId, Integer limit, Integer offset) {
        LambdaQueryWrapper<SysAiCompanionSkill> wrapper = new LambdaQueryWrapper<SysAiCompanionSkill>()
                .eq(companionId != null && !companionId.isBlank(), SysAiCompanionSkill::getCompanionId, companionId)
                .orderByDesc(SysAiCompanionSkill::getCreatedAt);
        if (offset != null && offset > 0) {
            wrapper.last("LIMIT " + (limit == null || limit <= 0 ? 200 : limit) + " OFFSET " + offset);
        }
        return skillMapper.selectList(wrapper);
    }

    public SysAiCompanionSkill updateSkill(String skillId, String companionId,
                                           String content, String status) {
        SysAiCompanionSkill skill = getSkill(skillId);
        if (companionId != null && !companionId.isBlank()
                && skill.getCompanionId() != null && !skill.getCompanionId().equals(companionId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillId);
        }
        if (content != null) {
            skill.setContent(content);
        }
        if (status != null) {
            skill.setStatus(status);
        }
        skill.setUpdatedAt(System.currentTimeMillis());
        skillMapper.updateById(skill);
        return skill;
    }

    /**
    * 删除skill。
    * @param skillId skillid
    * @param companionId companionid
     */
    public void deleteSkill(String skillId, String companionId) {
        SysAiCompanionSkill skill = getSkill(skillId);
        if (companionId != null && !companionId.isBlank()
                && skill.getCompanionId() != null && !skill.getCompanionId().equals(companionId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "技能不存在: " + skillId);
        }
        skillMapper.deleteById(skillId);
    }

    // ==================== 形象 CRUD ====================

    /**
    * 创建figure。
    * @param figure figure
    * @return 创建figure的结果
     */
    public SysAiCompanionFigure createFigure(SysAiCompanionFigure figure) {
        if (figure.getCreatedAt() == null) {
            figure.setCreatedAt(System.currentTimeMillis());
        }
        if (figure.getAspect() == null) {
            figure.setAspect(1D);
        }
        if (figure.getSizeTier() == null) {
            figure.setSizeTier("m");
        }
        figureMapper.insert(figure);
        return figure;
    }

    /**
    * 列表figures。
    * @return 列表figures的结果
     */
    public List<SysAiCompanionFigure> listFigures() {
        return figureMapper.selectList(
                new LambdaQueryWrapper<SysAiCompanionFigure>().orderByDesc(SysAiCompanionFigure::getCreatedAt));
    }

    /**
    * 获取figure。
    * @param figureId figureid
    * @return 获取figure的结果
     */
    public SysAiCompanionFigure getFigure(String figureId) {
        SysAiCompanionFigure figure = figureMapper.selectById(figureId);
        if (figure == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "形象不存在: " + figureId);
        }
        return figure;
    }

    public SysAiCompanionFigure updateFigure(String figureId, String name,
                                             Map<String, Object> headBox, String sizeTier) {
        SysAiCompanionFigure figure = getFigure(figureId);
        if (name != null) {
            figure.setName(name);
        }
        if (headBox != null) {
            figure.setHeadBox(headBox);
        }
        if (sizeTier != null) {
            figure.setSizeTier(sizeTier);
        }
        figureMapper.updateById(figure);
        return figure;
    }

    /**
    * 删除figure。
    * @param figureId figureid
     */
    public void deleteFigure(String figureId) {
        getFigure(figureId);
        figureMapper.deleteById(figureId);
    }

    // ==================== 共享配置 KV ====================

    /**
    * 获取配置值。
    * @param key 键
    * @return 获取配置值的结果
     */
    public String getConfigValue(String key) {
        SysAiCompanionSharedConfig config = configMapper.selectById(key);
        return config == null ? null : config.getConfigValue();
    }

    /**
    * 设置配置值。
    * @param key 键
    * @param jsonValue json值
     */
    public void setConfigValue(String key, String jsonValue) {
        SysAiCompanionSharedConfig config = configMapper.selectById(key);
        if (config == null) {
            config = new SysAiCompanionSharedConfig();
            config.setConfigKey(key);
            config.setConfigValue(jsonValue);
            config.setUpdatedAt(System.currentTimeMillis());
            configMapper.insert(config);
        } else {
            config.setConfigValue(jsonValue);
            config.setUpdatedAt(System.currentTimeMillis());
            configMapper.updateById(config);
        }
    }
}