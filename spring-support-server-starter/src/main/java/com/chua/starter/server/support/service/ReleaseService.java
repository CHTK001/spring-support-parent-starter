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
     * 生成内置四段脚本（启动/停止/重启/状态）。
     *
     * <p>这是前端「一键生成脚本」与服务端默认模板的<strong>唯一权威来源</strong>：
     * 前端不再自带一份会漂移的模板副本（历史上前端副本缺少 Windows 孤儿回收、
     * 计划任务回退、进程树解析等修复），改为调用本接口取回与执行期完全一致的脚本。</p>
     *
     * @param hostId      目标主机标识（决定 Windows/Linux 模板）
     * @param projectType 项目类型
     * @param mainEntry   主入口
     * @param projectPath 项目地址
     * @param logPath     日志地址
     * @param appName     应用名
     * @param envVars     环境变量 JSON（可为空）
     * @param jvmArgs     JVM 参数 JSON（可为空）
     * @param cmdArgs     命令参数 JSON（可为空）
     * @return startScript/stopScript/restartScript/statusScript 四段脚本
     */
    ReturnResult<Map<String, String>> generateScripts(Long hostId, String projectType, String mainEntry,
            String projectPath, String logPath, String appName,
            String envVars, String jvmArgs, String cmdArgs);

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

    /**
     * 列出主机上已安装的软件（同步执行，返回已安装包列表；依赖包管理器 supported 命令）
     *
     * @param hostId 主机ID
     */
    ReturnResult<List<SoftwarePackage>> listInstalledSoftware(Long hostId);

    /**
     * 卸载软件（异步，返回 taskId，实时进度经 socket 推送）
     *
     * @param hostId 主机ID
     * @param name 软件包名
     */
    ReturnResult<String> uninstallSoftware(Long hostId, String name);

    /**
     * 升级软件到仓库最新版（异步，返回 taskId，实时进度经 socket 推送）
     *
     * @param hostId 主机ID
     * @param name 软件包名
     */
    ReturnResult<String> upgradeSoftware(Long hostId, String name);

    /**
     * 列出有可用更新的软件（同步执行，供前端决定是否显示「升级」按钮）
     *
     * @param hostId 主机ID
     * @return 可升级条目（当前版本 + 候选版本）；包管理器不支持时返回空列表
     */
    ReturnResult<List<com.chua.starter.server.support.model.vo.SoftwareUpgradeVO>> listUpgradableSoftware(Long hostId);

    /**
     * 查询某个软件包的依赖关系（同步执行）
     *
     * @param hostId 主机ID
     * @param name   软件包名
     * @return 依赖包名列表；包管理器不支持时返回空列表
     */
    ReturnResult<List<String>> softwareDependencies(Long hostId, String name);

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
     *
     * @param projectId 项目ID；非空时额外返回该项目的默认升级配置（{@code project}）
     * @return global / last / project 三份配置
     */
    ReturnResult<Map<String, Object>> getUpgradeConfig(Long projectId);

    /**
     * 保存升级配置（scope=global 全局默认 / last 最近一次手动配置 / project 项目默认）
     *
     * @param scope     配置作用域
     * @param projectId 项目ID，scope=project 时必填
     * @param config    升级配置
     * @return 操作结果
     */
    ReturnResult<Void> saveUpgradeConfig(String scope, Long projectId, Map<String, Object> config);

    /**
     * 集群批量升级：按同一份配置依次升级多个项目（每个项目独立异步任务）
     *
     * @param projectIds 项目ID列表
     * @param config     升级配置
     * @return 每个项目的触发结果（taskId / 失败原因）
     */
    ReturnResult<List<Map<String, Object>>> batchUpgrade(List<Long> projectIds, Map<String, Object> config);

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
     * @param id         项目ID
     * @param file       安装包（jar / tar.gz / zip）
     * @param deployType 部署类型覆盖（main / lib / both，空则用项目默认）
     * @param autoRestart 是否重启，null 用配置默认
     * @return 异步任务标识（taskId）
     */
    ReturnResult<String> uploadAndUpgrade(Long id,
                                          org.springframework.web.multipart.MultipartFile file,
                                          String deployType, Boolean autoRestart);

    /**
     * Git 自动发布：从项目配置的 Git 仓库拉取代码 → Maven 构建 → SFTP 上传产物 → 升级重启。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>克隆或拉取 Git 仓库到本地临时工作区</li>
     *   <li>执行 Maven 构建（默认 clean,package，跳过测试）</li>
     *   <li>定位构建产物（jar/war）</li>
     *   <li>SFTP 上传到远程主机 uploads 目录</li>
     *   <li>调用 runUpgrade 执行备份+投放+重启</li>
     * </ol>
     *
     * @param id 项目ID
     * @return 异步任务标识（taskId）
     */
    ReturnResult<String> gitPullAndDeploy(Long id);

    /**
     * 统计分离式项目 lib 目录中的 jar 数量
     *
     * @param id 项目ID
     * @return jar 文件数量
     */
    ReturnResult<Integer> countLibFiles(Long id);
}
