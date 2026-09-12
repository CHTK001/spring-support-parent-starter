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
      * 角色 映射器
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
                        .eq(SysAiRole::getDelete, DELETE_NOT_DELETED)
                        .orderByAsc(SysAiRole::getSort));
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
                        .eq(SysAiRole::getDelete, DELETE_NOT_DELETED)
                        .eq(SysAiRole::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysAiRole::getSort));
    }

    /**
      * 按 标识 查询角色。
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
                        .eq(SysAiRole::getCode, code)
                        .eq(SysAiRole::getDelete, DELETE_NOT_DELETED)
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
        if (role.getStatus() == null) {
            role.setStatus(STATUS_ENABLED);
        }
        if (role.getDelete() == null) {
            role.setDelete(DELETE_NOT_DELETED);
        }
        if (role.getSort() == null) {
            role.setSort(0);
        }
        sysAiRoleMapper.insert(role);
        log.info("[SysAiRole] 新增角色: id={}, name={}, code={}",
                role.getId(), role.getName(), role.getCode());
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
        if (role == null || role.getId() == null) {
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
 // 使用 mybatis-Plus 内置 删除byid：@table逻辑 逻辑删除（设置 删除=1）
        return sysAiRoleMapper.deleteById(id) > 0;
    }
}