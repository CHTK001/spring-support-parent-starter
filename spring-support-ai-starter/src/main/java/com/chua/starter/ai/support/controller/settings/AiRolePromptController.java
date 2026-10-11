package com.chua.starter.ai.support.controller.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiPromptForm;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiRolePrompt;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiRolePromptMapper;
import com.chua.starter.ai.support.service.SysAiPromptFormService;
import com.chua.starter.ai.support.service.SysAiRolePromptResolver;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词形态与角色分形态提示词控制器。
 *
 * <p>两块职责：<b>形态定义</b>（可自定义增删改，build 恒存）与
 * <b>角色 × 形态的提示词正文</b>。取值优先级与运行时一致：
 * 角色分形态表 &gt; 角色主提示词（仅 build）&gt; 全局默认，详见
 * {@link SysAiRolePromptResolver}。</p>
 *
 * @author CH
 */
@Slf4j
@Tag(name = "AI 设置 - 提示词形态与角色分形态提示词")
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class AiRolePromptController {

    /**
     * 角色映射器
     */
    private final SysAiRoleMapper sysAiRoleMapper;

    /**
     * 分形态提示词映射器
     */
    private final SysAiRolePromptMapper sysAiRolePromptMapper;

    /**
     * 分形态提示词解析器（决定实际生效内容）
     */
    private final SysAiRolePromptResolver sysAiRolePromptResolver;

    /**
     * 形态定义服务
     */
    private final SysAiPromptFormService sysAiPromptFormService;

    // ==================== 形态定义 ====================

    /**
     * 查询当前用户可用的全部形态。
     *
     * <p>会话里的形态切换器直接渲染这个列表，因此响应里必须带上中文名、图标和
     * 说明——切换器上的每一项都是这四个字段。</p>
     *
     * @return 形态列表，build 恒在且排第一
     */
    @Operation(summary = "查询提示词形态列表")
    @GetMapping("/prompt-forms")
    public List<SysAiPromptForm> listForms() {
        return sysAiPromptFormService.listForms(currentUserId());
    }

    /**
     * 新增自定义形态。
     *
     * @param body 字段 {@code formKey}(必填) / {@code formName}(必填) / {@code formIcon} / {@code formDescription}
     * @return 新建后的形态；失败返回 null
     */
    @Operation(summary = "新增提示词形态")
    @Permission("admin")
    @PostMapping("/prompt-forms")
    public SysAiPromptForm createForm(@RequestBody Map<String, Object> body) {
        return sysAiPromptFormService.createForm(currentUserId(),
                str(body.get("formKey")), str(body.get("formName")),
                str(body.get("formIcon")), str(body.get("formDescription")));
    }

    /**
     * 修改形态的展示信息。
     *
     * <p>不含 key —— key 已被角色提示词行与历史会话引用，改了会让它们失联。</p>
     *
     * @param formKey 形态标识
     * @param body    字段 {@code formName} / {@code formIcon} / {@code formDescription} / {@code formSort}
     * @return 是否成功
     */
    @Operation(summary = "修改提示词形态")
    @Permission("admin")
    @PutMapping("/prompt-forms/{formKey}")
    public boolean updateForm(@Parameter(description = "形态标识") @PathVariable String formKey,
                              @RequestBody Map<String, Object> body) {
        Object sort = body.get("formSort");
        return sysAiPromptFormService.updateForm(currentUserId(), formKey,
                str(body.get("formName")), str(body.get("formIcon")),
                str(body.get("formDescription")),
                sort == null ? null : Integer.valueOf(String.valueOf(sort)));
    }

    /**
     * 删除自定义形态（build 拒绝删除）。
     *
     * @param formKey 形态标识
     * @return 是否成功
     */
    @Operation(summary = "删除提示词形态")
    @Permission("admin")
    @DeleteMapping("/prompt-forms/{formKey}")
    public boolean deleteForm(@Parameter(description = "形态标识") @PathVariable String formKey) {
        return sysAiPromptFormService.deleteForm(currentUserId(), formKey);
    }

    // ==================== 角色 × 形态提示词 ====================

    /**
     * 查询某角色在<b>全部形态</b>下的生效提示词。
     *
     * <p>形态来自 {@link #listForms()} 而不是写死两两，因此用户新增第三种形态后，
     * 角色编辑器会立刻多出一栏，无需后端再改。</p>
     *
     * <p>{@code content} 取自 {@link SysAiRolePromptResolver#resolve}，即会话里真正
     * 会用的那份，而不是仅回显本表——避免前端显示「配了 A」但运行时用 B。
     * {@code fromTable} 标出是否命中本表，编辑器据此显示「继承角色默认」。</p>
     *
     * @param id 角色主键
     * @return 角色ID + 形态 key → 生效配置的映射
     */
    @Operation(summary = "查询角色在各形态下的生效提示词")
    @GetMapping("/roles/{id}/prompts")
    public Map<String, Object> listPrompts(@Parameter(description = "角色主键") @PathVariable Integer id) {
        SysAiRole role = sysAiRoleMapper.selectById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        if (role == null) {
            result.put("error", "角色不存在");
            return result;
        }
        Map<String, Object> modes = new LinkedHashMap<>();
        for (SysAiPromptForm form : sysAiPromptFormService.listForms(currentUserId())) {
            SysAiRolePrompt own = findOwn(id, form.getFormKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fromTable", own != null);
            item.put("content", sysAiRolePromptResolver.resolve(role, form.getFormKey()));
            item.put("enabled", own == null || (own.getStatus() != null && own.getStatus() == 1));
            modes.put(form.getFormKey(), item);
        }
        result.put("roleId", id);
        result.put("modes", modes);
        return result;
    }

    /**
     * 新增或更新某角色某形态的提示词。
     *
     * <p>按 {@code (role_id, prompt_mode, user_id)} 唯一键 upsert，重复提交同一形态即覆盖，
     * 不会堆出多行。形态 key 必须是已存在的形态，否则会写出指向虚空的提示词行。</p>
     *
     * @param id      角色主键
     * @param formKey 形态标识
     * @param body    字段 {@code content}（可空表示清空本表配置）、{@code enabled}
     * @return 是否成功
     */
    @Operation(summary = "保存角色某形态的提示词")
    @Permission("admin")
    @PutMapping("/roles/{id}/prompts/{formKey}")
    public boolean savePrompt(@Parameter(description = "角色主键") @PathVariable Integer id,
                              @Parameter(description = "形态标识") @PathVariable String formKey,
                              @RequestBody Map<String, Object> body) {
        if (sysAiPromptFormService.getForm(currentUserId(), formKey) == null) {
            log.warn("[RolePrompt] 形态不存在，拒绝写入 roleId={} formKey={}", id, formKey);
            return false;
        }
        SysAiRole role = sysAiRoleMapper.selectById(id);
        if (role == null) {
            return false;
        }

        String content = str(body.get("content"));
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));

        SysAiRolePrompt existing = findOwn(id, formKey);
        if (existing == null) {
            // 正文为空且原本也没配置：不建行，保持「未配置 → 继承角色默认」。
            if (content == null || content.isBlank()) {
                return true;
            }
            // 删过一次又重新配置：唯一键不含删除标记，只能复活原行
            if (reviveDeleted(id, formKey, role.getUserId(), content, enabled ? 1 : 0)) {
                return true;
            }
            SysAiRolePrompt prompt = new SysAiRolePrompt();
            prompt.setRoleId(id);
            prompt.setUserId(role.getUserId());
            prompt.setPromptMode(formKey);
            prompt.setContent(content);
            prompt.setStatus(enabled ? 1 : 0);
            prompt.setDeleted(0);
            return insertQuietly(prompt);
        }

        if (content != null && !content.isBlank()) {
            existing.setContent(content);
        }
        existing.setStatus(enabled ? 1 : 0);
        return updateQuietly(existing);
    }

    /**
     * 删除某角色某形态的提示词配置（回退到继承角色默认）。
     *
     * @param id      角色主键
     * @param formKey 形态标识
     * @return 是否成功
     */
    @Operation(summary = "删除角色某形态提示词，回退到继承角色默认")
    @Permission("admin")
    @DeleteMapping("/roles/{id}/prompts/{formKey}")
    public boolean deletePrompt(@Parameter(description = "角色主键") @PathVariable Integer id,
                                @Parameter(description = "形态标识") @PathVariable String formKey) {
        SysAiRolePrompt existing = findOwn(id, formKey);
        if (existing == null) {
            return true;
        }
        try {
            return sysAiRolePromptMapper.deleteById(existing.getId()) > 0;
        } catch (Exception e) {
            log.error("[RolePrompt] 删除失败 roleId={} formKey={}：{}", id, formKey, e.getMessage());
            return false;
        }
    }

    // ==================== 内部 ====================

    /**
     * 查询本表中该角色该形态的配置行。
     *
     * @param roleId 角色主键
     * @param formKey 形态标识
     * @return 配置行；未配置返回 null
     */
    private SysAiRolePrompt findOwn(Integer roleId, String formKey) {
        if (roleId == null || formKey == null || formKey.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<SysAiRolePrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiRolePrompt::getRoleId, roleId)
                .eq(SysAiRolePrompt::getPromptMode, formKey)
                .last("LIMIT 1");
        try {
            return sysAiRolePromptMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("[RolePrompt] 读取失败 roleId={} formKey={}：{}", roleId, formKey, e.getMessage());
            return null;
        }
    }

    /**
     * 复活一条已逻辑删除的「角色 × 形态」提示词行。
     *
     * <p>删除是逻辑删除，而唯一键 {@code (role_id, prompt_mode, user_id)} 不含删除标记列，
     * 所以「清空提示词 → 再填一次」这个最常见的操作会撞唯一键。复活原行而不是新插，
     * 也顺带保住了这一行的主键与历史。</p>
     *
     * @param roleId  角色主键
     * @param formKey 形态标识
     * @param userId  归属用户
     * @param content 正文
     * @param status  状态
     * @return 是否复活了某一行
     */
    private boolean reviveDeleted(Integer roleId, String formKey, String userId, String content, int status) {
        try {
            SysAiRolePrompt dead = sysAiRolePromptMapper.selectIncludingDeleted(roleId, formKey, userId);
            if (dead == null || dead.getId() == null) {
                return false;
            }
            if (dead.getDeleted() != null && dead.getDeleted() == 0) {
                return false;
            }
            return sysAiRolePromptMapper.revive(dead.getId(), content, status) > 0;
        } catch (Exception e) {
            log.warn("[RolePrompt] 复活已删除配置失败 roleId={} formKey={}：{}", roleId, formKey, e.getMessage());
            return false;
        }
    }

    /**
     * 插入并在异常时降级为 false。
     *
     * <p>迁移 V1.3 尚未执行时表不存在，这里会抛 SQL 异常；写路径不能把异常
     * 冒泡成 500 堆栈——读路径已降级为「继承角色默认」，写路径保持一致地返回
     * false，让前端提示保存失败即可。</p>
     *
     * @param prompt 待插入记录
     * @return 是否插入成功
     */
    private boolean insertQuietly(SysAiRolePrompt prompt) {
        try {
            return sysAiRolePromptMapper.insert(prompt) > 0;
        } catch (Exception e) {
            log.error("[RolePrompt] 写入失败 roleId={} formKey={}：{}（是否已执行 V1.3 迁移？）",
                    prompt.getRoleId(), prompt.getPromptMode(), e.getMessage());
            return false;
        }
    }

    /**
     * 更新并在异常时降级为 false，降级理由同 {@link #insertQuietly}。
     *
     * @param prompt 待更新记录
     * @return 是否更新成功
     */
    private boolean updateQuietly(SysAiRolePrompt prompt) {
        try {
            return sysAiRolePromptMapper.updateById(prompt) > 0;
        } catch (Exception e) {
            log.error("[RolePrompt] 更新失败 roleId={} formKey={}：{}（是否已执行 V1.3 迁移？）",
                    prompt.getRoleId(), prompt.getPromptMode(), e.getMessage());
            return false;
        }
    }

    /**
     * 当前登录用户标识。
     *
     * @return 用户标识；取不到返回 null（形态查询会退化为「仅内置形态」）
     */
    private String currentUserId() {
        try {
            return AiUserContext.currentUserId();
        } catch (Exception e) {
            log.debug("[RolePrompt] 取当前用户失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 安全取字符串。
     *
     * @param v 任意值
     * @return 字符串；null 返回 null
     */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
