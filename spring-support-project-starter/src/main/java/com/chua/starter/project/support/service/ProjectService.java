package com.chua.starter.project.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.entity.MsProject;

import java.util.List;
import java.util.Map;

/**
* 发布项目服务
* <p>
* 提供部署项目台账的增删改查、主机选项查询与状态维护。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
public interface ProjectService {

    /**
    * 新增项目
    *
    * @param project 项目
    * @return 结果
    */
    ReturnResult<MsProject> create(MsProject project);

    /**
    * 更新项目
    *
    * @param id      主键
    * @param project 项目
    * @return 结果
    */
    ReturnResult<MsProject> update(Long id, MsProject project);

    /**
    * 删除项目
    *
    * @param id 主键
    * @return 结果
    */
    ReturnResult<Boolean> delete(Long id);

    /**
    * 项目详情
    *
    * @param id 主键
    * @return 结果
    */
    ReturnResult<MsProject> get(Long id);

    /**
    * 项目列表（卡片展示）
    *
    * @param keyword 关键字（名称/编码）
    * @param type    项目类型，可为空
    * @return 结果
    */
    ReturnResult<List<MsProject>> query(String keyword, String type);

    /**
    * 更新启用状态
    *
    * @param id      主键
    * @param enabled 是否启用
    * @return 结果
    */
    ReturnResult<Boolean> updateEnabled(Long id, Boolean enabled);

    /**
    * 查询可选服务器（按 OS 过滤，供前端选择服务器）
    *
    * @param os 操作系统类型 Linux/窗口，可为空
    * @return 结果（id/name/address/osType）
    */
    ReturnResult<List<Map<String, Object>>> hostOptions(String os);

    /**
    * 清理超期部署日志
    * <p>
    * 按 {@code project.support.deploy-log-retention-days} 保留天数删除
    * {@code ms_project_deploy_log} 中过期的日志记录。
    * </p>
    *
    * @return 删除的记录数
    */
    int cleanupDeployLogs();
}
