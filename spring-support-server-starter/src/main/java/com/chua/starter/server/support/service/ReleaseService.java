package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.List;
import java.util.Map;

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
     * 回滚项目（异步，返回 taskId）。backupName 为空时恢复最新备份，否则恢复指定备份
     */
    ReturnResult<String> rollbackProject(Long id, String backupName);

    /**
     * 列出项目 backups/ 目录下的备份记录（名称 / 时间 / 大小），用于编辑页展示
     */
    ReturnResult<List<com.chua.starter.server.support.model.vo.ProjectBackupVO>> listBackups(Long id);

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

    // ==================== 状态检测 ====================

    /**
     * 查询项目运行状态（同步执行状态脚本，返回输出）
     */
    ReturnResult<String> statusProject(Long id);

    // ==================== 系统服务挂载 ====================

    /**
     * 将项目挂载为系统服务（Linux systemd / Windows 计划任务），按 autostart/guardian 配置启用开机自启与守护（异步，返回 taskId）
     */
    ReturnResult<String> installService(Long id);

    /**
     * 卸载系统服务（停止 / 禁用 / 删除服务与守护任务，清空 serviceName）（异步，返回 taskId）
     */
    ReturnResult<String> uninstallService(Long id);

    // ==================== 升级配置 ====================

    /**
     * 读取升级配置：global 全局默认 + last 最近一次手动配置
     */
    ReturnResult<Map<String, Object>> getUpgradeConfig();

    /**
     * 保存升级配置（scope=global 全局默认 / last 最近一次手动配置）
     */
    ReturnResult<Void> saveUpgradeConfig(String scope, Map<String, Object> config);

    /**
     * 按给定升级配置执行升级（备份 / 投放包 / 解压 / 回滚 / 重启，按开关组合）（异步，返回 taskId）
     *
     * @param id     项目ID
     * @param config 升级配置
     */
    ReturnResult<String> runUpgrade(Long id, Map<String, Object> config);

    /**
     * 拖拽升级：上传安装包到目标主机 uploads 目录，按全局升级配置执行升级（异步，返回 taskId）
     *
     * @param id   项目ID
     * @param file 安装包（jar / tar.gz / zip）
     */
    ReturnResult<String> uploadAndUpgrade(Long id,
                                          org.springframework.web.multipart.MultipartFile file);
}
