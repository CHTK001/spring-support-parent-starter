package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.monitor.support.entity.MsMonitorNginxMetric;
import com.chua.starter.monitor.support.mapper.MsMonitorNginxMetricMapper;
import com.chua.starter.monitor.support.middleware.MiddlewareHttp;
import com.chua.starter.monitor.support.nginx.spi.NginxCommandProvider;
import com.chua.starter.monitor.support.nginx.spi.NginxExecutor;
import com.chua.starter.monitor.support.nginx.spi.NginxRuntimeRouter;
import com.chua.starter.monitor.support.spi.Platforms;
import com.chua.starter.monitor.support.model.NginxApplyResultVO;
import com.chua.starter.monitor.support.model.NginxBackupDiffVO;
import com.chua.starter.monitor.support.model.NginxBackupVO;
import com.chua.starter.monitor.support.model.NginxCommandResultVO;
import com.chua.starter.monitor.support.model.NginxConfigFileVO;
import com.chua.starter.monitor.support.model.NginxConfigParseVO;
import com.chua.starter.monitor.support.model.NginxDirectiveMetaVO;
import com.chua.starter.monitor.support.model.NginxLogChunkVO;
import com.chua.starter.monitor.support.model.NginxLogFileVO;
import com.chua.starter.monitor.support.model.NginxOverviewVO;
import com.chua.starter.monitor.support.model.NginxRuntimeVO;
import com.chua.starter.monitor.support.model.NginxSiteVO;
import com.chua.starter.monitor.support.model.NginxStatusVO;
import com.chua.starter.monitor.support.model.NginxAccessTopVO;
import com.chua.starter.monitor.support.model.NginxTemplateVO;
import com.chua.starter.monitor.support.model.NginxTrendVO;
import com.chua.starter.monitor.support.model.NginxUrlTrendVO;
import com.chua.starter.monitor.support.model.NginxVersionVO;
import com.chua.starter.monitor.support.model.dto.NginxApplyDTO;
import com.chua.starter.monitor.support.model.dto.NginxPatchDTO;
import com.chua.starter.monitor.support.model.dto.NginxSaveConfigDTO;
import com.chua.starter.monitor.support.nginx.NginxAccessLogParser;
import com.chua.starter.monitor.support.nginx.NginxConfigModelSupport;
import com.chua.starter.monitor.support.nginx.NginxDirectiveRegistry;
import com.chua.starter.monitor.support.nginx.NginxNode;
import com.chua.starter.monitor.support.nginx.NginxParser;
import com.chua.starter.monitor.support.nginx.NginxRenderer;
import com.chua.starter.monitor.support.nginx.NginxSiteExtractor;
import com.chua.starter.monitor.support.nginx.NginxTemplateLibrary;
import com.chua.starter.monitor.support.service.NginxManageService;
import com.chua.starter.server.support.service.ServerConfigService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Nginx 管理实现：全部经 SSH 在目标主机执行 nginx 命令。
 *
 * <p>每次执行新建一条短连接（Nginx 管理操作低频，不做会话缓存），命令超时 10 秒。
 * 配置落盘用 base64 编码再解码，绕开配置里的引号、反斜杠与换行。</p>
 *
 * <p><strong>写操作安全网</strong>：{@link #applyConfig} 固定按
 * 「备份 → 落盘 → nginx -t → reload」执行，每一步结果都记入返回值。
 * 语法检查不通过时<strong>不会</strong>执行 reload，因此错误配置不会被加载生效；
 * 已落盘的文件可通过 {@link #restore} 或备份列表回退。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NginxManageServiceImpl implements NginxManageService {

    /**
     * 数据源类型标识
     */
    private static final String DS_TYPE = "nginx";

    /**
     * SSH 默认端口
     */
    private static final int DEFAULT_SSH_PORT = 22;

    /**
     * 默认 nginx 配置根路径
     */
    private static final String DEFAULT_CONF_ROOT = "/etc/nginx";

    /**
     * 默认 access 日志。
     *
     * <p><strong>只作为解析不出配置时的兜底</strong>，不再是实际读取路径。
     * 实际路径由 {@link #defaultAccessLog} 从 {@code nginx -T} 的生效配置里解析——
     * 本机实例就把日志指到了 {@code /opt/ngx-console/nginx/logs/}，
     * 硬编码这里会永远读到一个 0 字节的旧文件。</p>
     */
    private static final String DEFAULT_ACCESS_LOG = "/var/log/nginx/access.log";

    /**
     * 默认 error 日志（同样只是兜底，见 {@link #DEFAULT_ACCESS_LOG}）
     */
    private static final String DEFAULT_ERROR_LOG = "/var/log/nginx/error.log";

    /**
     * 默认日志目录
     */
    private static final String DEFAULT_LOG_DIR = "/var/log/nginx";

    /**
     * 备份目录。
     *
     * <p>默认值 {@code /var/log/nginx-console-backup} 在多数发行版下属于 root 专属路径，
     * 非 root 的 SSH 用户执行 {@code mkdir -p} 会直接失败——真实部署（Kali）上就是这样：
     * {@code mkdir: cannot create directory '/var/log/nginx-console-backup': Permission denied}。
     * 因此实际使用时先探测该目录是否可写，不可写则退回到登录用户家目录下的
     * {@code ~/.nginx-console-backup}。可用 {@code monitor.nginx.backup-dir} 显式覆盖。</p>
     */
    @Value("${monitor.nginx.backup-dir:/var/log/nginx-console-backup}")
    private String backupDir = "/var/log/nginx-console-backup";

    /**
     * stub_status 地址的系统配置键
     */
    private static final String CFG_STUB_URL = "nginx.stub_status.url";

    /**
     * stub_status 路径
     */
    private static final String STUB_PATH = "/nginx_status";

    /**
     * 命令执行超时（秒）
     */
    private static final long EXEC_TIMEOUT_SECONDS = 10L;

    /**
     * 生命周期命令超时（秒）：start / stop / restart 需要更长时间
     */
    private static final long LIFECYCLE_TIMEOUT_SECONDS = 30L;

    /**
     * 连接超时（秒）
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    /**
     * 备份文件名时间戳格式
     */
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * 趋势查询默认回溯小时数
     */
    private static final int TREND_DEFAULT_HOURS = 24;

    /**
     * 趋势查询默认采样间隔（分钟）
     */
    private static final int TREND_DEFAULT_INTERVAL_MINUTES = 30;

    /**
     * 趋势查询单次返回的最大点数。
     *
     * <p>间隔太小时原始行数会暴涨（30s 粒度 × 24h = 2880 点/指标），
     * 超过这个上限就自动放大间隔，避免把整表拉进内存。</p>
     */
    private static final int TREND_MAX_POINTS = 288;

    /**
     * 累计型指标：值是「worker 启动以来」的总量，趋势必须换算成速率才看得出波动。
     * 瞬时型指标（active / reading / writing / waiting）本身就是瞬时值，直接用。
     */
    private static final Set<String> CUMULATIVE_METRICS = Set.of("accepts", "handled", "requests");

    /**
     * SSE 推送取「最近点」时回看的窗口（分钟）。
     *
     * <p>累计型指标的速率要靠相邻两点算，窗口至少要覆盖两个采集周期。
     * 按默认 30s 间隔，5 分钟 ≈ 10 个周期，既够算速率又不会把整张趋势表拉进内存。</p>
     */
    private static final int REALTIME_WINDOW_MINUTES = 5;

    /**
     * 等待 start/stop/restart 真正生效的轮询上限（毫秒）。
     *
     * <p>给到 20 秒：裸 {@code nginx -s stop} 通常 1~2 秒就完，但走 systemctl 时
     * 还要收尾在途请求与 worker，超时设太短会把「正常但稍慢」误报成失败。
     * 超过仍没生效就如实说明，不谎报成功。</p>
     */
    private static final long STOP_WAIT_MILLIS = 20_000L;

    /**
     * 停止时的轮询间隔（毫秒）
     */
    private static final long STOP_POLL_MILLIS = 500L;

    /**
     * 控制方式：本地目录裸进程。
     *
     * <p>与 {@code DatasourceHubConstants.CONTROL_LOCAL} 同值，此处独立定义是为了
     * 监控模块不因数据源中心的常量变动而重新编译；取值以数据源中心为准。</p>
     */
    private static final String CONTROL_MODE_LOCAL = "local";

    /**
     * 控制方式：由 systemd 托管
     */
    private static final String CONTROL_MODE_SYSTEMD = "systemd";

    /**
     * 托管者探测结果，按数据源缓存。
     *
     * <p>探测一次要开一条 SSH 连接，而 start/stop 都会用到，所以缓存；
     * TTL 见 {@link #INIT_SYSTEM_CACHE_MILLIS}。</p>
     */
    private final Map<String, CachedInitOwner> initOwnerCache = new ConcurrentHashMap<>();

    /**
     * 托管者探测结果的缓存时长（毫秒）
     */
    private static final long INIT_SYSTEM_CACHE_MILLIS = 60_000L;

    /**
     * 时序指标 Mapper（可选依赖：未装配时趋势与采集自动禁用，不影响其它功能）
     */
    private final ObjectProvider<MsMonitorNginxMetricMapper> nginxMetricMapper;

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * 系统配置服务（可选依赖：未引入 server-starter 时降级为不读配置）
     */
    private final ObjectProvider<ServerConfigService> configServiceProvider;

    /**
     * Nginx 运行时路由器：按数据源解析出「平台 + 命令文本 + 执行通道」。
     *
     * <p>本类原先自建 {@code SshClient} 并把 POSIX 命令硬编码在方法里，导致 Windows 上
     * 一条都跑不了（不是没测，是结构上不可能跑）。现在命令文本由
     * {@code NginxCommandProvider} 按平台给出，执行由 {@code NginxExecutor} 承担，
     * 二者都走 SPI；本类只负责业务编排。</p>
     */
    private final NginxRuntimeRouter nginxRuntimeRouter;

    /**
     * 路径解析结果的缓存有效期（毫秒）。
     *
     * <p>prefix / confPath 正常情况下是编译期常量，缓存只是为省掉每次命令多开一次
     * SSH 连接。但「解析失败」不是常量——远端 shell 类型不对、SSH 端口不通、
     * nginx 未装好时都会解析出空 prefix。这条空结果一旦被缓存，就会毒化整个进程
     * 生命周期：后续所有命令都带着错误的 prefix 执行，且永远不会自愈。本机验收时
     * 真实踩过：先在 cmd.exe 环境下解析出空 prefix，修好 DefaultShell 后不重启
     * 进程就依旧报 {@code CreateDirectory() "C:\\Users\\.../temp/client_body_temp"}。</p>
     *
     * <p>解析成功的结果<strong>长期缓存、不设 TTL</strong>：prefix 与 confPath 来自
     * nginx 编译期常量，同机不变；而每次重解析要连开两三次 SSH，偶发失败会让
     * confPath 变空，进而导致同机多实例的进程匹配失效。详见 {@link #resolvePaths}。</p>
     */

    /**
     * 各目标主机已解析的 nginx prefix / 主配置路径。
     *
     * <p>解析成功即长期缓存；只有解析结果不可用（prefix 为空）时才不写缓存，
     * 留待下次调用重新解析以自愈。线程安全由 {@link ConcurrentHashMap} 保证。</p>
     */
    private final Map<String, NginxPaths> pathCache = new ConcurrentHashMap<>();

    // ==================== 总览 ====================

    @Override
    public NginxOverviewVO overview(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String root = confRoot(ds);

        List<NginxConfigFileVO> files = listFiles(datasourceId);
        long totalSize = files.stream().map(NginxConfigFileVO::size).filter(java.util.Objects::nonNull)
                .reduce(0L, Long::sum);

        String running = detectRunning(ds);
        Long pid = detectMasterPid(ds);

        List<NginxLogFileVO> logs;
        try {
            logs = listLogFiles(datasourceId);
        } catch (RuntimeException e) {
            logs = List.of();
        }

        NginxStatusVO status;
        try {
            status = status(datasourceId);
        } catch (RuntimeException e) {
            status = NginxStatusVO.unavailable("读取实时状态失败: " + e.getMessage());
        }

        NginxVersionVO version;
        try {
            version = version(datasourceId);
        } catch (RuntimeException e) {
            version = new NginxVersionVO(null, null, "读取版本失败: " + e.getMessage());
        }

        List<Integer> ports = detectListenPorts(ds, root);

        List<String> issues = new ArrayList<>();
        if ("STOPPED".equals(running)) {
            issues.add("nginx 当前未运行，配置改动不会对外生效，需先执行「启动」");
        }
        if (!status.available()) {
            issues.add("未取到 stub_status 实时数据（" + status.message() + "）；连接数相关指标将显示为 —");
        }
        if (pid == null && "RUNNING".equals(running)) {
            issues.add("运行中但读不到 master PID，可能是 nginx 以非默认用户启动或 pid 文件不可读");
        }
        NginxCommandResultVO t = testQuietly(datasourceId);
        if (!t.success()) {
            issues.add("当前配置语法检查未通过，重载会被拒绝：" + firstLine(t.stderr(), t.stdout()));
        }

        return new NginxOverviewVO(running, pid, uptimeText(ds, pid), root, configPath(ds), version, status,
                files.size(), totalSize, ports, logs, issues);
    }

    // ==================== 配置文件 ====================

    @Override
    public List<NginxConfigFileVO> listFiles(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String root = confRoot(ds);
        // -printf 是 GNU find 扩展，BSD/macOS 上不可用；具体实现由平台提供者负责，
        // 这里只要「路径 + 字节数 + 修改时间」这份语义结果
        String out = exec(ds, commands(ds).listFilesWithMeta(root, "*.conf", true)).stdout();
        List<NginxConfigFileVO> result = new ArrayList<>();
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length >= 3) {
                result.add(new NginxConfigFileVO(parts[0], parseLong(parts[1]), parts[2]));
            } else {
                result.add(new NginxConfigFileVO(line, null, null));
            }
        }
        return result;
    }

    @Override
    public String readConfig(Long datasourceId, String path) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String safePath = requireText(path, "配置文件路径不能为空");
        return exec(ds, commands(ds).readFile(safePath)).stdout();
    }

    @Override
    public NginxConfigParseVO parseConfig(Long datasourceId, String path) {
        String content = readConfig(datasourceId, path);
        NginxParser parser = new NginxParser(content);
        NginxNode root = parser.parse();
        return NginxConfigModelSupport.toParseVo(root, requireText(path, "配置文件路径不能为空"), content, parser.warnings());
    }

    @Override
    public NginxApplyResultVO saveConfig(NginxSaveConfigDTO request) {
        if (request == null) {
            throw new IllegalStateException("保存请求不能为空");
        }
        return applyConfig(new NginxApplyDTO(request.datasourceId(), request.path(), "FILE",
                request.content(), List.of(), Boolean.TRUE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE));
    }

    @Override
    public NginxApplyResultVO applyConfig(NginxApplyDTO request) {
        if (request == null) {
            throw new IllegalStateException("保存请求不能为空");
        }
        DataSourceVO ds = requireDatasource(request.datasourceId());
        String path = requireText(request.path(), "配置文件路径不能为空");
        String original = readConfig(request.datasourceId(), path);

        List<String> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ---- 生成新内容 ----
        String next;
        if ("MODEL".equalsIgnoreCase(trim(request.mode()))) {
            NginxParser parser = new NginxParser(original);
            NginxNode root = parser.parse();
            warnings.addAll(NginxConfigModelSupport.applyPatches(root, request.patches()));
            next = NginxRenderer.render(root, original);
            if (next.equals(original)) {
                steps.add("补丁应用后内容与原文一致，未做无意义写入");
            }
        } else {
            next = request.content() == null ? "" : request.content();
        }

        int changed = NginxConfigModelSupport.diffLineCount(original, next);

        // ---- 只预览：不落盘、不备份、不校验 ----
        if (Boolean.TRUE.equals(request.dryRun())) {
            steps.add("预览模式：已渲染出结果，未写入任何文件");
            if (changed > 0) {
                steps.add("与当前内容相比约 " + changed + " 行差异");
            }
            return new NginxApplyResultVO(false, "预览结果（未保存）", null, false, false, null,
                    false, false, next, changed, warnings, steps);
        }

        if (next.equals(original) && !"MODEL".equalsIgnoreCase(trim(request.mode()))) {
            return new NginxApplyResultVO(true, "内容没有变化，未写入", null, false, false, null,
                    false, false, next, 0, warnings, steps);
        }

        // ---- 备份 ----
        String backupPath = null;
        if (Boolean.TRUE.equals(request.backup())) {
            try {
                // 新建配置时原文件尚不存在，backup() 里的 cp 会失败并被当成
                // 「备份失败」而中止整个保存流程；但这种情况本就没有可备份的原件，
                // 跳过即可——否则「用模板新建站点」这条核心链路根本走不通。
                if (exec(ds, commands(ds).fileExists(path)).exitCode() == 0) {
                    backupPath = backup(request.datasourceId(), path, "保存前自动备份").path();
                    steps.add("已备份原配置到 " + backupPath);
                } else {
                    steps.add("原文件不存在（新建配置），无需备份");
                }
            } catch (RuntimeException e) {
                steps.add("备份失败，已中止保存：" + e.getMessage());
                return new NginxApplyResultVO(false, "备份失败，未写入任何改动", null,
                        false, false, null, false, false, original, 0, warnings, steps);
            }
        }

        // ---- 落盘 ----
        writeFile(ds, path, next);
        steps.add("配置已写入 " + path + "（净变化约 " + changed + " 行）");

        // ---- 语法检查 ----
        boolean validated = Boolean.TRUE.equals(request.validate());
        boolean validateOk = false;
        String validateText = null;
        if (validated) {
            NginxCommandResultVO t = test(request.datasourceId());
            validateOk = t.success();
            validateText = mergeOut(t.stdout(), t.stderr());
            steps.add(validateOk ? "语法检查通过" : "语法检查未通过：" + firstLine(t.stderr(), t.stdout()));
        }

        // ---- 重载 ----
        boolean reloadReq = Boolean.TRUE.equals(request.reload());
        boolean reloaded = false;
        boolean reloadOk = false;
        if (reloadReq) {
            if (!validated) {
                NginxCommandResultVO t = test(request.datasourceId());
                validateOk = t.success();
                validateText = mergeOut(t.stdout(), t.stderr());
                validated = true;
                steps.add(t.success() ? "重载前语法检查通过" : "重载前语法检查未通过，已放弃重载");
            }
            if (validateOk) {
                NginxCommandResultVO r = reload(request.datasourceId());
                reloaded = true;
                reloadOk = r.success();
                steps.add(reloadOk ? "重载成功，新配置已生效" : "重载失败：" + firstLine(r.stderr(), r.stdout()));
            }
        }

        String message;
        if (reloadReq && !validateOk) {
            message = "配置已保存，但语法检查未通过，未执行重载——新配置尚未生效，请修正后重新重载";
        } else if (reloadReq && !reloadOk) {
            message = "配置已保存但重载失败，新配置尚未生效";
        } else if (reloadReq) {
            message = "配置已保存并重载生效";
        } else {
            message = "配置已保存，需执行「重载」后生效";
        }
        return new NginxApplyResultVO(true, message, backupPath, validated, validateOk, validateText,
                reloaded, reloadOk, next, changed, warnings, steps);
    }

    // ==================== 指令元数据与模板 ====================

    @Override
    public List<NginxDirectiveMetaVO> directives(String scope) {
        String s = trim(scope);
        if (s.isEmpty()) {
            return NginxDirectiveRegistry.all().stream().map(NginxConfigModelSupport::toMetaVo).toList();
        }
        return NginxDirectiveRegistry.byScope(s).stream().map(NginxConfigModelSupport::toMetaVo).toList();
    }

    @Override
    public List<NginxTemplateVO> templates() {
        return NginxTemplateLibrary.all();
    }

    @Override
    public List<NginxSiteVO> sites(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        List<NginxSiteVO> all = new ArrayList<>();
        List<String> readErrors = new ArrayList<>();
        for (NginxConfigFileVO file : listFiles(datasourceId)) {
            try {
                String content = readConfig(datasourceId, file.path());
                NginxParser parser = new NginxParser(content);
                NginxNode root = parser.parse();
                for (NginxSiteVO site : NginxSiteExtractor.extract(root, file.path())) {
                    // 解析告警挂到该文件的所有站点上，避免用户以为配置没问题
                    if (!parser.warnings().isEmpty()) {
                        List<String> merged = new ArrayList<>(site.warnings());
                        merged.addAll(parser.warnings());
                        site = new NginxSiteVO(site.nodePath(), site.file(), site.serverNames(),
                                site.displayName(), site.listenPorts(), site.https(), site.defaultServer(),
                                site.rootDir(), site.indexFiles(), site.proxyTargets(), site.locationCount(),
                                site.upstreamRefs(), site.hasCertificate(), merged);
                    }
                    all.add(site);
                }
            } catch (RuntimeException e) {
                // 单个文件读不到不影响其它文件；记下来在最后统一提示
                readErrors.add(file.path() + "（" + e.getMessage() + "）");
            }
        }
        all = markDuplicateEndpoints(all);
        if (!readErrors.isEmpty()) {
            log.warn("[nginx] 以下配置文件未能解析，站点清单不完整: {}", readErrors);
        }
        return all;
    }

    /**
     * 标记「同一域名 + 同一端口」被重复定义的站点。
     *
     * <p>nginx 遇到重复的 listen + server_name 组合时，只会采用先加载的那一个，
     * 后面的被静默忽略——不报错、不告警。因此按文件查看配置时完全看不出问题，
     * 必须跨文件比对才能发现。</p>
     *
     * @param sites 站点清单
     * @return 追加了重复提示的新清单
     */
    private List<NginxSiteVO> markDuplicateEndpoints(List<NginxSiteVO> sites) {
        Map<String, List<String>> seen = new LinkedHashMap<>();
        for (NginxSiteVO site : sites) {
            for (String name : site.serverNames()) {
                for (int port : site.listenPorts()) {
                    seen.computeIfAbsent(name + "|" + port, k -> new ArrayList<>()).add(site.file());
                }
            }
        }
        Map<String, String> dupHint = new LinkedHashMap<>();
        seen.forEach((key, files) -> {
            if (files.size() > 1) {
                for (String f : files) {
                    dupHint.putIfAbsent(f, key);
                }
            }
        });
        if (dupHint.isEmpty()) {
            return sites;
        }
        List<NginxSiteVO> out = new ArrayList<>(sites.size());
        for (NginxSiteVO site : sites) {
            List<String> warnings = new ArrayList<>(site.warnings());
            for (String name : site.serverNames()) {
                for (int port : site.listenPorts()) {
                    String key = name + "|" + port;
                    if (dupHint.containsKey(site.file()) && seen.getOrDefault(key, List.of()).size() > 1) {
                        warnings.add("域名 " + name + " 在端口 " + port
                                + " 上被定义了 " + seen.get(key).size() + " 次，只有最先加载的那个生效，其余被静默忽略");
                    }
                }
            }
            out.add(new NginxSiteVO(site.nodePath(), site.file(), site.serverNames(), site.displayName(),
                    site.listenPorts(), site.https(), site.defaultServer(), site.rootDir(),
                    site.indexFiles(), site.proxyTargets(), site.locationCount(), site.upstreamRefs(),
                    site.hasCertificate(), warnings));
        }
        return out;
    }

    // ==================== 生命周期 ====================

    @Override
    public NginxCommandResultVO test(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        ExecResult r = exec(ds, nginxCmd(ds, "-t"));
        boolean ok = r.exitCode() == 0;
        return new NginxCommandResultVO(ok, ok ? "语法检查通过" : "语法检查失败", r.stdout(), r.stderr());
    }

    @Override
    public NginxCommandResultVO reload(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        NginxCommandResultVO t = test(datasourceId);
        if (!t.success()) {
            return new NginxCommandResultVO(false, "语法检查未通过，未执行 reload", t.stdout(), t.stderr());
        }
        ExecResult r = exec(ds, nginxCmd(ds, "-s reload"));
        boolean ok = r.exitCode() == 0;
        return new NginxCommandResultVO(ok,
                ok ? "重载成功，新配置已生效" : "重载失败: " + mergeOut(r.stdout(), r.stderr()), r.stdout(), r.stderr());
    }

    @Override
    public NginxCommandResultVO restart(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        NginxCommandResultVO t = test(datasourceId);
        if (!t.success()) {
            return new NginxCommandResultVO(false, "语法检查未通过，未执行重启（避免服务停掉后起不来）", t.stdout(), t.stderr());
        }
        String running = detectRunning(ds);
        if ("STOPPED".equals(running)) {
            return new NginxCommandResultVO(false, "nginx 当前未运行，请直接执行「启动」", "", "");
        }
        // 被 systemd 托管时整件事交给 systemctl restart：
        // 它自己管「停→等→起」，进程始终在同一个单元内，
        // 而手工 quit + 裸启动会让新进程脱离单元托管
        // （本次已启动的能跑，但机器重启后行为与预期不符，很难查）。
        String unit = usableInitUnit(ds);
        if (!INIT_OWNER_NONE.equals(unit)) {
            ExecResult sr = exec(ds, systemctlCmd(unit, "restart"), LIFECYCLE_TIMEOUT_SECONDS);
            boolean sok = sr.exitCode() == 0 && awaitState(ds, true, STOP_WAIT_MILLIS);
            return new NginxCommandResultVO(sok,
                    sok ? "重启完成，nginx 已重新运行（经 " + unit + "）"
                            : "重启失败: " + mergeOut(sr.stdout(), sr.stderr()),
                    sr.stdout(), sr.stderr());
        }
        // quit 是优雅退出：处理完手上的请求再走，比 stop 温和
        ExecResult quit = exec(ds, nginxCmd(ds, "-s quit"), LIFECYCLE_TIMEOUT_SECONDS);
        if (quit.exitCode() != 0) {
            return new NginxCommandResultVO(false,
                    "发送退出信号失败: " + mergeOut(quit.stdout(), quit.stderr()), quit.stdout(), quit.stderr());
        }
        if (!awaitState(ds, false, STOP_WAIT_MILLIS)) {
            return new NginxCommandResultVO(false,
                    "优雅退出超时，进程仍在运行。为避免双实例冲突已放弃重新拉起，请先确认进程状态",
                    quit.stdout(), quit.stderr());
        }
        ExecResult up = exec(ds, nginxCmd(ds, ""), LIFECYCLE_TIMEOUT_SECONDS);
        boolean ok = up.exitCode() == 0 && awaitState(ds, true, STOP_WAIT_MILLIS);
        return new NginxCommandResultVO(ok,
                ok ? "重启完成，nginx 已重新运行" : "重新拉起失败: " + mergeOut(up.stdout(), up.stderr()),
                mergeOut(quit.stdout(), up.stdout()), up.stderr());
    }

    @Override
    public NginxCommandResultVO stop(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        if ("STOPPED".equals(detectRunning(ds))) {
            return new NginxCommandResultVO(false, "nginx 当前本就未运行，无需停止", "", "");
        }
        // 先问 nginx 由谁托管：被 systemd 托管时必须走 systemctl，
        // 否则 Restart=always 会把我们刚停掉的进程按策略拉回来，
        // 接口却报成功——用户点了停止、实例照跑，每次都这样，最难排查。
        String unit = usableInitUnit(ds);
        ExecResult r = INIT_OWNER_NONE.equals(unit)
                ? exec(ds, nginxCmd(ds, "-s stop"), LIFECYCLE_TIMEOUT_SECONDS)
                : exec(ds, systemctlCmd(unit, "stop"), LIFECYCLE_TIMEOUT_SECONDS);
        boolean cmdOk = r.exitCode() == 0;
        // 成功标志必须包含「状态真的收敛了」。原先只看 exitCode，导致 systemctl stop
        // 返回 0、但 nginx 其实没停时，接口照样回 success=true + 「操作成功」，
        // 而 message 里写的却是「仍未退出」——同一个响应里自相矛盾，用户无从判断。
        // 实测（生产 198）就是这样：stop 返回成功、8080 也确实断了，但
        // 随后的 start 被 processAlive 的误判挡回「已在运行」，界面陷入死结。
        boolean stopped = cmdOk && awaitState(ds, false, STOP_WAIT_MILLIS);
        String msg;
        if (!cmdOk) {
            msg = "停止失败: " + mergeOut(r.stdout(), r.stderr());
        } else if (stopped) {
            msg = INIT_OWNER_NONE.equals(unit) ? "已停止" : "已停止（经 " + unit + "）";
        } else {
            // 到达等待上限仍没停：如实说明，并给出最可能的原因，
            // 而不是像改造前那样只留一句「请稍后确认」让用户干等
            msg = INIT_OWNER_NONE.equals(unit)
                    ? "已发送停止信号，但进程在 " + (STOP_WAIT_MILLIS / 1000) + " 秒内仍未退出；"
                    + "若有进程管理器配了自动重启（如 systemd 的 Restart=always），它会立刻把 nginx 拉回"
                    : "已向 " + unit + " 发出停止指令，但 nginx 在 " + (STOP_WAIT_MILLIS / 1000)
                    + " 秒内仍未退出，请检查该单元是否有其他守护配置";
        }
        return new NginxCommandResultVO(stopped, msg, r.stdout(), r.stderr());
    }

    @Override
    public NginxCommandResultVO start(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String running = detectRunning(ds);
        if ("RUNNING".equals(running)) {
            return new NginxCommandResultVO(false, "nginx 已在运行，无需重复启动（需要加载新配置请用「重载」）", "", "");
        }
        // UNKNOWN 是「查不到」而不是「在运行」——不能一并回「已在运行」。
        //
        // 踩过的坑：原来只判 !STOPPED，于是 UNKNOWN 落到「已在运行」分支。
        // 而 UNKNOWN 恰恰发生在同机有多个 nginx 实例、进程匹配跟错对象时——
        // 用户明明刚点过停止、端口确实不通，却被告知「已在运行」，
        // 现象是「停止有效、启动无效」，极难定位。
        //
        // 这里对 UNKNOWN 继续往下走（尝试启动）：启动是幂等的，
        // 已经在跑的话 systemctl start / nginx 启动都会返回「已在运行」或直接成功，
        // 比拦住不给试安全得多。
        if ("UNKNOWN".equals(running)) {
            log.debug("[nginx] 启动前未能确定运行状态（UNKNOWN），按未运行处理并尝试启动");
        }
        NginxCommandResultVO t = test(datasourceId);
        if (!t.success()) {
            return new NginxCommandResultVO(false, "语法检查未通过，已放弃启动", t.stdout(), t.stderr());
        }
        // 与 stop 对称：被 systemd 托管时走 systemctl start，
        // 保证「停」和「起」是同一套控制面，不会停在半路
        // （用 nginx -s stop 停掉的进程，再 nginx 裸启动会脱离单元，
        //  下次机器重启后行为与预期不符）。
        String unit = usableInitUnit(ds);
        ExecResult r = INIT_OWNER_NONE.equals(unit)
                ? exec(ds, nginxCmd(ds, ""), LIFECYCLE_TIMEOUT_SECONDS)
                : exec(ds, systemctlCmd(unit, "start"), LIFECYCLE_TIMEOUT_SECONDS);
        boolean cmdOk = r.exitCode() == 0;
        // 与 stop 同一个口径：成功 = 命令成功 **且** 状态真的变成运行中。
        // 只看 exitCode 的话，「systemctl start 返回 0 但 nginx 没起来」会被报成成功。
        boolean started = cmdOk && awaitState(ds, true, STOP_WAIT_MILLIS);
        String msg;
        if (!cmdOk) {
            msg = "启动失败: " + mergeOut(r.stdout(), r.stderr());
        } else if (started) {
            msg = INIT_OWNER_NONE.equals(unit) ? "启动成功，nginx 已运行" : "启动成功，nginx 已运行（经 " + unit + "）";
        } else {
            msg = INIT_OWNER_NONE.equals(unit)
                    ? "命令已下发但未检测到运行中的进程，请检查错误日志"
                    : "已向 " + unit + " 发出启动指令，但未检测到运行中的 nginx 进程，请查看该单元日志";
        }
        return new NginxCommandResultVO(started, msg, r.stdout(), r.stderr());
    }

    /**
     * 构造 systemctl 操作命令。
     *
     * <p>带 {@code sudo -n} 前缀：systemctl 改的是系统级服务状态，
     * 数据源配置的 SSH 用户通常是普通用户（如 {@code kali}），直接调会被拒。
     * {@code -n} 表示不提示输入密码——不这么做的话，命令会卡在密码输入上直到
     * SSH 会话超时，表现为「停止」按钮点了没反应。</p>
     *
     * <p>不携带任何密码：免密 sudo 规则本身就是授权凭证，
     * 把密码写进命令行等于留在进程表里给同机所有人看。
     * 能不能免密由 {@link #canSudo} 预先判定，判为否时会退回裸信号方式，
     * 所以这里不需要 {@code ||} 兜底重试——那只会让失败信息被第二次尝试覆盖。</p>
     *
     * @param unit   单元名
     * @param action start / stop / restart
     * @return 命令串
     */
    private String systemctlCmd(String unit, String action) {
        // systemd 是 POSIX 专有：能走到这里就说明调用方已通过 usableInitUnit 判定该机被
        // systemd 托管（Windows 上恒判否）。所以命令文本固定取 POSIX 实现，不按数据源平台猜。
        return nginxRuntimeRouter.commandsOf(Platforms.LINUX).serviceControl(unit, action);
    }

    /**
     * 决定本次操作能否走 systemd 通道。
     *
     * <p>先看数据源上显式配置的控制方式，再回退到自动探测：</p>
     * <ul>
     *   <li>{@code local}：直接返回 {@link #INIT_OWNER_NONE}，永远走裸 {@code nginx -s}。
     *       用户明确说了「这个实例不归 systemd 管」，探测出来的结果不该再推翻它。</li>
     *   <li>{@code systemd}：已填单元名就用填的（不再探测，避免同机多实例下探到别人的单元），
     *       没填才自动探测；探测不到或无免密权限都退回裸信号。</li>
     *   <li>未配置：维持改造前的自动探测行为，老数据源不受影响。</li>
     * </ul>
     *
     * <p>两条自动探测分支都要求「认得出单元」且「当前 SSH 用户能免密执行该单元的 systemctl」，
     * 任一不满足就返回 {@link #INIT_OWNER_NONE}，由调用方退回裸 {@code nginx -s}——
     * 行为与改造前一致，不会更糟。</p>
     *
     * @param ds 数据源
     * @return 可用的单元名，或 {@link #INIT_OWNER_NONE}
     */
    private String usableInitUnit(DataSourceVO ds) {
        String mode = trim(ds.controlMode()).toLowerCase();
        if (CONTROL_MODE_LOCAL.equals(mode)) {
            log.debug("[nginx] 数据源显式配置为本地目录方式，跳过 systemd 探测");
            return INIT_OWNER_NONE;
        }
        String unit;
        if (CONTROL_MODE_SYSTEMD.equals(mode) && !trim(ds.controlTarget()).isEmpty()) {
            unit = withServiceSuffix(ds.controlTarget());
        } else {
            unit = detectInitOwner(ds);
        }
        if (INIT_OWNER_NONE.equals(unit)) {
            return INIT_OWNER_NONE;
        }
        if (!canSudo(ds, unit)) {
            log.debug("[nginx] 识别到托管单元 {} 但当前用户无免密 systemctl 权限，退回 nginx -s 方式", unit);
            return INIT_OWNER_NONE;
        }
        return unit;
    }

    /**
     * 给单元名补 {@code .service} 后缀。
     *
     * <p>sudoers 两种写法都常见（带后缀与不带），统一补齐后再交给
     * {@link #canSudo} 处理，避免「用户填了 {@code nginx} 却匹配不上放行规则」。</p>
     *
     * @param unit 用户填写的单元名
     * @return 规范化后的单元名
     */
    private static String withServiceSuffix(String unit) {
        var name = trim(unit);
        return name.endsWith(".service") ? name : name + ".service";
    }

    // ==================== 版本与状态 ====================

    @Override
    public NginxVersionVO version(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        // nginx -V 的编译参数输出在 stderr
        ExecResult r = exec(ds, commands(ds).versionDump(""));
        String raw = mergeOut(r.stdout(), r.stderr()).trim();
        String version = null;
        String configure = null;
        int idx = raw.indexOf("nginx/");
        if (idx >= 0) {
            int end = raw.indexOf(' ', idx);
            version = end > idx ? raw.substring(idx, end) : raw.substring(idx);
        }
        int cfgIdx = raw.indexOf("configure arguments:");
        if (cfgIdx >= 0) {
            configure = raw.substring(cfgIdx + "configure arguments:".length()).trim();
        }
        return new NginxVersionVO(version, configure, raw);
    }

    @Override
    public NginxStatusVO status(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String host = trim(ds.host());
        if (host.isEmpty()) {
            return NginxStatusVO.unavailable("数据源未配置主机地址");
        }
        List<String> tried = new ArrayList<>();
        for (String url : stubCandidates(ds, host)) {
            tried.add(url);
            try {
                String body = MiddlewareHttp.get(url, Map.of());
                if (body != null && body.contains("Active connections")) {
                    return parseStatus(url, body);
                }
            } catch (Exception e) {
                log.debug("[nginx] stub_status 尝试失败 url={} err={}", url, e.getMessage());
            }
        }
        return NginxStatusVO.unavailable("已尝试 " + String.join("、", tried) + " 均未取到；"
                + "请确认已配置 stub_status 模块、该端口可从管理端访问，并按需在「系统配置」里设置 " + CFG_STUB_URL);
    }

    /**
     * 依次解析 stub_status 的候选地址。
     *
     * <p>顺序：系统配置显式指定 → 配置里出现过的监听端口 → 默认 80。
     * 这里<strong>不能</strong>直接用数据源的 {@code port}，那是 SSH 端口，
     * 与 nginx 的 HTTP 监听端口无关（历史实现混用会导致永远取不到数据）。</p>
     *
     * @param ds   数据源
     * @param host 主机
     * @return 候选 URL 列表
     */
    private List<String> stubCandidates(DataSourceVO ds, String host) {
        Set<String> urls = new LinkedHashSet<>();
        String configured = "";
        ServerConfigService cfgService = configServiceProvider.getIfAvailable();
        if (cfgService != null) {
            try {
                configured = trim(cfgService.getString(CFG_STUB_URL, ""));
            } catch (RuntimeException e) {
                log.debug("[nginx] 读取 {} 失败: {}", CFG_STUB_URL, e.getMessage());
            }
        }
        if (!configured.isEmpty()) {
            urls.add(configured.endsWith(STUB_PATH) ? configured : configured + STUB_PATH);
            return new ArrayList<>(urls);
        }
        for (int port : detectListenPorts(ds, confRoot(ds))) {
            urls.add("http://" + host + ":" + port + STUB_PATH);
        }
        urls.add("http://" + host + ":80" + STUB_PATH);
        return new ArrayList<>(urls);
    }

    // ==================== 日志 ====================

    @Override
    public List<NginxLogFileVO> listLogFiles(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String dir = logDir(ds);
        String out = exec(ds, commands(ds).listFilesWithMeta(dir, "*.log", false)).stdout();
        List<NginxLogFileVO> result = new ArrayList<>();
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            String path = parts[0];
            result.add(new NginxLogFileVO(path, logType(path),
                    parts.length >= 2 ? parseLong(parts[1]) : null,
                    parts.length >= 3 ? parts[2] : null));
        }
        return result;
    }

    @Override
    public String log(Long datasourceId, String path, Integer lines) {
        DataSourceVO ds = requireDatasource(datasourceId);
        int n = lines == null || lines <= 0 ? 200 : Math.min(lines, 5000);
        String safePath = trim(path);
        if (safePath.isEmpty()) {
            safePath = defaultAccessLog(ds);
        }
        return exec(ds, commands(ds).tailLines(safePath, n)).stdout();
    }

    @Override
    public NginxLogChunkVO logChunk(Long datasourceId, String path, Integer lines, Long cursor) {
        DataSourceVO ds = requireDatasource(datasourceId);
        int n = lines == null || lines <= 0 ? 200 : Math.min(lines, 5000);
        String safePath = trim(path);
        if (safePath.isEmpty()) {
            safePath = defaultAccessLog(ds);
        }
        Long size = fileSize(ds, safePath);
        boolean rotated = false;
        if (cursor != null && cursor > 0) {
            // 文件比上次读到的位置还小，说明被轮转或截断了，此时全量重读
            rotated = size == null || size < cursor;
        }
        if (cursor == null || cursor <= 0 || rotated) {
            String all = exec(ds, commands(ds).tailLines(safePath, n)).stdout();
            return new NginxLogChunkVO(all, size, size, rotated, safePath);
        }
        // 只取游标之后新增的字节；单次上限 256KB，由命令提供者按平台各自实现
        // （原先在这里拼 `| head -c 262144`，那是 POSIX 管道，Windows 没有）。
        String delta = exec(ds, commands(ds).bytesFrom(safePath, cursor)).stdout();
        return new NginxLogChunkVO(delta, size, size, false, safePath);
    }

    /**
     * 取远端文件字节数。
     *
     * @param ds   数据源
     * @param path 文件路径
     * @return 字节数，取不到为 null
     */
    private Long fileSize(DataSourceVO ds, String path) {
        try {
            ExecResult r = exec(ds, commands(ds).fileSize(path));
            return parseLong(r.stdout().trim());
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取文件大小失败 path={} err={}", path, e.getMessage());
            return null;
        }
    }

    // ==================== 备份 ====================

    @Override
    public NginxBackupVO backup(Long datasourceId, String path, String reason) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String safePath = requireText(path, "配置文件路径不能为空");
        String dir = resolvePaths(ds).backupDir();
        // 名字里不能带平台非法字符：Windows 的 ':'（盘符）与 '\' 会让 copy 直接报
        // 「文件名语法不正确」。Linux 路径本来就没有这两个字符，替换后行为不变。
        // 代价：Windows 上无法从备份名还原盘符（originPathOfBackup 逆映射得到 /nginx/...），
        // 恢复时应显式传目标路径——这与该方法注释里的既有取舍一致。
        String name = trim(safePath).replace('/', '_').replace('\\', '_').replace(':', '_')
                + "." + LocalDateTime.now().format(BACKUP_STAMP) + ".bak";
        String target = dir + "/" + name;
        // 拆成三步平台中立调用：建目录 → 复制 → 取大小。
        // 原先是一条 `mkdir -p … && cp -p … && printf "%s" "$(wc -c < …)"` 复合命令，
        // 三处都是 POSIX 专有语法。
        exec(ds, commands(ds).makeDirs(dir));
        ExecResult cp = exec(ds, commands(ds).copyFile(safePath, target));
        if (cp.exitCode() != 0) {
            throw new IllegalStateException("备份失败: " + mergeOut(cp.stdout(), cp.stderr()));
        }
        ExecResult sized = exec(ds, commands(ds).fileSize(target));
        return new NginxBackupVO(name, target, parseLong(sized.stdout().trim()), LocalDateTime.now().toString(),
                null, trim(reason).isEmpty() ? "手动备份" : reason);
    }

    @Override
    public List<NginxBackupVO> listBackups(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String dir = resolvePaths(ds).backupDir();
        // 统一走「路径 + 大小 + 修改时间」能力。原先靠 `ls -1t` 在 shell 里排序、
        // 并反复拼 dir/$n，既依赖 POSIX，又和配置/日志列表各写一份同样的脚本。
        // 排序改在本地做：时间格式固定 yyyy-MM-dd HH:mm，字典序即时间序。
        String out = exec(ds, commands(ds).listFilesWithMeta(dir, "*", false)).stdout();
        List<String[]> rows = new ArrayList<>();
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            rows.add(line.split("\t", 3));
        }
        // 最新在前，与原 `ls -1t` 的呈现一致；缺失时间的排最后
        rows.sort((a, b) -> (b.length >= 3 ? b[2] : "").compareTo(a.length >= 3 ? a[2] : ""));
        List<NginxBackupVO> result = new ArrayList<>();
        for (String[] parts : rows) {
            String full = parts[0];
            result.add(new NginxBackupVO(baseName(full), full,
                    parts.length >= 2 ? parseLong(parts[1]) : null,
                    parts.length >= 3 ? parts[2] : null, null, null));
        }
        return result;
    }

    /**
     * 取路径的父目录，{@code /} 与 {@code \} 都认。
     *
     * <p>纯字符串运算，只用于解析配置里已给出的路径；需要软链接规范化的场合
     * （如定位 nginx 可执行目录）必须走 {@code NginxCommandProvider.parentDir}，
     * 不能用本方法代替。</p>
     *
     * @param path 路径
     * @return 父目录；无分隔符返回空串
     */
    private static String parentOf(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash > 0 ? path.substring(0, slash) : "";
    }

    /**
     * 取路径的文件名部分，{@code /} 与 {@code \} 都认。
     *
     * <p>不能只用 {@code lastIndexOf('/')}：Windows 返回的是反斜杠路径，
     * 那样会取到整条路径当作文件名。</p>
     *
     * @param path 路径
     * @return 文件名
     */
    private static String baseName(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    @Override
    public NginxCommandResultVO restore(Long datasourceId, String backupName, String targetPath) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String name = requireText(backupName, "备份文件名不能为空");
        String src = resolveBackupFile(ds, name);
        // 优先用调用方指定的目标路径；否则按备份名反推
        String origin = trim(targetPath);
        if (origin.isEmpty()) {
            origin = originPathOfBackup(name);
        }
        if (origin.isEmpty()) {
            return new NginxCommandResultVO(false,
                    "无法从备份文件名还原原始路径（路径含下划线时不可逆），请显式指定还原到的目标文件路径", "", "");
        }
        String content;
        try {
            content = readRemoteFile(ds, src);
        } catch (RuntimeException e) {
            return new NginxCommandResultVO(false, e.getMessage(), "", "");
        }
        // 恢复前先把当前内容也备份一份，保证"恢复"这一步本身可逆
        try {
            backup(datasourceId, origin, "恢复前自动备份");
        } catch (RuntimeException e) {
            return new NginxCommandResultVO(false, "恢复前备份失败，已放弃恢复: " + e.getMessage(), "", "");
        }
        writeFile(ds, origin, content);
        NginxCommandResultVO t = test(datasourceId);
        return new NginxCommandResultVO(t.success(),
                t.success() ? "已从备份恢复到 " + origin + " 并通过语法检查，请执行「重载」生效"
                        : "已从备份恢复，但语法检查未通过，请立即检查: " + firstLine(t.stderr(), t.stdout()),
                content, t.stderr());
    }

    @Override
    public String readBackupContent(Long datasourceId, String backupName) {
        DataSourceVO ds = requireDatasource(datasourceId);
        return readRemoteFile(ds, resolveBackupFile(ds, backupName));
    }

    @Override
    public NginxBackupDiffVO diffBackup(Long datasourceId, String backupName, String targetPath) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String backup = readRemoteFile(ds, resolveBackupFile(ds, backupName));
        String target = readRemoteFile(ds, requireText(targetPath, "对比目标路径不能为空"));
        List<String> lines = diffLines(backup, target);
        long added = lines.stream().filter(l -> l.startsWith("+") && !l.startsWith("+++")).count();
        long removed = lines.stream().filter(l -> l.startsWith("-") && !l.startsWith("---")).count();
        return new NginxBackupDiffVO(backupName, targetPath, added, removed, lines);
    }

    /**
     * 生成 unified-diff 风格的行序列（右侧为「当前文件」）。
     *
     * <p>在 Java 侧算而不用远端 {@code diff -u}：远端不一定装了 diffutils，
     * 而且 diff 的退出码有三种（0 相同 / 1 有差异 / 2 出错），
     * 「有差异」和「执行失败」用同一个码表达，很容易把「命令没跑起来」当成「内容相同」返回。</p>
     *
     * <p>算法是最朴素的 LCS 动态规划。nginx 配置通常几百行，
     * O(n×m) 完全够用；换成 Myers 只是为省内存，不值得引入可读性成本。</p>
     *
     * @param before 旧内容（备份）
     * @param after  新内容（当前文件）
     * @return 带 {@code +}/{@code -}/空格 前缀的行序列
     */
    private List<String> diffLines(String before, String after) {
        String[] a = splitLines(before);
        String[] b = splitLines(after);
        int n = a.length;
        int m = b.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = a[i].equals(b[j]) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                out.add("  " + a[i]);
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add("- " + a[i]);
                i++;
            } else {
                out.add("+ " + b[j]);
                j++;
            }
        }
        while (i < n) {
            out.add("- " + a[i]);
            i++;
        }
        while (j < m) {
            out.add("+ " + b[j]);
            j++;
        }
        return out;
    }

    /**
     * 按行切分，末尾空行不保留（{@code split("\n")} 的默认行为正是如此）。
     *
     * @param text 文本
     * @return 行数组
     */
    private static String[] splitLines(String text) {
        return trim(text).split("\n", -1);
    }

    // ==================== 内部工具 ====================

    /**
     * 校验备份文件名并拼出绝对路径。
     *
     * <p>只允许纯文件名：出现 {@code /}、{@code \} 或 {@code ..} 一律拒绝。
     * 备份目录来自配置（{@code datasource.backup}），如果允许路径分隔符，
     * 调用方就能拼出 {@code dir + "/" + "../../etc/shadow} 这类路径把备份目录之外的文件读出来——
     * 读取类接口（内容、下载）尤其危险，那等于一个任意文件读取漏洞。</p>
     *
     * <p>原先只有 {@link #restore} 里有这段校验，现在读内容与 diff 也要用到，
     * 抽出来避免三处各写一遍、改一处漏两处。</p>
     *
     * @param ds   数据源
     * @param name 备份文件名
     * @return 备份文件的绝对路径
     */
    private String resolveBackupFile(DataSourceVO ds, String name) {
        String safe = requireText(name, "备份文件名不能为空");
        if (safe.contains("/") || safe.contains("\\") || safe.contains("..")) {
            throw new IllegalStateException("备份文件名非法");
        }
        return resolvePaths(ds).backupDir() + "/" + safe;
    }

    /**
     * 读一个远端文件的全部内容。
     *
     * <p>先 {@code test -f} 再 {@code cat}：目录也会被 {@code cat} 读出内容并报成功，
     * 分开两步才能把「不是文件」和「读不了」区分开，给出不同的错误。</p>
     *
     * @param ds   数据源
     * @param path 绝对路径
     * @return 文件内容
     */
    private String readRemoteFile(DataSourceVO ds, String path) {
        ExecResult exists = exec(ds, commands(ds).fileExists(path));
        if (exists.exitCode() != 0) {
            throw new IllegalStateException("文件不存在: " + path);
        }
        ExecResult r = exec(ds, commands(ds).readFile(path));
        if (r.exitCode() != 0) {
            throw new IllegalStateException("读取失败: " + mergeOut(r.stdout(), r.stderr()));
        }
        return r.stdout();
    }

    /**
     * 解析 stub_status 纯文本输出。
     *
     * <pre>
     * Active connections: 29
     * server accepts handled requests
     *  16630948 16630948 31070465
     * Reading: 0 Writing: 1 Waiting: 28
     * </pre>
     *
     * @param source 数据来源地址
     * @param body   stub_status 文本
     * @return 状态对象
     */
    private NginxStatusVO parseStatus(String source, String body) {
        Long active = null, accepts = null, handled = null, requests = null;
        Long reading = null, writing = null, waiting = null;
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("Active connections:")) {
                active = parseLong(line.replaceAll(".*Active connections:\\s*", "").replaceAll("\\D.*", ""));
            } else if (line.matches("^\\d+\\s+\\d+\\s+\\d+.*")) {
                String[] nums = line.trim().split("\\s+");
                if (nums.length >= 3) {
                    accepts = parseLong(nums[0]);
                    handled = parseLong(nums[1]);
                    requests = parseLong(nums[2]);
                }
            } else if (line.startsWith("Reading:")) {
                reading = parseLong(extractNumber(line, "Reading"));
                writing = parseLong(extractNumber(line, "Writing"));
                waiting = parseLong(extractNumber(line, "Waiting"));
            }
        }
        return new NginxStatusVO(true, source, active, accepts, handled, requests, reading, writing, waiting,
                body.trim(), "数据来源 " + source);
    }

    /**
     * 从一行里取某个键后面的数字。
     *
     * @param line 整行
     * @param key  键名
     * @return 数字文本，取不到为空串
     */
    private static String extractNumber(String line, String key) {
        int idx = line.indexOf(key + ":");
        if (idx < 0) {
            return "";
        }
        String tail = line.substring(idx + key.length() + 1).trim();
        int end = 0;
        while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
            end++;
        }
        return end == 0 ? "" : tail.substring(0, end);
    }

    /**
     * 判断运行状态。
     *
     * <p>三级探测，逐级退化：{@code pgrep}（Linux / 部分 BSD）→ {@code ps -e | grep}
     * （通用 POSIX，含 Git Bash、MSYS）→ {@code tasklist}（Windows 原生 shell）。
     * 三者都不可用时返回 {@code UNKNOWN} 而不是 {@code STOPPED}——把「查不到」
     * 谎报成「已停止」会让界面给出「去点启动」的错误引导。</p>
     *
     * <p>历史上这里只跑 {@code pgrep -x nginx}，在没装 procps 的环境（Windows 上的
     * Git Bash、各类精简容器）上会稳定返回「未运行」，本机验收时真实复现了该误判。</p>
     *
     * <p><strong>同机多实例时按 conf 过滤</strong>：光看「有没有 nginx 进程」是错的。
     * 本机常驻一个 {@code nginx -c /etc/nginx/eduai-web.conf}（别的站点），
     * 它会让本数据源的 nginx 停掉之后仍被判成「运行中」——
     * 后果是「启动」按钮一直不出现、「停止」可以无限重复点、
     * 「重启」因找不到 pid 文件而报错。症状都是「点了没反应」，
     * 但根因是这个判定跟错了实例。所以有 conf 时只认命令行里带该 conf 的进程，
     * 与 {@link #detectMasterPid} 保持同一口径。</p>
     *
     * @param ds 数据源
     * @return RUNNING / STOPPED / UNKNOWN
     */
    private String detectRunning(DataSourceVO ds) {
        String confTail = confTail(ds);
        // 命令文本整体交给平台实现。原先这里是一段内联 shell 脚本，自带 pgrep → ps -e
        // → tasklist 三段平台分支——正是「把平台差异硬编码在业务方法里」的写法。
        // 现在 POSIX 实现保留 pgrep/ps 两级退化，Windows 实现走 Get-Process。
        //
        // 踩过的坑（保留在实现里）：曾用 Pattern.quote(conf) 塞进 '[]' 字符类，
        // 拼出 [\Qnginx.conf\E]，而 \Q..\E 不是 POSIX 正则语法，字符类提前闭合退化成
        // 「任意字符」，把内核线程全匹配上，永远返回 ON。现在一律用字面量比对。
        String out = exec(ds, commands(ds).processAlive(confTail)).stdout().trim();
        if ("ON".equals(out)) {
            return "RUNNING";
        }
        if ("OFF".equals(out)) {
            return "STOPPED";
        }
        return "UNKNOWN";
    }

    /**
     * 探测 nginx 进程由谁托管，决定 start/stop 该走哪条通道。
     *
     * <p><strong>为什么必须探测而不是直接用 nginx -s stop</strong>：
     * {@code nginx -s stop} 是给进程发信号，<em>只对「谁启动它谁负责停它」成立</em>。
     * 一旦 nginx 被 systemd（或其他进程管理器）托管，而那个管理器配了
     * {@code Restart=always}，就会与我们抢同一个进程：本接口把 nginx 停掉、
     * 返回成功，管理器几秒后按策略把它拉回来。用户看到的是「停止成功」，
     * 实例却还在跑，且每次点都这样——比直接报错更难排查。</p>
     *
     * <p><strong>怎么认，不认死单元名</strong>：从运行中的 master 进程读
     * {@code /proc/<pid>/cgroup}，里面带的是<em>实际托管它的那个单元</em>
     * （形如 {@code /system.slice/xxx.service}）。这样无论单元叫 nginx、nginx-proxy
     * 还是站点自己的名字都能认出来，也不用在代码里写死任何名字。
     * 读不到 cgroup（非 Linux、无权限、进程已停）时返回 {@link #INIT_OWNER_NONE}，
     * 一律回退到裸 {@code nginx -s}，行为与改造前完全一致。</p>
     *
     * <p>只认 systemd 是刻意的：supervisor / runit / pm2 的控制面差异太大，
     * 猜错比不猜更危险。认不出就退回原路径，这是安全的一侧。</p>
     *
     * @param ds 数据源
     * @return 单元名（如 {@code nginx.service}），或 {@link #INIT_OWNER_NONE} 表示无托管者
     */
    private String detectInitOwner(DataSourceVO ds) {
        // Windows 没有 systemd：托管者概念不存在，直接返回 NONE 让调用方退回「裸信号」
        // 方式。不这么做的话，下面几段探针脚本都是 POSIX（systemctl / ps -o / sudo -l），
        // 在 Windows 上会以「命令不存在」的形态失败，被消费成误判而不是「不支持」。
        if (runtime(ds).windows()) {
            return INIT_OWNER_NONE;
        }
        String key = trim(ds.host()) + ":" + (ds.port() == null ? "" : ds.port());
        CachedInitOwner cached = initOwnerCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.readAt() < INIT_SYSTEM_CACHE_MILLIS) {
            return cached.unit();
        }
        String unit = probeInitOwner(ds);
        initOwnerCache.put(key, new CachedInitOwner(unit, now));
        return unit;
    }

    /**
     * 实际执行 cgroup 探测
     *
     * <p><strong>必须按 conf 路径定位到「本数据源管的那个 master」</strong>：
     * 一台机器上同时跑多个 nginx 实例是常态（本机就有一个专供 eduai-web 的
     * {@code nginx -c /etc/nginx/eduai-web.conf}）。</p>
     *
     * <p>踩过的坑：先按「第一个 PPID=1 的 nginx 进程」取，本机两个 master 都满足，
     * 结果抓到的是 {@code eduai-web.service}——单元名认错了，于是
     * {@code canSudo} 拿这个名字去试权限，自然 NO，最终还是退回裸信号，
     * 而我们对着 {@code eduai-web} 做 systemctl 是会去动一台机器上完全无关的服务。
     * 所以必须用 {@code resolvePaths} 解析出的 conf 路径做过滤：同一个数据源
     * 解析出的 conf 只会匹配到它自己那个 master。</p>
     *
     * <p>conf 匹配不上时不猜，直接返回「无托管者」退回裸信号——
     * 猜错的后果比不猜严重得多。</p>
     *
     * <p><strong>两段式探测（第二段是停机态的补丁）</strong>：先按 cgroup 认运行中的
     * master；认不到再按 systemd 自己的 {@code ExecStart} 反查，后段不依赖进程是否在跑。
     * 踩过的坑：只有 cgroup 一段时，nginx 一停就没有 master 进程了，只能退回裸启动；
     * 而裸启动出来的 nginx <em>不在任何单元里</em>——{@code systemctl status} 看不到它，
     * 单元仍处于 inactive，机器下次重启时单元照常 ExecStart，直接撞端口。
     * 现象是「停止有效、启动无效」，与本文档里 {@code UNKNOWN} 那个坑叠在一起极难定位。</p>
     *
     * @param ds 数据源
     * @return 单元名或 {@link #INIT_OWNER_NONE}
     */
    private String probeInitOwner(DataSourceVO ds) {
        String confTail = confTail(ds);
        if (confTail.isEmpty()) {
            // 认不出 conf 就无法在同机多实例里定位到「自己那个」，
            // 宁可放弃走 systemd，也不要冒险停到别人的服务。
            // 此时第二段也无从匹配（它同样以 conf 为判据），直接放弃。
            return INIT_OWNER_NONE;
        }
        // 步骤 1：实例在跑时，从 master 进程的 cgroup 认单元（最直接，也最可信）。
        String byCgroup = probeUnitByCgroup(ds, confTail);
        if (!INIT_OWNER_NONE.equals(byCgroup)) {
            return byCgroup;
        }
        // 步骤 2：实例已停时没有 master 进程，cgroup 读不到，改用 systemd 自己的
        // ExecStart 元数据反查（不依赖进程是否在跑）。
        return probeUnitByExecStart(ds, confTail);
    }

    /**
     * 第一段探测：从运行中 master 进程的 cgroup 认托管单元。
     *
     * <p>本段只处理「认得出」与「认不出」，认不出时统一返回
     * {@link #INIT_OWNER_NONE}，由 {@link #probeInitOwner} 决定是否进入第二段。</p>
     *
     * @param ds       数据源
     * @param confTail 本数据源的 conf 文件名，已确认非空
     * @return 单元名，或 {@link #INIT_OWNER_NONE}
     */
    private String probeUnitByCgroup(DataSourceVO ds, String confTail) {
        String script = """
                command -v systemctl >/dev/null 2>&1 || { echo NONE; exit 0; }
                master=""
                if command -v pgrep >/dev/null 2>&1 && ps -o ppid= -p 1 >/dev/null 2>&1; then
                  master=$(pgrep -x nginx 2>/dev/null | while read -r p; do
                    [ "$(ps -o ppid= -p "$p" 2>/dev/null | tr -d ' ')" = "1" ] || continue
                    case "$(ps -o args= -p "$p" 2>/dev/null)" in
                      *%CONF%*) echo "$p"; break ;;
                    esac
                  done)
                fi
                [ -n "$master" ] || { echo NONE; exit 0; }
                cg=$(cat "/proc/$master/cgroup" 2>/dev/null)
                for u in $(printf '%s' "$cg" | tr ',' '\\n' | sed -n 's#.*[/ ]\\([^/]*\\.service\\)$#\\1#p' | sort -u); do
                  [ "$u" = "init.scope" ] && continue
                  echo "$u"; exit 0
                done
                echo NONE
                """.replace("%CONF%", confTail);
        try {
            String out = trim(exec(ds, script).stdout());
            // 只接受形如 xxx.service 的单元名；其余一律视为「没认出」
            if (!out.endsWith(".service") || out.contains(" ")) {
                return INIT_OWNER_NONE;
            }
            // 归属校验：cgroup 认出的单元，必须真的是这个 master 所在的服务。
            // 认错了就去 systemctl stop 等于停掉一台机器上无关的服务，
            // 比「按钮不生效」严重得多，所以宁可放弃走 systemd、退回发信号。
            if (!ownsUnit(ds, out)) {
                log.warn("[nginx] 单元 {} 不托管本数据源的 nginx 实例（conf={}），退回 nginx -s",
                        out, confTail);
                return INIT_OWNER_NONE;
            }
            return out;
        } catch (RuntimeException e) {
            // 探测失败不能阻断 start/stop：拿不到托管者就按原方式走
            log.debug("[nginx] cgroup 探测失败，转入 ExecStart 反查 host={}", ds.host(), e);
            return INIT_OWNER_NONE;
        }
    }

    /**
     * 第二段探测：从 systemd 单元的 {@code ExecStart} 反查托管者。
     *
     * <p>只在 cgroup 认不出（即 nginx 处于停机态、没有 master 进程）时才需要这一步。
     * 它不读进程表，因此停机时同样有效——这正是「停止之后要能启动回来」的前提。</p>
     *
     * <p><strong>整机扫描必须放在远端一次做完</strong>：逐个单元在 Java 侧循环
     * {@code systemctl show}，等于每台机器上的每个 service 都发一次 SSH 往返，
     * 一台装了几十个 service 的机器会卡到分钟级，而这条路径在「启动」按钮点击时同步执行。
     * 因此这里把整个筛选下推成一个 shell 脚本，一次 SSH 往返给出结论。</p>
     *
     * <p><strong>归属判据是「ExecStart 同时包含 nginx 与本数据源的 conf 文件名」</strong>，
     * 只认明确命中的，绝不猜：认错单元意味着 {@code systemctl start/stop} 会去动一台
     * 机器上无关的服务，后果比「按钮不生效」严重得多。认不出就返回
     * {@link #INIT_OWNER_NONE}，由调用方退回裸信号——这是安全的一侧。</p>
     *
     * <p><strong>残留风险（已知且未消除）</strong>：本段的判据是<b>文本包含</b>，
     * 比第一段的 MainPID 比对弱。构造一个既含 {@code nginx} 又含本数据源 conf 名的
     * 无关单元（例如某个巡检脚本的 ExecStart 里带上了该 conf 名）会被误认，
     * 后果是对它执行 {@code systemctl start}。真正能消除它需要 systemd 侧的
     * 单元反向索引，而 systemd 不提供；缓解手段是「认不出就退回」——
     * 即便误认，也还有 {@link #canSudo} 与 {@link #ownsUnit} 之外的人工确认环节。</p>
     *
     * @param ds       数据源
     * @param confTail 本数据源的 conf 文件名，已确认非空
     * @return 单元名，或 {@link #INIT_OWNER_NONE} 表示仍认不出
     */
    private String probeUnitByExecStart(DataSourceVO ds, String confTail) {
        String quotedConf = shQuote(confTail);
        // 注意 ExecStart 里的 conf 可能带引号或绝对路径，这里只做「包含 confTail」
        // 的字面量比对，不做正则：路径中 . / 等字符都有正则元字符含义。
        String script = """
                command -v systemctl >/dev/null 2>&1 || exit 0
                conf=%CONF%
                systemctl list-units --type=service --all --no-legend --no-pager --plain 2>/dev/null \
                  | awk '{print $1}' \
                  | while read -r u; do
                      case "$u" in
                        *.service) ;;
                        *) continue ;;
                      esac
                      cmd=$(systemctl show "$u" -p ExecStart --value 2>/dev/null) || continue
                      [ -n "$cmd" ] || continue
                      case "$cmd" in
                        *nginx*) ;;
                        *) continue ;;
                      esac
                      case "$cmd" in
                        *"$conf"*) echo "$u"; break ;;
                      esac
                    done
                """.replace("%CONF%", quotedConf);
        try {
            String out = trim(exec(ds, script).stdout());
            // stdout 可能因 while 子管道缓冲而带多行，只取首个 *.service
            for (String line : out.split("\\R+")) {
                String unit = trim(line);
                if (unit.endsWith(".service") && !unit.contains(" ")) {
                    log.debug("[nginx] 停机态下按 ExecStart 认出单元 {}（conf={}）", unit, confTail);
                    return unit;
                }
            }
            return INIT_OWNER_NONE;
        } catch (RuntimeException e) {
            log.debug("[nginx] ExecStart 反查失败，按无托管者处理 host={}", ds.host(), e);
            return INIT_OWNER_NONE;
        }
    }

    /**
     * 判断该单元是否真的托管着本数据源的 nginx 实例。
     *
     * <p>做法是让 systemd 自己回答：{@code systemctl show <unit> MainPID}
     * 就是该单元主进程的 PID，跟我们通过 cgroup 拿到的 master 比一下。
     * 两者一致才认——这样即使 cgroup 解析出别的名字，也不会误停无关服务。</p>
     *
     * <p>不依赖权限：{@code show} 不改任何状态，且通常不需要 root。</p>
     *
     * @param ds   数据源
     * @param unit 单元名
     * @return 该单元的 MainPID 是否等于本数据源的 master PID
     */
    private boolean ownsUnit(DataSourceVO ds, String unit) {
        // 纵深防御：当前唯一调用点 probeUnitByCgroup 已在 detectInitOwner 的 Windows 守卫之内，
        // 但本方法体是 POSIX（systemctl show），一旦将来被直接调用会在 Windows 上跑出
        // 「命令不存在」并被误读成「不是它的单元」。这里再挡一道。
        if (runtime(ds).windows()) {
            return false;
        }
        Long master = detectMasterPid(ds);
        if (master == null) {
            return false;
        }
        String script = "systemctl show " + shQuote(unit) + " -p MainPID --value 2>/dev/null";
        try {
            return String.valueOf(master).equals(trim(exec(ds, script).stdout()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 无托管者时的哨兵值
     */
    private static final String INIT_OWNER_NONE = "NONE";

    /**
     * 托管者探测结果
     *
     * @param unit   单元名，{@link #INIT_OWNER_NONE} 表示无
     * @param readAt 读取时刻（毫秒）
     */
    private record CachedInitOwner(String unit, long readAt) {
    }

    /**
     * 判断能否用 systemctl 操作该单元。
     *
     * <p><strong>问 sudo 自己，别去猜它的错误文案</strong>。
     * {@code sudo -n -l} 会列出当前用户被放行的命令，这正是我们要问的问题，
     * 且不改变任何状态、不需要密码。</p>
     *
     * <p>前面试过三种更「聪明」的办法，全都被实测否掉：</p>
     * <ul>
     *   <li>{@code sudo -n true}：只对 {@code NOPASSWD: ALL} 成立。现实部署里运维
     *       只放行具体某几条 systemctl（本机就是如此），于是永远判否，
     *       永远退回裸信号、永远被 Restart=always 拉回。</li>
     *   <li>用 {@code systemctl status} 的<strong>退出码</strong>判：status 在单元未运行
     *       时返回 3（LSB「服务未运行」），与「没权限」混在一起。实测在 inactive 时
     *       判成没权限，点启动退回裸 nginx——进程活着却脱离 systemd 托管，
     *       机器重启不会自启。</li>
     *   <li>匹配 stderr 里的<strong>权限措辞</strong>：本机 sudo 输出的是中文
     *       「需要密码」，英文正则 {@code *sudo*assword*} 匹配不上，
     *       判成「有权限」——放行了 ssh.service 这个反例也被误判通过。</li>
     * </ul>
     *
     * <p>而 {@code sudo -n -l} 的输出是结构化的（每条放行规则一行），
     * 只按「动词 + 单元名」做文本匹配，不涉及任何语言相关的措辞。</p>
     *
     * @param ds   数据源
     * @param unit 单元名
     * @return 是否可执行 systemctl
     */
    private boolean canSudo(DataSourceVO ds, String unit) {
        // Windows 没有 sudo，也没有 systemd：判为「不能免密 systemctl」即可，
        // 调用方随后会退回裸信号方式。这一步必须挡在拼脚本之前，否则会在 Windows 上
        // 执行 `sudo -n -l` 并把「命令不存在」当成「没权限」。
        if (runtime(ds).windows()) {
            return false;
        }
        // 单元名先剥掉 .service 后缀：sudoers 里带后缀与不带后缀两种写法都常见，
        // 匹配时统一按「无后缀名 + 可选 .service」处理，避免漏判。
        String bare = unit.endsWith(".service") ? unit.substring(0, unit.length() - ".service".length()) : unit;
        // 只认 start/stop/restart 三个真正要用的动词：status 未必被放行，
        // 而 reload/test 不走 systemctl，不该作为判据。
        // sudoers 里记录的是绝对路径，这里用 systemctl 结尾匹配即可，不写死路径。
        String script = "sudo -n -l 2>/dev/null | tr ',' '\\n' | tr -s ' ' "
                + "| grep -E 'systemctl' "
                // 第二个 grep 不能让它把匹配行打到 stdout：Java 侧取的是整个 stdout，
                // 中间这些规则原文会污染结果。改用 -q 只看退出码。
                + "| grep -qE '(start|stop|restart) +" + shQuote(bare) + "(\\.service)?( |$)' "
                + "&& echo YES || echo NO";
        try {
            return "YES".equals(trim(exec(ds, script).stdout()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 等待进程进入期望状态。
     *
     * @param ds      数据源
     * @param wantRun true 等运行，false 等停止
     * @param millis  最长等待毫秒
     * @return 是否在期限内达到期望状态
     */
    private boolean awaitState(DataSourceVO ds, boolean wantRun, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            boolean running = "RUNNING".equals(detectRunning(ds));
            if (running == wantRun) {
                return true;
            }
            sleep(STOP_POLL_MILLIS);
        }
        return "RUNNING".equals(detectRunning(ds)) == wantRun;
    }

    /**
     * 取 master 进程 PID。
     *
     * <p>master 由 init 拉起，PPID 为 1，据此与 worker 区分。优先走
     * {@code pgrep + ps -o ppid=}；该组合不可用时退化为解析 {@code ps -e} 的第 2 列
     * （Git Bash / BSD 的列序为 PID PPID PGID ...）。仍取不到时返回 null，
     * 界面显示「未读到 master 进程信息」而不是编一个值。</p>
     *
     * <p><strong>同机多实例时按 conf 路径过滤</strong>：光靠「PPID=1」是不够的——
     * 本机两个 nginx master 都满足这个条件（一个是 {@code eduai-web.conf}，
     * 一个是控制台管的 {@code nginx.conf}），取第一个会拿错。
     * 这里用 {@link #resolvePaths} 解析出的 conf 文件名做过滤，
     * 保证拿到的始终是本数据源管的那个。conf 解析不出来时不做过滤，
     * 退回原行为（此时也只有单实例才谈得准）。</p>
     *
     * @param ds 数据源
     * @return PID，取不到为 null
     */
    private Long detectMasterPid(DataSourceVO ds) {
        String confTail = confTail(ds);
        String script = """
                if command -v pgrep >/dev/null 2>&1 && ps -o ppid= -p 1 >/dev/null 2>&1; then
                  pgrep -x nginx 2>/dev/null | while read -r p; do
                    [ "$(ps -o ppid= -p "$p" 2>/dev/null | tr -d ' ')" = "1" ] || continue
                    %FILTER%
                  done
                  exit 0
                fi
                if ps -e >/dev/null 2>&1; then
                  ps -e 2>/dev/null | awk 'NR>1 && tolower($0) ~ /nginx/ && $2==1 %AWK% { print $1; exit }'
                  exit 0
                fi
                """;
        // 有 conf 时按命令行匹配；没有就不加过滤条件（保持原行为）
        String filter = confTail.isEmpty()
                ? ""
                : "case \"$(ps -o args= -p \"$p\" 2>/dev/null)\" in *" + confTail + "*) echo \"$p\"; break ;; esac";
        // ps -e 退化分支同样要按 conf 过滤，否则同机多实例时会拿到别人的 master
        String awk = confTail.isEmpty() ? "" : "&& index(tolower($0), \"" + confTail.toLowerCase() + "\")";
        try {
            String out = exec(ds, script.replace("%FILTER%", filter).replace("%AWK%", awk)).stdout().trim();
            return parseLong(out);
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取 master 进程失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取本数据源的 conf 文件名（不含目录），供进程匹配用。
     *
     * @param ds 数据源
     * @return 文件名，解析不出时返回空串
     */
    private String confTail(DataSourceVO ds) {
        try {
            String conf = trim(resolvePaths(ds).confPath());
            return conf.isEmpty() ? "" : baseName(conf);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * 取运行时长可读文本。
     *
     * <p>依次尝试 {@code ps -p PID -o etime=} 与 {@code ps -o etime= -p PID}；
     * 两者的 {@code -o} 支持情况因实现而异（procps 与 BusyBox 差异明显）。
     * 都取不到时返回 null。</p>
     *
     * @param ds  数据源
     * @param pid master PID
     * @return 可读文本，取不到为 null
     */
    private String uptimeText(DataSourceVO ds, Long pid) {
        if (pid == null) {
            return null;
        }
        String script = "ps -p " + pid + " -o etime= 2>/dev/null || ps -o etime= -p " + pid + " 2>/dev/null || true";
        try {
            String v = exec(ds, script).stdout().trim();
            return v.isEmpty() ? null : v;
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取运行时长失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从配置里收集监听端口。
     *
     * <p>取 {@code listen} 指令的端口部分，跳过以 ssl/http2 等关键字开头的 listen
     * （例如 {@code listen 443 ssl} 里 443 才是端口，{@code listen [::]:80} 需要剥掉前缀）。</p>
     *
     * @param ds   数据源
     * @param root 配置根
     * @return 端口列表，去重且按升序
     */
    private List<Integer> detectListenPorts(DataSourceVO ds, String root) {
        // 指令名交给平台实现去拼本机正则：POSIX 用 grep -E，Windows 用 .NET；
        // 传正则会逼调用方按平台分叉（[[:space:]] 在 .NET 里不成立）。
        ExecResult r = exec(ds, commands(ds).directiveMatches(root, "*.conf", "listen", 80));
        Set<Integer> ports = new LinkedHashSet<>();
        for (String raw : r.stdout().split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String tail = line.substring(line.indexOf("listen") + 6).trim();
            for (String token : tail.split("\\s+")) {
                String t = token;
                int colon = t.lastIndexOf(':');
                if (colon >= 0) {
                    t = t.substring(colon + 1);
                }
                if (t.matches("\\d{2,5}")) {
                    try {
                        int p = Integer.parseInt(t);
                        if (p > 0 && p < 65536) {
                            ports.add(p);
                        }
                    } catch (NumberFormatException ignored) {
                        // 非数字，跳过
                    }
                    break;
                }
            }
        }
        return new ArrayList<>(ports);
    }

    /**
     * 静默语法检查，供总览巡检使用。
     *
     * @param datasourceId 数据源 ID
     * @return 检查结果，异常时视为失败
     */
    private NginxCommandResultVO testQuietly(Long datasourceId) {
        try {
            return test(datasourceId);
        } catch (RuntimeException e) {
            return new NginxCommandResultVO(false, "语法检查执行失败: " + e.getMessage(), "", "");
        }
    }

    /**
     * 组装一条带完整路径的 nginx 命令。
     *
     * <p>所有 nginx 命令都显式带 {@code -p prefix} 与 {@code -c conf}。
     * 原因：nginx 把相对路径按 <strong>prefix</strong> 解析，而 prefix 默认是编译期的
     * {@code --prefix}；SSH 登录后的工作目录是登录用户家目录，直接跑 {@code nginx -t}
     * 会去 {@code ~/conf/nginx.conf} 找配置并报「文件不存在」。这在本地化验收时
     * 被真实复现过——命令本身没错，错在依赖了不可控的 cwd。</p>
     *
     * @param ds     数据源
     * @param action 动作参数，如 {@code -t}、{@code -s reload}；启动传空串
     * @return 完整命令文本
     */
    private String nginxCmd(DataSourceVO ds, String action) {
        NginxPaths paths = resolvePaths(ds);
        // 命令文本按平台分派：Windows 要 cd /d 先切盘符，POSIX 不要；引号规则也不同
        // （cmd 不认单引号）。这些差异全部收敛在 NginxCommandProvider 实现里。
        return commands(ds).nginxInvocation("nginx", paths.prefix(), paths.confPath(), action);
    }

    /**
     * 解析 nginx 的 prefix 与主配置路径，结果按数据源缓存。
     *
     * <p>prefix 优先取 {@code nginx -V} 的 {@code --prefix}；该值在 Windows 版 nginx 上
     * <strong>是空字符串</strong>，此时退回「nginx 可执行文件所在目录」——这正是 nginx
     * 自身在 prefix 为空时的默认解析基准。conf-path 相对 prefix 解析为绝对路径。</p>
     *
     * @param ds 数据源
     * @return prefix 与主配置路径
     */
    private NginxPaths resolvePaths(DataSourceVO ds) {
        String key = trim(ds.host()) + ":" + (ds.port() == null ? "" : ds.port());
        // 解析成功就长期缓存，不设 TTL。
        //
        // prefix 与 confPath 来自 nginx 的编译期常量（--prefix / --conf-path），
        // 同一台机器上不会变；每次过期重解析要连开两三次 SSH（nginx -V、
        // command -v、cd），碰上 SSH 慢或超时就会返回空。
        //
        // 踩过的坑正是这里：一次重解析失败让 confPath 变空，
        // 于是同机多实例的「按 conf 过滤」失效——进程匹配退化成只看有没有 nginx，
        // 结果 edurai-web 的 nginx 冒充了控制台实例，running 判成 UNKNOWN，
        // 表现成「停止成功、点启动却说已在运行」，非常难查。
        //
        // 远端真换了 nginx 安装位置时，重启后端进程即可重新解析；
        // 代价远小于「偶发解析失败导致状态判断错乱」。
        NginxPaths cached = pathCache.get(key);
        if (cached != null && cached.usable()) {
            return cached;
        }
        NginxPaths resolved = computePaths(ds);
        // 解析失败（prefix 与 confPath 皆空）不入缓存，避免毒化整个进程生命周期；
        // 下次调用会重新解析，远端环境修好后可自愈。
        if (resolved.usable()) {
            pathCache.put(key, resolved);
        } else {
            pathCache.remove(key);
        }
        log.info("[nginx] 已解析目标主机路径 prefix={} conf={}", resolved.prefix(), resolved.confPath());
        return resolved;
    }

    /**
     * 实际执行路径解析。
     *
     * @param ds 数据源
     * @return prefix 与主配置路径
     */
    private NginxPaths computePaths(DataSourceVO ds) {
        String buildArgs = "";
        try {
            // 逐 token 取 --xxx= 参数：--conf-path 与 --pid-path 相邻，
            // 用 grep -A1 之类的写法会取到下一个参数，必须按前缀精确匹配。
            // 原先是在远端 `tr ' ' '\n' | grep -E '^--[a-z-]+='`；改为取回原文后在本地切分，
            // 那两个工具都是 POSIX 专有，Windows 上没有。
            ExecResult r = exec(ds, commands(ds).versionDump(""));
            StringBuilder tokens = new StringBuilder();
            for (String token : mergeOut(r.stdout(), r.stderr()).split("\\s+")) {
                if (token.startsWith("--") && token.contains("=")) {
                    tokens.append(token).append('\n');
                }
            }
            buildArgs = tokens.toString();
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取 nginx -V 失败: {}", e.getMessage());
        }
        String prefix = firstArgValue(buildArgs, "--prefix=");
        if (prefix.isEmpty()) {
            // prefix 为空（Windows 版即如此）时，取可执行文件所在目录
            try {
                ExecResult r = exec(ds, commands(ds).which("nginx"));
                String bin = firstLine(r.stdout().trim(), "");
                if (!bin.isEmpty()) {
                    ExecResult dir = exec(ds, commands(ds).parentDir(bin));
                    prefix = dir.stdout().trim();
                }
            } catch (RuntimeException e) {
                log.debug("[nginx] 定位 nginx 可执行文件失败: {}", e.getMessage());
            }
        }
        // 去尾部分隔符：POSIX 给的是 '/etc/nginx'，Windows 的 %%~dpf 给的是 'C:\nginx\'
        // ——两种都要去，否则 Windows 上会拼出 'C:\nginx\/conf/nginx.conf' 这种混合形态
        while (prefix.endsWith("/") || prefix.endsWith("\\")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        String confPath = firstArgValue(buildArgs, "--conf-path=");
        if (confPath.isEmpty()) {
            confPath = confRoot(ds) + "/nginx.conf";
        } else if (!isAbsolutePath(confPath)) {
            // 相对路径按 prefix 解析，与 nginx 自身行为一致
            confPath = prefix.isEmpty() ? confPath : prefix + "/" + confPath;
        }
        return new NginxPaths(prefix, confPath, resolveBackupDir(ds), System.currentTimeMillis());
    }

    /**
     * 解析目标主机上实际可用的备份目录。
     *
     * <p>先探测配置的目录能否创建（{@code mkdir -p}），不能则退回家目录下的
     * {@code ~/.nginx-console-backup}。默认值位于 {@code /var/log} 下，非 root 用户
     * 通常无权创建，硬用默认值会让备份/回滚功能整体不可用。</p>
     *
     * @param ds 数据源
     * @return 可写入的备份目录绝对路径
     */
    private String resolveBackupDir(DataSourceVO ds) {
        String configured = trim(backupDir);
        if (configured.isEmpty()) {
            configured = "/var/log/nginx-console-backup";
        }
        try {
            ExecResult probe = exec(ds, commands(ds).makeDirs(configured));
            if (probe.exitCode() == 0) {
                return configured;
            }
            log.info("[nginx] 备份目录 {} 不可写，退回家目录", configured);
        } catch (RuntimeException e) {
            log.debug("[nginx] 备份目录探测失败: {}", e.getMessage());
        }
        try {
            // 回退到当前用户家目录下的固定目录名。家目录的取法按平台分派
            // （POSIX 是 $HOME，Windows 是 %USERPROFILE%），不再拼 "$HOME" 这种 POSIX 专有写法。
            String home = trim(exec(ds, commands(ds).homeDir()).stdout());
            if (!home.isEmpty()) {
                String dir = home + (home.endsWith("/") || home.endsWith("\\") ? "" : "/") + ".nginx-console-backup";
                if (exec(ds, commands(ds).makeDirs(dir)).exitCode() == 0) {
                    return dir;
                }
            }
        } catch (RuntimeException e) {
            log.debug("[nginx] 备份目录回退失败: {}", e.getMessage());
        }
        return configured;
    }

    /**
     * 从 nginx -V 的参数列表里取某个 {@code --key=value} 的值。
     *
     * @param args 参数文本，每行一个 token
     * @param key  参数前缀，含结尾等号
     * @return 参数值，取不到为空串
     */
    private static String firstArgValue(String args, String key) {
        for (String raw : args.split("\n")) {
            String line = raw.trim();
            if (line.startsWith(key)) {
                return line.substring(key.length()).trim();
            }
        }
        return "";
    }

    /**
     * 判断路径是否为绝对路径。
     *
     * <p>同时识别 POSIX 绝对路径（以 {@code /} 开头）与 Windows 盘符路径（{@code C:/}），
     * 避免在 Windows 版 nginx 上把 {@code C:/nginx/conf/nginx.conf} 误判为相对路径
     * 而再拼一次 prefix。</p>
     *
     * @param path 路径
     * @return true 表示绝对路径
     */
    private static boolean isAbsolutePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            return true;
        }
        return path.length() >= 3 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':'
                && (path.charAt(2) == '/' || path.charAt(2) == '\\');
    }

    /**
     * 取主配置文件路径。
     *
     * @param ds 数据源
     * @return 主配置绝对路径
     */
    private String configPath(DataSourceVO ds) {
        return resolvePaths(ds).confPath();
    }

    /**
     * nginx 的 prefix 与主配置路径，带解析时刻用于缓存失效判定。
     *
     * @param prefix     安装前缀目录
     * @param confPath   主配置文件绝对路径
     * @param backupDir  配置备份目录，备份与回滚的唯一落点，取自 {@link #resolveBackupDir(DataSourceVO)}：
     *                   先按配置项 {@code monitor.nginx.backup-dir}（默认 {@code /var/log/nginx-console-backup}）
     *                   探测 {@code mkdir -p} 是否可创建，不可写则退回到 SSH 用户家目录下的
     *                   {@code ~/.nginx-console-backup}，两处都失败时回退为配置值本身。
     *                   为目标主机上的路径（远端 POSIX 或 Windows 盘符路径），非空，
     *                   但回退成配置值那一支并不保证真的可写
     * @param resolvedAt 解析时刻（毫秒）；仅作诊断留痕，不参与缓存失效判定
     *                   （解析成功的结果长期缓存，见 {@link #resolvePaths}）
     */
    private record NginxPaths(String prefix, String confPath, String backupDir, long resolvedAt) {

        /**
         * 本条解析结果当前是否可直接使用。
         *
         * <p>只看 prefix：confPath 在解析失败时也会退回
         * {@code confRoot(ds) + "/nginx.conf"} 而始终非空，用它当判据会把「prefix
         * 解析失败」误判为可用。本机验收真实踩过——cmd.exe 环境下 {@code command -v nginx}
         * 取不到路径，prefix 空、confPath 非空，该结果被缓存后即使远端 shell 修好也一直
         * 带着空 prefix 执行，报 {@code CreateDirectory() "C:\\Users\\.../temp/client_body_temp"}。</p>
         *
         * @return true 表示 prefix 已成功解析
         */
        boolean usable() {
            return !prefix.isEmpty();
        }
    }

    /**
     * 取日志目录。
     *
     * <p><strong>从 nginx 实际生效的配置里解析，不再硬编码 {@code /var/log/nginx}。</strong></p>
     *
     * <p>踩过的坑：原先直接返回 {@link #DEFAULT_LOG_DIR}（方法收了 {@code ds} 参数却完全没用）。
     * 而本机的实例把日志指到了别处——{@code access_log /opt/ngx-console/nginx/logs/access.log}——
     * 于是日志面板读的一直是 {@code /var/log/nginx/access.log}，那个文件存在但是 0 字节。
     * 现象是「面板说日志是空的、访问统计全是 0」，而目标机 {@code nginx -T} 里日志明明有 5000+ 行。
     * 换个部署目录、换个日志路径就彻底失效，且没有任何报错。</p>
     *
     * <p>用 {@code nginx -T} 而不是解析配置文件：它会把 {@code include} 全部展开，
     * 不用自己递归；且它只读配置、不改状态。实测普通用户即可运行（配置文件 world-readable）。</p>
     *
     * <p>解析不到时回退 {@link #DEFAULT_LOG_DIR}：那是绝大多数发行版的默认位置，
     * 拿它兜底至少不会比改动前更差。</p>
     *
     * @param ds 数据源
     * @return 日志目录绝对路径
     */
    private String logDir(DataSourceVO ds) {
        String dir = logPathDir(ds);
        return dir.isEmpty() ? DEFAULT_LOG_DIR : dir;
    }

    /**
     * 从生效配置里解析日志文件所在目录。
     *
     * @param ds 数据源
     * @return 目录路径；解析不到返回空串
     */
    private String logPathDir(DataSourceVO ds) {
        // 一次 nginx -T 就能同时拿到 access_log 与 error_log，取其中任意一个的目录即可：
        // 两者通常同目录，分开放也没关系——这里只要「能列出 *.log 的目录」。
        // 原先在远端 `grep -oE … | head -5 | awk | sed | xargs dirname | head -1`——
        // 五个 POSIX 工具串成管道；改为取回 -T 原文后在本地解析。
        // 取 nginx -T 的配置全文。必须走 nginxInvocation 而不是裸 `nginx -T`：
        // 实测 Windows 上裸 `nginx -T` 会因「按当前工作目录找 logs/error.log」而
        // CreateFile 失败、exit=1、只回 351 字符错误文本；带 cd /d + -c 后才是
        // 完整配置（10725 字符）。POSIX 侧的 -p/-c 同理，只是恰好没暴露。
        NginxPaths paths = resolvePaths(ds);
        ExecResult r = exec(ds, commands(ds).nginxInvocation("nginx", paths.prefix(), paths.confPath(), "-T"));
        String dir = "";
        int seen = 0;
        for (String raw : mergeOut(r.stdout(), r.stderr()).split("\n")) {
            String line = raw.trim();
            if (!line.startsWith("access_log") && !line.startsWith("error_log")) {
                continue;
            }
            if (++seen > 5) {
                // 与原 `head -5` 一致：只看前 5 条，避免遍历整个配置树
                break;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            String path = parts[1].replace("\"", "").replace("'", "");
            if (path.isEmpty() || "off".equals(path)) {
                continue;
            }
            dir = parentOf(path);
            if (!dir.isEmpty()) {
                break;
            }
        }
        if (dir.isEmpty() || "/".equals(dir)) {
            return "";
        }
        // 解析出来却不存在，比解析失败更可疑：说明配置里的路径与磁盘对不上，
        // 此时回退默认值并留日志，比让用户对着空列表猜要强
        if (exec(ds, commands(ds).dirExists(dir)).exitCode() != 0) {
            log.debug("[nginx] 配置里的日志目录 {} 不存在，回退 {}", dir, DEFAULT_LOG_DIR);
            return "";
        }
        return dir;
    }

    /**
     * 取默认 access log 的实际路径。
     *
     * <p>与 {@link #logDir} 同源：配置里指到别处时就用那个路径，
     * 否则读一个 0 字节的旧文件会让人以为「没有访问记录」。</p>
     *
     * @param ds 数据源
     * @return access log 绝对路径
     */
    private String defaultAccessLog(DataSourceVO ds) {
        String dir = logDir(ds);
        return dir + "/access.log";
    }

    /**
     * 判断日志类型。
     *
     * @param path 日志路径
     * @return access / error / other
     */
    private static String logType(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("error")) {
            return "error";
        }
        if (p.contains("access")) {
            return "access";
        }
        return "other";
    }

    /**
     * 从备份文件名还原原始配置路径。
     *
     * <p>备份时把 {@code /} 换成了 {@code _}，因此这里只能还原「同深度相对路径」，
     * 无法区分 {@code /etc/nginx/conf.d/a.conf} 与 {@code /opt/etc/nginx/...} 这类
     * 含下划线的路径。这是刻意的取舍：与其猜错路径把文件写错地方，
     * 不如明确提示用户手工指定目标路径。</p>
     *
     * @param name 备份文件名
     * @return 推测的原始路径，无法还原时为空串
     */
    private String originPathOfBackup(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return "";
        }
        String stamp = name.substring(dot + 1, name.length() - 4);
        if (stamp.isEmpty() || !stamp.contains("-")) {
            return "";
        }
        String base = name.substring(0, dot).replace('_', '/');
        return base.startsWith("/") ? base : "";
    }

    /**
     * 解析数据源：按 ID 取并校验类型，缺省时挑第一个 nginx 数据源。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 数据源配置
     */
    private DataSourceVO requireDatasource(Long datasourceId) {
        if (datasourceId != null && datasourceId > 0L) {
            ReturnResult<DataSourceVO> result = datasourceService.get(datasourceId);
            if (result == null || !result.isOk() || result.getData() == null) {
                throw new IllegalStateException("数据源 #" + datasourceId + " 不存在");
            }
            DataSourceVO ds = result.getData();
            if (!DS_TYPE.equalsIgnoreCase(trim(ds.type()))) {
                throw new IllegalStateException("数据源 #" + datasourceId + " 是 " + ds.type() + " 类型，无法用于 Nginx 管理");
            }
            return ds;
        }
        ReturnResult<List<DataSourceVO>> list = datasourceService.list();
        if (list != null && list.getData() != null) {
            for (DataSourceVO ds : list.getData()) {
                if (DS_TYPE.equalsIgnoreCase(trim(ds.type()))) {
                    return ds;
                }
            }
        }
        throw new IllegalStateException("尚未登记 nginx 数据源，请先在「数据源管理」新增");
    }

    /**
     * 取配置根路径，空则用默认值。
     *
     * @param ds 数据源
     * @return 配置根路径
     */
    private static String confRoot(DataSourceVO ds) {
        String v = trim(ds.database());
        return v.isEmpty() ? DEFAULT_CONF_ROOT : v;
    }

    /**
     * 以 base64 写文件，绕开引号、反斜杠与换行。
     *
     * @param ds      数据源
     * @param path    目标路径
     * @param content 内容
     */
    private void writeFile(DataSourceVO ds, String path, String content) {
        String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        // 写路径交给平台实现：POSIX 是 printf|base64 -d，Windows 没有 base64 命令，
        // 由实现改用 .NET 解码落盘。这是「保存配置」的主干，Windows 上必须通。
        ExecResult r = exec(ds, commands(ds).writeFile(path, b64));
        if (r.exitCode() != 0) {
            throw new IllegalStateException("写入配置失败: " + mergeOut(r.stdout(), r.stderr()));
        }
    }

    /**
     * SSH 执行结果。
     *
     * @param exitCode 退出码
     * @param stdout   标准输出
     * @param stderr   标准错误
     */
    private record ExecResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * 在目标主机执行一条命令。
     *
     * @param ds  数据源
     * @param cmd 命令
     * @return 执行结果
     * @throws IllegalStateException 连接或执行失败
     */
    private ExecResult exec(DataSourceVO ds, String cmd) {
        // 默认超时按平台给：Windows 侧命令要经 cmd → PowerShell 两层进程，
        // 实测冷启动约 9.5s、机器繁忙时可达 27s；沿用 10s 会间歇性超时
        // （表现为「同一个接口时好时坏」，很难查）。POSIX 侧维持原值不动。
        return exec(ds, cmd, runtime(ds).windows() ? WINDOWS_EXEC_TIMEOUT_SECONDS : EXEC_TIMEOUT_SECONDS);
    }

    /**
     * Windows 侧默认执行超时（秒）。
     *
     * <p>PowerShell 冷启动实测约 9.5s，与原先 10s 的默认值几乎贴边，必然间歇失败。
     * 放宽到 60s 只影响「等多久才放弃」，不改变成功路径。</p>
     */
    private static final long WINDOWS_EXEC_TIMEOUT_SECONDS = 60L;

    /**
     * 在目标主机执行一条命令（可指定超时）。
     *
     * @param ds      数据源
     * @param cmd     命令
     * @param seconds 超时秒数
     * @return 执行结果
     * @throws IllegalStateException 连接或执行失败
     */
    private ExecResult exec(DataSourceVO ds, String cmd, long seconds) {
        // 执行改走 SPI 通道：远端 SSH（POSIX / Windows 同一段代码，命令文本已按平台拼好），
        // 或本机直接执行（数据源 host 为空 / 为 local 时）。本方法不再自建 SshClient。
        String key = trim(ds.host()) + ":" + (ds.port() == null ? 0 : ds.port());
        Long until = connectFailUntil.get(key);
        long now = System.currentTimeMillis();
        if (until != null && now < until) {
            // 快速失败：目标最近连不上，冷却期内连试都不试。
            // 不加这层的话，一次 overview 会顺序发多条命令、每条白等一次连接超时，
            // 实测不可达主机要 ~70 秒才返回；并发下会长时间占住线程。
            throw new IllegalStateException("目标主机 " + key + " 最近一次连接失败，"
                    + Math.max(1, (until - now) / 1000) + " 秒内快速失败（不再重试）。");
        }
        try {
            NginxExecutor.ExecResult r = runtime(ds).exec(cmd, seconds);
            // 成功即清掉冷却：恢复后立刻回归正常，不必等冷却自然过期
            connectFailUntil.remove(key);
            return new ExecResult(r.exitCode(), r.stdout(), r.stderr());
        } catch (RuntimeException e) {
            // 只有**连接级**失败才开断路器。命令级失败（如 nginx -t 报语法错）不能算，
            // 否则一次配置写错会把整台机器判成不可达、后面全部快速失败。
            if (isConnectFailure(e)) {
                connectFailUntil.put(key, System.currentTimeMillis() + CONNECT_FAIL_COOLDOWN_MILLIS);
            }
            throw e;
        }
    }

    /**
     * 连接级失败的冷却时长（毫秒）。
     *
     * <p><strong>必须明显大于「一次请求的耗时」，否则冷却会在请求中途过期、又开始重试。</strong>
     * 实测（198 → 不可达主机 192.0.2.1）：单条命令失败要 10s，而一次请求会顺序发多条命令
     * （log/files 还要先走 resolvePaths），总耗时可达 40~180s。冷却设 20s 时只挡住头几条命令，
     * 之后又全量重试——表现为「有点效果但依然极慢」。</p>
     *
     * <p>取 120s 的权衡：目标不可达通常意味着它长时间不在线，用「最多 2 分钟才重新探测一次」
     * 换「故障期间全程秒失败」是划算的；恢复后第一次访问仍会真连一次，成功后立即清除冷却。</p>
     */
    private static final long CONNECT_FAIL_COOLDOWN_MILLIS = 120_000L;

    /**
     * 「host:port → 冷却截止时刻」。
     *
     * <p>只记录连接级失败，成功即清除。</p>
     */
    private final Map<String, Long> connectFailUntil = new ConcurrentHashMap<>();

    /**
     * 判断异常是否为**连接级**失败（而非远端命令执行失败）。
     *
     * <p>按消息关键字判定，因为通道实现抛出的都是 {@link IllegalStateException}，
     * 类型上分不开。宁可漏判（退回逐条重试的老行为，只是慢），也不要误判——
     * 误判会把「命令报错」升级成「整机不可达」。</p>
     *
     * @param e 异常
     * @return 是连接级失败返回 true
     */
    private static boolean isConnectFailure(Throwable e) {
        String m = e == null || e.getMessage() == null ? "" : e.getMessage();
        return m.contains("SSH 连接失败")
                || m.contains("Timed out")
                || m.contains("Connection refused")
                || m.contains("Auth fail")
                || m.contains("认证失败")
                || m.contains("No route to host")
                || m.contains("不可达");
    }

    /**
     * 取数据源对应的运行时（平台 + 命令文本提供者 + 执行通道）。
     *
     * <p>平台首次解析需要一次探测往返，路由器内部按 host:port:user 缓存，后续不再探测。</p>
     *
     * @param ds 数据源
     * @return 运行时
     */
    private NginxRuntimeRouter.Runtime runtime(DataSourceVO ds) {
        return nginxRuntimeRouter.resolve(ds);
    }

    /**
     * 取数据源对应的命令文本提供者（拼命令用）。
     *
     * @param ds 数据源
     * @return 命令文本提供者
     */
    private NginxCommandProvider commands(DataSourceVO ds) {
        return runtime(ds).commands();
    }

    /**
     * 合并 stdout 与 stderr 作为可读结果。
     *
     * @param stdout 标准输出
     * @param stderr 标准错误
     * @return 合并文本
     */
    private static String mergeOut(String stdout, String stderr) {
        String out = trim(stdout);
        String err = trim(stderr);
        if (out.isEmpty()) {
            return err;
        }
        return err.isEmpty() ? out : out + "\n" + err;
    }

    /**
     * 合并执行结果。
     *
     * @param r 执行结果
     * @return 合并文本
     */
    private static String mergeOut(ExecResult r) {
        return mergeOut(r.stdout(), r.stderr());
    }

    /**
     * 取第一行非空文本，用于把多行命令输出压成一句提示。
     *
     * @param first  首选文本
     * @param second 次选文本
     * @return 第一行非空文本
     */
    private static String firstLine(String first, String second) {
        for (String candidate : List.of(first == null ? "" : first, second == null ? "" : second)) {
            for (String line : candidate.split("\n")) {
                String v = line.trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return "";
    }

    /**
     * Shell 单引号包裹路径，转义内部单引号。
     *
     * @param value 原始路径
     * @return 可安全拼进 shell 的字面量
     */
    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 宽松取长整数。
     *
     * @param value 原始文本
     * @return 长整数，取不到为 null
     */
    private static Long parseLong(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text.replaceAll("[^0-9-].*", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 睡眠。
     *
     * @param millis 毫秒
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 要求文本非空。
     *
     * @param value   原值
     * @param message 为空提示
     * @return 去空白值
     */
    private static String requireText(String value, String message) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    /**
     * 空值安全去空白。
     *
     * @param value 原值
     * @return 去空白结果
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // ==================== 时序指标：采集与趋势 ====================

    @Override
    public int collectMetric(Long datasourceId) {
        MsMonitorNginxMetricMapper mapper = nginxMetricMapper.getIfAvailable();
        if (mapper == null || datasourceId == null) {
            return 0;
        }
        NginxStatusVO st;
        try {
            st = status(datasourceId);
        } catch (RuntimeException e) {
            // 采集是后台行为，单个数据源失败不能打断调度
            log.warn("[nginx] 指标采集异常 datasourceId={} reason={}", datasourceId, e.getMessage());
            return 0;
        }
        if (st == null || !st.available()) {
            // 取不到就跳过，绝不写 0 充数——那会在趋势图上造出一条假的「归零」直线
            log.debug("[nginx] stub_status 不可用，跳过采集 datasourceId={} reason={}",
                    datasourceId, st == null ? "null" : st.message());
            return 0;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<MsMonitorNginxMetric> rows = new ArrayList<>();
        addMetricRow(rows, datasourceId, "active", st.activeConnections(), now);
        addMetricRow(rows, datasourceId, "accepts", st.accepts(), now);
        addMetricRow(rows, datasourceId, "handled", st.handled(), now);
        addMetricRow(rows, datasourceId, "requests", st.requests(), now);
        addMetricRow(rows, datasourceId, "reading", st.reading(), now);
        addMetricRow(rows, datasourceId, "writing", st.writing(), now);
        addMetricRow(rows, datasourceId, "waiting", st.waiting(), now);
        for (MsMonitorNginxMetric row : rows) {
            try {
                // 幂等：同一时刻重复采集（多实例 / 重试）由唯一键冲突兜住，
                // 这里只把「重复键」当正常情况静默跳过，其它异常照实告警
                mapper.insert(row);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.trace("[nginx] 指标已存在，跳过 datasourceId={} name={}", datasourceId, row.getMetricName());
            } catch (RuntimeException e) {
                log.warn("[nginx] 指标写入失败 datasourceId={} name={} reason={}",
                        datasourceId, row.getMetricName(), e.getMessage());
            }
        }
        return rows.size();
    }

    /**
     * 组装一行指标；值为 null 时跳过，不占位。
     *
     * @param rows  目标列表
     * @param dsId  数据源 id
     * @param name  指标名
     * @param value 原始值
     * @param time  采集时刻
     */
    private static void addMetricRow(List<MsMonitorNginxMetric> rows, Long dsId, String name,
                                     Long value, LocalDateTime time) {
        if (value == null) {
            return;
        }
        MsMonitorNginxMetric row = new MsMonitorNginxMetric();
        row.setDatasourceId(dsId);
        row.setMetricName(name);
        row.setValue(value.doubleValue());
        row.setCollectTime(time);
        rows.add(row);
    }

    @Override
    public List<Long> listNginxDatasourceIds() {
        List<Long> ids = new ArrayList<>();
        try {
            ReturnResult<List<DataSourceVO>> list = datasourceService.list();
            if (list == null || list.getData() == null) {
                return ids;
            }
            for (DataSourceVO ds : list.getData()) {
                if (ds != null && DS_TYPE.equalsIgnoreCase(trim(ds.type())) && ds.id() != null) {
                    ids.add(ds.id());
                }
            }
        } catch (RuntimeException e) {
            log.warn("[nginx] 列出数据源失败，跳过本轮采集 reason={}", e.getMessage());
        }
        return ids;
    }

    @Override
    public NginxAccessTopVO accessTop(Long datasourceId, String path, Integer lines, Integer minutes, Integer topN) {
        String logPath = trim(path);
        if (logPath.isEmpty()) {
            logPath = defaultAccessLog(requireDatasource(datasourceId));
        }
        // 尾部行数上限放大到 20000：Top 榜需要足够样本才有意义，
        // 但也不能无上限——高流量站的日志一行上百字节，拉 10 万行就是十几 MB。
        int n = lines == null || lines <= 0 ? 2000 : Math.min(lines, 20_000);
        int top = topN == null || topN <= 0 ? 10 : Math.min(topN, 50);
        int mins = minutes == null ? 0 : Math.max(minutes, 0);
        DataSourceVO ds = requireDatasource(datasourceId);
        String text = exec(ds, commands(ds).tailLines(logPath, n)).stdout();
        LocalDateTime since = mins > 0 ? LocalDateTime.now().minusMinutes(mins) : null;
        String name = baseName(logPath);
        return NginxAccessLogParser.parse(text, top, since, name);
    }

    @Override
    public NginxUrlTrendVO urlTrend(Long datasourceId, String path, Integer lines, Integer topN) {
        String logPath = trim(path);
        if (logPath.isEmpty()) {
            logPath = defaultAccessLog(requireDatasource(datasourceId));
        }
        // 尾部行数上限与 accessTop 一致（20000）：URL 趋势需要足够样本才能分出形状，
        // 但也不能无上限，高流量站一行上百字节，拉 10 万行就是十几 MB。
        int n = lines == null || lines <= 0 ? 5000 : Math.min(lines, 20_000);
        int cap = topN == null || topN <= 0 ? 5 : Math.min(topN, 10);
        DataSourceVO ds = requireDatasource(datasourceId);
        String text = exec(ds, commands(ds).tailLines(logPath, n)).stdout();
        String name = baseName(logPath);
        return NginxAccessLogParser.urlTrend(text, cap, name);
    }

    @Override
    public List<NginxTrendVO.Point> latestPoints(Long datasourceId) {
        if (datasourceId == null) {
            return List.of();
        }
        MsMonitorNginxMetricMapper mapper = nginxMetricMapper.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        // 窗口 5 分钟：默认 30s 间隔下约 10 个周期，足够算出速率又不至于把整表拉进内存
        List<MsMonitorNginxMetric> rows = mapper.selectList(
                new LambdaQueryWrapper<MsMonitorNginxMetric>()
                        .eq(MsMonitorNginxMetric::getDatasourceId, datasourceId)
                        .ge(MsMonitorNginxMetric::getCollectTime,
                                LocalDateTime.now().withNano(0).minusMinutes(REALTIME_WINDOW_MINUTES))
                        .orderByAsc(MsMonitorNginxMetric::getCollectTime));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 复用与 HTTP 趋势完全相同的换算逻辑，保证两条口径一致
        List<NginxTrendVO.Point> points = buildTrendPoints(rows);
        Map<String, NginxTrendVO.Point> latest = new LinkedHashMap<>();
        for (NginxTrendVO.Point point : points) {
            latest.put(point.name(), point);
        }
        return List.copyOf(latest.values());
    }

    @Override
    public NginxRuntimeVO collectRealtime(Long datasourceId) {
        if (datasourceId == null) {
            return new NginxRuntimeVO(null, "UNKNOWN", null, null, null, false,
                    LocalDateTime.now().withNano(0), "未指定数据源");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        DataSourceVO ds;
        try {
            ds = requireDatasource(datasourceId);
        } catch (RuntimeException e) {
            return new NginxRuntimeVO(datasourceId, "UNKNOWN", null, null, null, false, now,
                    "读取数据源失败: " + e.getMessage());
        }
        // 三项都走总览的同一份实现，口径不许分叉
        String running = detectRunning(ds);
        Long pid = null;
        String uptime = null;
        try {
            pid = detectMasterPid(ds);
            uptime = uptimeText(ds, pid);
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取进程信息失败 datasourceId={} reason={}", datasourceId, e.getMessage());
        }
        Long active = null;
        boolean available = false;
        String message = null;
        try {
            NginxStatusVO st = status(datasourceId);
            available = st != null && st.available();
            if (available) {
                active = st.activeConnections();
                message = st.message();
            } else {
                message = st == null ? "未取到 stub_status" : st.message();
            }
        } catch (RuntimeException e) {
            message = "读取 stub_status 失败: " + e.getMessage();
        }
        return new NginxRuntimeVO(datasourceId, running, pid, uptime, active, available, now, message);
    }

    @Override
    public NginxTrendVO trend(Long datasourceId, Integer hours, Integer interval) {
        int h = (hours == null || hours <= 0) ? TREND_DEFAULT_HOURS : Math.min(hours, 24 * 30);
        int step = (interval == null || interval <= 0) ? TREND_DEFAULT_INTERVAL_MINUTES : interval;
        LocalDateTime to = LocalDateTime.now().withNano(0);
        LocalDateTime from = to.minusHours(h);
        if (datasourceId == null) {
            return NginxTrendVO.empty(null, from, to, step, "未指定数据源");
        }
        MsMonitorNginxMetricMapper mapper = nginxMetricMapper.getIfAvailable();
        if (mapper == null) {
            return NginxTrendVO.empty(datasourceId, from, to, step, "时序指标未启用");
        }
        // 间隔过小会拉爆返回体，按点数上限自动放大
        int maxRowsPerMetric = Math.max(1, h * 60 / step);
        if (maxRowsPerMetric > TREND_MAX_POINTS) {
            step = (int) Math.ceil((double) h * 60 / TREND_MAX_POINTS);
        }
        List<MsMonitorNginxMetric> rows = mapper.selectList(
                new LambdaQueryWrapper<MsMonitorNginxMetric>()
                        .eq(MsMonitorNginxMetric::getDatasourceId, datasourceId)
                        .ge(MsMonitorNginxMetric::getCollectTime, from)
                        .le(MsMonitorNginxMetric::getCollectTime, to)
                        .orderByAsc(MsMonitorNginxMetric::getCollectTime));
        if (rows == null || rows.isEmpty()) {
            return NginxTrendVO.empty(datasourceId, from, to, step,
                    "最近 " + h + " 小时没有采集到数据。趋势依赖后台定时采集，"
                            + "请确认目标机已配置 stub_status（/nginx_status）且可访问。");
        }
        List<NginxTrendVO.Point> points = buildTrendPoints(rows);
        return new NginxTrendVO(datasourceId, from, to, step, points, null);
    }

    /**
     * 把原始行整理成曲线点，并把累计型指标换算成速率。
     *
     * <p>速率一律用「实际时间差」计算，而不是配置的 step：采样稀疏时
     * 用配置间隔会把速率算小。</p>
     *
     * @param rows 原始行，按时间升序
     * @return 曲线点
     */
    private static List<NginxTrendVO.Point> buildTrendPoints(List<MsMonitorNginxMetric> rows) {
        List<NginxTrendVO.Point> points = new ArrayList<>(rows.size());
        // 每个指标各自维护「上一个点」，因为不同指标的采集时刻可能不同
        Map<String, MsMonitorNginxMetric> prev = new LinkedHashMap<>();
        for (MsMonitorNginxMetric row : rows) {
            String name = row.getMetricName();
            Double value = row.getValue();
            Double rate = null;
            if (value != null) {
                if (CUMULATIVE_METRICS.contains(name)) {
                    MsMonitorNginxMetric last = prev.get(name);
                    if (last != null && last.getValue() != null) {
                        long seconds = Duration.between(last.getCollectTime(), row.getCollectTime()).getSeconds();
                        double diff = value - last.getValue();
                        // 负差值 = worker 重启后计数器归零。画成负尖峰会被误读成「流量暴跌」，
                        // 这里直接断线，让前端看到的是「这里重启过」而不是「流量崩了」
                        if (seconds > 0 && diff >= 0) {
                            rate = diff / seconds;
                        }
                    }
                } else {
                    rate = value;
                }
            }
            points.add(new NginxTrendVO.Point(row.getCollectTime(), name, value, rate));
            prev.put(name, row);
        }
        return points;
    }
}
