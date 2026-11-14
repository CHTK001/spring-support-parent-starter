// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.service.SysAiRoleService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * AI 角色（人设）服务实现。
 *
 * <p>角色按当前登录用户隔离：所有查询、写入、更新与删除均限定在当前用户范围内，
 * 账号间互不可见。内置角色模板（classpath 目录）不经过本表，为全局共享。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiRoleServiceImpl implements SysAiRoleService {

    /**
     * 启用状态值：1
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 未删除标记值：0
     */
    private static final int DELETE_NOT_DELETED = 0;

    /**
     * 通用助手编码：code=general-assistant，系统内置，
     * 不允许删除、不允许改 code（前端同样约束，接口层兜底）。
     *
     * <p>只认 code、不认主键：种子角色（三国角色库等）自增主键同样会占用小 id，
     * 按 id 判定会把普通角色误认成通用助手。</p>
     */
    public static final String GENERAL_ASSISTANT_CODE = "general-assistant";

    /**
     * 角色 映射器
     */
    private final SysAiRoleMapper sysAiRoleMapper;

    /**
     * 查询当前用户所有未删除的角色列表，按排序值升序。
     *
     * <p>排除 world-sim 来源：世界沙盒 AI 生成的模拟 NPC 复用了本表，但不是
     * 用户维护的角色，不应出现在「角色管理」。列 NOT NULL 默认 user，直接用
     * ne 即可，无需额外判 NULL。</p>
     *
     * @return 角色列表
     */
    @Override
    public List<SysAiRole> list() {
        String userId = AiUserContext.currentUserId();
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyList();
        }
        return sysAiRoleMapper.selectList(
                Wrappers.lambdaQuery(SysAiRole.class)
                        .eq(SysAiRole::getUserId, userId)
                        .eq(SysAiRole::getDeleted, DELETE_NOT_DELETED)
                        .ne(SysAiRole::getSource, SysAiRole.SOURCE_WORLD_SIM)
                        .orderByAsc(SysAiRole::getSort));
    }

    /**
     * 查询当前用户所有启用状态的角色列表，按排序值升序。
     *
     * <p>与 list() 同口径：排除世界沙盒模拟 NPC，避免「角色管理看不到、
     * 但启用角色下拉里还能选到」的漏网。</p>
     *
     * @return 启用角色列表
     */
    @Override
    public List<SysAiRole> listEnabled() {
        String userId = AiUserContext.currentUserId();
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyList();
        }
        return sysAiRoleMapper.selectList(
                Wrappers.lambdaQuery(SysAiRole.class)
                        .eq(SysAiRole::getUserId, userId)
                        .eq(SysAiRole::getDeleted, DELETE_NOT_DELETED)
                        .eq(SysAiRole::getStatus, STATUS_ENABLED)
                        .ne(SysAiRole::getSource, SysAiRole.SOURCE_WORLD_SIM)
                        .orderByAsc(SysAiRole::getSort));
    }

    /**
     * 按 标识 查询当前用户的角色。
     *
     * @param id 角色主键
     * @return 角色实体；不属于当前用户时返回 {@code null}
     */
    @Override
    public SysAiRole getById(Integer id) {
        if (id == null) {
            return null;
        }
        SysAiRole role = sysAiRoleMapper.selectById(id);
        return owns(role) ? role : null;
    }

    /**
     * 按编码查询当前用户的角色。
     *
     * @param code 角色编码
     * @return 角色实体
     */
    @Override
    public SysAiRole getByCode(String code) {
        String userId = AiUserContext.currentUserId();
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(code)) {
            return null;
        }
        return sysAiRoleMapper.selectOne(
                Wrappers.lambdaQuery(SysAiRole.class)
                        .eq(SysAiRole::getUserId, userId)
                        .eq(SysAiRole::getCode, code)
                        .eq(SysAiRole::getDeleted, DELETE_NOT_DELETED)
                        .last("LIMIT 1"));
    }

    /**
     * 新增当前用户的角色。
     *
     * @param role 角色实体
     * @return 保存后的角色
     */
    @Override
    public SysAiRole save(SysAiRole role) {
        String userId = AiUserContext.requireUserId();
        role.setUserId(userId);
        if (role.getStatus() == null) {
            role.setStatus(STATUS_ENABLED);
        }
        if (role.getDeleted() == null) {
            role.setDeleted(DELETE_NOT_DELETED);
        }
        if (role.getSort() == null) {
            role.setSort(0);
        }
        // 自定义新建角色未提供 code 时，生成当前用户下唯一 code，避免命中唯一约束
        if (StringUtils.isBlank(role.getCode())) {
            role.setCode(generateUniqueCode(userId));
        }
        sysAiRoleMapper.insert(role);
        log.info("[SysAiRole] 新增角色: userId={}, id={}, name={}, code={}",
                userId, role.getId(), role.getName(), role.getCode());
        return role;
    }

    /**
     * 生成不与当前用户现有角色冲突的唯一编码。
     *
     * @param userId 当前用户
     * @return 唯一角色编码
     */
    private String generateUniqueCode(String userId) {
        for (int i = 0; i < 10; i++) {
            String code = "role-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            Long count = sysAiRoleMapper.selectCount(
                    Wrappers.lambdaQuery(SysAiRole.class)
                            .eq(SysAiRole::getUserId, userId)
                            .eq(SysAiRole::getCode, code));
            if (count == null || count == 0L) {
                return code;
            }
        }
        return "role-" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 更新当前用户的角色。
     *
     * @param role 角色实体
     * @return 是否成功
     */
    @Override
    public boolean update(SysAiRole role) {
        if (role == null || role.getId() == null) {
            return false;
        }
        SysAiRole existing = sysAiRoleMapper.selectById(role.getId());
        if (!owns(existing)) {
            return false;
        }
        // 归属不可变更，强制锁定为当前用户
        role.setUserId(existing.getUserId());
        return sysAiRoleMapper.updateById(role) > 0;
    }

    /**
     * 逻辑删除当前用户的角色。
     *
     * <p>2026-10-14 新增通用助手保护：code=general-assistant（「通用助手」）
     * 是系统内置角色，不允许删除（前端同样隐藏删除按钮，但接口必须自己兜底，
     * 防止绕过前端直接调 DELETE）。返回 false，前端按删除失败提示。</p>
     *
     * <p>2026-11-14 改为只按 code 判定：此前按固定主键 1 判定，一旦种子角色
     * 占用了 id=1（三国角色库「刘备」），该角色会被误当通用助手、永远删不掉。</p>
     *
     * @param id 角色主键
     * @return 是否成功
     */
    @Override
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
        SysAiRole existing = sysAiRoleMapper.selectById(id);
        if (!owns(existing)) {
            return false;
        }
        if (GENERAL_ASSISTANT_CODE.equals(existing.getCode())) {
            log.warn("[Role] 拒绝删除通用助手（code=general-assistant，系统内置）");
            return false;
        }
        // 使用 mybatis-Plus 内置 deleteById：@TableLogic 逻辑删除（设置 删除=1）
        return sysAiRoleMapper.deleteById(id) > 0;
    }

    /**
     * 判断角色是否属于当前登录用户。
     *
     * @param role 待校验角色
     * @return 归属当前用户返回 {@code true}
     */
    private boolean owns(SysAiRole role) {
        if (role == null) {
            return false;
        }
        String userId = AiUserContext.currentUserId();
        return StringUtils.isNotBlank(userId) && userId.equals(role.getUserId());
    }
}