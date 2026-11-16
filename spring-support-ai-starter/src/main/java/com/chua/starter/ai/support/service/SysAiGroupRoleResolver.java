package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.settings.SysAiGroup;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.settings.AiGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 分组默认角色解析。
 *
 * <p><b>取值层级（合并后的角色解析链）</b></p>
 * <pre>
 *   1. sys_ai_group.sys_ai_group_default_role_id 指向的角色   ← 分组级默认角色
 *   2. sys_ai_role_prompt 中该角色 + 该形态的启用记录（交给 {@link SysAiRolePromptResolver}）
 *   3. sys_ai_role.system_prompt（同上，仅 build）
 *   4. 「设置中心 → 系统提示词」                              ← 全局默认（调用方兜底）
 * </pre>
 *
 * <p>第 1 步只决定「用哪个角色」；具体形态下出哪段提示词仍是
 * {@link SysAiRolePromptResolver} 的职责，两级各管一件事，不重复实现回退。</p>
 *
 * <p>分组不存在 / 已停用 / 未设默认角色 / 角色已停用时一律返回 {@code null}，
 * 调用方按「没配就用全局」处理 —— 与分组模型清单「没配就不限制」同一口径。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiGroupRoleResolver {

    /**
     * 分组映射器（设置中心侧，承载 sys_ai_group_default_role_id）
     */
    private final AiGroupMapper aiGroupMapper;

    /**
     * 角色映射器
     */
    private final SysAiRoleMapper sysAiRoleMapper;

    /**
     * 分形态提示词解析
     */
    private final SysAiRolePromptResolver sysAiRolePromptResolver;

    /**
     * 解析分组默认角色。
     *
     * @param groupId 分组主键，可为 null
     * @return 分组默认角色；未配置 / 已停用时返回 {@code null}
     */
    public SysAiRole resolve(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        try {
            SysAiGroup group = aiGroupMapper.selectById(groupId);
            if (group == null || Boolean.FALSE.equals(group.getGroupEnabled())) {
                return null;
            }
            Long roleId = group.getDefaultRoleId();
            if (roleId == null || roleId <= 0) {
                return null;
            }
            SysAiRole role = sysAiRoleMapper.selectById(roleId.intValue());
            if (role == null || (role.getStatus() != null && role.getStatus() != 1)) {
                return null;
            }
            return role;
        } catch (Exception e) {
            // 表未补列（V6.11 未执行）时不能让对话整体失败，降级为「没配分组角色」。
            log.warn("[GroupRole] 解析分组默认角色失败 groupId={}：{}", groupId, e.getMessage());
            return null;
        }
    }

    /**
     * 解析分组默认角色在指定形态下的系统提示词。
     *
     * @param groupId 分组主键，可为 null
     * @param mode    形态 key，null / 非法按 build 处理
     * @return 提示词正文；整条链都没命中时返回 {@code null}
     */
    public String systemPrompt(Integer groupId, String mode) {
        SysAiRole role = resolve(groupId);
        if (role == null) {
            return null;
        }
        String prompt = sysAiRolePromptResolver.resolve(role, mode);
        if (prompt != null && !prompt.isBlank()) {
            log.debug("[GroupRole] 命中分组默认角色 groupId={} roleId={} mode={}",
                    groupId, role.getId(), SysAiRolePromptResolver.normalize(mode));
        }
        return prompt;
    }
}
