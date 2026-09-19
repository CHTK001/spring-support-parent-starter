package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.List;

/**
 * 发布管理服务
 *
 * @author CH
 */
public interface ReleaseService {

    /**
     * 获取发布列表
     */
    ReturnResult<List<MsServerRelease>> listReleases();

    /**
     * 创建发布
     */
    ReturnResult<MsServerRelease> createRelease(MsServerRelease release);

    /**
     * 更新发布
     */
    ReturnResult<MsServerRelease> updateRelease(Long id, MsServerRelease release);

    /**
     * 删除发布
     */
    ReturnResult<Void> deleteRelease(Long id);

    /**
     * 获取项目列表
     */
    ReturnResult<List<MsServerProject>> listProjects(Long releaseId);

    /**
     * 创建项目
     */
    ReturnResult<MsServerProject> createProject(MsServerProject project);

    /**
     * 更新项目
     */
    ReturnResult<MsServerProject> updateProject(Long id, MsServerProject project);

    /**
     * 删除项目
     */
    ReturnResult<Void> deleteProject(Long id);

    /**
     * 启动项目（异步，返回 taskId，进度经 socket 推送）
     */
    ReturnResult<String> startProject(Long id);

    /**
     * 停止项目（异步，返回 taskId）
     */
    ReturnResult<String> stopProject(Long id);

    /**
     * 重启项目（异步，返回 taskId）
     */
    ReturnResult<String> restartProject(Long id);

    /**
     * 升级项目（异步，返回 taskId）
     */
    ReturnResult<String> upgradeProject(Long id);

    /**
     * 备份项目（异步，返回 taskId）
     */
    ReturnResult<String> backupProject(Long id);

    /**
     * 回滚项目（异步，返回 taskId）
     */
    ReturnResult<String> rollbackProject(Long id);

    /**
     * 获取项目日志（一次性 tail，用于打开时的初始内容）
     */
    ReturnResult<String> getProjectLog(Long id);

    /**
     * 开启实时日志（返回 sessionId，日志行经 socket 推送）
     *
     * @param id 项目ID
     * @return sessionId
     */
    ReturnResult<String> startProjectLog(Long id);

    /**
     * 停止实时日志
     *
     * @param sessionId 日志会话ID
     */
    ReturnResult<Void> stopProjectLog(String sessionId);

    /**
     * 搜索软件包
     *
     * @param hostId 主机ID
     * @param keyword 关键词
     */
    ReturnResult<List<SoftwarePackage>> searchSoftware(Long hostId, String keyword);

    /**
     * 安装软件（异步，返回 taskId，实时进度经 socket 推送）
     *
     * @param hostId 主机ID
     * @param name 软件包名
     */
    ReturnResult<String> installSoftware(Long hostId, String name);
}
