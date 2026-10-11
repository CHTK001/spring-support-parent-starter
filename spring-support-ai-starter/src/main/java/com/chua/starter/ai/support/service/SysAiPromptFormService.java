package com.chua.starter.ai.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiPromptForm;
import com.chua.starter.ai.support.mapper.SysAiPromptFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 提示词形态定义的增删改查。
 *
 * <p><b>唯一强约束：{@code build} 形态恒存。</b>会话在形态缺失时会落到 build，
 * 角色在 build 未配置时还会回退历史主提示词列——这条链一断，提示词就退化成
 * 硬编码英文兜底。所以这里不允许删除 build，也不允许改它的 key / 中文名 / 删除标记。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiPromptFormService {

    /**
     * 形态 key 允许的字符：小写字母、数字、连字符。
     *
     * <p>key 会直接进接口路径（{@code /v2/roles/{id}/prompts/{formKey}}）并作为
     * 会话 runMode 的取值落库，限定字符集可以挡掉路径穿越和空格一类难处理的输入。</p>
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    /**
     * 内置形态所属用户：与 V1.3 迁移里的种子数据一致。
     */
    private static final String BUILTIN_OWNER = "admin";

    /**
     * 形态定义映射器
     */
    private final SysAiPromptFormMapper sysAiPromptFormMapper;

    /**
     * 列出某用户可用的形态：内置形态 + 该用户自定义形态，按 sort、id 排序。
     *
     * <p>内置形态始终返回，即便库里那一行被误删——前端据此渲染切换器，
     * build 缺位会直接表现为「会话里没有形态可切」。</p>
     *
     * @param userId 归属用户；为空时只返回内置形态
     * @return 形态列表，build 一定在第一位
     */
    public List<SysAiPromptForm> listForms(String userId) {
        List<SysAiPromptForm> forms = new ArrayList<>(query(userId, true));
        boolean hasBuild = forms.stream().anyMatch(f -> SysAiPromptForm.FORM_KEY_BUILD.equals(f.getFormKey()));
        if (!hasBuild) {
            SysAiPromptForm fallback = new SysAiPromptForm();
            fallback.setFormKey(SysAiPromptForm.FORM_KEY_BUILD);
            fallback.setFormName("构建");
            fallback.setFormIcon("flash");
            fallback.setFormDescription("直接执行，边做边确认");
            fallback.setBuiltin(1);
            fallback.setSort(1);
            fallback.setStatus(1);
            log.warn("[PromptForm] 用户 {} 的 build 形态缺失，返回内置兜底定义", userId);
            forms.add(fallback);
        }
        forms.sort(Comparator
                .comparing((SysAiPromptForm f) -> f.getSort() == null ? Integer.MAX_VALUE : f.getSort())
                .thenComparing(f -> f.getId() == null ? Integer.MAX_VALUE : f.getId()));
        return forms;
    }

    /**
     * 按 key 取单个形态。
     *
     * @param userId 归属用户
     * @param formKey 形态标识
     * @return 形态；不存在返回 null
     */
    public SysAiPromptForm getForm(String userId, String formKey) {
        if (!isValidKey(formKey)) {
            return null;
        }
        List<SysAiPromptForm> forms = query(userId, false);
        return forms.stream()
                .filter(f -> formKey.equals(f.getFormKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 新增自定义形态。
     *
     * @param userId  归属用户
     * @param formKey 形态标识
     * @param name    中文名
     * @param icon    图标标识
     * @param description 说明
     * @return 新建后的形态；参数非法或 key 重复返回 null
     */
    public SysAiPromptForm createForm(String userId, String formKey, String name,
                                      String icon, String description) {
        if (!isValidKey(formKey) || name == null || name.isBlank()) {
            log.warn("[PromptForm] 新增被拒：参数非法 formKey={} name={}", formKey, name);
            return null;
        }
        if (SysAiPromptForm.FORM_KEY_BUILD.equals(formKey)) {
            log.warn("[PromptForm] build 为内置形态，不允许新增同名形态");
            return null;
        }
        if (getForm(userId, formKey) != null) {
            log.warn("[PromptForm] 形态已存在 userId={} formKey={}", userId, formKey);
            return null;
        }
        String key = formKey.trim();
        String formName = name.trim();
        int sort = nextSort(userId);
        // 软删除的行仍然占着唯一键 (user_id, form_key)，同名再建只能复活，不能插入
        SysAiPromptForm revived = reviveDeleted(userId, key, formName, icon, description, sort);
        if (revived != null) {
            log.info("[PromptForm] 复用已删除形态 userId={} key={} name={}", userId, key, formName);
            return revived;
        }
        SysAiPromptForm form = new SysAiPromptForm();
        form.setUserId(userId);
        form.setFormKey(key);
        form.setFormName(formName);
        form.setFormIcon(icon);
        form.setFormDescription(description);
        form.setBuiltin(0);
        form.setSort(sort);
        form.setStatus(1);
        form.setDeleted(0);
        try {
            if (sysAiPromptFormMapper.insert(form) <= 0) {
                return null;
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时建同名形态：让后来的那个复用先落库的那条
            log.warn("[PromptForm] 形态 key 已被并发占用 userId={} formKey={}", userId, key);
            SysAiPromptForm existing = getForm(userId, key);
            return existing;
        }
        log.info("[PromptForm] 新增形态 userId={} key={} name={}", userId, form.getFormKey(), form.getFormName());
        return form;
    }

    /**
     * 复活一条已逻辑删除的同名形态。
     *
     * @param userId      归属用户
     * @param formKey     形态标识
     * @param name        中文名
     * @param icon        图标标识
     * @param description 说明
     * @param sort        排序
     * @return 复活后的形态；没有可复用的行返回 null
     */
    private SysAiPromptForm reviveDeleted(String userId, String formKey, String name,
                                          String icon, String description, int sort) {
        try {
            SysAiPromptForm dead = sysAiPromptFormMapper.selectIncludingDeleted(userId, formKey);
            if (dead == null || dead.getId() == null) {
                return null;
            }
            if (dead.getDeleted() != null && dead.getDeleted() == 0) {
                // 还在用，不是软删除行
                return null;
            }
            if (sysAiPromptFormMapper.revive(dead.getId(), name, icon, description, sort) <= 0) {
                return null;
            }
            dead.setFormName(name);
            dead.setFormIcon(icon);
            dead.setFormDescription(description);
            dead.setSort(sort);
            dead.setStatus(1);
            dead.setDeleted(0);
            return dead;
        } catch (Exception e) {
            // 表未迁移时不该阻断新增
            log.warn("[PromptForm] 复活已删除形态失败 formKey={}：{}", formKey, e.getMessage());
            return null;
        }
    }

    /**
     * 修改自定义形态的展示信息。
     *
     * <p>刻意<b>不允许改 key</b>：key 已经被角色提示词行和历史会话的 runMode 引用，
     * 改 key 等于让这些引用全部失联。中文名 / 图标 / 描述 / 排序可以随时改。</p>
     *
     * @param userId 归属用户
     * @param formKey 形态标识
     * @param name    新中文名
     * @param icon    新图标
     * @param description 新说明
     * @param sort    新排序
     * @return 是否成功
     */
    public boolean updateForm(String userId, String formKey, String name,
                              String icon, String description, Integer sort) {
        SysAiPromptForm existing = getForm(userId, formKey);
        if (existing == null) {
            return false;
        }
        if (!ownedBy(existing, userId)) {
            log.warn("[PromptForm] 拒绝修改非本人形态 userId={} formKey={}", userId, formKey);
            return false;
        }
        // build 允许改图标/说明/排序（纯展示），但中文名锁定：它是默认形态的对外称呼。
        if (!SysAiPromptForm.FORM_KEY_BUILD.equals(formKey) && (name == null || name.isBlank())) {
            return false;
        }
        if (name != null && !name.isBlank() && !SysAiPromptForm.FORM_KEY_BUILD.equals(formKey)) {
            existing.setFormName(name.trim());
        }
        existing.setFormIcon(icon);
        existing.setFormDescription(description);
        if (sort != null) {
            existing.setSort(sort);
        }
        return sysAiPromptFormMapper.updateById(existing) > 0;
    }

    /**
     * 删除自定义形态。
     *
     * <p>拒绝删除 build；其逻辑删除掉的角色提示词行不在这里处理——那是数据一致性
     * 问题，不该让「删一个形态」这个操作顺带改别的表。</p>
     *
     * @param userId 归属用户
     * @param formKey 形态标识
     * @return 是否成功
     */
    public boolean deleteForm(String userId, String formKey) {
        if (SysAiPromptForm.FORM_KEY_BUILD.equals(formKey)) {
            log.warn("[PromptForm] build 形态恒存，拒绝删除");
            return false;
        }
        SysAiPromptForm existing = getForm(userId, formKey);
        if (existing == null) {
            return true;
        }
        if (!ownedBy(existing, userId)) {
            log.warn("[PromptForm] 拒绝删除非本人形态 userId={} formKey={}", userId, formKey);
            return false;
        }
        return sysAiPromptFormMapper.deleteById(existing.getId()) > 0;
    }

    /**
     * 形态行是否归当前用户所有。
     *
     * <p>{@link #getForm} 的查询会把内置形态一并捞出来（读的时候需要），所以改和删
     * 必须在这里再挡一道，否则任何登录用户都能删掉内置的 plan。</p>
     *
     * @param form   形态行
     * @param userId 当前用户
     * @return 是否本人所有
     */
    private boolean ownedBy(SysAiPromptForm form, String userId) {
        return form.getUserId() != null && form.getUserId().equals(userId);
    }

    /**
     * 校验形态 key 格式。
     *
     * @param formKey 待校验值
     * @return 是否合法
     */
    public static boolean isValidKey(String formKey) {
        return formKey != null && KEY_PATTERN.matcher(formKey.trim()).matches();
    }

    /**
     * 下一个排序值：取现有最大值 +1，保证新形态排在最后。
     *
     * @param userId 归属用户
     * @return 建议排序值
     */
    private int nextSort(String userId) {
        return query(userId, true).stream()
                .map(SysAiPromptForm::getSort)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    /**
     * 查询形态行：内置形态 + 指定用户的自定义形态。
     *
     * @param userId 归属用户
     * @param onlyEnabled 是否只要启用的
     * @return 形态行列表
     */
    private List<SysAiPromptForm> query(String userId, boolean onlyEnabled) {
        LambdaQueryWrapper<SysAiPromptForm> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(SysAiPromptForm::getUserId, BUILTIN_OWNER)
                .or().eq(SysAiPromptForm::getBuiltin, 1));
        if (userId != null && !userId.isBlank()) {
            wrapper.or(o -> o.eq(SysAiPromptForm::getUserId, userId));
        }
        if (onlyEnabled) {
            wrapper.eq(SysAiPromptForm::getStatus, 1);
        }
        try {
            List<SysAiPromptForm> list = sysAiPromptFormMapper.selectList(wrapper);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            // 表未迁移时不能让会话/角色保存整体失败：退化成「只有 build」。
            log.warn("[PromptForm] 读取形态失败（是否已执行 V1.3 迁移？）：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 形态 key 到形态实体的映射，便于前端一次性拉全量。
     *
     * @param userId 归属用户
     * @return key → 形态
     */
    public Map<String, SysAiPromptForm> mapForms(String userId) {
        Map<String, SysAiPromptForm> map = new LinkedHashMap<>();
        for (SysAiPromptForm form : listForms(userId)) {
            map.put(form.getFormKey(), form);
        }
        return map;
    }
}
