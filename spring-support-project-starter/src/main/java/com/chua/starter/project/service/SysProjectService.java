package com.chua.starter.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.project.pojo.project.ProjectDefaultFunctionV1Request;

import java.util.Set;

/**
 * 系统项目服务
 *
 * @author CH
 */
public interface SysProjectService extends IService<SysProject> {

    /**
     * 分页查询系统项目
     *
     * @param query      查询条件对象，用于分页和排序
     * @param sysProject 系统项目实体对象，作为查询条件
     * @param roles      用户角色集合，用于权限控制
     * @return 返回分页查询结果，包含查询到的系统项目信息
     */
    IPage<SysProject> pageFor(Query<SysProject> query, SysProject sysProject, Set<String> roles);

    /**
     * 根据项目ID获取系统项目信息
     *
     * @param sysProjectId 项目ID
     * @return 返回根据项目ID获取的系统项目信息
     */
    SysProject getOne(Integer sysProjectId);
    /**
     * 获取默认的系统项目
     *
     * @param typeName 项目类型名称
     * @return 返回默认的系统项目信息
     */
    SysProject getDefaultByType(String typeName);
    /**
     * 删除系统项目
     *
     * @param sysProjectId 系统项目的唯一标识
     * @return 返回删除操作的结果，包括是否删除成功的标志
     */
    ReturnResult<Boolean> delete(Integer sysProjectId);

    /**
     * 更新系统项目信息
     *
     * @param roles      用户角色集合，用于权限控制
     * @param sysProject 待更新的系统项目实体对象
     * @return 返回更新操作的结果，包括是否更新成功的标志
     */
    ReturnResult<Boolean> update(Set<String> roles, SysProject sysProject);
    /**
     * 更新系统项目信息
     *
     * @param request 待更新的系统项目实体对象
     * @return 返回更新操作的结果，包括是否更新成功的标志
     */
    ReturnResult<SysProject> updateDefault(ProjectDefaultFunctionV1Request request);
    /**
     * 新增系统项目信息
     *
     * @param sysProject 待保存的系统项目实体对象
     * @param roles      用户角色集合，用于权限控制
     * @return 返回保存操作的结果，包括保存是否成功的标志以及保存的系统部门信息
     */
    ReturnResult<SysProject> save(SysProject sysProject, Set<String> roles);


}
