package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.monitor.support.entity.MsMonitorNginxMetric;
import com.chua.starter.monitor.support.mapper.MsMonitorNginxMetricMapper;
import com.chua.starter.monitor.support.middleware.MiddlewareHttp;
import com.chua.starter.monitor.support.model.NginxApplyResultVO;
import com.chua.starter.monitor.support.model.NginxBackupVO;
import com.chua.starter.monitor.support.model.NginxCommandResultVO;
import com.chua.starter.monitor.support.model.NginxConfigFileVO;
import com.chua.starter.monitor.support.model.NginxConfigParseVO;
import com.chua.starter.monitor.support.model.NginxDirectiveMetaVO;
import com.chua.starter.monitor.support.model.NginxLogChunkVO;
import com.chua.starter.monitor.support.model.NginxLogFileVO;
import com.chua.starter.monitor.support.model.NginxOverviewVO;
import com.chua.starter.monitor.support.model.NginxSiteVO;
import com.chua.starter.monitor.support.model.NginxStatusVO;
import com.chua.starter.monitor.support.model.NginxAccessTopVO;
import com.chua.starter.monitor.support.model.NginxTemplateVO;
import com.chua.starter.monitor.support.model.NginxTrendVO;
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
     * 默认 access 日志
     */
    private static final String DEFAULT_ACCESS_LOG = "/var/log/nginx/access.log";

    /**
     * 默认 error 日志
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
     * 退出后等待进程真正结束的轮询上限
     */
    private static final int QUIT_WAIT_TIMES = 10;

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
     * 路径解析结果的缓存有效期（毫秒）。
     *
     * <p>prefix / confPath 正常情况下是编译期常量，缓存只是为省掉每次命令多开一次
     * SSH 连接。但「解析失败」不是常量——远端 shell 类型不对、SSH 端口不通、
     * nginx 未装好时都会解析出空 prefix。这条空结果一旦被缓存，就会毒化整个进程
     * 生命周期：后续所有命令都带着错误的 prefix 执行，且永远不会自愈。本机验收时
     * 真实踩过：先在 cmd.exe 环境下解析出空 prefix，修好 DefaultShell 后不重启
     * 进程就依旧报 {@code CreateDirectory() "C:\\Users\\.../temp/client_body_temp"}。</p>
     */
    private static final long PATH_CACHE_TTL_MILLIS = 600_000L;

    /**
     * 各目标主机已解析的 nginx prefix / 主配置路径。
     *
     * <p>取值带时间戳与可用性判定，超过 {@link #PATH_CACHE_TTL_MILLIS} 或解析结果
     * 不可用时都会重新解析。线程安全由 {@link ConcurrentHashMap} 保证。</p>
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
        // -printf 是 GNU find 扩展，BSD/macOS 上不可用；因此改用 stat 的可移植组合
        String out = exec(ds, "find " + shQuote(root) + " -name '*.conf' -type f 2>/dev/null | while read -r f; do "
                + "printf '%s\\t%s\\t%s\\n' \"$f\" \"$(wc -c < \"$f\" 2>/dev/null | tr -d ' ')\" "
                + "\"$(date -r \"$f\" '+%Y-%m-%d %H:%M' 2>/dev/null || stat -c '%y' \"$f\" 2>/dev/null | cut -d. -f1)\"; "
                + "done").stdout();
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
        return exec(ds, "cat " + shQuote(safePath)).stdout();
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
                if (exec(ds, "test -f " + shQuote(path)).exitCode() == 0) {
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
        // quit 是优雅退出：处理完手上的请求再走，比 stop 温和
        ExecResult quit = exec(ds, nginxCmd(ds, "-s quit"), LIFECYCLE_TIMEOUT_SECONDS);
        if (quit.exitCode() != 0) {
            return new NginxCommandResultVO(false,
                    "发送退出信号失败: " + mergeOut(quit.stdout(), quit.stderr()), quit.stdout(), quit.stderr());
        }
        for (int i = 0; i < QUIT_WAIT_TIMES; i++) {
            if ("STOPPED".equals(detectRunning(ds))) {
                break;
            }
            sleep(600L);
        }
        if (!"STOPPED".equals(detectRunning(ds))) {
            return new NginxCommandResultVO(false,
                    "优雅退出超时，进程仍在运行。为避免双实例冲突已放弃重新拉起，请先确认进程状态",
                    quit.stdout(), quit.stderr());
        }
        ExecResult up = exec(ds, nginxCmd(ds, ""), LIFECYCLE_TIMEOUT_SECONDS);
        boolean ok = up.exitCode() == 0;
        String running2 = detectRunning(ds);
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
        ExecResult r = exec(ds, nginxCmd(ds, "-s stop"), LIFECYCLE_TIMEOUT_SECONDS);
        boolean ok = r.exitCode() == 0;
        String msg;
        if (!ok) {
            msg = "停止失败: " + mergeOut(r.stdout(), r.stderr());
        } else {
            for (int i = 0; i < QUIT_WAIT_TIMES; i++) {
                if ("STOPPED".equals(detectRunning(ds))) {
                    break;
                }
                sleep(500L);
            }
            msg = "STOPPED".equals(detectRunning(ds)) ? "已停止" : "已发送停止信号，但进程尚未退出，请稍后确认";
        }
        return new NginxCommandResultVO(ok, msg, r.stdout(), r.stderr());
    }

    @Override
    public NginxCommandResultVO start(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        if (!"STOPPED".equals(detectRunning(ds))) {
            return new NginxCommandResultVO(false, "nginx 已在运行，无需重复启动（需要加载新配置请用「重载」）", "", "");
        }
        NginxCommandResultVO t = test(datasourceId);
        if (!t.success()) {
            return new NginxCommandResultVO(false, "语法检查未通过，已放弃启动", t.stdout(), t.stderr());
        }
        ExecResult r = exec(ds, nginxCmd(ds, ""), LIFECYCLE_TIMEOUT_SECONDS);
        boolean ok = r.exitCode() == 0;
        String state = detectRunning(ds);
        String msg;
        if (!ok) {
            msg = "启动失败: " + mergeOut(r.stdout(), r.stderr());
        } else if ("RUNNING".equals(state)) {
            msg = "启动成功，nginx 已运行";
        } else {
            msg = "命令已下发但未检测到运行中的进程，请检查错误日志";
        }
        return new NginxCommandResultVO(ok, msg, r.stdout(), r.stderr());
    }

    // ==================== 版本与状态 ====================

    @Override
    public NginxVersionVO version(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        // nginx -V 的编译参数输出在 stderr
        ExecResult r = exec(ds, "nginx -V 2>&1");
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
        String out = exec(ds, "ls -1 " + shQuote(dir) + "/*.log 2>/dev/null | while read -r f; do "
                + "printf '%s\\t%s\\t%s\\n' \"$f\" \"$(wc -c < \"$f\" 2>/dev/null | tr -d ' ')\" "
                + "\"$(date -r \"$f\" '+%Y-%m-%d %H:%M' 2>/dev/null || stat -c '%y' \"$f\" 2>/dev/null | cut -d. -f1)\"; "
                + "done").stdout();
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
            safePath = DEFAULT_ACCESS_LOG;
        }
        return exec(ds, "tail -n " + n + " " + shQuote(safePath) + " 2>&1").stdout();
    }

    @Override
    public NginxLogChunkVO logChunk(Long datasourceId, String path, Integer lines, Long cursor) {
        DataSourceVO ds = requireDatasource(datasourceId);
        int n = lines == null || lines <= 0 ? 200 : Math.min(lines, 5000);
        String safePath = trim(path);
        if (safePath.isEmpty()) {
            safePath = DEFAULT_ACCESS_LOG;
        }
        Long size = fileSize(ds, safePath);
        boolean rotated = false;
        if (cursor != null && cursor > 0) {
            // 文件比上次读到的位置还小，说明被轮转或截断了，此时全量重读
            rotated = size == null || size < cursor;
        }
        if (cursor == null || cursor <= 0 || rotated) {
            String all = exec(ds, "tail -n " + n + " " + shQuote(safePath) + " 2>&1").stdout();
            return new NginxLogChunkVO(all, size, size, rotated, safePath);
        }
        // 只取游标之后新增的字节；单次最多 256KB，避免日志暴涨时一次传输过大
        String delta = exec(ds, "tail -c +" + (cursor + 1) + " " + shQuote(safePath)
                + " 2>/dev/null | head -c 262144").stdout();
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
            ExecResult r = exec(ds, "wc -c < " + shQuote(path) + " 2>/dev/null | tr -d ' '");
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
        String name = trim(safePath).replace('/', '_') + "." + LocalDateTime.now().format(BACKUP_STAMP) + ".bak";
        String target = dir + "/" + name;
        ExecResult r = exec(ds, "mkdir -p " + shQuote(dir) + " && cp -p " + shQuote(safePath)
                + " " + shQuote(target) + " && printf '%s' \"$(wc -c < " + shQuote(target) + " | tr -d ' ')\"");
        if (r.exitCode() != 0) {
            throw new IllegalStateException("备份失败: " + mergeOut(r.stdout(), r.stderr()));
        }
        return new NginxBackupVO(name, target, parseLong(r.stdout().trim()), LocalDateTime.now().toString(),
                null, trim(reason).isEmpty() ? "手动备份" : reason);
    }

    @Override
    public List<NginxBackupVO> listBackups(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String dir = resolvePaths(ds).backupDir();
        String out = exec(ds, "ls -1t " + shQuote(dir) + " 2>/dev/null | while read -r n; do "
                + "printf '%s\\t%s\\t%s\\n' \"$n\" \"$(wc -c < \"" + dir + "/$n\" 2>/dev/null | tr -d ' ')\" "
                + "\"$(date -r \"" + dir + "/$n\" '+%Y-%m-%d %H:%M' 2>/dev/null)\"; "
                + "done").stdout();
        List<NginxBackupVO> result = new ArrayList<>();
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            result.add(new NginxBackupVO(parts[0], dir + "/" + parts[0],
                    parts.length >= 2 ? parseLong(parts[1]) : null,
                    parts.length >= 3 ? parts[2] : null, null, null));
        }
        return result;
    }

    @Override
    public NginxCommandResultVO restore(Long datasourceId, String backupName, String targetPath) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String name = requireText(backupName, "备份文件名不能为空");
        // 只允许纯文件名，禁止出现路径分隔符，避免越权读到备份目录之外
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalStateException("备份文件名非法");
        }
        // 优先用调用方指定的目标路径；否则按备份名反推
        String origin = trim(targetPath);
        if (origin.isEmpty()) {
            origin = originPathOfBackup(name);
        }
        if (origin.isEmpty()) {
            return new NginxCommandResultVO(false,
                    "无法从备份文件名还原原始路径（路径含下划线时不可逆），请显式指定还原到的目标文件路径", "", "");
        }
        String src = resolvePaths(ds).backupDir() + "/" + name;
        ExecResult exists = exec(ds, "test -f " + shQuote(src));
        if (exists.exitCode() != 0) {
            return new NginxCommandResultVO(false, "备份文件不存在: " + name, "", "");
        }
        ExecResult r = exec(ds, "cat " + shQuote(src));
        if (r.exitCode() != 0) {
            return new NginxCommandResultVO(false, "读取备份失败: " + mergeOut(r.stdout(), r.stderr()),
                    r.stdout(), r.stderr());
        }
        // 恢复前先把当前内容也备份一份，保证"恢复"这一步本身可逆
        try {
            backup(datasourceId, origin, "恢复前自动备份");
        } catch (RuntimeException e) {
            return new NginxCommandResultVO(false, "恢复前备份失败，已放弃恢复: " + e.getMessage(), "", "");
        }
        writeFile(ds, origin, r.stdout());
        NginxCommandResultVO t = test(datasourceId);
        return new NginxCommandResultVO(t.success(),
                t.success() ? "已从备份恢复到 " + origin + " 并通过语法检查，请执行「重载」生效"
                        : "已从备份恢复，但语法检查未通过，请立即检查: " + firstLine(t.stderr(), t.stdout()),
                r.stdout(), t.stderr());
    }

    // ==================== 内部工具 ====================

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
     * @param ds 数据源
     * @return RUNNING / STOPPED / UNKNOWN
     */
    private String detectRunning(DataSourceVO ds) {
        String script = """
                if command -v pgrep >/dev/null 2>&1; then
                  pgrep -x nginx >/dev/null 2>&1 && echo ON || echo OFF
                  exit 0
                fi
                if ps -e >/dev/null 2>&1; then
                  ps -e 2>/dev/null | grep -qi '[n]ginx' && echo ON || echo OFF
                  exit 0
                fi
                if command -v tasklist >/dev/null 2>&1; then
                  tasklist 2>/dev/null | grep -qi 'nginx' && echo ON || echo OFF
                  exit 0
                fi
                echo UNKNOWN
                """;
        String out = exec(ds, script).stdout().trim();
        if ("ON".equals(out)) {
            return "RUNNING";
        }
        if ("OFF".equals(out)) {
            return "STOPPED";
        }
        return "UNKNOWN";
    }

    /**
     * 取 master 进程 PID。
     *
     * <p>master 由 init 拉起，PPID 为 1，据此与 worker 区分。优先走
     * {@code pgrep + ps -o ppid=}；该组合不可用时退化为解析 {@code ps -e} 的第 2 列
     * （Git Bash / BSD 的列序为 PID PPID PGID ...）。仍取不到时返回 null，
     * 界面显示「未读到 master 进程信息」而不是编一个值。</p>
     *
     * @param ds 数据源
     * @return PID，取不到为 null
     */
    private Long detectMasterPid(DataSourceVO ds) {
        String script = """
                if command -v pgrep >/dev/null 2>&1 && ps -o ppid= -p 1 >/dev/null 2>&1; then
                  pgrep -x nginx 2>/dev/null | while read -r p; do
                    if [ "$(ps -o ppid= -p "$p" 2>/dev/null | tr -d ' ')" = "1" ]; then echo "$p"; break; fi
                  done
                  exit 0
                fi
                if ps -e >/dev/null 2>&1; then
                  ps -e 2>/dev/null | awk 'NR>1 && tolower($0) ~ /nginx/ && $2==1 { print $1; exit }'
                  exit 0
                fi
                """;
        try {
            return parseLong(exec(ds, script).stdout().trim());
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取 master 进程失败: {}", e.getMessage());
            return null;
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
        ExecResult r = exec(ds, "grep -rhoE 'listen[[:space:]]+[^;]+' " + shQuote(root)
                + " --include='*.conf' 2>/dev/null | head -80");
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
        StringBuilder sb = new StringBuilder("nginx");
        if (!paths.prefix().isEmpty()) {
            sb.append(" -p ").append(shQuote(paths.prefix()));
        }
        if (!paths.confPath().isEmpty()) {
            sb.append(" -c ").append(shQuote(paths.confPath()));
        }
        String act = trim(action);
        if (!act.isEmpty()) {
            sb.append(' ').append(act);
        }
        return sb.toString();
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
        long now = System.currentTimeMillis();
        NginxPaths cached = pathCache.get(key);
        if (cached != null && cached.usable() && cached.fresh(now)) {
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
            // 用 grep -A1 之类的写法会取到下一个参数，必须按前缀精确匹配
            ExecResult r = exec(ds, "nginx -V 2>&1 | tr ' ' '\\n' | grep -E '^--[a-z-]+=' || true");
            buildArgs = r.stdout();
        } catch (RuntimeException e) {
            log.debug("[nginx] 读取 nginx -V 失败: {}", e.getMessage());
        }
        String prefix = firstArgValue(buildArgs, "--prefix=");
        if (prefix.isEmpty()) {
            // prefix 为空（Windows 版即如此）时，取可执行文件所在目录
            try {
                ExecResult r = exec(ds, "command -v nginx 2>/dev/null || which nginx 2>/dev/null");
                String bin = firstLine(r.stdout().trim(), "");
                if (!bin.isEmpty()) {
                    ExecResult dir = exec(ds, "cd \"$(dirname " + shQuote(bin) + ")\" && pwd");
                    prefix = dir.stdout().trim();
                }
            } catch (RuntimeException e) {
                log.debug("[nginx] 定位 nginx 可执行文件失败: {}", e.getMessage());
            }
        }
        while (prefix.endsWith("/")) {
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
            ExecResult probe = exec(ds, "mkdir -p " + shQuote(configured) + " 2>/dev/null && echo __WRITABLE__");
            if (probe.stdout().contains("__WRITABLE__")) {
                return configured;
            }
            log.info("[nginx] 备份目录 {} 不可写，退回家目录", configured);
        } catch (RuntimeException e) {
            log.debug("[nginx] 备份目录探测失败: {}", e.getMessage());
        }
        try {
            ExecResult fallback = exec(ds, "d=\"$HOME/.nginx-console-backup\"; mkdir -p \"$d\" && printf '%s' \"$d\"");
            String dir = fallback.stdout().trim();
            if (!dir.isEmpty()) {
                return dir;
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
     * @param resolvedAt 解析时刻（毫秒）
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

        /**
         * 本条解析结果是否仍在有效期内。
         *
         * @return true 表示未超过缓存有效期
         */
        boolean fresh(long now) {
            return now - resolvedAt < PATH_CACHE_TTL_MILLIS;
        }
    }

    /**
     * 取日志目录。
     *
     * @param ds 数据源
     * @return 目录路径
     */
    private String logDir(DataSourceVO ds) {
        return DEFAULT_LOG_DIR;
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
        ExecResult r = exec(ds, "printf '%s' " + shQuote(b64) + " | base64 -d > " + shQuote(path));
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
        return exec(ds, cmd, EXEC_TIMEOUT_SECONDS);
    }

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
        String host = trim(ds.host());
        if (host.isEmpty()) {
            throw new IllegalStateException("Nginx 数据源未配置主机地址");
        }
        int port = ds.port() != null && ds.port() > 0 ? ds.port() : DEFAULT_SSH_PORT;
        String username = trim(ds.username());
        if (username.isEmpty()) {
            throw new IllegalStateException("Nginx 数据源未配置 SSH 用户名");
        }
        String password = ds.password() == null ? "" : ds.password();
        com.chua.ssh.support.client.SshClient ssh = com.chua.ssh.support.client.SshClient.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS)
                .sessionTimeout((int) seconds)
                .execTimeout(seconds)
                .build();
        try {
            ssh.connect();
            com.chua.ssh.support.client.SshClient.ExecResult r = ssh.exec().command(cmd).execute();
            return new ExecResult(r.exitCode(),
                    r.stdout() == null ? "" : r.stdout(),
                    r.stderr() == null ? "" : r.stderr());
        } catch (Exception e) {
            throw new IllegalStateException("SSH 执行失败 " + host + ":" + port + " 命令[" + cmd + "]: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        } finally {
            try {
                ssh.disconnect();
            } catch (Exception ignored) {
                // 关闭失败不影响结果
            }
        }
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
            logPath = DEFAULT_ACCESS_LOG;
        }
        // 尾部行数上限放大到 20000：Top 榜需要足够样本才有意义，
        // 但也不能无上限——高流量站的日志一行上百字节，拉 10 万行就是十几 MB。
        int n = lines == null || lines <= 0 ? 2000 : Math.min(lines, 20_000);
        int top = topN == null || topN <= 0 ? 10 : Math.min(topN, 50);
        int mins = minutes == null ? 0 : Math.max(minutes, 0);
        DataSourceVO ds = requireDatasource(datasourceId);
        String text = exec(ds, "tail -n " + n + " " + shQuote(logPath) + " 2>&1").stdout();
        LocalDateTime since = mins > 0 ? LocalDateTime.now().minusMinutes(mins) : null;
        String name = logPath.substring(logPath.lastIndexOf('/') + 1);
        return NginxAccessLogParser.parse(text, top, since, name);
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
