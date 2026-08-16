package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.service.SysAiRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 角色（人设）服务实现。
 *
 * @author CH
 * @since 4.0.0.42
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
     * 角色 Mapper
     */
    private final SysAiRoleMapper sysAiRoleMapper;

    /**
     * 查询所有未删除的角色列表，按排序值升序。
     *
     * @return 角色列表
     */
    @Override
    public List<SysAiRole> list() {
        return sysAiRoleMapper.selectList(
                Wrappers.lambdaQuery(SysAiRole.class)
                        .eq(SysAiRole::getSysAiRoleDelete, DELETE_NOT_DELETED)
                        .orderByAsc(SysAiRole::getSysAiRoleSort));
    }

    /**
     * 查询所有启用状态的角色列表，按排序值升序。
     *
     * @return 启用角色列表
     */
    @Override
    public List<SysAiRole> listEnabled() {
        return sysAiRoleMapper.selectList(
                Wrappers.lambdaQuery(SysAiRole.class)
                        .eq(SysAiRole::getSysAiRoleDelete, DELETE_NOT_DELETED)
                        .eq(SysAiRole::getSysAiRoleStatus, STATUS_ENABLED)
                        .orderByAsc(SysAiRole::getSysAiRoleSort));
    }

    /**
     * 按 ID 查询角色。
     *
     * @param id 角色主键
     * @return 角色实体
     */
    @Override
    public SysAiRole getById(Integer id) {
        if (id == null) {
            return null;
        }
        return sysAiRoleMapper.selectById(id);
    }

    /**
     * 按编码查询角色。
     *
     * @param code 角色编码
     * @return 角色实体
     */
    @Override
    public SysAiRole getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return sysAiRoleMapper.selectOne(
                Wrappers.lambdaQuery(SysAiRole.class)
                        .eq(SysAiRole::getSysAiRoleCode, code)
                        .eq(SysAiRole::getSysAiRoleDelete, DELETE_NOT_DELETED)
                        .last("LIMIT 1"));
    }

    /**
     * 新增角色。
     *
     * @param role 角色实体
     * @return 保存后的角色
     */
    @Override
    public SysAiRole save(SysAiRole role) {
        if (role.getSysAiRoleStatus() == null) {
            role.setSysAiRoleStatus(STATUS_ENABLED);
        }
        if (role.getSysAiRoleDelete() == null) {
            role.setSysAiRoleDelete(DELETE_NOT_DELETED);
        }
        if (role.getSysAiRoleSort() == null) {
            role.setSysAiRoleSort(0);
        }
        sysAiRoleMapper.insert(role);
        log.info("[SysAiRole] 新增角色: id={}, name={}, code={}",
                role.getSysAiRoleId(), role.getSysAiRoleName(), role.getSysAiRoleCode());
        return role;
    }

    /**
     * 更新角色。
     *
     * @param role 角色实体
     * @return 是否成功
     */
    @Override
    public boolean update(SysAiRole role) {
        if (role == null || role.getSysAiRoleId() == null) {
            return false;
        }
        return sysAiRoleMapper.updateById(role) > 0;
    }

    /**
     * 逻辑删除角色。
     *
     * @param id 角色主键
     * @return 是否成功
     */
    @Override
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
        // 使用 MyBatis-Plus 内置 deleteById：@TableLogic 逻辑删除（SET delete=1）
        return sysAiRoleMapper.deleteById(id) > 0;
    }
}