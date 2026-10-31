package com.chua.starter.ai.support.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiRolePrompt;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiRolePromptMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 内置角色 plan 提示词初始化器。
 *
 * <p>system prompt 继续由角色本身的 {@code systemPrompt}（即 build）承担，
 * 这里只回填更符合「先规划后执行」语义的 plan 提示词。已存在的用户自定义
 * plan 不会被覆盖；已逻辑删除的行会按唯一键约束复活。</p>
 *
 * @since 4.0.0.56
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinRolePromptSeeder {

    private static final String DEFAULT_PLAN_RESOURCE =
            "characters-builtin/default-role-plan-prompts.json";

    private final SysAiRoleMapper sysAiRoleMapper;
    private final SysAiRolePromptMapper sysAiRolePromptMapper;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动后给已存在的内置角色补齐默认 plan 提示词。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnReady() {
        try {
            seedAll();
        } catch (Exception e) {
            log.warn("[BuiltinRolePrompt] 启动时回填内置角色 plan 提示词失败: {}", e.getMessage());
        }
    }

    /**
     * 所有内置角色按 code 回填缺少的 plan 提示词。
     */
    public void seedAll() {
        Map<String, Map<String, String>> defaults = loadDefaults();
        for (Map.Entry<String, Map<String, String>> entry : defaults.entrySet()) {
            List<SysAiRole> roles = sysAiRoleMapper.selectList(
                    Wrappers.<SysAiRole>lambdaQuery()
                            .eq(SysAiRole::getCode, entry.getKey())
                            .eq(SysAiRole::getDeleted, 0)
                            .last("LIMIT 100"));
            for (SysAiRole role : roles) {
                seedForRole(role);
            }
        }
    }

    /**
     * 为单个角色补齐默认 plan 提示词。
     *
     * @param role 角色
     */
    public void seedForRole(SysAiRole role) {
        if (role == null || role.getId() == null || role.getCode() == null) {
            return;
        }
        String plan = defaultPlan(role.getCode());
        if (plan == null || plan.isBlank()) {
            return;
        }
        upsertPlan(role, plan);
    }

    /**
     * load Defaults
     * @return 返回值
    */
    private Map<String, Map<String, String>> loadDefaults() {
        ClassPathResource resource = new ClassPathResource(DEFAULT_PLAN_RESOURCE);
        try (var in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<Map<String, Map<String, String>>>() {
            });
        } catch (Exception e) {
            log.warn("[BuiltinRolePrompt] 读取默认 plan 提示词失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * default Plan
     * @param code 字符串
     * @return 返回值
    */
    private String defaultPlan(String code) {
        Map<String, Map<String, String>> defaults = loadDefaults();
        Map<String, String> item = defaults.get(code);
        return item == null ? null : item.get("plan");
    }

    /**
     * upsert Plan
     * @param content 字符串
     * @param role 参数
     * @return 返回值
    */
    private void upsertPlan(SysAiRole role, String content) {
        try {
            SysAiRolePrompt existing = sysAiRolePromptMapper.selectIncludingDeleted(
                    role.getId(), "plan", role.getUserId());
            if (existing == null) {
                SysAiRolePrompt prompt = new SysAiRolePrompt();
                prompt.setRoleId(role.getId());
                prompt.setUserId(role.getUserId());
                prompt.setPromptMode("plan");
                prompt.setContent(content);
                prompt.setStatus(1);
                prompt.setDeleted(0);
                sysAiRolePromptMapper.insert(prompt);
                log.info("[BuiltinRolePrompt] 新增角色 plan 提示词 roleId={} code={}",
                        role.getId(), role.getCode());
                return;
            }
            if (existing.getDeleted() != null && existing.getDeleted() == 1) {
                sysAiRolePromptMapper.revive(existing.getId(), content, 1);
                log.info("[BuiltinRolePrompt] 复活角色 plan 提示词 roleId={} code={}",
                        role.getId(), role.getCode());
            }
        } catch (Exception e) {
            log.warn("[BuiltinRolePrompt] 写入角色 plan 提示词失败 roleId={}: {}",
                    role.getId(), e.getMessage());
        }
    }
}