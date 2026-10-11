package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiPromptForm;
import com.chua.starter.ai.support.entity.SysAiRolePrompt;
import com.chua.starter.ai.support.mapper.SysAiRolePromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 角色分形态提示词解析。
 *
 * <p><b>取值链（与角色 model / temperature 的回退语义保持一致：角色没配就沿用全局）</b></p>
 * <pre>
 *   1. sys_ai_role_prompt 中该角色 + 该形态的启用记录      ← 优先级最高
 *   2. sys_ai_role.system_prompt                           ← 历史单条提示词（仅 build）
 *   3. 「设置中心 → 系统提示词」                            ← 全局默认
 *   4. 门面兜底                                             ← AiChatFacadeServiceImpl
 * </pre>
 *
 * <p>第 2 步刻意只在 {@link SysAiPromptForm#FORM_KEY_BUILD} 形态回退：
 * {@code sys_ai_role.system_prompt} 表达的是「这个角色是谁」，而不是「以这种风格
 * 干活时该守什么规矩」。其它形态若也回退它，等于把默认形态的约束复制过去，
 * 恰恰是拆表要解决的问题。</p>
 *
 * <p>形态 key 本身由 {@link SysAiPromptFormService} 管理，可自定义扩展；
 * 本类只认 key，不关心它叫什么。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiRolePromptResolver {

    /**
     * 直接执行形态（内置，恒存）
     */
    public static final String MODE_BUILD = SysAiPromptForm.FORM_KEY_BUILD;

    /**
     * 分形态提示词映射器
     */
    private final SysAiRolePromptMapper sysAiRolePromptMapper;

    /**
     * 解析指定角色在指定形态下应使用的系统提示词。
     *
     * @param role 角色，可为 null（未选角色）
     * @param mode 形态 key，null / 非法 / 空一律按 {@link #MODE_BUILD} 处理
     * @return 命中的提示词正文；全部未命中时返回 null，交给调用方回退
     */
    public String resolve(SysAiRole role, String mode) {
        if (role == null || role.getId() == null) {
            return null;
        }
        String normalizedMode = normalize(mode);

        String fromTable = findContent(role, normalizedMode);
        if (fromTable != null && !fromTable.isBlank()) {
            log.info("[RolePrompt] 角色 {} 命中分形态提示词 mode={}", role.getId(), normalizedMode);
            return fromTable;
        }

        // 仅 build 回退历史列：那一列表达的是角色身份，不是某种风格下的执行约束。
        if (MODE_BUILD.equals(normalizedMode)) {
            String legacy = role.getSystemPrompt();
            if (legacy != null && !legacy.isBlank()) {
                return legacy;
            }
        }
        return null;
    }

    /**
     * 归一化形态 key：空值或非法格式一律落到 build。
     *
     * <p>不能直接回退到 build 之外的任何值——形态 key 会进接口路径和库表查询，
     * 未校验就使用等于把外部输入直接拼进 SQL 条件。</p>
     *
     * @param mode 原始形态 key
     * @return 归一化后的形态 key
     */
    public static String normalize(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_BUILD;
        }
        String trimmed = mode.trim();
        return SysAiPromptFormService.isValidKey(trimmed) ? trimmed : MODE_BUILD;
    }

    /**
     * 查询某角色在某形态下的启用提示词正文。
     *
     * @param role 角色
     * @param mode 形态
     * @return 正文；未配置或已禁用时返回 null
     */
    private String findContent(SysAiRole role, String mode) {
        LambdaQueryWrapper<SysAiRolePrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiRolePrompt::getRoleId, role.getId())
                .eq(SysAiRolePrompt::getPromptMode, mode)
                .eq(SysAiRolePrompt::getStatus, 1)
                .last("LIMIT 1");
        try {
            SysAiRolePrompt prompt = sysAiRolePromptMapper.selectOne(wrapper);
            return prompt == null ? null : prompt.getContent();
        } catch (Exception e) {
            // 表未迁移（V1.3 未执行）时不能让对话整体失败，降级到历史列。
            log.warn("[RolePrompt] 读取角色分形态提示词失败 roleId={} mode={}：{}",
                    role.getId(), mode, e.getMessage());
            return null;
        }
    }
}