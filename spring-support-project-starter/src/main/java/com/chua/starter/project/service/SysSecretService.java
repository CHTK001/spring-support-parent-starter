package com.chua.starter.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.entity.project.SysSecret;

import java.util.Set;

/**
 *
 * 系统密钥服务接口，提供对系统密钥的分页查询、删除、更新和保存操作
 * @author CH
 * @since 2024/8/30
 */
public interface SysSecretService extends IService<SysSecret> {

    /**
     * 分页查询系统密钥
     * @param query 查询条件对象，用于分页和排序
     * @param sysSecret 系统密钥实体对象，作为查询条件
     * @param roles 用户角色集合，用于权限控制
     * @return 返回分页查询结果，包含查询到的系统密钥信息
     */
    IPage<SysSecret> pageForSecret(Query<SysSecret> query, SysSecret sysSecret, Set<String> roles);

    /**
     * 删除系统密钥
     * @param sysSecretId 系统密钥的唯一标识
     * @return 返回删除操作的结果，包括是否删除成功的标志
     */
    ReturnResult<Boolean> delete(Long sysSecretId);

    /**
     * 更新系统密钥信息
     * @param roles 用户角色集合，用于权限控制
     * @param sysSecret 待更新的系统密钥实体对象
     * @return 返回更新操作的结果，包括是否更新成功的标志
     */
    ReturnResult<Boolean> update(Set<String> roles, SysSecret sysSecret);

    /**
     * 新增系统密钥信息
     * @param sysSecret 待保存的系统密钥实体对象
     * @param roles 用户角色集合，用于权限控制
     * @return 返回保存操作的结果，包括保存是否成功的标志以及保存的系统部门信息
     */
    ReturnResult<SysSecret> save(SysSecret sysSecret, Set<String> roles);
}
