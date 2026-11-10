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
 * @since 4.0.0.51
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
     *
     * <p>读取行数取项目「系统配置 → 日志读取行数」，未配置时为默认 100 行。</p>
     */
    ReturnResult<String> getProjectLog(Long id);

    /**
     * 在目标主机上检索项目日志（后端检索）。
     *
     * <p>关键字按字面匹配，只回传命中行；检索耗时随日志体积增长，
     * 结果里带 {@code elapsedMs} 供前端提示预计时间。</p>
     *
     * @param id        项目ID
     * @param keyword   关键字（字面匹配，非正则）
     * @param maxLines  最多返回的命中行数，为空时用默认上限
     * @param matchCase 是否区分大小写
     * @return 检索结果（命中行 + 耗时 + 是否截断）
     */
    ReturnResult<com.chua.starter.server.support.model.vo.ProjectLogSearchVO> searchProjectLog(
            Long id, String keyword, Integer maxLines, Boolean matchCase);

    /**
     * 开启实时日志（返回 sessionId，日志行经发布管理全局 SSE 推送）
     *
     * @param id       项目ID
     * @param clientId 发布管理 SSE 客户端标识
     * @return sessionId
     */
    ReturnResult<String> startProjectLog(Long id, String clientId);

    /**
     * 停止实时日志
     *
     * @param sessionId 日志会话ID
     */
    ReturnResult<Void> stopProjectLog(String sessionId);

    /**
     * 读取项目的「日志读取行数」配置。
     *
     * @param id 项目ID
     * @return 生效行数（未配置时 100）
     */
    ReturnResult<Integer> getLogLines(Long id);

    /**
     * 保存项目的「日志读取行数」配置。
     *
     * @param id    项目ID
     * @param lines 行数（10 ~ 5000，越界按边界收口）
     * @return 操作结果
     */
    ReturnResult<Void> updateLogLines(Long id, Integer lines);

    /**
     * 读取项目的健康检测配置。
     *
     * @param id 项目ID
     * @return url=已保存的检测地址；defaultUrl=按端口推导的建议地址
     */
    ReturnResult<Map<String, Object>> getProjectHealthCheck(Long id);

    /**
     * 保存项目的健康检测地址与预热期判定开关。
     *
     * @param id          项目ID
     * @param url         检测地址（http/https）；为空时保留原地址
     * @param warmupCheck 启动预热期是否也按严格规则判定；为空时保留原值
     * @return 操作结果
     */
    ReturnResult<Void> saveProjectHealthCheck(Long id, String url, Boolean warmupCheck);

    /**
     * 执行一次健康检测（仅在目标主机上取 HTTP 状态码，200 为通过）。
     *
     * @param id  项目ID
     * @param url 本次检测地址；为空时用已保存的配置
     * @return ok / statusCode / elapsedMs / checkedAt / message
     */
    ReturnResult<Map<String, Object>> checkProjectHealth(Long id, String url);

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
     * 集群批量回滚：按同一份配置依次回滚多个项目（每个项目独立异步任务）。
     *
     * <p>未指定 {@code backupName} 时各项目恢复自己的最新备份；指定后所有项目都按该名字定位备份，
     * 找不到备份的项目单独失败，不影响其它项目。</p>
     *
     * @param projectIds 项目ID列表
     * @param backupName 备份标识，为空恢复各项目最新备份
     * @return 每个项目的触发结果（taskId / 失败原因）
     */
    ReturnResult<List<Map<String, Object>>> batchRollback(List<Long> projectIds, String backupName);

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

    // ==================== 发布管理增强 ====================

    /**
     * 复制项目到其他服务器（发布）：逐行复制项目配置，仅替换 releaseId。
     *
     * @param projectIds 源项目ID列表
     * @param releaseIds 目标发布（服务器）ID列表
     * @return 复制结果：success / failed（含原因）
     */
    ReturnResult<Map<String, Object>> copyProjects(List<Long> projectIds, List<Long> releaseIds);

    /**
     * 探测项目的 Swagger / Actuator 端点（在目标主机内 curl，返回卡片可用的地址与摘要）。
     *
     * @param id 项目ID
     * @return swagger/actuator 探测结果
     */
    ReturnResult<Map<String, Object>> probeProjectEndpoints(Long id);

    /**
     * 读取项目的 Actuator 定时采集配置。
     *
     * <p>配置只是「开关 + 间隔」，真正的采集由全局时间轮统一调度，
     * 所有项目共用一个调度线程与一条发布管理 SSE 长连接。</p>
     *
     * @param id 项目ID
     * @return enabled / intervalSeconds
     */
    ReturnResult<Map<String, Object>> getActuatorMonitor(Long id);

    /**
     * 保存项目的 Actuator 定时采集配置。
     *
     * @param id     项目ID
     * @param config enabled / intervalSeconds
     * @return 保存结果
     */
    ReturnResult<Void> saveActuatorMonitor(Long id, Map<String, Object> config);

    /**
     * 列出所有已开启 Actuator 定时采集的项目配置（全局时间轮读取）。
     *
     * @return 每项含 projectId / intervalSeconds
     */
    List<Map<String, Object>> listActuatorMonitors();

    /**
     * Git 定时轮询：fetch 后比较本地 HEAD 与远端分支，有更新才触发一次发布。
     *
     * @param id 项目ID
     * @return changed=true 表示已触发发布并返回 taskId；false 表示无更新
     */
    ReturnResult<Map<String, Object>> gitPollDeploy(Long id);

    /**
     * 查看项目 Git 仓库（已拉取的本地工作区）。
     *
     * <p>给「Git 仓库」面板用：当前分支、本地/远端提交、工作区改动、最近提交、分支列表。
     * 全部由 {@code utils-support-git-starter} 的 {@code GitClient} 提供，
     * 本模块不直接依赖 jgit。本地还没克隆时返回 {@code cloned=false}，
     * 让界面能区分「没配 Git」与「配了但还没拉过」。</p>
     *
     * @param id 项目ID
     * @return 仓库概览
     */
    ReturnResult<Map<String, Object>> gitRepository(Long id);

    /**
     * 拉取项目 Git 仓库（只更新本地工作区，不构建、不发布）。
     *
     * <p>与 {@link #gitPullAndDeploy(Long)} 的区别：那个是「拉取 + 构建 + 投放 + 重启」的完整发布，
     * 这个是纯同步 —— 用户在仓库面板里只想先看看远端有什么，不该顺手把线上服务重启一遍。</p>
     *
     * @param id     项目ID
     * @param branch 目标分支（空则用项目配置的分支 / 当前分支）
     * @return 拉取结果（updated / message / 当前提交）
     */
    ReturnResult<Map<String, Object>> gitPull(Long id, String branch);

    /**
     * 读取页面布局配置（保存在 ms_server_config，key = ui.layout.{pageKey}）
     *
     * @param pageKey 页面标识
     * @return 布局 JSON（无配置时返回空对象）
     */
    ReturnResult<Map<String, Object>> getPageLayout(String pageKey);

    /**
     * 保存页面布局配置
     *
     * @param pageKey 页面标识
     * @param layout  布局 JSON 字符串
     * @return 操作结果
     */
    ReturnResult<Void> savePageLayout(String pageKey, String layout);
}
