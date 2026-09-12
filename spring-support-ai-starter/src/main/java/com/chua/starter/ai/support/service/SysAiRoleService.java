package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiRole;

import java.util.List;

/**
* AI 角色（人设）服务接口。
*
* <p>提供角色（人设）的增删改查与启用停用能力。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface SysAiRoleService {

    /**
    * 查询所有未删除的角色列表。
    *
    * @return 角色列表
     */
    List<SysAiRole> list();

    /**
    * 查询所有启用状态的角色列表。
    *
    * @return 启用角色列表
     */
    List<SysAiRole> listEnabled();

    /**
    * 按 标识 查询角色。
    *
    * @param id 角色主键
    * @return 角色实体
     */
    SysAiRole getById(Integer id);

    /**
    * 按编码查询角色。
    *
    * @param code 角色编码
    * @return 角色实体
     */
    SysAiRole getByCode(String code);

    /**
    * 新增角色。
    *
    * @param role 角色实体
    * @return 保存后的角色
     */
    SysAiRole save(SysAiRole role);

    /**
    * 更新角色。
    *
    * @param role 角色实体
    * @return 是否成功
     */
    boolean update(SysAiRole role);

    /**
    * 逻辑删除角色。
    *
    * @param id 角色主键
    * @return 是否成功
     */
    boolean deleteById(Integer id);
}