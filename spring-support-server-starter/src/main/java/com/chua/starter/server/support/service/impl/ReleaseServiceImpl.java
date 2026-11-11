// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.placeholder.MapMixSystemPlaceholderResolver;
import com.chua.common.support.lang.placeholder.PlaceholderSupport;
import com.chua.common.support.lang.placeholder.StringValuePropertyResolver;
import com.chua.git.support.GitClient;
import com.chua.maven.support.MavenClient;
import com.chua.maven.support.MavenCompileResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerConfig;
import com.chua.starter.server.support.entity.MsExecLog;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.SoftwarePackage;
import com.chua.starter.server.support.mapper.MsServerConfigMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.mapper.MsServerReleaseMapper;
import com.chua.starter.server.support.model.vo.ProjectBackupVO;
import com.chua.starter.server.support.model.vo.SoftwareUpgradeVO;
import com.chua.starter.server.support.push.ProjectTaskService;
import com.chua.starter.server.support.push.ProjectTaskService.ProjectStep;
import com.chua.starter.server.support.service.ExecService;
import com.chua.starter.server.support.service.ProjectOpLogService;
import com.chua.starter.server.support.service.ReleaseService;
import com.chua.starter.server.support.service.SftpService;
import com.chua.starter.server.support.spi.PackageManagerProvider;
import com.chua.starter.server.support.spi.PackageManagerRouter;
import com.chua.starter.server.support.spi.ProjectPlatformProvider;
import com.chua.starter.server.support.spi.ProjectPlatformProviderRegistry;
import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.spi.ProjectTypeProviderRegistry;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.spi.SystemServiceProvider;
import com.chua.starter.server.support.spi.SystemServiceProviderRegistry;
import com.chua.starter.server.support.util.DockerProjectSettings;
import com.chua.starter.server.support.util.HostPlatforms;
import com.chua.starter.server.support.util.ProjectScripts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
   * 发布管理服务实现
   *
   * @author CH
   * @since 4.0.0.51
   */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReleaseServiceImpl implements ReleaseService {

    private final MsServerReleaseMapper releaseMapper;
    private final MsServerProjectMapper projectMapper;
    private final MsServerHostMapper hostMapper;
    private final ExecService execService;
    private final ProjectTaskService projectTaskService;
      /**
       * 项目操作日志（同步的增删改/复制直接记终态）
       */
    private final ProjectOpLogService projectOpLogService;

      /**
       * 备份保留数量：每次备份后只保留最新 N 个，清理更旧的，防止备份无限增长撑满磁盘
       */
    private static final int MAX_BACKUPS = 10;

      /**
       * 启动参数里的业务端口：{@code --server.port=8112}
       */
    private static final Pattern SERVER_PORT_ARG = Pattern.compile("--server\\.port=(\\d{1,5})");

      /**
       * 外部配置里的业务端口：{@code server.port=8112}
       */
    private static final Pattern SERVER_PORT_PROP = Pattern.compile("server\\.port\\s*=\\s*(\\d{1,5})");
      /**
       * SFTP 服务（Git 构建产物上传等，Linux）
       */
    private final SftpService sftpService;
      /**
       * 软件仓库包管理器路由器（按操作系统/实际包管理器选择 SPI 实现）
       */
    private final PackageManagerRouter packageManagerRouter;

      /**
       * 系统服务挂载路由器（按操作系统选择 SPI 实现：systemd / launchd / 计划任务）
       */
    private final SystemServiceProviderRegistry systemServiceRouter;

      /**
       * 平台命令路由器（按操作系统选择 SPI 实现：POSIX / Windows）。
       * 备份/回滚/执行包装/变量块/外部配置/解压投放等平台命令一律经它取得，
       * 业务层不再出现 tar/robocopy/powershell 等平台字面量与 windows 命令二选一。
       */
    private final ProjectPlatformProviderRegistry projectPlatformRouter;

      /**
       * 项目类型路由器（按项目类型选择 SPI 实现：内置脚本、升级投放、重启判定、包来源）。
       * 与项目类型相关的分支一律经它取得，业务层不再出现类型字面量比较。
       */
    private final ProjectTypeProviderRegistry projectTypeRouter;

      /**
       * 系统配置 Mapper（读写升级全局/最近配置的原始 JSON）
       */
    private final MsServerConfigMapper serverConfigMapper;

      /**
       * JSON 序列化（升级配置解析）
       */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final String CFG_GLOBAL_KEY = "upgrade.global";
    private static final String CFG_LAST_KEY = "upgrade.last";
      /** 项目级默认升级配置的键前缀（后接项目ID），不改表结构 */
    private static final String CFG_PROJECT_PREFIX = "upgrade.project.";

      /**
       * 项目级 Actuator 定时采集配置的键前缀（后接项目ID），不改表结构
       */
    private static final String CFG_ACTUATOR_MONITOR_PREFIX = "release.actuator.monitor.";

      /**
       * 项目级健康检测配置的键前缀（后接项目ID），不改表结构
       */
    private static final String CFG_HEALTH_CHECK_PREFIX = "release.health.check.";

      /**
       * 健康检测命令里状态码标记：响应体之后单独输出，便于同时取到 body
       */
    private static final String HC_CODE_MARK = "__HC_CODE__";

      /**
       * Windows 健康检测命令里响应体的行前缀
       */
    private static final String HC_BODY_PREFIX = "BODY=";

      /**
       * 响应体参与判断的最大长度：只需要看 readinessState，超出部分没有意义
       */
    private static final int HC_BODY_MAX = 2000;


      /**
       * Actuator 定时采集默认间隔（秒）
       */
    private static final int ACTUATOR_MONITOR_DEFAULT_SECONDS = 30;

      /**
       * Actuator 定时采集最小间隔（秒）：再短会持续占住目标主机的命令通道
       */
    private static final int ACTUATOR_MONITOR_MIN_SECONDS = 5;

      /**
       * Actuator 定时采集最大间隔（秒）
       */
    private static final int ACTUATOR_MONITOR_MAX_SECONDS = 3600;

      /**
       * 操作上下文：项目 + 目标主机
       */
    private static class OperationContext {
        private MsServerProject project;
        private Long hostId;
          /** 目标主机（挂载服务时取运行账户，避免 unit 以 root 常驻） */
        private MsServerHost host;
          /** 归一后的系统类型（HostPlatforms 取值域），系统服务挂载按此路由 SPI 实现 */
        private String osType;
          /** 目标主机是否为 Windows（决定命令按 PowerShell 还是 bash 构造） */
        private boolean windows;
    }

      // ==================== 发布 CRUD ====================

    @Override
    public ReturnResult<List<MsServerRelease>> listReleases() {
        List<MsServerRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<MsServerRelease>().orderByDesc(MsServerRelease::getId));
          // 回填主机IP，保证发布卡片主机信息完整
        releases.forEach(this::fillHostInfo);
        return ReturnResult.list(releases);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * create Release
       * @param release 释放
       * @return 返回值
      */
    public ReturnResult<MsServerRelease> createRelease(MsServerRelease release) {
        fillHostInfo(release);
        release.setCreateTime(LocalDateTime.now());
        release.setUpdateTime(LocalDateTime.now());
        releaseMapper.insert(release);
        return ReturnResult.ok(release);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * update Release
       * @param id 长整数
       * @param release 释放
       * @return 返回值
      */
    public ReturnResult<MsServerRelease> updateRelease(Long id, MsServerRelease release) {
        MsServerRelease exists = releaseMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("发布不存在: " + id);
        }
        exists.setReleaseName(release.getReleaseName() == null ? exists.getReleaseName() : release.getReleaseName());
        exists.setHostId(release.getHostId() == null ? exists.getHostId() : release.getHostId());
        exists.setHostIp(release.getHostIp() == null ? exists.getHostIp() : release.getHostIp());
        exists.setDescription(release.getDescription() == null ? exists.getDescription() : release.getDescription());
        exists.setBackgroundImage(release.getBackgroundImage() == null ? exists.getBackgroundImage() : release.getBackgroundImage());
          // 发布级 SkyWalking 配置：先留快照再合并。
          // 若「先合并再比较」，比较的其实是「合并后的自己」与「本次提交值」，只要调用方把四项都带上
          // （前端保存即如此）就永远判等 —— 同步永远不会触发，等于这条触发点从未生效过。
        Boolean previousSwEnabled = exists.getSwEnabled();
        String previousSwJarPath = exists.getSwJarPath();
        String previousSwPrefix = exists.getSwServiceNamePrefix();
        String previousSwBackend = exists.getSwBackendService();
        exists.setSwEnabled(release.getSwEnabled() == null ? exists.getSwEnabled() : release.getSwEnabled());
        exists.setSwJarPath(release.getSwJarPath() == null ? exists.getSwJarPath() : release.getSwJarPath());
        exists.setSwServiceNamePrefix(release.getSwServiceNamePrefix() == null ? exists.getSwServiceNamePrefix() : release
            .getSwServiceNamePrefix());
        exists.setSwBackendService(release.getSwBackendService() == null ? exists.getSwBackendService() : release.getSwBackendService());
          // 发布级 SkyWalking 配置变更后需要同步到该发布下的已有项目
        boolean swChanged = !Objects.equals(previousSwEnabled, exists.getSwEnabled())
                || !Objects.equals(previousSwJarPath, exists.getSwJarPath())
                || !Objects.equals(previousSwPrefix, exists.getSwServiceNamePrefix())
                || !Objects.equals(previousSwBackend, exists.getSwBackendService());
        fillHostInfo(exists);
        exists.setUpdateTime(LocalDateTime.now());
        releaseMapper.updateById(exists);
        if (swChanged) {
            syncReleaseApm(exists);
        }
        return ReturnResult.ok(exists);
    }

      /**
       * 发布级 SkyWalking 配置变更后，同步到该发布下的全部项目。
       *
       * <p>同步只碰「发布自己写进去的那套参数」：开关/地址/路径失效时清理这些参数行，
       * 重新生效时写回新的覆盖项；项目自己声明的 APM（自研 agent、自有 OAP 地址）一律不动。</p>
       *
       * <p><b>为什么需要归属判定</b>：注入是把三条参数<b>复制</b>进项目 jvmArgs，落库后发布侧
       * 就没有任何记录能区分「这是我注入的」还是「项目自己填的」。若按「只要项目里有
       * {@code -Dskywalking.*} 就跳过」，关闭开关时永远清不掉（清空分支变成死代码）；
       * 若反过来「有就清」，则会把项目自研 agent 一起删掉。故按发布自身的特征做归属判定：
       * agent jar 路径一致，或「前缀 + OAP 地址」同时命中 —— 两者都来自本发布配置，
       * 项目自己配置的 APM 几乎不可能同时满足。</p>
       *
       * @param release 发布
       */
    private void syncReleaseApm(MsServerRelease release) {
        if (release == null) {
            return;
        }
        List<MsServerProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<MsServerProject>().eq(MsServerProject::getReleaseId, release.getId()));
        if (projects.isEmpty()) {
            return;
        }
        boolean enabled = Boolean.TRUE.equals(release.getSwEnabled());
        boolean ready = !isBlank(release.getSwJarPath()) && !isBlank(release.getSwBackendService());
        boolean inject = enabled && ready;
        for (MsServerProject project : projects) {
            List<String> args = readStringList(project.getJvmArgs());
            boolean declared = args.stream().anyMatch(this::isApmArg);
            if (declared && !injectedByRelease(release, project, args)) {
                  // 项目自己声明的 APM：发布级永远不覆盖、也不清理
                continue;
            }
            List<String> out = new ArrayList<>(inject
                    ? apmArgs(release, project)
                    : Collections.<String>emptyList());
            for (String arg : args) {
                if (!isApmArg(arg)) {
                    out.add(arg);
                }
            }
            if (out.equals(args)) {
                  // 结论与现状一致：不写库，避免无谓的 updateById 与 updateTime 抖动
                continue;
            }
            project.setJvmArgs(writeArgs(out));
            project.setUpdateTime(LocalDateTime.now());
            projectMapper.updateById(project);
        }
    }

      /**
       * 判断项目里的 APM 参数是否由本发布注入（用于「同步/清理」的范围界定）。
       *
       * @param release 发布
       * @param project 项目
       * @param args    项目当前参数
       * @return 属于本发布注入时为 {@code true}
       */
    private boolean injectedByRelease(MsServerRelease release, MsServerProject project, List<String> args) {
        String jar = release.getSwJarPath();
        if (!isBlank(jar) && args.contains("-javaagent:" + jar)) {
            return true;
        }
          // jar 路径被改空时退化为「service_name 与 OAP 地址同时命中」：
          // 前缀为空时 service_name 就是项目名，与项目自研配置无法区分，故此种情况不做归属判定
          // （宁可留着让用户手动清理，也不误删项目自研 agent）。
        String prefix = release.getSwServiceNamePrefix();
        String backend = release.getSwBackendService();
        return !isBlank(prefix) && !isBlank(backend)
                && args.contains("-Dskywalking.agent.service_name=" + serviceNameOf(release, project))
                && args.contains("-Dskywalking.collector.backend_service=" + backend);
    }

      /**
       * 发布级 APM 注入的三条参数（顺序固定：agent → service_name → backend）。
       *
       * @param release 发布
       * @param project 项目
       * @return 参数列表
       */
    private List<String> apmArgs(MsServerRelease release, MsServerProject project) {
        return Arrays.asList(
                "-javaagent:" + release.getSwJarPath(),
                "-Dskywalking.agent.service_name=" + serviceNameOf(release, project),
                "-Dskywalking.collector.backend_service=" + release.getSwBackendService());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * delete Release
       * @param id 长整数
       * @return 返回值
      */
    public ReturnResult<Void> deleteRelease(Long id) {
          // 删除发布下的所有项目
        projectMapper.delete(new LambdaQueryWrapper<MsServerProject>()
                .eq(MsServerProject::getReleaseId, id));
        releaseMapper.deleteById(id);
        return ReturnResult.success();
    }

      // ==================== 项目 CRUD ====================

    @Override
    public ReturnResult<List<MsServerProject>> listProjects(Long releaseId) {
        List<MsServerProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<MsServerProject>()
                        .eq(MsServerProject::getReleaseId, releaseId)
                        .orderByDesc(MsServerProject::getEnabled)
                        .orderByDesc(MsServerProject::getId));
          // 回填类型能力（不落库）：模型仓库等无常驻进程类型，前端据此隐藏启停并展示「已发布」
        for (MsServerProject project : projects) {
            ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());
            project.setRuntimeProcess(type.runtimeProcess());
            project.setJavaProcess(type.javaProcess());
        }
        return ReturnResult.list(projects);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * create Project
       * @param project 参数
       * @return 返回值
      */
    public ReturnResult<MsServerProject> createProject(MsServerProject project) {
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        if (isBlank(project.getStatus())) {
            project.setStatus("stopped");
        }
        if (project.getEnabled() == null) {
            project.setEnabled(true);
        }
          // 发布级 SkyWalking APM 注入（项目级 APM 选项优先，不会被覆盖）
        applyReleaseApm(project);
          // 四个脚本留空时用内置通用模板（含占位符）补全，用户无需手写
        applyDefaultScripts(project);
        projectMapper.insert(project);
        recordSyncOp(project.getId(), project.getProjectName(), project.getReleaseId(),
                "create", true, "创建成功");
        return ReturnResult.ok(project);
    }

      /**
       * 把发布级 SkyWalking APM 配置合入项目 JVM 参数。
       *
       * <p>项目级 APM 选项（已写在 jvmArgs 的 {@code -javaagent:} / {@code -Dskywalking.*}）
       * 始终优先：只有项目「尚未声明 APM」时，才把发布的 agent 路径、service_name 前缀
       * 与 OAP 地址追加到 jvmArgs 中，项目无需手工配置即可在启动时采集调用链。</p>
       *
       * @param project 项目（releaseId 必填）
       */
    private void applyReleaseApm(MsServerProject project) {
        if (project.getReleaseId() == null) {
            return;
        }
        MsServerRelease release = releaseMapper.selectById(project.getReleaseId());
          // 未开启 / 缺少 jar 路径 / 缺少 OAP 地址时不注入
        if (release == null || !Boolean.TRUE.equals(release.getSwEnabled())
                || isBlank(release.getSwJarPath()) || isBlank(release.getSwBackendService())) {
            return;
        }
        List<String> args = readStringList(project.getJvmArgs());
        if (args.stream().anyMatch(this::isApmArg)) {
            return;
        }
        List<String> out = new ArrayList<>(apmArgs(release, project));
        out.addAll(args);
        project.setJvmArgs(writeArgs(out));
    }

      /**
       * 把参数列表序列化为 JSON 字符串。
       *
       * <p>使用 {@code ObjectMapper.writeValueAsString} 时受检异常必须处理；序列化和项目经理 APM 参数
       * 脱落无关，抛错时按「原样」回写。</p>
       */
    private String writeArgs(List<String> args) {
        try {
            return JSON.writeValueAsString(args);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[release] JVM 参数序列化失败，按原样回写: {}", args);
            return String.valueOf(args);
        }
    }

      /**
       * 是否 APM 参数（已由项目自行声明）
       */
    private boolean isApmArg(String arg) {
        return arg.startsWith("-javaagent:") || arg.startsWith("-Dskywalking.");
    }

      /**
       * service_name 拼装：前缀（如有）- 项目名称
       *
       * @param release 发布
       * @param project 项目
       * @return 服务名
       */
    private String serviceNameOf(MsServerRelease release, MsServerProject project) {
        String prefix = release.getSwServiceNamePrefix();
        String name = project.getProjectName();
        if (isBlank(prefix)) return name;
        if (isBlank(name)) return prefix;
        return prefix + "-" + name;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * update Project
       * @param id 长整数
       * @param project 参数
       * @return 返回值
      */
    public ReturnResult<MsServerProject> updateProject(Long id, MsServerProject project) {
        MsServerProject exists = projectMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        exists.setProjectName(project.getProjectName() == null ? exists.getProjectName() : project.getProjectName());
        exists.setProjectType(project.getProjectType() == null ? exists.getProjectType() : project.getProjectType());
        exists.setMainEntry(project.getMainEntry() == null ? exists.getMainEntry() : project.getMainEntry());
        exists.setStartScript(project.getStartScript() == null ? exists.getStartScript() : project.getStartScript());
        exists.setStopScript(project.getStopScript() == null ? exists.getStopScript() : project.getStopScript());
        exists.setRestartScript(project.getRestartScript() == null ? exists.getRestartScript() : project.getRestartScript());
        exists.setProjectPath(project.getProjectPath() == null ? exists.getProjectPath() : project.getProjectPath());
        exists.setLogPath(project.getLogPath() == null ? exists.getLogPath() : project.getLogPath());
        exists.setLogMode(project.getLogMode() == null ? exists.getLogMode() : project.getLogMode());
        exists.setDescription(project.getDescription() == null ? exists.getDescription() : project.getDescription());
                exists.setIcon(project.getIcon() == null ? exists.getIcon() : project.getIcon());
        exists.setEnvVars(project.getEnvVars() == null ? exists.getEnvVars() : project.getEnvVars());
        exists.setCmdArgs(project.getCmdArgs() == null ? exists.getCmdArgs() : project.getCmdArgs());
        exists.setJvmArgs(project.getJvmArgs() == null ? exists.getJvmArgs() : project.getJvmArgs());
        exists.setStatusScript(project.getStatusScript() == null ? exists.getStatusScript() : project.getStatusScript());
        exists.setServiceName(project.getServiceName() == null ? exists.getServiceName() : project.getServiceName());
        exists.setAutostart(project.getAutostart() == null ? exists.getAutostart() : project.getAutostart());
        exists.setGuardian(project.getGuardian() == null ? exists.getGuardian() : project.getGuardian());
        exists.setEnabled(project.getEnabled() == null ? exists.getEnabled() : project.getEnabled());
        exists.setPid(project.getPid() == null ? exists.getPid() : project.getPid());
        exists.setAppConfig(project.getAppConfig() == null ? exists.getAppConfig() : project.getAppConfig());
        exists.setConfigDir(project.getConfigDir() == null ? exists.getConfigDir() : project.getConfigDir());
        exists.setLibDir(project.getLibDir() == null ? exists.getLibDir() : project.getLibDir());
        exists.setNginxDir(project.getNginxDir() == null ? exists.getNginxDir() : project.getNginxDir());
        exists.setGitUrl(project.getGitUrl() == null ? exists.getGitUrl() : project.getGitUrl());
        exists.setGitBranch(project.getGitBranch() == null ? exists.getGitBranch() : project.getGitBranch());
        exists.setGitAuthType(project.getGitAuthType() == null ? exists.getGitAuthType() : project.getGitAuthType());
        exists.setGitToken(project.getGitToken() == null ? exists.getGitToken() : project.getGitToken());
        exists.setGitBuildGoals(project.getGitBuildGoals() == null ? exists.getGitBuildGoals() : project.getGitBuildGoals());
        exists.setGitSkipTests(project.getGitSkipTests() == null ? exists.getGitSkipTests() : project.getGitSkipTests());
        exists.setMavenHome(project.getMavenHome() == null ? exists.getMavenHome() : project.getMavenHome());
        exists.setMavenVersion(project.getMavenVersion() == null ? exists.getMavenVersion() : project.getMavenVersion());
        exists.setJdkHome(project.getJdkHome() == null ? exists.getJdkHome() : project.getJdkHome());
        exists.setUploadExtract(project.getUploadExtract() == null ? exists.getUploadExtract() : project.getUploadExtract());
        exists.setUploadTargetDir(project.getUploadTargetDir() == null ? exists.getUploadTargetDir() : project.getUploadTargetDir());
        exists.setDeployType(project.getDeployType() == null ? exists.getDeployType() : project.getDeployType());
        exists.setGitPollEnabled(project.getGitPollEnabled() == null ? exists.getGitPollEnabled() : project.getGitPollEnabled());
        exists.setGitPollCron(project.getGitPollCron() == null ? exists.getGitPollCron() : project.getGitPollCron());
          // 定时发布：开关或时间变化时重置状态；只接受未来时间，避免登记一个永远不会触发的 cron
        boolean scheduleTouched = project.getScheduleEnabled() != null || project.getScheduleTime() != null;
        exists.setScheduleEnabled(project.getScheduleEnabled() == null ? exists.getScheduleEnabled() : project.getScheduleEnabled());
        exists.setScheduleTime(project.getScheduleTime() == null ? exists.getScheduleTime() : project.getScheduleTime());
        if (scheduleTouched) {
            boolean enabled = Boolean.TRUE.equals(exists.getScheduleEnabled()) && exists.getScheduleTime() != null;
            if (enabled && exists.getScheduleTime().isBefore(LocalDateTime.now())) {
                return ReturnResult.error("定时发布时间必须晚于当前时间");
            }
            exists.setScheduleStatus(enabled ? "PENDING" : "NONE");
        }
          // 四个脚本为空时用内置通用模板补全
        applyDefaultScripts(exists);
        exists.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(exists);
        recordSyncOp(exists.getId(), exists.getProjectName(), exists.getReleaseId(),
                "update", true, "修改成功");
        return ReturnResult.ok(exists);
    }

      /**
       * 生成内置四段脚本：与执行期（prepare→expandProjectScripts）走完全相同的模板与展开路径，
       * 保证前端预览 / 一键生成与真正下发的脚本逐字一致。
       *
       * @param hostId      目标主机标识
       * @param projectType 项目类型
       * @param mainEntry   主入口
       * @param projectPath 项目地址
       * @param logPath     日志地址
       * @param appName     应用名
       * @param envVars     环境变量 JSON
       * @param jvmArgs     JVM 参数 JSON
       * @param cmdArgs     命令参数 JSON
       * @return startScript/stopScript/restartScript/statusScript
       */
    @Override
    public ReturnResult<Map<String, String>> generateScripts(Long hostId, String projectType, String mainEntry,
            String projectPath, String logPath, String appName,
            String envVars, String jvmArgs, String cmdArgs, String jdkHome) {
        MsServerHost host = hostId == null ? null : hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        String osType = HostPlatforms.normalize(host.getServerHostOsType());
        boolean windows = HostPlatforms.WINDOWS.equals(osType);
        ProjectScripts.Scripts def = projectTypeRouter.resolveOrGeneric(projectType).scripts(windows, mainEntry);
          // 用一个只承载上下文的临时项目走 materializeVars，使 envVars/jvmArgs/cmdArgs 的
          // VARS 块与参数展开与执行期一致（前端拿到的即可直接执行，无需二次加工）。
        MsServerProject stub = new MsServerProject();
        stub.setProjectType(projectType);
        stub.setMainEntry(mainEntry);
        stub.setProjectPath(projectPath);
        stub.setLogPath(logPath);
        stub.setProjectName(appName);
        stub.setEnvVars(envVars);
        stub.setJvmArgs(jvmArgs);
        stub.setCmdArgs(cmdArgs);
        stub.setJdkHome(jdkHome);
        ProjectPlatformProvider pf = projectPlatformRouter.resolveOrPosix(osType);
        String start = materializeVars(def.start(), stub, pf);
        String restart = materializeVars(def.restart(), stub, pf);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("startScript", ProjectScripts.apply(start, mainEntry, projectPath, logPath, appName));
        out.put("stopScript", ProjectScripts.apply(def.stop(), mainEntry, projectPath, logPath, appName));
        out.put("restartScript", ProjectScripts.apply(restart, mainEntry, projectPath, logPath, appName));
        out.put("statusScript", ProjectScripts.apply(def.status(), mainEntry, projectPath, logPath, appName));
        return ReturnResult.ok(out);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * delete Project
       * @param id 长整数
       * @return 返回值
      */
    public ReturnResult<Void> deleteProject(Long id) {
        MsServerProject exists = projectMapper.selectById(id);
        projectMapper.deleteById(id);
        recordSyncOp(id, exists == null ? null : exists.getProjectName(),
                exists == null ? null : exists.getReleaseId(), "delete", true, "删除成功");
        return ReturnResult.success();
    }

      // ==================== 项目操作（异步任务） ====================

    @Override
    public ReturnResult<String> startProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            recordPrecheckFailure(id, "start", prepared.getMsg());
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());
        if (!type.runtimeProcess()) {
            return ReturnResult.error("该项目类型（" + type.label() + "）没有常驻进程，无需启动");
        }
        if (isBlank(project.getStartScript())) {
            return ReturnResult.error("启动脚本未配置");
        }
          // 幂等：已在运行则不再重复启动（避免重复拉起、覆盖 app.pid、产生孤儿进程）
        if (!isBlank(project.getStatusScript())) {
            ReturnResult<String> cur = statusProject(id);
            if (cur.isOk() && cur.getData() != null
                    && cur.getData().toLowerCase().contains("running")) {
                return ReturnResult.error("项目已在运行，无需重复启动");
            }
        }
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
        String path = project.getProjectPath();
        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfg = springConfigStep(win, project);
        if (cfg != null) {
            steps.add(cfg);
        }
        steps.add(new ProjectStep("启动项目", buildCommand(win, path, project.getStartScript()), 3));
        steps.add(pidStep(win, path, type));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "start", steps, "running");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> stopProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            recordPrecheckFailure(id, "stop", prepared.getMsg());
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());
        if (!type.runtimeProcess()) {
            return ReturnResult.error("该项目类型（" + type.label() + "）没有常驻进程，无需停止");
        }
        if (isBlank(project.getStopScript())) {
            return ReturnResult.error("停止脚本未配置");
        }
        List<ProjectStep> steps = List.of(new ProjectStep("停止项目",
                buildCommand(projectPlatformRouter.resolveOrPosix(prepared.getData().osType), project.getProjectPath(), project.getStopScript()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "stop", steps, "stopped");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> restartProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            recordPrecheckFailure(id, "restart", prepared.getMsg());
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());
        if (!type.runtimeProcess()) {
            return ReturnResult.error("该项目类型（" + type.label() + "）没有常驻进程，无需重启");
        }
        String path = project.getProjectPath();
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfg = springConfigStep(win, project);
        if (cfg != null) {
            steps.add(cfg);
        }
        if (!isBlank(project.getRestartScript())) {
            steps.add(new ProjectStep("重启项目", buildCommand(win, path, project.getRestartScript()), 3));
        } else {
            if (isBlank(project.getStopScript()) || isBlank(project.getStartScript())) {
                return ReturnResult.error("重启脚本未配置（需停止+启动脚本或重启脚本）");
            }
            steps.add(new ProjectStep("停止服务", buildCommand(win, path, project.getStopScript()), 2));
            steps.add(new ProjectStep("启动服务", buildCommand(win, path, project.getStartScript()), 3));
        }
        steps.add(pidStep(win, path, type));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "restart", steps, "running");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> upgradeProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            recordPrecheckFailure(id, "upgrade", prepared.getMsg());
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
        String path = project.getProjectPath();
        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfg = springConfigStep(win, project);
        if (cfg != null) {
            steps.add(cfg);
        }
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(win, path, project.getStopScript()), 1));
        }
        steps.add(new ProjectStep("备份当前版本", backupCommand(win, path), 2));
        if (!isBlank(project.getStartScript())) {
            steps.add(new ProjectStep("启动服务", buildCommand(win, path, project.getStartScript()), 3));
        }
        if (type.runtimeProcess()) {
            steps.add(pidStep(win, path, type));
        }
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "upgrade", steps, type.runtimeProcess() ? "running" : null);
        return task(taskId);
    }

    @Override
    public ReturnResult<String> backupProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            recordPrecheckFailure(id, "backup", prepared.getMsg());
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        List<ProjectStep> steps = List.of(new ProjectStep("备份项目",
                backupCommand(projectPlatformRouter.resolveOrPosix(prepared.getData().osType), project.getProjectPath()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "backup", steps, null);
        if (taskId == null) {
            recordSyncOp(project.getId(), project.getProjectName(), project.getReleaseId(),
                    "backup", false, "该项目有操作正在进行，请稍后再试");
        }
        return task(taskId);
    }

    @Override
    public ReturnResult<String> rollbackProject(Long id, String backupName) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            recordPrecheckFailure(id, "rollback", prepared.getMsg());
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
        List<ProjectStep> steps = new ArrayList<>();
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(win, project.getProjectPath(), project.getStopScript()), 1));
        }
        String stepLabel = isBlank(backupName) ? "恢复最新备份" : ("恢复备份 " + backupName);
        steps.add(new ProjectStep(stepLabel, rollbackCommand(win, project.getProjectPath(), backupName), 2));
        String finishStatus = null;
        if (!isBlank(project.getStartScript())) {
            steps.add(new ProjectStep("启动服务", buildCommand(win, project.getProjectPath(), project.getStartScript()), 1));
            if (type.runtimeProcess()) {
                  // 回滚同样要回写 PID：旧实现只重启不读 pid，接口 pid=null、卡片显示「PID —」，
                  // 实际进程却已在跑（验收时 Git 回滚复现）。
                steps.add(pidStep(win, project.getProjectPath(), type));
                finishStatus = "running";
            }
        }
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "rollback", steps, finishStatus);
        return task(taskId);
    }

    @Override
    public ReturnResult<List<ProjectBackupVO>> listBackups(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        String command = backupListCommand(projectPlatformRouter.resolveOrPosix(ctx.osType), ctx.project.getProjectPath());
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(ctx.hostId), command, 30, null);
        List<ProjectBackupVO> rows = new ArrayList<>();
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            String output = result.getData().get(0).getMsExecLogOutput();
            if (output != null) {
                for (String raw : output.split("\n")) {
                    String line = raw.trim();
                    if (!line.startsWith("backup_")) {
                        continue;
                    }
                      // name|epochSeconds|size|kind
                    String[] parts = line.split("\\|", -1);
                    ProjectBackupVO vo = new ProjectBackupVO(
                            parts[0],
                            parseEpochSeconds(parts.length > 1 ? parts[1] : null),
                            parseLongOrNull(parts.length > 2 ? parts[1 + 1] : null),
                            parts.length > 3 && !parts[3].isBlank() ? parts[3]
                                    : (parts[0].endsWith(".tar.gz") ? "file" : "dir"));
                    rows.add(vo);
                }
            }
        }
          // 最新备份在前
        rows.sort((x, y) -> Long.compare(
                y.time() == null ? 0L : y.time(),
                x.time() == null ? 0L : x.time()));
        return ReturnResult.ok(rows);
    }

      /**
       * parse Epoch Seconds
       * @param raw 字符串
       * @return 返回值
      */
    private Long parseEpochSeconds(String raw) {
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (Exception e) {
            return null;
        }
    }

      /**
       * parse Long Or Null
       * @param raw 字符串
       * @return 返回值
      */
    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

      /**
       * 构造「列出 backups/ 备份记录」命令，输出每行 name|epochSeconds|size|kind
       * @param pf 参数
       * @param projectPath 字符串
       * @return 返回值
      */
    private String backupListCommand(ProjectPlatformProvider pf, String projectPath) {
        return pf.listBackups(projectPath);
    }

      // ==================== 日志 ====================

    /** 「日志读取行数」配置键前缀（项目级） */
    private static final String CFG_LOG_LINES_PREFIX = "release.log.lines.";

    /** 日志读取行数默认值 */
    private static final int LOG_LINES_DEFAULT = 100;

    /** 日志读取行数下限 */
    private static final int LOG_LINES_MIN = 10;

    /** 日志读取行数上限 */
    private static final int LOG_LINES_MAX = 5000;

    /** 后端检索命中行上限默认值 */
    private static final int LOG_SEARCH_MAX_DEFAULT = 500;

    /** 后端检索命中行硬上限：再多前端也放不下，且回传体积不可控 */
    private static final int LOG_SEARCH_MAX_LIMIT = 5000;

    /** 后端检索超时（秒）：大文件 grep 比普通命令慢，但不能无限等 */
    private static final int LOG_SEARCH_TIMEOUT_SECONDS = 120;

    /** 投放复制失败后的重试次数：目标文件被上一个进程短暂占用时，等一会就能复制成功 */
    private static final int PLACE_COPY_RETRY = 3;

    /** 投放复制重试间隔（毫秒）：Windows 上 taskkill 后句柄释放通常 1~2 秒内完成 */
    private static final long PLACE_COPY_RETRY_INTERVAL_MS = 2000L;

    @Override
    public ReturnResult<String> getProjectLog(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getLogPath())) {
            return ReturnResult.ok("未配置日志路径");
        }
          // 按操作系统生成读取命令（由平台 SPI 提供：POSIX tail / Windows Get-Content -Tail）
        String logPath = project.getLogPath();
        String command = projectPlatformRouter.resolveOrPosix(prepared.getData().osType)
                .tailLog(logPath, resolveLogLines(id));
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(prepared.getData().hostId), command, 30, null);

        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            return ReturnResult.ok(result.getData().get(0).getMsExecLogOutput());
        }
        return ReturnResult.ok("暂无日志内容");
    }

    @Override
    public ReturnResult<com.chua.starter.server.support.model.vo.ProjectLogSearchVO> searchProjectLog(
            Long id, String keyword, Integer maxLines, Boolean matchCase) {
        if (isBlank(keyword)) {
            return ReturnResult.error("检索关键字不能为空");
        }
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (isBlank(project.getLogPath())) {
            return ReturnResult.error("日志路径未配置");
        }
        int limit = maxLines == null || maxLines <= 0
                ? LOG_SEARCH_MAX_DEFAULT
                : Math.min(LOG_SEARCH_MAX_LIMIT, maxLines);
        boolean caseSensitive = matchCase != null && matchCase;
        String command = projectPlatformRouter.resolveOrPosix(ctx.osType)
                .searchLog(project.getLogPath(), keyword, limit, caseSensitive);
        long begin = System.currentTimeMillis();
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(ctx.hostId), command,
                        LOG_SEARCH_TIMEOUT_SECONDS, null);
        long elapsed = System.currentTimeMillis() - begin;
        if (!result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
            return ReturnResult.error("日志检索失败: " + (result.getMsg() == null ? "无返回" : result.getMsg()));
        }
        String output = result.getData().get(0).getMsExecLogOutput();
        List<String> lines = new ArrayList<>();
        if (output != null) {
            for (String raw : output.split("\n")) {
                String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        boolean noHit = lines.size() == 1 && lines.get(0).startsWith("未检索到匹配内容");
        if (noHit) {
            lines.clear();
        }
        boolean truncated = lines.size() >= limit;
        return ReturnResult.ok(new com.chua.starter.server.support.model.vo.ProjectLogSearchVO(
                keyword, project.getLogPath(), lines, lines.size(), truncated, elapsed, null));
    }

    /**
     * 读取项目的「日志读取行数」；未配置或非法时回退默认 100 行。
     *
     * @param projectId 项目ID
     * @return 行数（夹在 10 ~ 5000）
     */
    private int resolveLogLines(Long projectId) {
        Object saved = readCfg(CFG_LOG_LINES_PREFIX + projectId, null);
        int lines = LOG_LINES_DEFAULT;
        if (saved instanceof Map) {
            Object raw = ((Map<?, ?>) saved).get("lines");
            if (raw != null) {
                try {
                    lines = Integer.parseInt(String.valueOf(raw).trim());
                } catch (NumberFormatException e) {
                    lines = LOG_LINES_DEFAULT;
                }
            }
        } else if (saved != null) {
            try {
                lines = Integer.parseInt(String.valueOf(saved).trim());
            } catch (NumberFormatException e) {
                lines = LOG_LINES_DEFAULT;
            }
        }
        return Math.max(LOG_LINES_MIN, Math.min(LOG_LINES_MAX, lines));
    }

    /**
     * 保存项目的「日志读取行数」（写后回读口径与采集配置一致）。
     *
     * @param projectId 项目ID
     * @param lines     行数
     * @return 操作结果
     */
    private ReturnResult<Void> saveLogLines(Long projectId, int lines) {
        int clamped = Math.max(LOG_LINES_MIN, Math.min(LOG_LINES_MAX, lines));
        String key = CFG_LOG_LINES_PREFIX + projectId;
        String value;
        try {
            value = JSON.writeValueAsString(Map.of("lines", clamped));
        } catch (Exception e) {
            return ReturnResult.error("配置序列化失败");
        }
        MsServerConfig exists = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (exists == null) {
            MsServerConfig cfg = new MsServerConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setConfigDesc("项目 " + projectId + " 日志读取行数");
            serverConfigMapper.insert(cfg);
        } else {
            exists.setConfigValue(value);
            serverConfigMapper.updateById(exists);
        }
        return ReturnResult.success();
    }

    public ReturnResult<Integer> getLogLines(Long id) {
        return ReturnResult.ok(resolveLogLines(id));
    }

    public ReturnResult<Void> updateLogLines(Long id, Integer lines) {
        if (lines == null) {
            return ReturnResult.error("行数不能为空");
        }
        return saveLogLines(id, lines);
    }

    @Override
    public ReturnResult<String> startProjectLog(Long id, String clientId) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getLogPath())) {
            return ReturnResult.error("日志路径未配置");
        }
        String sessionId = projectTaskService.startLogTail(prepared.getData().hostId,
                project.getId(), project.getLogPath(), clientId, resolveLogLines(id));
        return ReturnResult.ok(sessionId);
    }

    @Override
    public ReturnResult<Void> stopProjectLog(String sessionId) {
        projectTaskService.stopLogTail(sessionId);
        return ReturnResult.success();
    }

      // ==================== 软件仓库 ====================

    @Override
    public ReturnResult<List<SoftwarePackage>> searchSoftware(Long hostId, String keyword) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }

        String command = provider.searchCommand(keyword);
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(hostId), command, 30, null);

        List<SoftwarePackage> packages = new ArrayList<>();
        if (result.getData() != null && !result.getData().isEmpty()) {
            com.chua.starter.server.support.entity.MsExecLog log = result.getData().get(0);
              // 同样检查单条日志状态：SSH 凭据缺失时命令执行失败，输出为空
            if (!ServerConstants.EXEC_STATUS_SUCCESS.equals(log.getMsExecLogStatus())) {
                String reason = log.getMsExecLogError();
                if (isBlank(reason)) {
                    reason = "命令执行失败（退出码 " + log.getMsExecLogExitCode() + "）";
                }
                return ReturnResult.error("搜索软件失败: " + reason);
            }
            String output = log.getMsExecLogOutput();
            if (isBlank(output)) {
                return ReturnResult.ok(packages);
            }
            packages = provider.parse(output, keyword);
        }
        return ReturnResult.ok(packages);
    }

      /**
       * 安装软件：构建包管理器安装命令，转为异步任务，实时进度经 socket 推送
       */
    @Override
    public ReturnResult<String> installSoftware(Long hostId, String name) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isBlank(name)) {
            return ReturnResult.error("软件包名不能为空");
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        String command = provider.installCommand(name);

        List<ProjectStep> steps = List.of(new ProjectStep("安装 " + name, command, 1));
        String taskId = projectTaskService.startTask(hostId, null, "install", steps, null);
        return task(taskId);
    }

      /**
       * 列出已安装软件：同步执行 provider 的 installed 命令并解析。
       */
    @Override
    public ReturnResult<List<SoftwarePackage>> listInstalledSoftware(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }

        String command = provider.installedCommand();
        if (isBlank(command)) {
            return ReturnResult.error("该主机包管理器（" + provider.manager() + "）暂不支持查看已安装软件");
        }

        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(hostId), command, 60, null);

        List<SoftwarePackage> packages = new ArrayList<>();
        if (result.getData() != null && !result.getData().isEmpty()) {
            com.chua.starter.server.support.entity.MsExecLog log = result.getData().get(0);
              // 必须检查单条日志的执行状态：runCommand 的 ReturnResult.isSuccess()
              // 只表示 HTTP 层成功，不代表命令执行成功。SSH 凭据缺失、命令退出码非 0
              // 等情况日志状态为 FAIL，输出为空——若不检查直接 parseInstalled("")
              // 会返回空列表，前端显示「尚未安装任何软件」而非真实错误。
            if (!ServerConstants.EXEC_STATUS_SUCCESS.equals(log.getMsExecLogStatus())) {
                String reason = log.getMsExecLogError();
                if (isBlank(reason)) {
                    reason = "命令执行失败（退出码 " + log.getMsExecLogExitCode() + "）";
                }
                return ReturnResult.error("读取已安装软件失败: " + reason);
            }
            String output = log.getMsExecLogOutput();
            if (isBlank(output)) {
                return ReturnResult.ok(packages);
            }
            packages = provider.parseInstalled(output);
        }
        return ReturnResult.ok(packages);
    }

      /**
       * 卸载软件：构建包管理器卸载命令，转为异步任务
       */
    @Override
    public ReturnResult<String> uninstallSoftware(Long hostId, String name) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isBlank(name)) {
            return ReturnResult.error("软件包名不能为空");
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        String command = provider.uninstallCommand(name);

        List<ProjectStep> steps = List.of(new ProjectStep("卸载 " + name, command, 1));
        String taskId = projectTaskService.startTask(hostId, null, "uninstall", steps, null);
        return task(taskId);
    }

      /**
       * 升级软件到最新版：构建包管理器升级命令，转为异步任务
       */
    @Override
    public ReturnResult<String> upgradeSoftware(Long hostId, String name) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isBlank(name)) {
            return ReturnResult.error("软件包名不能为空");
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        String command = provider.upgradeCommand(name);

        List<ProjectStep> steps = List.of(new ProjectStep("升级 " + name, command, 1));
        String taskId = projectTaskService.startTask(hostId, null, "upgrade", steps, null);
        return task(taskId);
    }

      /**
       * 列出有可用更新的软件。
       *
       * <p>判断依据是包管理器自己的「可升级清单」（apt list --upgradable /
       * dnf check-update / winget upgrade / choco outdated），而不是「仓库里有没有这个包」。
       * 再把清单与已安装版本按包名合并，前端拿到的每一条都是「当前 → 候选」，
       * 点下去一定会真的升级。</p>
       */
    @Override
    public ReturnResult<List<SoftwareUpgradeVO>> listUpgradableSoftware(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        String command = provider.upgradableCommand();
        if (isBlank(command)) {
            return ReturnResult.ok(new ArrayList<>());
        }

        ReturnResult<List<MsExecLog>> result =
                execService.runCommand(Collections.singletonList(hostId), command, 90, null);
        List<SoftwarePackage> candidates = new ArrayList<>();
        if (result.getData() != null && !result.getData().isEmpty()) {
            MsExecLog log = result.getData().get(0);
            String output = log.getMsExecLogOutput();
            if (isBlank(output)) {
                  // 输出为空且执行失败：不能当成「没有更新」，否则用户永远看不到升级按钮
                if (!ServerConstants.EXEC_STATUS_SUCCESS.equals(log.getMsExecLogStatus())) {
                    String reason = log.getMsExecLogError();
                    if (isBlank(reason)) {
                        reason = "命令执行失败（退出码 " + log.getMsExecLogExitCode() + "）";
                    }
                    return ReturnResult.error("检查可升级软件失败: " + reason);
                }
                return ReturnResult.ok(new ArrayList<>());
            }
            candidates = provider.parseUpgradable(output);
        }
        if (candidates.isEmpty()) {
            return ReturnResult.ok(new ArrayList<>());
        }

          // 合并已安装版本：只保留确实装在这台机器上的包
        Map<String, String> installedVersion = new LinkedHashMap<>();
        ReturnResult<List<SoftwarePackage>> installedResult = listInstalledSoftware(hostId);
        if (installedResult.isOk() && installedResult.getData() != null) {
            for (SoftwarePackage pkg : installedResult.getData()) {
                if (pkg.name() != null) {
                    installedVersion.put(pkg.name().toLowerCase(), pkg.version());
                }
            }
        }

        List<SoftwareUpgradeVO> upgrades = new ArrayList<>();
        for (SoftwarePackage candidate : candidates) {
            if (candidate.name() == null || candidate.name().isBlank()) {
                continue;
            }
            String key = candidate.name().toLowerCase();
            if (!installedVersion.containsKey(key)) {
                continue;
            }
            String current = installedVersion.get(key);
            String target = candidate.version();
            if (!isBlank(current) && !isBlank(target) && current.trim().equals(target.trim())) {
                  // 版本完全相同不是升级；包管理器偶尔会把刚更新的包列一次
                continue;
            }
            upgrades.add(new SoftwareUpgradeVO(candidate.name(), current, target));
        }
        return ReturnResult.ok(upgrades);
    }

      /**
       * 查询某个软件包的依赖关系。
       *
       * <p>依赖是「读了才有」的信息，不预加载：软件列表动辄几百条，逐包跑
       * {@code apt-cache depends} 会让页面打开变成分钟级。详情页展开时按需查一次。</p>
       */
    @Override
    public ReturnResult<List<String>> softwareDependencies(Long hostId, String name) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isBlank(name)) {
            return ReturnResult.error("软件包名不能为空");
        }
        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        String command = provider.dependenciesCommand(name);
        if (isBlank(command)) {
            return ReturnResult.ok(new ArrayList<>());
        }

        ReturnResult<List<MsExecLog>> result =
                execService.runCommand(Collections.singletonList(hostId), command, 30, null);
        if (result.getData() == null || result.getData().isEmpty()) {
            return ReturnResult.ok(new ArrayList<>());
        }
        String output = result.getData().get(0).getMsExecLogOutput();
        if (isBlank(output)) {
            return ReturnResult.ok(new ArrayList<>());
        }
        return ReturnResult.ok(provider.parseDependencies(output));
    }

      // ==================== 状态检测 ====================

    @Override
    public ReturnResult<String> statusProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (isBlank(project.getStatusScript())) {
            return ReturnResult.error("状态脚本未配置");
        }
        String command = buildCommand(projectPlatformRouter.resolveOrPosix(ctx.osType), project.getProjectPath(), project.getStatusScript());
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(ctx.hostId), command, 20, null);
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            return ReturnResult.ok(result.getData().get(0).getMsExecLogOutput());
        }
        return ReturnResult.ok("unknown");
    }

      // ==================== 系统服务挂载 ====================

    @Override
    public ReturnResult<String> installService(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (isBlank(project.getProjectPath())) {
            return ReturnResult.error("项目地址未配置，无法挂载服务");
        }
        var provider = systemServiceRouter.resolve(ctx.osType);
        if (provider.isEmpty()) {
            return ReturnResult.error("目标主机系统类型「" + ctx.osType + "」暂不支持挂载系统服务");
        }
        var serviceContext = serviceContext(ctx);
        String svc = isBlank(project.getServiceName())
                ? provider.get().defaultServiceName(serviceContext)
                : project.getServiceName();
        String command = provider.get().install(serviceContext, svc);
        List<ProjectStep> steps = List.of(new ProjectStep("挂载系统服务", command, 1));
          // 服务名只在任务真正成功后才回写：旧实现在提交任务时就写库，
          // 安装失败（如守护进程未拉起）会留下「库里有服务名、机器上没有服务」的假状态。
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "install-service", steps, null, new ProjectTaskService.ServiceNameWrite(svc));
        return task(taskId);
    }

    @Override
    public ReturnResult<String> uninstallService(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        var provider = systemServiceRouter.resolve(ctx.osType);
        if (provider.isEmpty()) {
            return ReturnResult.error("目标主机系统类型「" + ctx.osType + "」暂不支持卸载系统服务");
        }
        var serviceContext = serviceContext(ctx);
        String svc = isBlank(project.getServiceName())
                ? provider.get().defaultServiceName(serviceContext)
                : project.getServiceName();
        String command = provider.get().uninstall(serviceContext, svc);
        List<ProjectStep> steps = List.of(new ProjectStep("卸载系统服务", command, 1));
          // 卸载失败（例如「自启脚本仍存在」）时不能清空库中服务名：那会让 UI 谎报未挂载。
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "uninstall-service", steps, null, new ProjectTaskService.ServiceNameWrite(null));
        return task(taskId);
    }

      // ==================== 升级配置 ====================

      /**
       * 装配系统服务挂载上下文：把项目实体与结构化变量转换为 SPI 输入
       *
       * @param ctx 操作上下文
       * @return 挂载上下文
       */
    private SystemServiceContext serviceContext(OperationContext ctx) {
        var env = new LinkedHashMap<String, String>();
        for (EnvVar e : readEnvList(ctx.project.getEnvVars())) {
            env.put(e.key, e.value);
        }
        return new SystemServiceContext(
                ctx.project.getProjectName(),
                ctx.project.getId(),
                ctx.project.getProjectType(),
                ctx.project.getMainEntry(),
                ctx.project.getProjectPath(),
                ctx.project.getLogPath(),
                ctx.project.getStartScript(),
                ctx.project.getStopScript(),
                ctx.project.getStatusScript(),
                env,
                readStringList(ctx.project.getJvmArgs()),
                readStringList(ctx.project.getCmdArgs()),
                ctx.host == null ? "" : ctx.host.getServerHostAccount(),
                Boolean.TRUE.equals(ctx.project.getAutostart()),
                Boolean.TRUE.equals(ctx.project.getGuardian()),
                  // 前台常驻命令由项目类型 SPI 生成，平台实现不再持有项目类型分支
                projectTypeRouter.resolveOrGeneric(ctx.project.getProjectType())
                        .posixForeground(ctx.project.getMainEntry()));
    }

    @Override
    public ReturnResult<Map<String, Object>> getUpgradeConfig(Long projectId) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("global", readCfg(CFG_GLOBAL_KEY, defaultGlobalConfig()));
        out.put("last", readCfg(CFG_LAST_KEY, null));
        if (projectId != null) {
            out.put("project", projectUpgradeConfig(projectId));
        }
        return ReturnResult.ok(out);
    }

      /**
       * 汇总某个项目的「默认升级配置」。
       *
       * <p>项目默认 = 项目自身字段（部署类型 / 解压开关 / 模型上传目录）作为基线，
       * 再被用户显式保存过的项目级配置覆盖。这样升级弹框按项目打开时，
       * 显示的一定是这个项目自己的默认值，而不是上一次别的项目留下来的全局配置。</p>
       *
       * @param projectId 项目ID
       * @return 项目默认升级配置
       */
    @SuppressWarnings("unchecked")
    private Map<String, Object> projectUpgradeConfig(Long projectId) {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>(defaultGlobalConfig());
        MsServerProject project = projectMapper.selectById(projectId);
        if (project != null) {
            if (!isBlank(project.getDeployType())) {
                cfg.put("deployType", project.getDeployType());
            }
            if (project.getUploadExtract() != null) {
                cfg.put("uploadExtract", project.getUploadExtract());
            }
            if (!isBlank(project.getUploadTargetDir())) {
                cfg.put("uploadTargetDir", project.getUploadTargetDir());
            }
            cfg.put("projectType", project.getProjectType());
            cfg.put("mainEntry", project.getMainEntry());
            cfg.put("projectName", project.getProjectName());
        }
        Object saved = readCfg(CFG_PROJECT_PREFIX + projectId, null);
        if (saved instanceof Map) {
            cfg.putAll((Map<String, Object>) saved);
            cfg.put("__saved", true);
        } else {
            cfg.put("__saved", false);
        }
        return cfg;
    }

    @Override
    public ReturnResult<Void> saveUpgradeConfig(String scope, Long projectId, Map<String, Object> config) {
        String key;
        String desc;
        if ("global".equalsIgnoreCase(scope)) {
            key = CFG_GLOBAL_KEY;
            desc = "升级全局默认配置";
        } else if ("last".equalsIgnoreCase(scope)) {
            key = CFG_LAST_KEY;
            desc = "最近一次手动升级配置";
        } else if ("project".equalsIgnoreCase(scope)) {
            if (projectId == null) {
                return ReturnResult.error("scope=project 时必须指定 projectId");
            }
            key = CFG_PROJECT_PREFIX + projectId;
            desc = "项目 " + projectId + " 默认升级配置";
        } else {
            return ReturnResult.error("非法 scope: " + scope);
        }
        if (config == null) {
            return ReturnResult.error("配置不能为空");
        }
        String value;
        try {
            value = JSON.writeValueAsString(config);
        } catch (Exception e) {
            return ReturnResult.error("配置序列化失败");
        }
        MsServerConfig exists = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (exists == null) {
            MsServerConfig cfg = new MsServerConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setConfigDesc(desc);
            serverConfigMapper.insert(cfg);
        } else {
            exists.setConfigValue(value);
            serverConfigMapper.updateById(exists);
        }
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> batchUpgrade(List<Long> projectIds, Map<String, Object> config) {
        if (projectIds == null || projectIds.isEmpty()) {
            return ReturnResult.error("请选择要升级的项目");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long projectId : projectIds) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("projectId", projectId);
            try {
                Map<String, Object> cfg = config == null
                        ? new java.util.LinkedHashMap<>()
                        : new java.util.LinkedHashMap<>(config);
                  // 批量升级只对一个项目下发一次：清掉可能残留的包路径判断，交给调用方显式传入
                ReturnResult<String> r = runUpgrade(projectId, cfg);
                row.put("ok", r.isOk());
                row.put("taskId", r.isOk() ? r.getData() : null);
                if (!r.isOk()) {
                    row.put("message", r.getMsg());
                }
            } catch (Exception e) {
                row.put("ok", false);
                row.put("message", e.getMessage());
            }
            results.add(row);
        }
        return ReturnResult.ok(results);
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> batchRollback(List<Long> projectIds, String backupName) {
        if (projectIds == null || projectIds.isEmpty()) {
            return ReturnResult.error("请选择要回滚的项目");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long projectId : projectIds) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("projectId", projectId);
            try {
                ReturnResult<String> r = rollbackProject(projectId, backupName);
                row.put("ok", r.isOk());
                row.put("taskId", r.isOk() ? r.getData() : null);
                if (!r.isOk()) {
                    row.put("message", r.getMsg());
                }
            } catch (Exception e) {
                row.put("ok", false);
                row.put("message", e.getMessage());
            }
            results.add(row);
        }
        return ReturnResult.ok(results);
    }

      // ==================== 按配置升级 ====================

    @Override
    public ReturnResult<String> runUpgrade(Long id, Map<String, Object> config) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(ctx.osType);
        Map<String, Object> cfg = config == null ? new java.util.LinkedHashMap<>() : config;
        boolean autoBackup = boolCfg(cfg, "autoBackup", true);
        boolean autoExtract = boolCfg(cfg, "autoExtract", true);
        boolean autoRestart = boolCfg(cfg, "autoRestart", true);
        boolean autoRollback = boolCfg(cfg, "autoRollback", false);
        String packagePath = strCfg(cfg, "packagePath", "");
        boolean uploadExtract = boolCfg(cfg, "uploadExtract", true);
        String uploadTargetDir = strCfg(cfg, "uploadTargetDir", "");
        String deployType = strCfg(cfg, "deployType", "");
        ProjectTypeProvider type = projectTypeRouter.resolveOrGeneric(project.getProjectType());

          // 包来源由项目类型声明：Git 类型先构建，再走本类型正常的投放与重启。
          // 构建必须放在任务线程里：克隆 + Maven 构建动辄几十秒，超过前端 HTTP 的 30s 超时，
          // 放在请求线程会让 UI 拿不到 taskId（表现为「操作触发失败」且没有任务面板）。
        String source = strCfg(cfg, "source", "").trim();
        boolean gitSource = ProjectTypeProvider.SOURCE_GIT.equalsIgnoreCase(source)
                || (source.isEmpty() && isBlank(packagePath)
                        && ProjectTypeProvider.SOURCE_GIT.equals(type.sourceKind()));

        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfgStep = springConfigStep(win, project);
        if (cfgStep != null) {
            steps.add(cfgStep);
        }
        if (gitSource) {
              // 构建产物路径要到构建结束才确定，先占住步骤位；成功后再把投放/重启步骤追加到同一个列表，
              // 任务执行器按索引遍历，能继续执行追加进来的步骤。
            steps.add(new ProjectStep("拉取源码并构建", "", 8, () -> {
                ReturnResult<String> built = prepareGitPackage(ctx);
                if (!built.isOk()) {
                    throw new IllegalStateException(built.getMsg());
                }
                String builtPackage = built.getData();
                appendDeploySteps(steps, win, ctx, project, type, builtPackage,
                        autoBackup, autoExtract, autoRestart, autoRollback,
                        uploadExtract, uploadTargetDir, deployType);
                return List.of("构建产物: " + builtPackage);
            }));
        } else {
            appendDeploySteps(steps, win, ctx, project, type, packagePath,
                    autoBackup, autoExtract, autoRestart, autoRollback,
                    uploadExtract, uploadTargetDir, deployType);
        }
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "upgrade", steps, type.runtimeProcess() ? "running" : null);
        return task(taskId);
    }

      /**
       * 追加「停服 → 备份 → 投放 → 重启/健康检查 → 记录 PID」步骤。
       *
       * <p>Git 类型要等构建成功后才能确定包路径，因此这段编排抽成方法，由调用方决定何时追加。</p>
       *
       * @param steps           步骤列表（直接追加）
       * @param pf              平台命令提供者
       * @param ctx             操作上下文
       * @param project         项目
       * @param type            项目类型实现
       * @param packagePath     产物路径；为空表示只重启不投放
       * @param autoBackup      是否自动备份
       * @param autoExtract     是否自动解压
       * @param autoRestart     是否自动重启
       * @param autoRollback    是否健康检查失败自动回滚
       * @param uploadExtract   产物是否解压
       * @param uploadTargetDir 产物投放目录
       * @param deployType      部署类型
       */
    private void appendDeploySteps(List<ProjectStep> steps, ProjectPlatformProvider pf, OperationContext ctx,
                                   MsServerProject project, ProjectTypeProvider type, String packagePath,
                                   boolean autoBackup, boolean autoExtract, boolean autoRestart,
                                   boolean autoRollback, boolean uploadExtract, String uploadTargetDir,
                                   String deployType) {
        String path = project.getProjectPath();
          // 先停服：运行中的进程会占用目标文件（尤其 Windows 上 jar 被 JVM 锁），不先停则备份/投放/解压必失败
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(pf, path, project.getStopScript()), 1));
        }
        if (autoBackup) {
            steps.add(new ProjectStep("备份当前版本", backupCommand(pf, path), 2));
        }
        if (!isBlank(packagePath)) {
            ProjectPlacementContext pc = new ProjectPlacementContext(
                    path,
                    project.getMainEntry(),
                    project.getLibDir(),
                    packagePath,
                    isArchive(packagePath),
                    autoExtract,
                    uploadExtract,
                    uploadTargetDir,
                    deployType);
            steps.addAll(placementSteps(pf, ctx, type, pc));
        }
          // 没有常驻进程的类型（模型仓库）无需重启，也没有可回写的 PID
        if (!type.runtimeProcess()) {
            return;
        }
          // 启动前补齐日志目录：首次发布时 logs/ 尚不存在，而启动脚本把标准输出重定向到
          // logs/app.log，shell 在重定向阶段就失败、java 根本没起来，但脚本末尾的
          // echo $! > app.pid 仍返回 0，任务于是报「成功」且状态 running（实测踩到）。
        String logFile = ProjectScripts.resolveLogFile(project.getLogPath(), path);
        String logDir = dirName(logFile);
        if (!isBlank(logDir)) {
            steps.add(new ProjectStep("准备日志目录", pf.wrap(path, pf.ensureDir(logDir)), 1));
        }
        if (autoRollback && !isBlank(project.getStatusScript())) {
            steps.add(new ProjectStep("重启并健康检测（失败自动回滚）",
                    guardedRestartCommand(pf, path, project, autoRestart), 3));
        } else if (autoRestart) {
            if (!isBlank(project.getRestartScript())) {
                steps.add(new ProjectStep("重启服务",
                        buildCommand(pf, path, project.getRestartScript()), 3));
            } else if (!isBlank(project.getStopScript()) && !isBlank(project.getStartScript())) {
                steps.add(new ProjectStep("停止服务",
                        buildCommand(pf, path, project.getStopScript()), 2));
                steps.add(new ProjectStep("启动服务",
                        buildCommand(pf, path, project.getStartScript()), 3));
            }
              // 重启后必须由项目自己的状态脚本确认「真的在跑」：只看退出码不够，
              // 启动脚本末尾的 echo 即使 java 没起来也会返回 0。自动回滚分支已内置健康检查，
              // 这里不重复；autoRestart=false 时服务本就应停着，也不校验。
            if (!isBlank(project.getStatusScript())) {
                steps.add(verifyRunningStep(pf, ctx, project, logFile));
            }
        }
        steps.add(pidStep(pf, path, type));
    }

      /**
       * 构造「校验运行状态」步骤：执行项目状态脚本，输出必须包含 {@code running}。
       *
       * <p>把「启动脚本返回 0」与「服务真的在跑」区分开：状态脚本是项目自己的判定依据
       * （Java 用 {@code app.pid} + {@code kill -0}，Nginx 用 master pid），比任何通用猜测可靠。</p>
       *
       * @param pf      平台命令提供者
       * @param ctx     操作上下文
       * @param project 项目
       * @param logFile 日志文件（失败提示用）
       * @return 校验步骤
       */
    private ProjectStep verifyRunningStep(ProjectPlatformProvider pf, OperationContext ctx,
                                          MsServerProject project, String logFile) {
        String command = buildCommand(pf, project.getProjectPath(), project.getStatusScript());
        return new ProjectStep("校验运行状态", "", 1, () -> {
            ReturnResult<java.util.List<MsExecLog>> result = execService.runCommand(
                    java.util.Collections.singletonList(ctx.hostId), command, 30, null);
            if (!result.isOk() || anyExecFailed(result.getData())) {
                throw new IllegalStateException("状态脚本执行失败: " + result.getMsg());
            }
            String output = result.getData().get(0).getMsExecLogOutput();
            String text = output == null ? "" : output.trim();
            if (!text.toLowerCase().contains("running")) {
                throw new IllegalStateException("启动后状态校验未通过（状态脚本输出: "
                          + (text.isEmpty() ? "空" : text) + "），请查看日志 " + logFile);
            }
            return java.util.List.of("运行状态: " + text);
        });
    }

      /**
       * 取路径的父目录（兼容 / 与 \）。
       *
       * @param file 文件路径
       * @return 父目录；无分隔符时返回空串
       */
    private static String dirName(String file) {
        if (file == null || file.isBlank()) {
            return "";
        }
        String normalized = file.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        if (idx < 0) {
            return "";
        }
        return idx == 0 ? "/" : normalized.substring(0, idx);
    }

      /**
       * 按项目类型声明的投放计划生成步骤。
       *
       * <p>类型只声明「投到哪、叫什么、是否建目录、失败是否还原重启」，执行细节统一在这里：
       * 优先 SFTP 重命名（同文件系统内即移动），SDK 不可用才回退平台命令。</p>
       *
       * <p>投放失败必须检查命令结果：{@code runCommand} 外层常常是成功的，真正的失败只体现在
       * 退出码上。若忽略它，目标文件已被删除却仍报「步骤成功」，编排器会继续去重启，
       * 最终表现为「健康检查超时、自动回滚也没跑」。</p>
       *
       * @param pf   平台命令提供者
       * @param ctx  操作上下文
       * @param type 项目类型实现
       * @param pc   投放上下文
       * @return 投放步骤
       */
    private List<ProjectStep> placementSteps(ProjectPlatformProvider pf, OperationContext ctx,
                                             ProjectTypeProvider type, ProjectPlacementContext pc) {
        List<ProjectStep> steps = new ArrayList<>();
        for (ProjectPlacement placement : type.placements(pc)) {
            if (placement.kind() == ProjectPlacement.Kind.EXTRACT) {
                steps.add(new ProjectStep(placement.title(),
                        extractCommand(pf, placement.targetDir(), pc.packagePath()), 2));
                continue;
            }
            steps.add(new ProjectStep(placement.title(), "", 2, () -> {
                String src = pc.packagePath().replace('\\', '/');
                String dir = placement.targetDir().replace('\\', '/');
                String dst = dir + "/" + placement.fileName();
                  // 源包与目标为同一文件时必须直接跳过：cp -f 会报「are the same file」，
                  // 随后的 SFTP rename 也会因源/目标相同而失败。典型场景：升级包就放在项目目录
                  // 且文件名与主入口一致（node2 批量升级实测）。文件已在目标位置，视为投放成功。
                if (samePath(src, dst)) {
                    return java.util.List.of("源包与目标为同一文件，跳过复制: " + dst);
                }
                if (placement.createTargetDir()) {
                      // 首次升级时 lib 目录往往还不存在，不建目录投放会失败
                    sftpService.mkdir(ctx.hostId, null, dir);
                }
                  // 投放必须是「复制」而不是「移动」：源包被移走后，同一个 packagePath
                  // 给第二个项目或二次升级就会报文件不存在（实测升级后上传目录里的包已消失），
                  // 而且 SDK 路径移动、命令回退路径复制，两条路径的副作用还不一致。
                  // 因此先走平台命令复制（cp -f / Copy-Item），源包始终保留。
                String placeCmd = placeCommand(pf, dir, pc.packagePath(), placement.fileName());
                ReturnResult<java.util.List<MsExecLog>> copied =
                        execService.runCommand(java.util.Collections.singletonList(ctx.hostId),
                                placeCmd, 60, null);
                if (copied.isOk() && !anyExecFailed(copied.getData())) {
                    return java.util.List.of("已复制到 " + dst + "（保留源包）");
                }
                String cmdWhy = copied.isOk() ? execFailureDetail(copied.getData()) : copied.getMsg();
                  // 命令通道可用、只是复制失败（最典型：目标 jar 仍被上一个进程占用，写入被拒）时，
                  // 绝不能退化成「重命名源包」：批量升级让多个项目共用同一个源包，源包一旦被移动，
                  // 后续项目就只剩「文件不存在」，整批升级必然失败（实测 38 在批量升级里因此失败）。
                  // 这里改为短暂等待后重试复制，源包始终保留。
                if (copied.isOk()) {
                    for (int retry = 1; retry <= PLACE_COPY_RETRY; retry++) {
                        sleepQuietly(PLACE_COPY_RETRY_INTERVAL_MS);
                        ReturnResult<java.util.List<MsExecLog>> again =
                                execService.runCommand(java.util.Collections.singletonList(ctx.hostId),
                                        placeCmd, 60, null);
                        if (again.isOk() && !anyExecFailed(again.getData())) {
                            return java.util.List.of("已复制到 " + dst + "（第 " + retry
                                      + " 次重试成功，保留源包；首次失败: " + cmdWhy + "）");
                        }
                        cmdWhy = again.isOk() ? execFailureDetail(again.getData()) + "（第 " + retry + " 次重试）"
                                : again.getMsg();
                    }
                    if (placement.restoreOnFailure()) {
                        String restoreNote = restoreAndRestart(pf, ctx, pc.projectPath(), ctx.project);
                        throw new IllegalStateException(placement.title() + "失败；" + restoreNote
                                  + "复制重试 " + PLACE_COPY_RETRY + " 次后仍失败=" + cmdWhy);
                    }
                    throw new IllegalStateException(placement.title() + "失败: " + cmdWhy);
                }
                  // 命令通道不可用（如仅开 SFTP 的主机）才退回重命名：这条路径必然移动源包，
                  // 属于次优但唯一可行的手段，会在步骤日志里写明。
                ReturnResult<Void> mv = sftpService.rename(ctx.hostId, null, src, dst);
                if (!mv.isOk()) {
                      // 多数 SFTP 实现不支持覆盖式 rename（目标已存在的升级场景必然失败）：
                      // 先删目标再 rename，仍走 SDK。
                    sftpService.delete(ctx.hostId, null, dst, false);
                    mv = sftpService.rename(ctx.hostId, null, src, dst);
                }
                if (mv.isOk()) {
                    return java.util.List.of("命令投放不可用（" + cmdWhy + "），已回退 sftp rename（源包被移动）: "
                              + src + " -> " + dst);
                }
                  // cp -f 是就地截断写入，失败时目标可能已被写坏，仍需按备份还原并重启止损
                if (placement.restoreOnFailure()) {
                    String restoreNote = restoreAndRestart(pf, ctx, pc.projectPath(), ctx.project);
                    throw new IllegalStateException(placement.title() + "失败；" + restoreNote
                              + "命令投放=" + cmdWhy + "；SFTP rename=" + mv.getMsg());
                }
                throw new IllegalStateException(placement.title() + "失败: " + cmdWhy
                          + "；SFTP rename=" + mv.getMsg());
            }));
        }
        return steps;
    }

      /**
       * 静默等待：投放复制失败后释放被占用的目标文件需要时间。
       *
       * @param millis 等待毫秒
       */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

      /**
       * 提炼命令失败原因：执行失败的真实报错只在日志的 error/output 里，
       * 只报「退出码非 0」会让运维完全看不出原因
       * （实测投放失败被这个空泛信息挡了两轮才定位到 shell 选错）。
       *
       * @param logs 执行日志
       * @return 带退出码与首行错误信息的简述
       */
    private static String execFailureDetail(java.util.List<MsExecLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return "退出码非 0（无执行日志）";
        }
        MsExecLog first = logs.get(0);
        String why = first.getMsExecLogError();
        if (why == null || why.isBlank()) {
            why = first.getMsExecLogOutput();
        }
        String line = why == null ? "" : why.replace("\r", "").split("\n", 2)[0].trim();
        if (line.length() > 200) {
            line = line.substring(0, 200);
        }
        return "退出码 " + first.getMsExecLogExitCode() + (line.isEmpty() ? "" : "：" + line);
    }

      /**
       * 判断两个远端路径是否指向同一文件。
       *
       * <p>只做字面规范化（分隔符、重复斜杠、结尾斜杠），不访问目标机：
       * 投放前就能识别「源包就是目标文件」的情况，避免 {@code cp} 报 same file。</p>
       *
       * @param a 路径 A
       * @param b 路径 B
       * @return 规范化后相等时为 {@code true}
       */
    private static boolean samePath(String a, String b) {
        String x = normalizeRemotePath(a);
        return !x.isEmpty() && x.equals(normalizeRemotePath(b));
    }

      /**
       * 远端路径字面规范化。
       *
       * @param path 原始路径
       * @return 统一使用 {@code /}、去掉重复与结尾斜杠后的路径
       */
    private static String normalizeRemotePath(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim().replace('\\', '/');
        while (p.contains("//")) {
            p = p.replace("//", "/");
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    @Override
    @SuppressWarnings("unchecked")
      /**
       * git Pull And Deploy
       * @param id 长整数
       * @return 返回值
      */
    public ReturnResult<String> gitPullAndDeploy(Long id) {
          // 与普通升级共用同一条链路：类型决定投放与重启，这里只声明「包来自 Git 构建」。
        Object global = readCfg(CFG_GLOBAL_KEY, defaultGlobalConfig());
        Map<String, Object> cfg = global instanceof Map
                ? new java.util.LinkedHashMap<>((Map<String, Object>) global)
                : defaultGlobalConfig();
        cfg.put("source", ProjectTypeProvider.SOURCE_GIT);
        return runUpgrade(id, cfg);
    }

      /**
       * 拉取源码 → Maven 构建 → 上传构建产物，返回目标主机上的构建产物绝对路径。
       *
       * <p>只负责「把包做出来」，不碰投放与重启：备份、投放、健康检查仍走
       * {@link #runUpgrade(Long, Map)} 的统一编排，避免 Git 发布维护第二套发布流程。
       * 管理端无需预装 Maven，缺失时自动下载。</p>
       *
       * @param ctx 操作上下文
       * @return 目标主机上的构建产物路径
       */
    private ReturnResult<String> prepareGitPackage(OperationContext ctx) {
        MsServerProject project = ctx.project;

          // 1. 校验 Git 配置
        if (isBlank(project.getGitUrl())) {
            return ReturnResult.error("项目未配置 Git 仓库地址，请在项目配置中填写");
        }
        String gitUrl = project.getGitUrl().trim();
        if (gitUrl.startsWith("git@") || gitUrl.startsWith("ssh://")) {
            return ReturnResult.error("暂不支持 SSH 方式的 Git 仓库，请使用 HTTPS 地址（如 https://github.com/org/repo.git）");
        }

          // 2. 创建本地工作区
        Path workspace = Paths.get(System.getProperty("java.io.tmpdir"),
                "git-deploy", String.valueOf(project.getId()));
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            return ReturnResult.error("创建工作目录失败: " + e.getMessage());
        }

          // 3. 克隆或拉取
        boolean isClone = !Files.exists(workspace.resolve(".git"));
        try {
            if (isClone) {
                  // 清空旧工作区（可能残留上次失败的克隆）
                deleteDirectory(workspace.toFile());
                Files.createDirectories(workspace);
                log.info("[Git发布] 克隆仓库: {} -> {}", gitUrl, workspace);
                GitClient.Builder builder = GitClient.builder()
                        .remoteUrl(gitUrl)
                        .localPath(workspace);
                if ("token".equalsIgnoreCase(project.getGitAuthType())
                        && !isBlank(project.getGitToken())) {
                    builder.credentials("oauth2", project.getGitToken());
                }
                GitClient cloned = (GitClient) builder.build().cloneOp().execute();
                  // 切换到配置的分支
                String branch = project.getGitBranch();
                if (!isBlank(branch)) {
                    cloned.getGit().checkout().setName(branch).call();
                    log.info("[Git发布] 切换分支到: {}", branch);
                }
                cloned.close();
            } else {
                log.info("[Git发布] 拉取仓库更新: {}", workspace);
                GitClient.Builder builder = GitClient.builder()
                        .remoteUrl(gitUrl)
                        .localPath(workspace);
                if ("token".equalsIgnoreCase(project.getGitAuthType())
                        && !isBlank(project.getGitToken())) {
                    builder.credentials("oauth2", project.getGitToken());
                }
                GitClient client = builder.build().open();
                  // 切换到配置的分支（如果不同）
                String branch = project.getGitBranch();
                if (!isBlank(branch)) {
                    String currentBranch = client.getRepository().getBranch();
                    if (!branch.equals(currentBranch)) {
                        client.getGit().checkout().setName(branch).call();
                        log.info("[Git发布] 切换分支 {} -> {}", currentBranch, branch);
                    }
                }
                client.pull(null, null);
                client.close();
            }
        } catch (Exception e) {
            log.error("[Git发布] Git 操作失败", e);
            return ReturnResult.error("Git 操作失败: " + e.getMessage());
        }

          // 4. 查找 pom.xml
        Path pomPath = findPomXml(workspace);
        if (pomPath == null) {
            return ReturnResult.error("未找到 pom.xml，该项目可能不是 Maven 项目");
        }

          // 5. 确保 Maven 可用（自动检测 / 自动下载）
        String mavenHome;
        try {
            mavenHome = ensureMavenAvailable(project);
        } catch (Exception e) {
            log.error("[Git发布] Maven 准备失败", e);
            return ReturnResult.error("Maven 准备失败: " + e.getMessage());
        }

          // 6. Maven 构建
        String goalsStr = isBlank(project.getGitBuildGoals())
                ? "clean,package" : project.getGitBuildGoals();
        String[] goals = goalsStr.split(",");
        boolean skipTests = project.getGitSkipTests() == null || project.getGitSkipTests();

        log.info("[Git发布] 执行 Maven 构建: {} [goals={}, skipTests={}]",
                pomPath, goalsStr, skipTests);
        MavenCompileResult buildResult;
        try {
            buildResult = MavenClient.create()
                    .projectPath(pomPath.toString())
                    .goal(goals)
                    .skipTests(skipTests)
                    .mavenHome(mavenHome)
                    .javaHome(project.getJdkHome())
                    .execute();
        } catch (Exception e) {
            log.error("[Git发布] Maven 构建异常", e);
            return ReturnResult.error("Maven 构建异常: " + e.getMessage());
        }

        if (!buildResult.isSuccess()) {
            String errors = String.join("; ", buildResult.getErrors());
            return ReturnResult.error("Maven 构建失败: " + errors);
        }

          // 7. 获取构建产物
        String artifactPath = buildResult.getMainArtifact();
        if (isBlank(artifactPath)) {
            return ReturnResult.error("Maven 构建完成但未找到产物（jar/war），请检查项目打包配置");
        }
        log.info("[Git发布] 构建产物: {}", artifactPath);

          // 8. 上传到目标主机
        if (isBlank(project.getProjectPath())) {
            return ReturnResult.error("项目地址未配置，无法上传构建产物");
        }
        String uploadDir = project.getProjectPath() + "/uploads";
        ReturnResult<Void> mk = sftpService.mkdir(ctx.hostId, null, uploadDir);
        if (!mk.isOk()) {
            return ReturnResult.error("创建上传目录失败: " + mk.getMsg());
        }
        File artifactFile = new File(artifactPath);
        MultipartFile multipartFile = createMultipartFile(artifactFile);
        ReturnResult<String> up = sftpService.upload(ctx.hostId, null, uploadDir, multipartFile);
        if (!up.isOk()) {
            return ReturnResult.error("上传构建产物失败: " + up.getMsg());
        }
        String remotePackage = up.getData();
        log.info("[Git发布] 产物已上传: {}", remotePackage);
        return ReturnResult.ok(remotePackage);
    }

    @Override
    public ReturnResult<Integer> countLibFiles(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (!projectTypeRouter.resolveOrGeneric(project.getProjectType()).supportsLibUpgrade()) {
            return ReturnResult.error("仅分离式项目支持依赖计数");
        }
        String libDir = !isBlank(project.getLibDir()) ? project.getLibDir() : "lib";
        String libPath = (project.getProjectPath().endsWith("/")
                ? project.getProjectPath() : project.getProjectPath() + "/") + libDir;
          // 统计 lib 目录下的 jar 文件数
        String cmd = "find '" + libPath.replace("'", "'\\''") + "' -maxdepth 1 -name '*.jar' 2>/dev/null | wc -l";
        try {
            ReturnResult<java.util.List<MsExecLog>> result = execService.runCommand(
                    java.util.Collections.singletonList(ctx.hostId), cmd, 30, null);
            if (!result.isOk() || result.getData() == null || result.getData().isEmpty()) {
                return ReturnResult.error("执行依赖计数失败");
            }
            String output = result.getData().get(0).getMsExecLogOutput();
            if (output == null) {
                return ReturnResult.error("依赖计数无输出");
            }
            int count = Integer.parseInt(output.trim());
            return ReturnResult.ok(count);
        } catch (Exception e) {
            return ReturnResult.error("依赖计数异常: " + e.getMessage());
        }
    }

      /**
       * 查找工作区中的 pom.xml（根目录或一级子目录）
       *
       * @param workspace 工作区路径
       * @return pom.xml 路径，未找到返回 null
       */
    private Path findPomXml(Path workspace) {
          // 根目录
        Path rootPom = workspace.resolve("pom.xml");
        if (Files.exists(rootPom)) {
            return rootPom;
        }
          // 一级子目录（兼容 monorepo / 项目在子目录中的场景）
        try (var entries = Files.list(workspace)) {
            return entries.filter(Files::isDirectory)
                    .map(dir -> dir.resolve("pom.xml"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

      /**
       * 将本地文件包装为 MultipartFile（供 SFTP 上传使用）
       *
       * @param file 本地文件
       * @return MultipartFile 实例
       */
    private MultipartFile createMultipartFile(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String name = file.getName();
            return new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return name;
                }

                @Override
                public String getContentType() {
                    return "application/octet-stream";
                }

                @Override
                public boolean isEmpty() {
                    return bytes.length == 0;
                }

                @Override
                public long getSize() {
                    return bytes.length;
                }

                @Override
                public byte[] getBytes() {
                    return bytes;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(bytes);
                }

                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                    Files.write(dest.toPath(), bytes);
                }
            };
        } catch (IOException e) {
            throw new IllegalStateException("读取构建产物失败: " + file.getAbsolutePath(), e);
        }
    }

      /**
       * 递归删除目录
       *
       * @param dir 目录
       */
    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try (var walk = Files.walk(dir.toPath())) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.warn("[Git发布] 清理工作区失败: {}", dir.getAbsolutePath(), e);
        }
    }

      /**
       * 确保 Maven 可用：优先使用项目配置的 mavenHome，其次检测系统环境，
       * 都不可用时自动下载 Maven 到临时目录。
       *
       * @param project 项目（含 Maven 配置）
       * @throws Exception 下载或解压失败
       */
    private String ensureMavenAvailable(MsServerProject project) throws Exception {
          // 1. 项目配置的 mavenHome
        if (!isBlank(project.getMavenHome())) {
            File mh = new File(project.getMavenHome().trim());
            if (mh.exists() && new File(mh, "bin").exists()) {
                log.info("[Git发布] 使用项目配置的 Maven: {}", mh.getAbsolutePath());
                return mh.getAbsolutePath();
            }
            log.warn("[Git发布] 项目配置的 Maven 路径无效: {}，尝试自动检测/下载", project.getMavenHome());
        }

          // 2. 已设置的系统属性
        String sysHome = System.getProperty("maven.home");
        if (!isBlank(sysHome) && new File(sysHome).exists()) {
            return sysHome;
        }

          // 3. 环境变量 MAVEN_HOME / M2_HOME
        String envHome = System.getenv("MAVEN_HOME");
        if (isBlank(envHome)) {
            envHome = System.getenv("M2_HOME");
        }
        if (!isBlank(envHome) && new File(envHome).exists()) {
            return envHome;
        }

          // 4. PATH 中查找 mvn
        String pathEnv = System.getenv("PATH");
        if (!isBlank(pathEnv)) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (new File(dir, "mvn.cmd").exists() || new File(dir, "mvn").exists()) {
                    File mavenDir = new File(dir).getParentFile();
                    if (mavenDir != null && new File(mavenDir, "lib").exists()) {
                        return mavenDir.getAbsolutePath();
                    }
                }
            }
        }

          // 5. 自动下载
        return downloadMaven(project);
    }

      /**
       * 自动下载 Maven 到临时目录并设置 maven.home 系统属性
       *
       * @param project 项目（含 Maven 版本配置）
       * @throws Exception 下载或解压失败
       */
    private String downloadMaven(MsServerProject project) throws Exception {
        String version = isBlank(project.getMavenVersion())
                ? "3.9.9" : project.getMavenVersion().trim();
        String artifactName = "apache-maven-" + version;
        String archiveName = artifactName + "-bin.zip";
        String url = "https://archive.apache.org/dist/maven/maven-3/"
                  + version + "/binaries/" + archiveName;

        Path mavenRoot = Paths.get(System.getProperty("java.io.tmpdir"),
                "git-deploy", "maven");
        Path archivePath = mavenRoot.resolve(archiveName);
        Path mavenHome = mavenRoot.resolve(artifactName);

          // 已下载过则直接使用
        if (Files.exists(mavenHome.resolve("bin"))) {
              // 确保 mvn 脚本有执行权限（旧版本下载的可能没有）
            Path mvnScript = mavenHome.resolve("bin").resolve("mvn");
            if (Files.exists(mvnScript)) {
                try {
                    mvnScript.toFile().setExecutable(true);
                } catch (Exception e) {
                    log.warn("[Git发布] 设置 mvn 脚本可执行权限失败: {}", mvnScript, e);
                }
            }
            log.info("[Git发布] 复用已下载的 Maven: {}", mavenHome);
            return mavenHome.toString();
        }

        Files.createDirectories(mavenRoot);
        log.info("[Git发布] Maven 未检测到，自动下载: {}", url);

          // 下载
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<Path> resp = http.send(req,
                HttpResponse.BodyHandlers.ofFile(archivePath));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Maven 下载失败，HTTP " + resp.statusCode() + ": " + url);
        }
        log.info("[Git发布] Maven 下载完成: {} ({} bytes)", archivePath, Files.size(archivePath));

          // 解压
        log.info("[Git发布] 解压 Maven 到: {}", mavenRoot);
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path out = mavenRoot.resolve(entry.getName()).normalize();
                if (!out.startsWith(mavenRoot)) {
                    continue; // 防止 zip slip
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream is = zip.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(out.toFile())) {
                        is.transferTo(fos);
                    }
                }
            }
        }

          // 清理下载的压缩包
        Files.deleteIfExists(archivePath);

          // Linux 下设置 mvn 脚本可执行权限（ZipFile 解压不保留 Unix 权限位）
        Path mvnScript = mavenHome.resolve("bin").resolve("mvn");
        if (Files.exists(mvnScript)) {
            try {
                mvnScript.toFile().setExecutable(true);
                log.info("[Git发布] 已设置 mvn 脚本可执行权限: {}", mvnScript);
            } catch (Exception e) {
                log.warn("[Git发布] 设置 mvn 脚本可执行权限失败: {}", mvnScript, e);
            }
        }

        System.setProperty("maven.home", mavenHome.toString());
        log.info("[Git发布] Maven 自动下载完成: {}", mavenHome);
        return mavenHome.toString();
    }

      // ==================== 辅助 ====================

      /**
       * 加载并校验项目、发布、目标主机
       *
       * @param id 项目ID
       * @return 操作上下文
       */
    private ReturnResult<OperationContext> prepare(Long id) {
        MsServerProject project = projectMapper.selectById(id);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        MsServerRelease release = releaseMapper.selectById(project.getReleaseId());
        if (release == null || release.getHostId() == null) {
            return ReturnResult.error("发布或主机不存在");
        }
        MsServerHost targetHost = hostMapper.selectById(release.getHostId());
        if (targetHost == null) {
            return ReturnResult.error("目标主机不存在: " + release.getHostId());
        }
          // 发布模块基于 OpenSSH 执行：主机须开启 SSH（Linux/macOS 默认开，Windows 默认关）
        Boolean sshOn = resolveSshEnabled(targetHost);
        if (!sshOn) {
            return ReturnResult.error("目标主机「" + targetHost.getServerHostName()
                      + "」未开启 SSH（Windows 需启用 OpenSSH 并打开「开启 SSH」开关），无法执行发布操作");
        }
          // 执行前就地展开脚本占位符（__ENTRY__/__LOG__/__PATH__/__APPNAME__），
          // 使内置模板保持通用、按项目字段即时生效
        expandProjectScripts(project);
        OperationContext context = new OperationContext();
        context.project = project;
        context.hostId = release.getHostId();
        context.osType = HostPlatforms.normalize(targetHost.getServerHostOsType());
        context.host = targetHost;
        context.windows = HostPlatforms.WINDOWS.equals(context.osType);
        return ReturnResult.ok(context);
    }

      /**
       * 补齐项目四个脚本的默认模板：仅当对应脚本为空时用内置模板（占位符形式）填充
       *
       * @param project 项目
       */
    private void applyDefaultScripts(MsServerProject project) {
        MsServerRelease release = project.getReleaseId() == null ? null : releaseMapper.selectById(project.getReleaseId());
        MsServerHost host = (release != null && release.getHostId() != null)
                ? hostMapper.selectById(release.getHostId()) : null;
        boolean windows = host != null && "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
        ProjectScripts.Scripts def = projectTypeRouter
                .resolveOrGeneric(project.getProjectType())
                .scripts(windows, project.getMainEntry());
        if (isBlank(project.getStartScript())) {
            project.setStartScript(def.start());
        }
        if (isBlank(project.getStopScript())) {
            project.setStopScript(def.stop());
        }
        if (isBlank(project.getRestartScript())) {
            project.setRestartScript(def.restart());
        }
        if (isBlank(project.getStatusScript())) {
            project.setStatusScript(def.status());
        }
    }

      /**
       * 就地展开项目四个脚本中的占位符（不落库，仅执行期生效）
       *
       * @param project 项目
       */
    private void expandProjectScripts(MsServerProject project) {
        ProjectPlatformProvider pf = projectPlatformRouter.resolveOrPosix(osTypeOfProject(project));
          // 结构化变量（envVars/jvmArgs/cmdArgs）注入 VARS 块 + 展开 __JVMARGS__/__ARGS__，
          // 使「API 创建 / 内置模板」的项目也能生效（此前仅前端保存时注入）
        String start = materializeVars(project.getStartScript(), project, pf);
        String restart = materializeVars(project.getRestartScript(), project, pf);
        project.setStartScript(ProjectScripts.apply(start,
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        project.setStopScript(ProjectScripts.apply(project.getStopScript(),
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        project.setRestartScript(ProjectScripts.apply(restart,
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        project.setStatusScript(ProjectScripts.apply(project.getStatusScript(),
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        // Docker 项目的容器名 / 镜像 / 端口 / 环境变量来自 appConfig，只在执行期展开：
        // 保存时不展开，改了 Docker 配置后无需重新生成脚本即可生效。
        project.setStartScript(DockerProjectSettings.expand(project.getStartScript(), project));
        project.setStopScript(DockerProjectSettings.expand(project.getStopScript(), project));
        project.setRestartScript(DockerProjectSettings.expand(project.getRestartScript(), project));
        project.setStatusScript(DockerProjectSettings.expand(project.getStatusScript(), project));
    }

      /**
       * 判断项目目标主机是否 Windows（按所属发布绑定的主机）
       */
      /**
       * 取项目目标主机归一后的系统类型（供平台 SPI 路由）。
       *
       * @param project 项目
       * @return 系统类型（HostPlatforms 取值域）；主机缺失返回空串
       */
    private String osTypeOfProject(MsServerProject project) {
        MsServerRelease release = project.getReleaseId() == null ? null : releaseMapper.selectById(project.getReleaseId());
        MsServerHost host = (release != null && release.getHostId() != null)
                ? hostMapper.selectById(release.getHostId()) : null;
        return host == null ? "" : HostPlatforms.normalize(host.getServerHostOsType());
    }

      /**
       * 判断项目目标主机是否 Windows（按所属发布绑定的主机）
       *
       * @param project 项目
       * @return 是否 Windows
       */
    private boolean isWindowsProject(MsServerProject project) {
        return HostPlatforms.WINDOWS.equals(osTypeOfProject(project));
    }

      /**
       * 将 envVars/jvmArgs/cmdArgs 注入脚本：构建 VARS 托管块（幂等）并展开参数占位符
       *
       * @param script  脚本（可为空）
       * @param project 项目（读取 envVars/jvmArgs/cmdArgs）
       * @param pf      平台命令提供者（变量块与参数展开的平台差异由其负责）
       * @return 注入后的脚本
       */
    private String materializeVars(String script, MsServerProject project, ProjectPlatformProvider pf) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        List<EnvVar> envs = readEnvList(project.getEnvVars());
        List<String> jvms = readStringList(project.getJvmArgs());
        List<String> args = readStringList(project.getCmdArgs());
        String out = script;
        if (!envs.isEmpty() || !jvms.isEmpty() || !args.isEmpty()) {
            List<String> names = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (EnvVar e : envs) {
                if (!isBlank(e.key)) {
                    names.add(e.key.trim());
                    values.add(e.value == null ? "" : e.value);
                }
            }
            StringBuilder b = new StringBuilder(VARS_BEGIN).append('\n');
            b.append(pf.varsBlock(names, values, jvms, args));
            b.append(VARS_END).append('\n');
            out = injectVarsBlock(out, b.toString());
        }
        out = out.replace("__JVMARGS__", pf.jvmArgsLiteral(jvms))
                .replace("__ARGS__", pf.appArgsLiteral(args));
        // 项目级 JDK：把裸 java 换成该 JDK 的可执行文件（含平台的引号与路径规则）。
        // 放在占位符展开之后，历史脚本同样生效，无需重新生成。
        return pf.applyJava(out, project.getJdkHome());
    }

      /**
       * 幂等注入 VARS 托管块：已存在则替换，不存在则前插 幂等注入 VARS 托管块：已存在则替换，不存在则前置
       * @param block 字符串
       * @param script 字符串
       * @return 返回值
      */
    private String injectVarsBlock(String script, String block) {
        Matcher m = VARS_BLOCK_RE.matcher(script);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(block));
        }
        return block + script;
    }

      /**
       * 根据主机ID回填主机IP，保证发布卡片主机信息完整
       * @param release 释放
       * @return 返回值
      */
    private void fillHostInfo(MsServerRelease release) {
        if (release == null || release.getHostId() == null) {
            return;
        }
        MsServerHost host = hostMapper.selectById(release.getHostId());
        if (host != null) {
            release.setHostIp(host.getServerHostAddress());
        }
    }

      /** 变量托管块标记（与前端 ProjectsView 保持一致） */
    private static final String VARS_BEGIN = "# >>> VARS (auto-managed) >>>";
    private static final String VARS_END = "# <<< VARS (auto-managed) <<<";
      /** 变量托管块匹配（幂等替换用） */
    private static final Pattern VARS_BLOCK_RE =
            Pattern.compile("# >>> VARS \\(auto-managed\\) >>>[\\s\\S]*?# <<< VARS \\(auto-managed\\) <<<\\n?");

      /**
       * 执行前解析变量托管块中的占位符，支持： ${key:default} 默认值、${now(pattern)} 日期格式化、${uuid}、${random(n)}、${upper/lower/length
          } 等。 仅处理托管块，避免误改脚本中正常的 shell / PowerShell 语法； 无法解析的占位符保留原样（交给 shell），不中断执行。
       * @param pf 参数
       * @param script 字符串
       * @return 返回值
      */
    private String resolveScriptPlaceholders(String script, ProjectPlatformProvider pf) {
        if (script == null) {
            return null;
        }
        int b = script.indexOf(VARS_BEGIN);
        if (b < 0) {
            return script;
        }
        int e = script.indexOf(VARS_END, b);
        if (e < 0) {
            return script;
        }
        int eEnd = e + VARS_END.length();
        String block = script.substring(b, eEnd);

          // 收集块内已定义的环境变量作为占位符上下文（KEY -> 原始值）
        Map<String, Object> ctx = new LinkedHashMap<>();
        Pattern pat = Pattern.compile(pf.envPattern());
        for (String line : block.split("\n")) {
            Matcher m = pat.matcher(line.trim());
            if (m.find()) {
                ctx.put(m.group(1), m.group(2));
            }
        }
        PlaceholderSupport support = new PlaceholderSupport();
        support.setResolver(new MapMixSystemPlaceholderResolver(ctx));
        support.setIgnoreUnresolvablePlaceholders(true);
        String resolved = new StringValuePropertyResolver(support).resolvePlaceholders(block);
        return script.substring(0, b) + resolved + script.substring(eEnd);
    }

      /**
       * 构建执行命令。 Linux：bash（cd path && script）；Windows：PowerShell（Set-Location 后执行，PS4.0 不支持 &&）。
       * @param pf 参数
       * @param projectPath 字符串
       * @param script 字符串
       * @return 返回值
      */
    private String buildCommand(ProjectPlatformProvider pf, String projectPath, String script) {
        return pf.wrap(projectPath, resolveScriptPlaceholders(script, pf));
    }

      /**
       * 构造「生成 SpringBoot 专属外部配置」步骤： 写入项目目录下 config/application.properties，Spring Boot 会自动加载 （file:./config/ 的优先级高于 jar 内 application
          .properties，实现覆盖）。
       * @param pf 参数
       * @param project 项目
       * @param windows 是否 Windows
       * @return 步骤；无配置项时返回 null
      */
    private ProjectStep springConfigStep(ProjectPlatformProvider pf, MsServerProject project) {
        List<EnvVar> entries = readEnvList(project.getAppConfig());
        if (entries.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (EnvVar e : entries) {
            if (e.key != null && !e.key.isBlank()) {
                keys.add(e.key);
                values.add(e.value == null ? "" : e.value);
            }
        }
          // 必须在项目目录下执行：否则 config/application.properties 会写到 SSH 默认工作目录
        return new ProjectStep("生成项目配置",
                pf.wrap(project.getProjectPath(), pf.springConfigScript(keys, values)), 1);
    }

      /**
       * 构造「读取 PID」步骤（启动 / 重启 / 升级 / 回滚的最后一步，其输出仅为 PID，供回写卡片）。
       * 经平台包装后在项目目录下读取类型声明的 PID 文件（Java 为 app.pid，Nginx 为 logs/nginx.pid），
       * 避免在 SSH 默认工作目录读到残留文件。
       *
       * @param pf          平台命令提供者
       * @param projectPath 项目地址
       * @param type        项目类型（决定 PID 文件位置）
       * @return 步骤
       */
    private ProjectStep pidStep(ProjectPlatformProvider pf, String projectPath, ProjectTypeProvider type) {
        String pidFile = type == null ? "app.pid" : type.pidFile();
        return new ProjectStep("记录PID", pf.wrap(projectPath, pf.readPid(pidFile)), 1);
    }

      /**
       * 构建备份命令（平台实现由 {@link ProjectPlatformProvider} 提供）。
       *
       * @param pf          平台命令提供者
       * @param projectPath 项目地址
       * @return 备份命令
       */
    private String backupCommand(ProjectPlatformProvider pf, String projectPath) {
        return pf.backup(projectPath, MAX_BACKUPS);
    }

      /**
       * 构建回滚命令（恢复指定/最新备份），平台实现由 {@link ProjectPlatformProvider} 提供。
       *
       * @param pf          平台命令提供者
       * @param projectPath 项目地址
       * @param backupName  备份名；为空表示最新
       * @return 回滚命令
       */
    private String rollbackCommand(ProjectPlatformProvider pf, String projectPath, String backupName) {
        return pf.rollback(projectPath, backupName);
    }

      /**
       * 包装项目任务提交结果：项目已有操作在跑（startTask 返回 空）时给出明确提示，
       * 避免并发「启动/停止/重启/升级」互相竞争、覆盖 app.pid 或产生孤儿进程。
       *
       * @param taskId 任务 标识（可能为 空）
       * @return 成功返回 taskId；项目占用中返回错误
       */
    private ReturnResult<String> task(String taskId) {
        return taskId == null
                ? ReturnResult.error("该项目有操作正在进行，请稍后再试")
                : ReturnResult.ok(taskId);
    }

      /**
       * 记录一次同步项目操作的终态（失败只记日志，不影响操作结果）。
       *
       * @param projectId   项目标识
       * @param projectName 项目名称
       * @param releaseId   发布标识
       * @param operation   操作类型（create/update/delete/copy/toggle/...）
       * @param success     是否成功
       * @param message     结果说明或失败原因
       */
    private void recordSyncOp(Long projectId, String projectName, Long releaseId,
                              String operation, boolean success, String message) {
        try {
            projectOpLogService.recordOne(projectId, projectName, releaseId, operation, success, message);
        } catch (Exception e) {
            log.debug("[release] 记录操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 记录一次「还没建任务就失败」的项目操作（如主机未开 SSH 的前置校验）。
     *
     * <p>这类失败没有 taskId，若不在这里留痕，「所有项目操作都记日志」就漏掉了
     * 用户最需要回看的场景：点了备份/回滚却只得到一个错误提示。</p>
     *
     * @param projectId 项目标识
     * @param operation 操作类型
     * @param message   失败原因
     */
    private void recordPrecheckFailure(Long projectId, String operation, String message) {
        MsServerProject project = projectId == null ? null : projectMapper.selectById(projectId);
        recordSyncOp(projectId,
                project == null ? null : project.getProjectName(),
                project == null ? null : project.getReleaseId(),
                operation, false, message);
    }

      /**
       * 字符串空白判断
       */
      /**
       * 解析主机是否开启 SSH：显式设置取该值；未设置时非 Windows 视为开启、Windows 视为关闭
       *
       * @param host 主机
       * @return 是否开启
       */
    private boolean resolveSshEnabled(MsServerHost host) {
        Integer v = host.getServerHostSshEnabled();
        if (v != null) {
            return v == 1;
        }
        return !"WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
    }

      /**
       * is Blank
       * @param value 字符串
       * @return 返回值
      */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

      // ---------- 升级配置辅助 ----------

      /**
       * default Global Config
       * @return 返回值
      */
    private Map<String, Object> defaultGlobalConfig() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("autoBackup", true);
        m.put("autoExtract", true);
        m.put("autoRestart", true);
        m.put("autoRollback", false);
        m.put("packagePath", "");
        return m;
    }

      /**
       * read Cfg
       * @param defValue 对象
       * @param key 字符串
       * @return 返回值
      */
    private Object readCfg(String key, Object defValue) {
        MsServerConfig cfg = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (cfg == null || isBlank(cfg.getConfigValue())) {
            return defValue;
        }
        try {
            return JSON.readValue(cfg.getConfigValue(), Object.class);
        } catch (Exception e) {
            return defValue;
        }
    }

      /**
       * bool Cfg
       * @param Map<String 参数
       * @param cfg 参数
       * @param defValue 布尔值
       * @param key 字符串
       * @return 返回值
      */
    private boolean boolCfg(Map<String, Object> cfg, String key, boolean defValue) {
        Object v = cfg.get(key);
        if (v == null) {
            return defValue;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

      /**
       * str Cfg
       * @param Map<String 参数
       * @param cfg 参数
       * @param defValue 字符串
       * @param key 字符串
       * @return 返回值
      */
    private String strCfg(Map<String, Object> cfg, String key, String defValue) {
        Object v = cfg.get(key);
        return v == null ? defValue : String.valueOf(v);
    }

      // ---------- 升级包处理 ----------

      /**
       * is Archive
       * @param name 字符串
       * @return 返回值
      */
    private boolean isArchive(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar")
                || n.endsWith(".zip") || n.endsWith(".gz");
    }

      /**
       * extract Command
       * @param packagePath 字符串
       * @param pf 参数
       * @param projectPath 字符串
       * @return 返回值
      */
    private String extractCommand(ProjectPlatformProvider pf, String projectPath, String packagePath) {
        return pf.extract(projectPath, packagePath);
    }

      /**
       * 判断一批执行日志里是否存在失败项。
       *
       * <p>不能只看外层 {@code ReturnResult}：命令投放这类操作外层常常是成功的，
       * 真正的失败只体现在每台主机的退出码上（真实踩过：SFTP 重命名失败后回退命令投放，
       * 外层 ok 但退出码非 0，步骤却报了「成功」）。</p>
       *
       * @param logs 执行日志
       * @return 有任一主机失败时为 {@code true}；日志为空按失败处理（无法确认成功）
       */
    private static boolean anyExecFailed(java.util.List<MsExecLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return true;
        }
        for (MsExecLog log : logs) {
            if (log == null || log.getMsExecLogExitCode() == null || log.getMsExecLogExitCode() != 0) {
                return true;
            }
        }
        return false;
    }

      /**
   * 投放失败后的止损：用最新备份还原文件并把服务重新拉起。
   *
   * <p>为什么必须连服务一起拉起：升级流程是「停服 → 备份 → 投放 → 重启并健康检查」，
   * 投放这步失败后整单中止，第 4 步不会执行。此时文件已还原但进程没起来，
   * 项目停在停止状态 —— 一次失败的升级把在跑的服务弄停，比升级失败本身更严重。</p>
   *
   * @param pf      平台
   * @param ctx     上下文（取 hostId）
   * @param path    项目地址
   * @param project 项目
   * @return 止损执行结果描述，用于拼进异常信息
   */
    private String restoreAndRestart(ProjectPlatformProvider pf, OperationContext ctx, String path,
                                     MsServerProject project) {
        StringBuilder sb = new StringBuilder();
        try {
            execService.runCommand(java.util.Collections.singletonList(ctx.hostId),
                    rollbackCommand(pf, path, null), 120, null);
            sb.append("已用最新备份还原；");
        } catch (Exception e) {
            sb.append("还原备份失败(").append(e.getMessage()).append(")；");
        }
        if (!isBlank(project.getStartScript())) {
            try {
                ReturnResult<java.util.List<MsExecLog>> started = execService.runCommand(
                        java.util.Collections.singletonList(ctx.hostId),
                        buildCommand(pf, path, project.getStartScript()), 180, null);
                boolean startedOk = started.isOk() && !anyExecFailed(started.getData());
                sb.append(startedOk ? "已重新拉起服务" : "重新拉起服务未成功");
                if (startedOk) {
                      // 把真实 PID 写进止损结果：任务虽然失败，服务其实在跑。任务服务据此回写
                      // running + PID，否则库里留着升级前的旧 PID（验收时 node2 复现）。
                    String pid = readProjectPid(pf, ctx, path, project);
                    if (pid != null) {
                        sb.append(" running pid=").append(pid);
                    }
                }
            } catch (Exception e) {
                sb.append("重新拉起服务异常(").append(e.getMessage()).append(")");
            }
        }
        return sb.toString();
    }

      /**
       * 读取项目当前 PID（失败还原重启后的状态对账用）。
       *
       * @param pf      平台命令提供者
       * @param ctx     操作上下文（取 hostId）
       * @param path    项目地址
       * @param project 项目
       * @return PID；读取失败或输出不是纯数字时返回 {@code null}
       */
    private String readProjectPid(ProjectPlatformProvider pf, OperationContext ctx, String path,
                                  MsServerProject project) {
        try {
            String pidFile = projectTypeRouter.resolveOrGeneric(project.getProjectType()).pidFile();
            ReturnResult<java.util.List<MsExecLog>> result = execService.runCommand(
                    java.util.Collections.singletonList(ctx.hostId),
                    pf.wrap(path, pf.readPid(pidFile)), 30, null);
            if (!result.isOk() || anyExecFailed(result.getData())) {
                return null;
            }
            String output = result.getData().get(0).getMsExecLogOutput();
            if (output == null) {
                return null;
            }
            for (String line : output.split("\\R")) {
                String t = line.trim();
                if (t.matches("\\d+")) {
                    return t;
                }
            }
        } catch (Exception e) {
            log.warn("[release] 读取项目 PID 失败: {}", e.getMessage());
        }
        return null;
    }

      /**
       * place Command
       * @param mainEntry 字符串
       * @param packagePath 字符串
       * @param pf 参数
       * @param projectPath 字符串
       * @return 返回值
      */
    private String placeCommand(ProjectPlatformProvider pf, String projectPath, String packagePath, String mainEntry) {
        return pf.place(projectPath, packagePath, mainEntry);
    }

      /**
       * 重启后健康检测，失败则回滚（平台实现由 {@link ProjectPlatformProvider} 提供）。
       *
       * @param pf          平台命令提供者
       * @param projectPath 项目地址
       * @param project     项目
       * @param restart     是否重启（false 时仅健康检查）
       * @return 命令
       */
    private String guardedRestartCommand(ProjectPlatformProvider pf, String projectPath,
                                         MsServerProject project, boolean restart) {
        return pf.guardedRestart(projectPath, project.getRestartScript(), project.getStopScript(),
                project.getStartScript(), project.getStatusScript(), restart, resolveHealthPort(project));
    }

      /**
       * 从项目配置里解析业务端口，供健康检查做端口探活。
       *
       * <p>解析顺序与「实际生效」一致：显式启动参数（{@code --server.port=}）优先于
       * 外部配置文件里的 {@code server.port=}，两者都没有则返回 0（不做端口探活）。
       * 解析失败一律返回 0 而不是抛错——端口只是增强判据，不能因为它让升级流程中断。</p>
       *
       * @param project 项目配置
       * @return 端口号；无法确定时为 0
       */
    private int resolveHealthPort(MsServerProject project) {
        int port = firstPort(SERVER_PORT_ARG, project.getCmdArgs());
        if (port <= 0) {
            port = firstPort(SERVER_PORT_ARG, project.getJvmArgs());
        }
        if (port <= 0) {
            port = firstPort(SERVER_PORT_PROP, project.getAppConfig());
        }
        return port;
    }

      /**
       * 取文本中第一个匹配到的合法端口号。
       *
       * @param pattern 端口正则（首个捕获组为端口）
       * @param text    待解析文本，可为空
       * @return 合法端口号；未匹配或越界时为 0
       */
    private int firstPort(Pattern pattern, String text) {
        if (isBlank(text)) {
            return 0;
        }
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v > 0 && v <= 65535) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                  // 超出 int 范围的数字串直接跳过，继续找下一个候选
            }
        }
        return 0;
    }

      // ---------- 系统服务（Linux systemd）----------

      /**
       * read String List
       * @param json 字符串
       * @return 返回值
      */
    private List<String> readStringList(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        String text = json.trim();
          // 正常来源是前端 JSON.stringify(string[])，即 JSON 数组；但手工写入或历史数据
          // 往往是「--server.port=8112 --spring.profiles.active=prod」这样的裸串。
          // 原实现遇到裸串时 JSON 解析抛错并静默返回空列表 —— 用户填了参数却毫无提示地被丢弃，
          // 表现为「端口/参数没生效」，很难排查。此处改为：非 JSON 数组一律按空白切分。
        if (!text.startsWith("[")) {
            return splitArgs(text);
        }
        try {
            return JSON.readValue(text, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
              // JSON 数组解析失败同样不能静默丢参：剥掉括号与引号后按空白切分，并留下告警
            log.warn("[release] 参数不是合法 JSON 数组，已按空白切分兜底: {}", text);
            return splitArgs(text.replaceAll("[\\[\\]\"]", " "));
        }
    }

      /**
       * 把裸参数串按空白切分为列表。
       *
       * @param text 参数串（如 {@code --server.port=8112 --spring.profiles.active=prod}）
       * @return 非空参数项列表
       */
    private List<String> splitArgs(String text) {
        if (isBlank(text)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

      /**
       * read Env List
       * @param json 字符串
       * @return 返回值
      */
    private List<EnvVar> readEnvList(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = JSON.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
            List<EnvVar> out = new ArrayList<>();
            raw.forEach(m -> out.add(new EnvVar(
                    String.valueOf(m.get("key")),
                    m.get("value") == null ? "" : String.valueOf(m.get("value")))));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

      /** 环境变量键值（仅服务启动器内部使用） */
    private static final class EnvVar {
        private final String key;
        private final String value;

        private EnvVar(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

      // ==================== 发布管理增强 ====================

      /**
       * 页面布局在系统配置表中的键前缀
       */
    private static final String CFG_LAYOUT_PREFIX = "ui.layout.";

    @Override
    @Transactional(rollbackFor = Exception.class)
      /**
       * copy Projects
       * @param projectIds 参数
       * @param releaseIds 参数
       * @return 返回值
      */
    public ReturnResult<Map<String, Object>> copyProjects(List<Long> projectIds, List<Long> releaseIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return ReturnResult.error("请选择要复制的项目");
        }
        if (releaseIds == null || releaseIds.isEmpty()) {
            return ReturnResult.error("请选择目标服务器");
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long projectId : projectIds) {
            MsServerProject source = projectMapper.selectById(projectId);
            if (source == null) {
                failed.add(Map.of("projectId", projectId, "reason", "项目不存在"));
                recordSyncOp(projectId, null, null, "copy", false, "项目不存在");
                continue;
            }
            for (Long releaseId : releaseIds) {
                if (releaseId == null || releaseMapper.selectById(releaseId) == null) {
                    failed.add(Map.of("projectId", projectId, "releaseId", String.valueOf(releaseId),
                            "reason", "目标发布不存在"));
                    recordSyncOp(projectId, source.getProjectName(), releaseId, "copy", false,
                            "目标发布不存在");
                    continue;
                }
                MsServerProject copy = new MsServerProject();
                org.springframework.beans.BeanUtils.copyProperties(source, copy);
                copy.setId(null);
                copy.setReleaseId(releaseId);
                copy.setPid(null);
                copy.setStatus("stopped");
                copy.setRunningSince(null);
                copy.setServiceName(null);
                copy.setCreateTime(LocalDateTime.now());
                copy.setUpdateTime(LocalDateTime.now());
                  // 复制到的目标发布级 SkyWalking APM（源项目自己的 APM 选项优先保留）
                applyReleaseApm(copy);
                if (copy.getScheduleEnabled() == null) {
                    copy.setScheduleEnabled(false);
                }
                if (isBlank(copy.getScheduleStatus())) {
                    copy.setScheduleStatus("NONE");
                }
                projectMapper.insert(copy);
                recordSyncOp(copy.getId(), copy.getProjectName(), releaseId, "copy", true,
                        "复制自项目 " + source.getProjectName());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sourceProjectId", projectId);
                row.put("projectId", copy.getId());
                row.put("releaseId", releaseId);
                row.put("projectName", copy.getProjectName());
                copied.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", copied.size());
        out.put("copied", copied);
        out.put("failed", failed);
        return ReturnResult.ok(out);
    }

    @Override
    public ReturnResult<Map<String, Object>> probeProjectEndpoints(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("swagger", false);
        out.put("actuator", false);
        int port = resolveHealthPort(project);
        out.put("port", port);
        if (port <= 0) {
            out.put("message", "项目未配置 server.port，无法探测 Swagger/Actuator");
            return ReturnResult.ok(out);
        }
        String base = "http://127.0.0.1:" + port;
        /*
         * 探测命令必须跑在「目标主机」上，所以不能在后端直接开 URL 连接：
         * 项目跑在远端主机（如 192.168.50.101），后端所在机器访问 127.0.0.1 只会打到后端自己。
         * POSIX 用 curl，Windows 用 Invoke-WebRequest，两者都把结果压成单行 KEY=值，
         * 响应体先 base64 再输出，避免 JSON 里的换行把行号切乱。
         */
        String command = ctx.windows ? windowsProbeCommand(base) : posixProbeCommand(base);
        ReturnResult<List<MsExecLog>> result = execService.runCommand(
                Collections.singletonList(ctx.hostId), command, 30, null);
        if (!result.isOk() || result.getData() == null || result.getData().isEmpty()) {
            out.put("message", "端点探测失败: " + result.getMsg());
            return ReturnResult.ok(out);
        }
        String output = result.getData().get(0).getMsExecLogOutput();
        String docsCode;
        String uiCode;
        String healthJson;
        String metricsJson;
        if (ctx.windows) {
            Map<String, String> kv = new LinkedHashMap<>();
            for (String line : (output == null ? "" : output).replace("\r", "").split("\n")) {
                int idx = line.indexOf('=');
                if (idx > 0) {
                    kv.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
            }
            docsCode = kv.getOrDefault("DOCS", "");
            uiCode = kv.getOrDefault("UI", "");
            healthJson = decodeBase64(kv.get("HEALTH"));
            metricsJson = decodeBase64(kv.get("METRICS"));
        } else {
            String[] lines = (output == null ? "" : output).replace("\r", "").split("\n", -1);
            docsCode = lines.length > 0 ? lines[0].trim() : "";
            uiCode = lines.length > 1 ? lines[1].trim() : "";
            healthJson = lines.length > 2 ? lines[2].trim() : "";
            metricsJson = lines.length > 3 ? lines[3].trim() : "";
        }
        boolean swagger = "200".equals(docsCode) || "200".equals(uiCode);
        boolean actuator = healthJson.startsWith("{") || metricsJson.startsWith("{");
        out.put("swagger", swagger);
        out.put("actuator", actuator);
        String hostAddress = ctx.host == null || isBlank(ctx.host.getServerHostAddress())
                ? "127.0.0.1" : ctx.host.getServerHostAddress();
        out.put("swaggerUrl", "http://" + hostAddress + ":" + port + "/swagger-ui/index.html");
        out.put("swaggerApiUrl", "http://" + hostAddress + ":" + port + "/v3/api-docs");
        out.put("actuatorUrl", "http://" + hostAddress + ":" + port + "/actuator");
        if (healthJson.startsWith("{")) {
            try {
                Map<String, Object> health = JSON.readValue(healthJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                out.put("health", health.get("status"));
            } catch (Exception ignored) {
                out.put("health", "UNKNOWN");
            }
        }
        if (metricsJson.startsWith("{")) {
            try {
                Map<String, Object> metrics = JSON.readValue(metricsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
                Object names = metrics.get("names");
                if (names instanceof List<?> list) {
                    out.put("metricCount", list.size());
                    // 摘要给底部指标条悬停展示用：多留一些，前端再按长度截断
                    out.put("metrics", list.size() > 40 ? list.subList(0, 40) : list);
                }
            } catch (Exception ignored) {
                  // 指标摘要只是增强信息，解析失败不影响按钮可用性
            }
        }
        return ReturnResult.ok(out);
    }

    /**
     * 读取项目的健康检测配置。
     *
     * @param id 项目标识
     * @return url=已保存的检测地址；defaultUrl=按项目端口推导的建议地址
     */
    @Override
    public ReturnResult<Map<String, Object>> getProjectHealthCheck(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", savedHealthUrl(id));
        out.put("defaultUrl", defaultHealthUrl(prepared.getData().project));
        out.put("warmupCheck", savedWarmupCheck(id));
        return ReturnResult.ok(out);
    }

    /**
     * 保存项目的健康检测地址与预热期判定开关。
     *
     * @param id          项目标识
     * @param url         检测地址（http/https）；为空时保留原地址
     * @param warmupCheck 是否在启动预热期也按严格规则判定；为空时保留原值
     * @return 操作结果
     */
    @Override
    public ReturnResult<Void> saveProjectHealthCheck(Long id, String url, Boolean warmupCheck) {
        if (id == null) {
            return ReturnResult.error("项目ID不能为空");
        }
        if (projectMapper.selectById(id) == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        String target = url == null ? null : url.trim();
        if (target != null && !isBlank(target) && !isSafeHealthUrl(target)) {
            return ReturnResult.error("检测地址必须是 http:// 或 https:// 开头的 URL，且不能包含引号、空格或 shell 特殊字符");
        }
        return persistHealthUrl(id, target, warmupCheck);
    }

    /**
     * 执行一次健康检测：在项目所在主机上发 HTTP 请求，仅以状态码 200 判定通过。
     *
     * <p>和端点探测同样的理由，请求必须跑在目标主机上 —— 项目可能只监听远端主机的
     * 127.0.0.1，后端直接连接会打到后端自己。</p>
     *
     * <p>只检测、不落库：临时试一个地址不应覆盖已保存的配置，落库走「保存地址」。</p>
     *
     * @param id  项目标识
     * @param url 本次使用的检测地址；为空时用已保存的配置
     * @return ok / statusCode / elapsedMs / checkedAt / message
     */
    @Override
    public ReturnResult<Map<String, Object>> checkProjectHealth(Long id, String url) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        String target = isBlank(url) ? savedHealthUrl(id) : url.trim();
        if (isBlank(target)) {
            return ReturnResult.error("请先配置健康检测地址");
        }
        if (!isSafeHealthUrl(target)) {
            return ReturnResult.error("检测地址必须是 http:// 或 https:// 开头的 URL，且不能包含引号、空格或 shell 特殊字符");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", target);
        out.put("ok", false);
        out.put("statusCode", 0);
        out.put("checkedAt", LocalDateTime.now().toString());
        String command = ctx.windows ? windowsHealthCommand(target) : posixHealthCommand(target);
        long start = System.currentTimeMillis();
        ReturnResult<List<MsExecLog>> result = execService.runCommand(
                Collections.singletonList(ctx.hostId), command, 20, null);
        out.put("elapsedMs", System.currentTimeMillis() - start);
        if (!result.isOk() || result.getData() == null || result.getData().isEmpty()) {
            out.put("message", "健康检测执行失败: " + result.getMsg());
            return ReturnResult.ok(out);
        }
        String output = result.getData().get(0).getMsExecLogOutput();
        int code = parseHealthCode(output, ctx.windows);
        String body = parseHealthBody(output, ctx.windows);
        boolean warmup = isWarmupState(code, body);
        // 预热期开关关闭时：readiness 未就绪的 503 只表示服务刚启动，不判失败
        boolean passWarmup = warmup && !savedWarmupCheck(id);
        out.put("statusCode", code);
        out.put("warmup", warmup);
        out.put("ok", code == 200 || passWarmup);
        if (code == 200) {
            out.put("message", "HTTP 200，健康检测通过");
        } else if (passWarmup) {
            out.put("message", "HTTP 503，启动预热中（readiness 未就绪）；预热期健康检测已关闭，不判失败");
        } else if (warmup) {
            out.put("message", "HTTP 503，启动预热中（readiness 未就绪）；预热期健康检测开启，按未就绪处理");
        } else if (code > 0) {
            out.put("message", "HTTP " + code + "，仅 200 视为健康");
        } else {
            out.put("message", "未取到 HTTP 状态码：目标不可达、超时或地址有误");
        }
        return ReturnResult.ok(out);
    }

    /**
     * 读取已保存的健康检测地址。
     *
     * @param projectId 项目标识
     * @return 地址；未配置时为空串
     */
    private String savedHealthUrl(Long projectId) {
        Object saved = readCfg(CFG_HEALTH_CHECK_PREFIX + projectId, null);
        if (saved instanceof Map<?, ?> map) {
            Object value = map.get("url");
            return value == null ? "" : String.valueOf(value).trim();
        }
        return "";
    }

    /**
     * 读取「启动预热期是否也做健康检测」开关。
     *
     * @param projectId 项目标识
     * @return true=预热期也按严格规则判定（默认）；false=预热期不判失败
     */
    private boolean savedWarmupCheck(Long projectId) {
        Object saved = readCfg(CFG_HEALTH_CHECK_PREFIX + projectId, null);
        if (saved instanceof Map<?, ?> map) {
            Object value = map.get("warmupCheck");
            return value == null || Boolean.parseBoolean(String.valueOf(value));
        }
        return true;
    }

    /**
     * 判断一次检测结果是否处于「启动预热期」。
     *
     * <p>Spring Boot 的 readiness 未就绪时，actuator/health 会返回 503 且
     * readinessState=OUT_OF_SERVICE。此时服务其实已经能接请求（登录、页面都正常），
     * 只是还在做启动收尾，不应等同故障。</p>
     *
     * @param code 状态码
     * @param body 响应体（可能被截断）
     * @return 是否预热期
     */
    private boolean isWarmupState(int code, String body) {
        if (code != 503 || body == null || body.isEmpty()) {
            return false;
        }
        String text = body.toUpperCase();
        return text.contains("READINESSSTATE") && text.contains("OUT_OF_SERVICE");
    }

    /**
     * 按项目端口推导一个建议的检测地址（未配置时给前端预填）。
     *
     * @param project 项目
     * @return http://127.0.0.1:端口/actuator/health；无端口时为空串
     */
    private String defaultHealthUrl(MsServerProject project) {
        int port = resolveHealthPort(project);
        return port <= 0 ? "" : "http://127.0.0.1:" + port + "/actuator/health";
    }

    /**
     * 校验检测地址：必须是 http(s)，且不含会被 shell 解释的字符。
     *
     * @param url 地址
     * @return 是否可用
     */
    private boolean isSafeHealthUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (!(value.startsWith("http://") || value.startsWith("https://")) || value.length() > 2048) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' || c == '"' || c == '`' || c == '$' || c == '|' || c == ';'
                    || c == '<' || c == '>' || c == '\\' || Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 写入健康检测配置（存在则合并更新）。
     *
     * <p>地址与预热期开关共用同一条配置，写入时按字段合并，
     * 避免「保存地址」把开关覆盖回默认值、或反之。</p>
     *
     * @param projectId   项目标识
     * @param url         地址；为 null 表示不改
     * @param warmupCheck 预热期开关；为 null 表示不改
     * @return 操作结果
     */
    private ReturnResult<Void> persistHealthUrl(Long projectId, String url, Boolean warmupCheck) {
        String key = CFG_HEALTH_CHECK_PREFIX + projectId;
        Map<String, Object> data = new LinkedHashMap<>();
        Object saved = readCfg(key, null);
        if (saved instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    data.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        if (url != null) {
            data.put("url", url);
        }
        if (warmupCheck != null) {
            data.put("warmupCheck", warmupCheck);
        }
        data.putIfAbsent("warmupCheck", true);
        String value;
        try {
            value = JSON.writeValueAsString(data);
        } catch (Exception e) {
            return ReturnResult.error("配置序列化失败");
        }
        MsServerConfig exists = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (exists == null) {
            MsServerConfig cfg = new MsServerConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setConfigDesc("项目 " + projectId + " 健康检测配置");
            serverConfigMapper.insert(cfg);
        } else {
            exists.setConfigValue(value);
            serverConfigMapper.updateById(exists);
        }
        return ReturnResult.success();
    }

    /**
     * POSIX 健康检测命令：输出响应体，末行用标记带出状态码。
     *
     * <p>需要 body 判断 readiness 是否未就绪；tail -c 只保留末尾，
     * 既能拿到标记，又不会被超大响应淹没。</p>
     *
     * @param url 检测地址
     * @return curl 命令
     */
    private String posixHealthCommand(String url) {
        return "curl -s -m 10 -w '\n" + HC_CODE_MARK + "%{http_code}' '" + url + "' | tail -c 4096";
    }

    /**
     * Windows 健康检测命令：输出状态码与响应体，异常时回落响应里的状态码与 body。
     *
     * @param url 检测地址
     * @return PowerShell 命令
     */
    private String windowsHealthCommand(String url) {
        return "$ProgressPreference='SilentlyContinue'\n"
                + "$ErrorActionPreference='SilentlyContinue'\n"
                + "function __probe($u){ try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 -Uri $u; "
                + "return @{ code=[int]$r.StatusCode; body=[string]$r.Content } } "
                + "catch { $resp=$_.Exception.Response; if ($resp -ne $null) { $c=[int]$resp.StatusCode; $b=''; "
                + "try { $sr=New-Object System.IO.StreamReader($resp.GetResponseStream()); $b=$sr.ReadToEnd(); $sr.Close() } "
                + "catch { try { $b=[string]$resp.Content.ReadAsStringAsync().Result } catch { } }; "
                + "return @{ code=$c; body=$b } } return @{ code=0; body='' } } }\n"
                + "$p=__probe '" + url + "'\n"
                + "$b=[string]$p.body\n"
                + "if ($b.Length -gt " + HC_BODY_MAX + ") { $b=$b.Substring(0," + HC_BODY_MAX + ") }\n"
                + "$b=$b.Replace([char]13,' ').Replace([char]10,' ')\n"
                + "Write-Output ('" + HC_BODY_PREFIX + "' + $b)\n"
                + "Write-Output ('" + HC_CODE_MARK + "' + $p.code)\n";
    }

    /**
     * 从检测命令输出里取 HTTP 状态码。
     *
     * @param output  命令输出
     * @param windows 是否 Windows 主机（BODY= / CODE= 行）
     * @return 状态码；无法解析时为 0
     */
    private int parseHealthCode(String output, boolean windows) {
        String text = output == null ? "" : output.replace("\r", "").trim();
        int mark = text.lastIndexOf(HC_CODE_MARK);
        if (mark >= 0) {
            text = text.substring(mark + HC_CODE_MARK.length()).trim();
            int end = 0;
            while (end < text.length() && Character.isDigit(text.charAt(end))) {
                end++;
            }
            text = text.substring(0, end);
        } else if (windows) {
            for (String line : text.split("\n")) {
                if (line.startsWith("CODE=")) {
                    text = line.substring(5).trim();
                    break;
                }
            }
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从检测命令输出里取响应体（只用于识别 readiness 状态，可能被截断）。
     *
     * @param output  命令输出
     * @param windows 是否 Windows 主机
     * @return 响应体；取不到时为空串
     */
    private String parseHealthBody(String output, boolean windows) {
        String text = output == null ? "" : output.replace("\r", "");
        if (windows) {
            for (String line : text.split("\n")) {
                if (line.startsWith(HC_BODY_PREFIX)) {
                    return line.substring(HC_BODY_PREFIX.length()).trim();
                }
            }
            return "";
        }
        int mark = text.lastIndexOf(HC_CODE_MARK);
        return mark >= 0 ? text.substring(0, mark).trim() : "";
    }

    @Override
    public ReturnResult<Map<String, Object>> getActuatorMonitor(Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", false);
        out.put("intervalSeconds", ACTUATOR_MONITOR_DEFAULT_SECONDS);
        Object saved = readCfg(CFG_ACTUATOR_MONITOR_PREFIX + id, null);
        if (saved instanceof Map<?, ?> map) {
            out.put("enabled", Boolean.parseBoolean(String.valueOf(map.get("enabled"))));
            out.put("intervalSeconds", normalizeActuatorInterval(map.get("intervalSeconds")));
        }
        return ReturnResult.ok(out);
    }

    @Override
    public ReturnResult<Void> saveActuatorMonitor(Long id, Map<String, Object> config) {
        if (id == null) {
            return ReturnResult.error("项目ID不能为空");
        }
        if (projectMapper.selectById(id) == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("enabled", config != null
                && Boolean.parseBoolean(String.valueOf(config.get("enabled"))));
        value.put("intervalSeconds", normalizeActuatorInterval(
                config == null ? null : config.get("intervalSeconds")));
        String json;
        try {
            json = JSON.writeValueAsString(value);
        } catch (Exception e) {
            return ReturnResult.error("配置序列化失败");
        }
        String key = CFG_ACTUATOR_MONITOR_PREFIX + id;
        MsServerConfig exists = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (exists == null) {
            MsServerConfig cfg = new MsServerConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(json);
            cfg.setConfigDesc("项目 " + id + " Actuator 定时采集配置");
            serverConfigMapper.insert(cfg);
        } else {
            exists.setConfigValue(json);
            serverConfigMapper.updateById(exists);
        }
        return ReturnResult.success();
    }

    @Override
    public List<Map<String, Object>> listActuatorMonitors() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<MsServerConfig> rows = serverConfigMapper.selectList(
                new LambdaQueryWrapper<MsServerConfig>()
                        .likeRight(MsServerConfig::getConfigKey, CFG_ACTUATOR_MONITOR_PREFIX));
        for (MsServerConfig row : rows) {
            Long projectId = parseMonitorProjectId(row.getConfigKey());
            if (projectId == null || isBlank(row.getConfigValue())) {
                continue;
            }
            Object parsed;
            try {
                parsed = JSON.readValue(row.getConfigValue(), Object.class);
            } catch (Exception e) {
                continue;
            }
            if (!(parsed instanceof Map<?, ?> map)
                    || !Boolean.parseBoolean(String.valueOf(map.get("enabled")))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", projectId);
            item.put("intervalSeconds", normalizeActuatorInterval(map.get("intervalSeconds")));
            out.add(item);
        }
        return out;
    }

    /**
     * 归一化 Actuator 采集间隔：非法值回落默认，并夹在允许区间内。
     *
     * @param raw 原始间隔（可能为 null / 字符串 / 数字）
     * @return 归一化后的秒数
     */
    private int normalizeActuatorInterval(Object raw) {
        int seconds = ACTUATOR_MONITOR_DEFAULT_SECONDS;
        if (raw != null) {
            try {
                seconds = Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                seconds = ACTUATOR_MONITOR_DEFAULT_SECONDS;
            }
        }
        return Math.max(ACTUATOR_MONITOR_MIN_SECONDS, Math.min(ACTUATOR_MONITOR_MAX_SECONDS, seconds));
    }

    /**
     * 从配置键解析项目ID。
     *
     * @param key 配置键（release.actuator.monitor.{projectId}）
     * @return 项目ID；无法解析时返回 null
     */
    private Long parseMonitorProjectId(String key) {
        if (key == null || !key.startsWith(CFG_ACTUATOR_MONITOR_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(key.substring(CFG_ACTUATOR_MONITOR_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * POSIX 端点探测命令：4 行输出，依次是 api-docs / swagger-ui 的状态码和 health / metrics 的 JSON。
     *
     * @param base 目标主机上的服务根地址（如 http://127.0.0.1:8080）
     * @return shell 命令
     */
    private String posixProbeCommand(String base) {
        return "curl -s -m 4 -o /dev/null -w '%{http_code}' " + base + "/v3/api-docs; echo; "
                + "curl -s -m 4 -o /dev/null -w '%{http_code}' " + base + "/swagger-ui/index.html; echo; "
                + "curl -s -m 4 " + base + "/actuator/health; echo; "
                + "curl -s -m 4 " + base + "/actuator/metrics; echo";
    }

    /**
     * Windows 端点探测命令（PowerShell / Invoke-WebRequest）。
     *
     * <p>目标主机是 Windows 时没有 curl（旧版系统自带的是 curl 别名，参数也不同），
     * 统一走 PowerShell：状态码单行输出，响应体 base64 后单行输出 —— JSON 自带换行，
     * 直接打印会把「第 n 行」的解析打乱。</p>
     *
     * @param base 目标主机上的服务根地址
     * @return PowerShell 命令
     */
    private String windowsProbeCommand(String base) {
        String docs = base + "/v3/api-docs";
        String ui = base + "/swagger-ui/index.html";
        String health = base + "/actuator/health";
        String metrics = base + "/actuator/metrics";
        return "$ProgressPreference='SilentlyContinue'\n"
                + "$ErrorActionPreference='SilentlyContinue'\n"
                + "function __code($u){ try { return [int](Invoke-WebRequest -UseBasicParsing -TimeoutSec 4 -Uri $u).StatusCode } "
                + "catch { if ($_.Exception.Response -ne $null) { return [int]$_.Exception.Response.StatusCode } return 0 } }\n"
                + "function __body($u){ try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 4 -Uri $u; $c=$r.Content; "
                + "if ($c -is [byte[]]) { $c=[Text.Encoding]::UTF8.GetString($c) }; "
                + "return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$c)) } "
                + "catch { $m=$null; if ($_.ErrorDetails) { $m=$_.ErrorDetails.Message }; "
                + "if (-not $m -and $_.Exception.Response -ne $null) { try { $s=$_.Exception.Response.GetResponseStream(); $rd=New-Object IO.StreamReader($s); $m=$rd.ReadToEnd() } catch { } }; "
                + "if ($m) { return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$m)) }; return '' } }\n"
                + "Write-Output ('DOCS=' + (__code '" + docs + "'))\n"
                + "Write-Output ('UI=' + (__code '" + ui + "'))\n"
                + "Write-Output ('HEALTH=' + (__body '" + health + "'))\n"
                + "Write-Output ('METRICS=' + (__body '" + metrics + "'))\n";
    }

    /**
     * 解码 Windows 探测输出里的 base64 响应体。
     *
     * @param raw base64 文本
     * @return 原文；空值或解码失败返回空串（按「端点不可用」处理）
     */
    private String decodeBase64(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        try {
            return new String(java.util.Base64.getMimeDecoder().decode(raw), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> gitPollDeploy(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getGitUrl())) {
            return ReturnResult.error("项目未配置 Git 仓库地址");
        }
        Path workspace = Paths.get(System.getProperty("java.io.tmpdir"),
                "git-deploy", String.valueOf(project.getId()));
        boolean changed = !Files.exists(workspace.resolve(".git"));
        String commit = null;
        if (!changed) {
            try (GitClient client = buildGitClient(project, workspace).build()) {
                String branch = isBlank(project.getGitBranch()) ? null : project.getGitBranch().trim();
                // fetch + 比对交给 git-starter 的 remote()：本模块不再直接碰 jgit 的 refs
                com.chua.git.support.model.FetchResult fetched = client.remote().fetch(branch);
                commit = fetched.remoteCommit();
                changed = fetched.updated();
            } catch (Exception e) {
                log.warn("[Git轮询] 项目 {} 检查更新失败: {}", id, e.getMessage());
                return ReturnResult.error("Git 轮询失败: " + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changed", changed);
        out.put("commit", commit);
        if (!changed) {
            return ReturnResult.ok(out);
        }
        ReturnResult<String> deploy = gitPullAndDeploy(id);
        if (!deploy.isOk()) {
            return ReturnResult.error(deploy.getMsg());
        }
        if (!isBlank(commit)) {
            project.setGitLastCommit(commit);
            project.setUpdateTime(LocalDateTime.now());
            projectMapper.updateById(project);
        }
        out.put("taskId", deploy.getData());
        return ReturnResult.ok(out);
    }

    @Override
    public ReturnResult<Map<String, Object>> getPageLayout(String pageKey) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("layout", readCfg(CFG_LAYOUT_PREFIX + pageKey, null));
        return ReturnResult.ok(out);
    }

    @Override
    public ReturnResult<Void> savePageLayout(String pageKey, String layout) {
        if (isBlank(pageKey)) {
            return ReturnResult.error("页面标识不能为空");
        }
        String key = CFG_LAYOUT_PREFIX + pageKey;
        List<MsServerConfig> rows = serverConfigMapper.selectList(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        MsServerConfig cfg = rows.isEmpty() ? null : rows.get(0);
        if (cfg == null) {
            cfg = new MsServerConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(layout);
            cfg.setConfigDesc("页面布局: " + pageKey);
            serverConfigMapper.insert(cfg);
        } else {
            cfg.setConfigValue(layout);
            serverConfigMapper.updateById(cfg);
        }
        return ReturnResult.ok();
    }

    @Override
    public ReturnResult<Map<String, Object>> gitRepository(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getGitUrl())) {
            return ReturnResult.error("项目未配置 Git 仓库地址，请在项目配置里填写 Git 仓库地址与分支");
        }
        Map<String, Object> out = new LinkedHashMap<>(16);
        out.put("gitUrl", project.getGitUrl());
        out.put("configuredBranch", isBlank(project.getGitBranch()) ? "" : project.getGitBranch().trim());
        out.put("authType", isBlank(project.getGitAuthType()) ? "none" : project.getGitAuthType());
        out.put("workspace", gitWorkspace(project).toString());
        out.put("lastCommit", project.getGitLastCommit());
        out.put("pollEnabled", Boolean.TRUE.equals(project.getGitPollEnabled()));
        out.put("pollCron", project.getGitPollCron());

        Path workspace = gitWorkspace(project);
        if (!Files.exists(workspace.resolve(".git"))) {
            // 没克隆过不是错误：界面要能说清「配了地址但还没拉过」
            out.put("cloned", false);
            out.put("message", "本地还没有该仓库的工作区，点「拉取」先克隆");
            return ReturnResult.ok(out);
        }
        out.put("cloned", true);
        try (GitClient client = buildGitClient(project, workspace).build()) {
            client.open();
            // HEAD / 分支 / 落后判断都走 SDK，本模块不直接碰 jgit 的 ObjectId 与 refs
            var head = client.head();
            String branch = head.branch();
            out.put("branch", branch);
            out.put("commit", head.commit());
            out.put("shortCommit", head.shortCommit());
            out.put("remoteCommit", head.remoteCommit());
            out.put("behind", head.behind());

            // 分支：本地在前，远程去重（同一个分支名只保留一条，标 remote=true）
            // jgit 的 getAllBranches 会把 refs/heads/main 与 refs/remotes/origin/main 都列出来，
            // 而 remote 分支的 shortName 同样是 "main"，直接铺开会显示成两条一模一样的 main。
            List<Map<String, Object>> branches = new ArrayList<>();
            Set<String> localNames = new HashSet<>();
            var allRefs = client.branch().listAll();
            for (var b : allRefs) {
                if (b.isRemote() || "HEAD".equals(b.shortName())) {
                    continue;
                }
                localNames.add(b.shortName());
                Map<String, Object> item = new LinkedHashMap<>(4);
                item.put("name", b.shortName());
                item.put("remote", false);
                item.put("current", b.shortName().equals(branch));
                branches.add(item);
            }
            for (var b : allRefs) {
                if (!b.isRemote() || "HEAD".equals(b.shortName())) {
                    continue;
                }
                String name = b.shortName();
                if (localNames.contains(name)) {
                    // 已有同名本地分支：远程那条不重复展示，本地分支的当前/落后状态已足够
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>(4);
                item.put("name", name);
                item.put("remote", true);
                item.put("current", false);
                branches.add(item);
            }
            out.put("branches", branches);

            // 工作区改动：有未提交改动时不能 pull，界面要能提前提示
            var status = client.status().execute();
            List<String> dirty = new ArrayList<>();
            status.indexToWorkTree().forEach((kind, files) -> files.forEach(f -> dirty.add("staged " + kind + ": " + f)));
            status.workTreeToIndex().forEach((kind, files) -> files.forEach(f -> dirty.add(kind + ": " + f)));
            for (String f : status.untracked()) {
                dirty.add("untracked: " + f);
            }
            out.put("clean", status.isClean());
            out.put("changes", dirty.size() > 200 ? dirty.subList(0, 200) : dirty);
            out.put("changeCount", dirty.size());

            List<Map<String, Object>> commits = new ArrayList<>();
            for (var entry : client.log().list(20)) {
                Map<String, Object> item = new LinkedHashMap<>(8);
                item.put("sha", entry.sha());
                item.put("shortSha", entry.shortSha());
                item.put("message", entry.message());
                item.put("author", entry.author());
                item.put("time", entry.commitTime());
                commits.add(item);
            }
            out.put("commits", commits);
            return ReturnResult.ok(out);
        } catch (Exception e) {
            log.warn("[Git仓库] 项目 {} 读取仓库信息失败: {}", id, e.getMessage());
            return ReturnResult.error("读取 Git 仓库失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> gitPull(Long id, String branch) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getGitUrl())) {
            return ReturnResult.error("项目未配置 Git 仓库地址");
        }
        Path workspace = gitWorkspace(project);
        boolean clone = !Files.exists(workspace.resolve(".git"));
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("cloned", clone);
        try {
            if (clone) {
                Files.createDirectories(workspace);
                try (GitClient client = buildGitClient(project, workspace).build()) {
                    client.cloneOp().execute();
                }
            }
            try (GitClient client = buildGitClient(project, workspace).build()) {
                client.open();
                String target = isBlank(branch)
                        ? (isBlank(project.getGitBranch()) ? client.head().branch() : project.getGitBranch().trim())
                        : branch.trim();
                String current = client.head().branch();
                if (!target.equals(current)) {
                    client.branch().checkout(target);
                }
                // 有未提交改动时 pull 会失败并可能留下半个工作区：先明确拒绝，让用户自己决定
                if (!clone && !client.status().execute().isClean()) {
                    return ReturnResult.error("工作区有未提交的改动，已停止拉取；请先在主机上处理或丢弃这些改动");
                }
                out.put("branch", target);
                out.put("commit", client.head().commit());
                if (clone) {
                    // 刚克隆完，本地就是远端最新：再 pull 一次只会白跑一趟 fetch
                    out.put("updated", true);
                    out.put("message", "克隆完成");
                    return ReturnResult.ok(out);
                }
                var result = client.pull(null, null);
                out.put("updated", result.updated());
                out.put("message", result.messages());
                return ReturnResult.ok(out);
            }
        } catch (Exception e) {
            log.warn("[Git拉取] 项目 {} 失败: {}", id, e.getMessage());
            return ReturnResult.error("Git 拉取失败: " + e.getMessage());
        }
    }

    /**
     * 项目的 Git 工作区目录。
     *
     * <p>与 Git 发布共用同一目录：发布链路先 clone 到这里，仓库面板读到的就是「已拉下来的那份」，
     * 不会出现「面板显示没克隆、发布却说有更新」的两套状态。</p>
     *
     * @param project 项目
     * @return 工作区路径
     */
    private Path gitWorkspace(MsServerProject project) {
        return Paths.get(System.getProperty("java.io.tmpdir"),
                "git-deploy", String.valueOf(project.getId()));
    }

      /**
       * 构造 Git 客户端（含 token 认证），供轮询与发布共用。
       * @param project 参数
       * @param workspace 路径
       * @return 返回值
      */
    private GitClient.Builder buildGitClient(MsServerProject project, Path workspace) {
        GitClient.Builder builder = GitClient.builder()
                .remoteUrl(project.getGitUrl().trim())
                .localPath(workspace);
        if ("token".equalsIgnoreCase(project.getGitAuthType()) && !isBlank(project.getGitToken())) {
            builder.credentials("oauth2", project.getGitToken());
        }
        return builder;
    }
}
