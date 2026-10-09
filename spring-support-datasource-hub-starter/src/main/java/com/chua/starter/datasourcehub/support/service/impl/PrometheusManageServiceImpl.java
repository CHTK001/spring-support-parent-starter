package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsPrometheusDashboard;
import com.chua.starter.datasourcehub.support.mapper.MsPrometheusDashboardMapper;
import com.chua.starter.datasourcehub.support.middleware.MiddlewareHttp;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.PrometheusApplyResultVO;
import com.chua.starter.datasourcehub.support.model.PrometheusCommandVO;
import com.chua.starter.datasourcehub.support.model.PrometheusConfigVO;
import com.chua.starter.datasourcehub.support.model.PrometheusDashboardVO;
import com.chua.starter.datasourcehub.support.model.PrometheusFileVO;
import com.chua.starter.datasourcehub.support.model.dto.PrometheusDashboardDTO;
import com.chua.starter.datasourcehub.support.model.dto.PrometheusSaveConfigDTO;
import com.chua.starter.datasourcehub.support.nginx.spi.NginxCommandProvider;
import com.chua.starter.datasourcehub.support.nginx.spi.NginxExecutor;
import com.chua.starter.datasourcehub.support.nginx.spi.NginxRuntimeRouter;
import com.chua.starter.datasourcehub.support.nginx.spi.impl.LocalNginxExecutor;
import com.chua.starter.datasourcehub.support.nginx.spi.impl.SshNginxExecutor;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.service.PrometheusManageService;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/**
 * Prometheus 控制台服务实现。
 *
 * <p><strong>字段约定</strong>（与 {@link DatasourceHubConstants#DS_TYPE_PROMETHEUS} 的常量
 * 注释一致）：{@code port} 是 Prometheus HTTP API 端口（默认 9090），查询与探测都用它；
 * 配置文件管理固定走 SSH 22 端口；{@code database} 是配置根目录（默认 {@code /etc/prometheus}），
 * 填完整 http(s) URL 时改当 HTTP 基地址用。</p>
 *
 * <p><strong>为什么配置线不经 {@link NginxRuntimeRouter#resolve(DataSourceVO)}</strong>：
 * 那条路把数据源端口当 SSH 端口用（nginx 的端口恰好就是 SSH 端口），而 Prometheus
 * 的端口语义是 HTTP API 端口，直接复用会把 SSH 连到 9090 上。这里直接实例化
 * 执行通道（SSH / 本机），命令文本仍取自 SPI 的 POSIX 实现——新增平台命令文本
 * 时不会漏掉 Prometheus 控制台。</p>
 *
 * <p><strong>配置线只支持 Linux 目标</strong>：prometheus.yml 的读写、promtool 校验、
 * SIGHUP 重载都是 POSIX 语义；Windows 上仅查询可用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrometheusManageServiceImpl implements PrometheusManageService {

    /**
     * 数据源类型标识
     */
    private static final String DS_TYPE = DatasourceHubConstants.DS_TYPE_PROMETHEUS;

    /**
     * 默认 HTTP API 端口
     */
    private static final int DEFAULT_HTTP_PORT = DatasourceHubConstants.PROMETHEUS_DEFAULT_HTTP_PORT;

    /**
     * 默认配置根目录
     */
    private static final String DEFAULT_CONFIG_ROOT = DatasourceHubConstants.PROMETHEUS_DEFAULT_CONFIG_ROOT;

    /**
     * 主配置文件相对名
     */
    private static final String MAIN_CONFIG_NAME = "prometheus.yml";

    /**
     * 备份目录（配置根下）
     */
    private static final String BACKUP_DIR_NAME = ".console-backups";

    /**
     * SSH 固定端口：Prometheus 控制台的配置管理不提供自定义 SSH 端口
     */
    private static final int SSH_PORT = DatasourceHubConstants.SSH_DEFAULT_PORT;

    /**
     * 默认执行超时（秒）
     */
    private static final long EXEC_TIMEOUT_SECONDS = 20L;

    /**
     * promtool 校验超时（秒）
     */
    private static final long VALIDATE_TIMEOUT_SECONDS = 30L;

    /**
     * JSON 解析器：只解析 Prometheus 响应，不做对象映射
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 嵌入令牌随机源
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 更新时间文本格式
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 备份文件名时间戳格式（带毫秒，防同一秒内两次保存互相覆盖）
     */
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 标签名合法字符集：与 Prometheus 的 label 命名一致
     */
    private static final String LABEL_NAME_PATTERN = "[a-zA-Z_][a-zA-Z0-9_]*";

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * Nginx 运行时路由器：这里只借它的「平台 → 命令文本提供者」映射
     */
    private final NginxRuntimeRouter nginxRuntimeRouter;

    /**
     * 自定义看板 Mapper
     */
    private final MsPrometheusDashboardMapper dashboardMapper;

    // ==================== 总览 ====================

    @Override
    public Map<String, Object> overview(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("datasourceId", ds.id());
        out.put("datasourceName", ds.name());
        out.put("prometheusUrl", httpBase(ds));
        out.put("configRoot", configRoot(ds));
        out.put("configPath", mainConfigPath(ds));
        out.put("backupDir", backupDir(ds));

        boolean reachable = false;
        try {
            JsonNode build = api(ds, "/api/v1/status/buildinfo", null).path("data");
            out.put("version", build.path("version").asText(""));
            out.put("revision", build.path("revision").asText(""));
            reachable = true;
        } catch (RuntimeException e) {
            out.put("version", "");
            out.put("revision", "");
            out.put("error", e.getMessage());
        }
        out.put("reachable", reachable);

        int up = 0;
        int down = 0;
        int unknown = 0;
        List<Map<String, Object>> jobs = new ArrayList<>();
        int activeAlerts = 0;
        int ruleGroups = 0;
        int ruleCount = 0;
        if (reachable) {
            try {
                JsonNode data = api(ds, "/api/v1/targets", null).path("data");
                Map<String, int[]> byJob = new LinkedHashMap<>();
                for (JsonNode target : data.path("activeTargets")) {
                    String health = target.path("health").asText("");
                    if ("up".equalsIgnoreCase(health)) {
                        up++;
                    } else if ("down".equalsIgnoreCase(health)) {
                        down++;
                    } else {
                        unknown++;
                    }
                    String job = target.path("labels").path("job").asText("(未命名)");
                    int[] counter = byJob.computeIfAbsent(job, k -> new int[3]);
                    if ("up".equalsIgnoreCase(health)) {
                        counter[0]++;
                    } else if ("down".equalsIgnoreCase(health)) {
                        counter[1]++;
                    } else {
                        counter[2]++;
                    }
                }
                for (Map.Entry<String, int[]> entry : byJob.entrySet()) {
                    Map<String, Object> job = new LinkedHashMap<>();
                    job.put("job", entry.getKey());
                    job.put("up", entry.getValue()[0]);
                    job.put("down", entry.getValue()[1]);
                    job.put("unknown", entry.getValue()[2]);
                    jobs.add(job);
                }
            } catch (RuntimeException e) {
                log.warn("[prometheus] 目标统计失败: {}", e.getMessage());
            }
            try {
                activeAlerts = api(ds, "/api/v1/alerts", null).path("data").path("alerts").size();
            } catch (RuntimeException e) {
                log.warn("[prometheus] 告警统计失败: {}", e.getMessage());
            }
            try {
                JsonNode groups = api(ds, "/api/v1/rules", null).path("data").path("groups");
                ruleGroups = groups.size();
                for (JsonNode group : groups) {
                    ruleCount += group.path("rules").size();
                }
            } catch (RuntimeException e) {
                log.warn("[prometheus] 规则统计失败: {}", e.getMessage());
            }
        }
        out.put("targetTotal", up + down + unknown);
        out.put("targetUp", up);
        out.put("targetDown", down);
        out.put("targetUnknown", unknown);
        out.put("activeAlerts", activeAlerts);
        out.put("ruleGroups", ruleGroups);
        out.put("ruleCount", ruleCount);
        out.put("jobs", jobs);

        try {
            NginxExecutor.ExecResult r = exec(ds, commands().fileExists(mainConfigPath(ds)));
            out.put("configExists", r.exitCode() == 0);
        } catch (RuntimeException e) {
            out.put("configExists", null);
            out.put("configCheckError", e.getMessage());
        }
        return out;
    }

    // ==================== 查询 ====================

    @Override
    public JsonNode query(Long datasourceId, String query, String time) {
        DataSourceVO ds = requireDatasource(datasourceId);
        requireQuery(query);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);
        if (!isBlank(time)) {
            params.put("time", time.trim());
        }
        return api(ds, "/api/v1/query", params);
    }

    @Override
    public JsonNode queryRange(Long datasourceId, String query, String start, String end, String step) {
        DataSourceVO ds = requireDatasource(datasourceId);
        requireQuery(query);
        requireText(start, "起始时刻不能为空");
        requireText(end, "结束时刻不能为空");
        requireText(step, "步长不能为空");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("start", start.trim());
        params.put("end", end.trim());
        params.put("step", step.trim());
        return api(ds, "/api/v1/query_range", params);
    }

    @Override
    public JsonNode targets(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        return api(ds, "/api/v1/targets", null).path("data");
    }

    @Override
    public JsonNode rules(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        return api(ds, "/api/v1/rules", null).path("data");
    }

    @Override
    public JsonNode alerts(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        return api(ds, "/api/v1/alerts", null).path("data");
    }

    @Override
    public List<String> labels(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        return stringList(api(ds, "/api/v1/labels", null).path("data"));
    }

    @Override
    public List<String> labelValues(Long datasourceId, String name) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String label = trim(name);
        if (!label.matches(LABEL_NAME_PATTERN)) {
            throw new IllegalArgumentException("标签名不合法: " + name);
        }
        return stringList(api(ds, "/api/v1/label/" + label + "/values", null).path("data"));
    }

    // ==================== 配置管理 ====================

    @Override
    public PrometheusConfigVO readConfig(Long datasourceId, String path) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String target = isBlank(path) ? mainConfigPath(ds) : resolveUnderConfigRoot(ds, path);
        NginxCommandProvider cmds = commands();
        if (exec(ds, cmds.fileExists(target)).exitCode() != 0) {
            throw new IllegalStateException("配置文件不存在: " + target);
        }
        NginxExecutor.ExecResult r = exec(ds, cmds.readFile(target));
        if (r.exitCode() != 0) {
            throw new IllegalStateException("读取配置失败: " + firstNonBlank(r.stderr(), r.stdout()));
        }
        return new PrometheusConfigVO(target, r.stdout() == null ? "" : r.stdout(), true);
    }

    @Override
    public List<PrometheusFileVO> listFiles(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String main = mainConfigPath(ds);
        List<PrometheusFileVO> files = listYml(ds, configRoot(ds), main);
        // 主配置固定排最前，其余按路径排
        files.sort(Comparator.comparing(PrometheusFileVO::main).reversed()
                .thenComparing(PrometheusFileVO::path));
        return files;
    }

    @Override
    public PrometheusApplyResultVO saveConfig(PrometheusSaveConfigDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        DataSourceVO ds = requireDatasource(request.datasourceId());
        String path = isBlank(request.path()) ? mainConfigPath(ds) : resolveUnderConfigRoot(ds, request.path());
        if (request.content() == null) {
            throw new IllegalArgumentException("文件内容不能为空（要清空请传空字符串）");
        }
        boolean backup = request.backup() == null || request.backup();
        boolean validate = request.validate() == null || request.validate();
        boolean reload = request.reload() == null || request.reload();

        List<String> steps = new ArrayList<>();
        NginxCommandProvider cmds = commands();
        steps.add("目标文件: " + path);

        // 1) 先写临时文件：校验要在覆盖原文件之前完成，否则一次手误就把好配置弄坏了。
        // 文件名带时间戳 + 随机段：固定名既会在并发保存时互相覆盖，也能被提前布下的
        // 同名符号链接劫持（写到链接指向的任意文件）。
        String tmp = makeTempFile(path);
        writeFile(ds, cmds, tmp, request.content());
        steps.add("已写入临时文件 " + tmp);

        // 2) promtool 校验（可选）：不可用时跳过只提示，不算失败
        String validateOutput = "";
        Boolean validated = null;
        if (validate) {
            String promtool = detectPromtool(ds, cmds);
            if (promtool == null) {
                steps.add("未找到 promtool，跳过语法校验");
            } else {
                NginxExecutor.ExecResult r = exec(ds, promtool + " check config " + shQuote(tmp),
                        VALIDATE_TIMEOUT_SECONDS);
                validateOutput = merge(r.stdout(), r.stderr());
                if (r.exitCode() != 0) {
                    deleteFile(ds, tmp);
                    steps.add("promtool 校验未通过，已放弃写入");
                    return new PrometheusApplyResultVO(false, "配置校验未通过", null, false,
                            false, validateOutput, steps);
                }
                validated = true;
                steps.add("promtool 校验通过");
            }
        }

        // 3) 备份
        String backupPath = null;
        if (backup) {
            if (exec(ds, cmds.fileExists(path)).exitCode() == 0) {
                String dir = backupDir(ds);
                exec(ds, cmds.makeDirs(dir));
                String candidate = dir + "/" + fileNameOf(path) + "." + BACKUP_STAMP.format(LocalDateTime.now()) + ".bak";
                NginxExecutor.ExecResult cp = exec(ds, cmds.copyFile(path, candidate));
                if (cp.exitCode() != 0) {
                    deleteFile(ds, tmp);
                    throw new IllegalStateException("备份原配置失败: " + merge(cp.stdout(), cp.stderr()));
                }
                backupPath = candidate;
                steps.add("已备份原配置到 " + backupPath);
            } else {
                steps.add("原文件不存在，跳过备份（新建配置）");
            }
        }

        // 4) 覆盖写入
        NginxExecutor.ExecResult cp = exec(ds, cmds.copyFile(tmp, path));
        if (cp.exitCode() != 0) {
            deleteFile(ds, tmp);
            throw new IllegalStateException("写入配置失败: " + merge(cp.stdout(), cp.stderr()));
        }
        deleteFile(ds, tmp);
        steps.add("配置已写入 " + path);

        // 5) 重载
        boolean reloaded = false;
        String reloadOutput = "";
        if (reload) {
            PrometheusCommandVO result = doReload(ds);
            reloaded = result.success();
            reloadOutput = result.output();
            steps.add(result.message());
        } else {
            steps.add("未开启重载，改动将在下次重载或重启后生效");
        }

        String message = "配置已保存";
        if (reload) {
            message += reloaded ? "并已重载" : "，但重载未成功（见输出）";
        }
        return new PrometheusApplyResultVO(true, message, backupPath, validated, reloaded,
                firstNonBlank(reloadOutput, validateOutput), steps);
    }

    @Override
    public PrometheusCommandVO reload(Long datasourceId) {
        return doReload(requireDatasource(datasourceId));
    }

    @Override
    public List<PrometheusFileVO> listBackups(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String dir = backupDir(ds);
        try {
            NginxExecutor.ExecResult r = exec(ds, commands().listFilesWithMeta(dir, "*.bak", false));
            if (r.exitCode() != 0) {
                return List.of();
            }
            List<PrometheusFileVO> out = parseFileRows(r.stdout(), null);
            // 文件名带时间戳，字典序倒排≈新在前
            out.sort(Comparator.comparing(PrometheusFileVO::path).reversed());
            return out;
        } catch (RuntimeException e) {
            log.warn("[prometheus] 列出备份失败 dir={} err={}", dir, e.getMessage());
            return List.of();
        }
    }

    @Override
    public PrometheusApplyResultVO restoreBackup(Long datasourceId, String backupPath) {
        if (isBlank(backupPath)) {
            throw new IllegalArgumentException("备份文件路径不能为空");
        }
        DataSourceVO ds = requireDatasource(datasourceId);
        // 只认配置根下备份目录里的文件：接口参数不能变成「读任意文件再写回配置」的通道
        String safePath = resolveUnderConfigRoot(ds, backupPath);
        if (!safePath.startsWith(backupDir(ds) + "/")) {
            throw new IllegalArgumentException("备份文件必须位于 " + backupDir(ds) + " 下: " + safePath);
        }
        PrometheusConfigVO backup = readConfig(datasourceId, safePath);
        // 恢复 = 用备份内容走一次完整保存（校验 + 备份当前 + 覆盖 + 重载）：
        // 备份当前很重要——恢复的往往是「刚被改坏的」配置，恢复错了还得再回得来
        return saveConfig(new PrometheusSaveConfigDTO(datasourceId, null, backup.content(), true, true, true));
    }

    // ==================== 自定义看板 ====================

    @Override
    public PrometheusDashboardVO getDashboard(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        MsPrometheusDashboard row = selectDashboard(ds.id());
        return toDashboardVO(ds.id(), row);
    }

    @Override
    public PrometheusDashboardVO saveDashboard(PrometheusDashboardDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        DataSourceVO ds = requireDatasource(request.datasourceId());
        String config = request.config() == null ? "" : request.config();
        if (config.trim().isEmpty()) {
            throw new IllegalArgumentException("看板配置不能为空");
        }
        MsPrometheusDashboard row = selectDashboard(ds.id());
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new MsPrometheusDashboard();
            row.setDatasourceId(ds.id());
            row.setDashboardName(isBlank(request.name()) ? "Prometheus 看板" : request.name().trim());
            row.setDashboardConfig(config);
            row.setEmbedToken(newToken());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            dashboardMapper.insert(row);
        } else {
            if (!isBlank(request.name())) {
                row.setDashboardName(request.name().trim());
            }
            row.setDashboardConfig(config);
            if (isBlank(row.getEmbedToken())) {
                row.setEmbedToken(newToken());
            }
            row.setUpdateTime(now);
            dashboardMapper.updateById(row);
        }
        return toDashboardVO(ds.id(), row);
    }

    @Override
    public PrometheusDashboardVO regenerateToken(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        MsPrometheusDashboard row = selectDashboard(ds.id());
        if (row == null) {
            row = new MsPrometheusDashboard();
            row.setDatasourceId(ds.id());
            row.setDashboardName("Prometheus 看板");
            row.setDashboardConfig("{}");
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            row.setEmbedToken(newToken());
            dashboardMapper.insert(row);
        } else {
            row.setEmbedToken(newToken());
            row.setUpdateTime(LocalDateTime.now());
            dashboardMapper.updateById(row);
        }
        return toDashboardVO(ds.id(), row);
    }

    @Override
    public PrometheusDashboardVO embedDashboard(String token) {
        MsPrometheusDashboard row = requireDashboardByToken(token);
        return toDashboardVO(row.getDatasourceId(), row);
    }

    @Override
    public JsonNode embedQuery(String token, String query, String time) {
        MsPrometheusDashboard row = requireDashboardByToken(token);
        return query(row.getDatasourceId(), query, time);
    }

    @Override
    public JsonNode embedQueryRange(String token, String query, String start, String end, String step) {
        MsPrometheusDashboard row = requireDashboardByToken(token);
        return queryRange(row.getDatasourceId(), query, start, end, step);
    }

    // ==================== 内部：数据源与 HTTP ====================

    /**
     * 解析数据源：按 ID 取并校验类型，缺省时挑第一个 prometheus 数据源。
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
                throw new IllegalStateException("数据源 #" + datasourceId + " 是 " + ds.type()
                        + " 类型，无法用于 Prometheus 管理");
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
        throw new IllegalStateException("尚未登记 prometheus 数据源，请先在「数据源管理」新增");
    }

    /**
     * HTTP 基地址：database 填完整 URL 时原样使用；否则 host + port（默认 9090）。
     *
     * @param ds 数据源
     * @return 基地址，形如 {@code http://127.0.0.1:9090}
     */
    private static String httpBase(DataSourceVO ds) {
        String database = trim(ds.database());
        if (database.startsWith("http://") || database.startsWith("https://")) {
            return stripTrailingSlash(database);
        }
        String host = trim(ds.host());
        if (host.isEmpty() || "local".equalsIgnoreCase(host)) {
            host = "127.0.0.1";
        }
        int port = ds.port() == null || ds.port() <= 0 ? DEFAULT_HTTP_PORT : ds.port();
        return "http://" + host + ":" + port;
    }

    /**
     * 调 Prometheus HTTP API 并校验 status。
     *
     * @param ds    数据源
     * @param path  API 路径，如 {@code /api/v1/query}
     * @param query 查询参数，可为空
     * @return 响应根节点
     */
    private JsonNode api(DataSourceVO ds, String path, Map<String, String> query) {
        try {
            String text = MiddlewareHttp.get(httpBase(ds) + path,
                    query == null ? Map.of() : query, Map.of());
            JsonNode root = MAPPER.readTree(text);
            if (root == null) {
                throw new IllegalStateException("Prometheus 响应为空: " + path);
            }
            if (!"success".equalsIgnoreCase(root.path("status").asText(""))) {
                String type = root.path("errorType").asText("");
                String error = root.path("error").asText("");
                throw new IllegalStateException("Prometheus 查询失败"
                        + (type.isEmpty() ? "" : "[" + type + "] ") + error);
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("访问 Prometheus " + httpBase(ds) + " 失败: " + e.getMessage());
        }
    }

    // ==================== 内部：SSH 执行 ====================

    /**
     * 取 POSIX 命令文本提供者。
     *
     * @return 命令文本提供者
     */
    private NginxCommandProvider commands() {
        return nginxRuntimeRouter.commandsOf(Platforms.LINUX);
    }

    /**
     * 在目标主机执行一条命令。
     *
     * @param ds      数据源
     * @param command 命令
     * @return 执行结果
     */
    private NginxExecutor.ExecResult exec(DataSourceVO ds, String command) {
        return exec(ds, command, EXEC_TIMEOUT_SECONDS);
    }

    /**
     * 在目标主机执行一条命令（指定超时）。
     *
     * <p>SSH 端口固定 22：数据源端口字段留给 Prometheus HTTP API，语义见类注释。</p>
     *
     * @param ds      数据源
     * @param command 命令
     * @param seconds 超时秒数
     * @return 执行结果
     */
    private NginxExecutor.ExecResult exec(DataSourceVO ds, String command, long seconds) {
        String host = trim(ds.host());
        boolean local = NginxExecutor.Target.isLocalMarker(host);
        NginxExecutor.Target target = new NginxExecutor.Target(
                host, local ? 0 : SSH_PORT, trim(ds.username()), ds.password(), local);
        NginxExecutor executor = local ? new LocalNginxExecutor() : new SshNginxExecutor();
        return executor.exec(target, command, seconds);
    }

    /**
     * 探测 promtool 可执行文件。
     *
     * @param ds   数据源
     * @param cmds 命令提供者
     * @return 已引号包裹的 promtool 路径；不可用返回 null
     */
    private String detectPromtool(DataSourceVO ds, NginxCommandProvider cmds) {
        try {
            NginxExecutor.ExecResult r = exec(ds, cmds.which("promtool"));
            if (r.exitCode() != 0) {
                return null;
            }
            String path = firstLine(r.stdout());
            return path.isEmpty() ? null : shQuote(path);
        } catch (RuntimeException e) {
            log.warn("[prometheus] promtool 探测失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写文件（base64，绕开引号与换行）。
     *
     * @param ds      数据源
     * @param cmds    命令提供者
     * @param path    目标路径
     * @param content 内容
     */
    private void writeFile(DataSourceVO ds, NginxCommandProvider cmds, String path, String content) {
        String b64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        NginxExecutor.ExecResult r = exec(ds, cmds.writeFile(path, b64));
        if (r.exitCode() != 0) {
            throw new IllegalStateException("写入文件失败 " + path + ": " + merge(r.stdout(), r.stderr()));
        }
    }

    /**
     * 删除文件（失败只记日志：临时文件残留不影响结果）。
     *
     * @param ds   数据源
     * @param path 路径
     */
    private void deleteFile(DataSourceVO ds, String path) {
        try {
            exec(ds, "rm -f " + shQuote(path));
        } catch (RuntimeException e) {
            log.warn("[prometheus] 清理临时文件失败 {}: {}", path, e.getMessage());
        }
    }

    /**
     * 触发重载：先 HTTP 生命周期接口，失败回退 SIGHUP。
     *
     * <p>HTTP 接口要求 Prometheus 启动参数带 {@code --web.enable-lifecycle}；
     * 没带时 405，回退 SIGHUP（SSH 用户需与 Prometheus 进程属主一致）。</p>
     *
     * <p>回退命中也做两级收敛：先按「命令行里带本数据源主配置文件路径」精确挑进程
     * （同一台机器可能跑多个 Prometheus 实例，无差别 HUP 会把无关实例也打一遍）；
     * 进程命令行里没写 {@code --config.file}（用默认路径启动）时，才退回按进程名匹配。</p>
     *
     * @param ds 数据源
     * @return 执行结果
     */
    private PrometheusCommandVO doReload(DataSourceVO ds) {
        String base = httpBase(ds);
        try {
            MiddlewareHttp.postForm(base + "/-/reload", Map.of(), Map.of());
            return new PrometheusCommandVO(true, "已通过 HTTP 触发重载", "");
        } catch (IOException httpError) {
            String httpMessage = httpError.getMessage() == null ? "" : httpError.getMessage();
            try {
                String configPath = mainConfigPath(ds);
                NginxExecutor.ExecResult r = exec(ds, "pkill -HUP -f " + shQuote(configPath));
                if (r.exitCode() == 0) {
                    String output = merge(r.stdout(), r.stderr());
                    return new PrometheusCommandVO(true,
                            "HTTP 重载不可用，已按配置文件定位进程并 SIGHUP 触发重载", output);
                }
                // 命令行里没有 --config.file（默认路径启动）时按进程名兜底
                NginxExecutor.ExecResult fallback = exec(ds, "pkill -HUP -x prometheus");
                String output = merge(merge(r.stdout(), r.stderr()), merge(fallback.stdout(), fallback.stderr()));
                if (fallback.exitCode() == 0) {
                    return new PrometheusCommandVO(true,
                            "HTTP 重载不可用，已通过 SIGHUP（按进程名）触发重载", output);
                }
                return new PrometheusCommandVO(false, "重载失败（HTTP 与 SIGHUP 均不可用）",
                        "HTTP: " + httpMessage + "\nSIGHUP: " + firstNonBlank(output,
                                "exit=" + r.exitCode() + "/" + fallback.exitCode()));
            } catch (RuntimeException execError) {
                return new PrometheusCommandVO(false, "重载失败: " + execError.getMessage(),
                        "HTTP: " + httpMessage);
            }
        }
    }

    // ==================== 内部：配置路径与解析 ====================

    /**
     * 配置根目录：database 字段；填 URL 或留空时回退默认值。
     *
     * @param ds 数据源
     * @return 配置根目录
     */
    private static String configRoot(DataSourceVO ds) {
        String database = trim(ds.database());
        if (database.isEmpty() || database.startsWith("http://") || database.startsWith("https://")) {
            return DEFAULT_CONFIG_ROOT;
        }
        return stripTrailingSlash(database);
    }

    /**
     * 主配置文件路径。
     *
     * @param ds 数据源
     * @return 路径
     */
    private static String mainConfigPath(DataSourceVO ds) {
        return configRoot(ds) + "/" + MAIN_CONFIG_NAME;
    }

    /**
     * 备份目录。
     *
     * @param ds 数据源
     * @return 目录
     */
    private static String backupDir(DataSourceVO ds) {
        return configRoot(ds) + "/" + BACKUP_DIR_NAME;
    }

    /**
     * 把请求里的路径收敛到配置根目录之下。
     *
     * <p>接口允许传自定义路径（rules 文件在子目录里），但绝不能变成任意文件读写通道：
     * SSH 用户能碰到的文件远不止配置目录。两道闸：拒绝 {@code ..} 段（防
     * {@code /etc/prometheus/../shadow} 这类字面量前缀合法的逃逸），再要求路径以
     * 配置根 + {@code /} 开头。</p>
     *
     * @param ds   数据源
     * @param path 请求路径
     * @return 规范化后的路径
     */
    private static String resolveUnderConfigRoot(DataSourceVO ds, String path) {
        String root = configRoot(ds);
        String value = trim(path);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        if (value.contains("..")) {
            throw new IllegalArgumentException("路径不允许包含 .. : " + value);
        }
        if (!value.startsWith(root + "/")) {
            throw new IllegalArgumentException("路径必须位于配置目录 " + root + " 下: " + value);
        }
        return value;
    }

    /**
     * 生成同目录下的唯一临时文件名。
     *
     * <p>固定名（如 {@code .prometheus.yml.console-tmp}）有两个问题：并发保存互相
     * 覆盖；若目标机上被人预先放了同名符号链接，会把内容写到链接指向的文件里。
     * 时间戳 + 随机段让这两个问题都不成立。</p>
     *
     * @param path 目标文件路径
     * @return 临时文件路径
     */
    private static String makeTempFile(String path) {
        String suffix = Long.toHexString(System.currentTimeMillis())
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        return parentOf(path) + "/." + fileNameOf(path) + ".console-tmp." + suffix;
    }

    /**
     * 列出配置根下的 yml（递归含 rules 子目录）。
     *
     * @param ds   数据源
     * @param root 根目录
     * @param main 主配置文件路径
     * @return 文件列表
     */
    private List<PrometheusFileVO> listYml(DataSourceVO ds, String root, String main) {
        try {
            NginxExecutor.ExecResult r = exec(ds, commands().listFilesWithMeta(root, "*.yml", true));
            if (r.exitCode() != 0) {
                return new ArrayList<>();
            }
            return parseFileRows(r.stdout(), main);
        } catch (RuntimeException e) {
            log.warn("[prometheus] 列出配置文件失败 root={} err={}", root, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析「路径 \t 字节数 \t 修改时间」行。
     *
     * @param stdout 命令输出
     * @param main   主配置文件路径，null 表示不标注
     * @return 文件条目
     */
    private static List<PrometheusFileVO> parseFileRows(String stdout, String main) {
        List<PrometheusFileVO> out = new ArrayList<>();
        if (stdout == null || stdout.isEmpty()) {
            return out;
        }
        for (String line : stdout.split("\n")) {
            String row = line.trim();
            if (row.isEmpty()) {
                continue;
            }
            String[] parts = row.split("\t");
            String path = parts[0].trim();
            if (path.isEmpty()) {
                continue;
            }
            Long size = parts.length > 1 ? parseLong(parts[1]) : null;
            String mtime = parts.length > 2 ? parts[2].trim() : "";
            out.add(new PrometheusFileVO(path, size, mtime, path.equals(main)));
        }
        return out;
    }

    // ==================== 内部：看板存取 ====================

    /**
     * 查数据源对应的看板行。
     *
     * @param datasourceId 数据源 ID
     * @return 行，未保存过为 null
     */
    private MsPrometheusDashboard selectDashboard(Long datasourceId) {
        return dashboardMapper.selectOne(new LambdaQueryWrapper<MsPrometheusDashboard>()
                .eq(MsPrometheusDashboard::getDatasourceId, datasourceId));
    }

    /**
     * 按令牌查看板行。
     *
     * @param token 令牌
     * @return 行
     * @throws IllegalStateException 令牌无效
     */
    private MsPrometheusDashboard requireDashboardByToken(String token) {
        String value = trim(token);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("嵌入令牌不能为空");
        }
        MsPrometheusDashboard row = dashboardMapper.selectOne(new LambdaQueryWrapper<MsPrometheusDashboard>()
                .eq(MsPrometheusDashboard::getEmbedToken, value));
        if (row == null) {
            throw new IllegalStateException("嵌入链接无效或已失效");
        }
        return row;
    }

    /**
     * 转看板视图。
     *
     * @param datasourceId 数据源 ID
     * @param row          行，可为 null
     * @return 视图
     */
    private static PrometheusDashboardVO toDashboardVO(Long datasourceId, MsPrometheusDashboard row) {
        if (row == null) {
            return new PrometheusDashboardVO(datasourceId, "", "", "", null);
        }
        return new PrometheusDashboardVO(
                datasourceId,
                nullToEmpty(row.getDashboardName()),
                nullToEmpty(row.getDashboardConfig()),
                nullToEmpty(row.getEmbedToken()),
                row.getUpdateTime() == null ? null : TIME_FORMAT.format(row.getUpdateTime()));
    }

    /**
     * 生成 32 位十六进制随机令牌。
     *
     * @return 令牌
     */
    private static String newToken() {
        byte[] buffer = new byte[16];
        RANDOM.nextBytes(buffer);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : buffer) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // ==================== 内部：小工具 ====================

    /**
     * 取字符串列表。
     *
     * @param array JSON 数组
     * @return 字符串列表
     */
    private static List<String> stringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                out.add(node.asText());
            }
        }
        return out;
    }

    /**
     * 校验查询语句。
     *
     * @param query PromQL
     */
    private static void requireQuery(String query) {
        requireText(query, "查询语句不能为空");
    }

    /**
     * 校验文本非空。
     *
     * @param value   文本
     * @param message 报错文案
     */
    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 取第一条非空文本。
     *
     * @param first  首选
     * @param second 次选
     * @return 非空文本，可能为空串
     */
    private static String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    /**
     * 取第一行非空文本。
     *
     * @param text 文本
     * @return 第一行
     */
    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        for (String line : text.split("\n")) {
            String v = line.trim();
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    /**
     * 合并输出。
     *
     * @param stdout 标准输出
     * @param stderr 标准错误
     * @return 合并文本
     */
    private static String merge(String stdout, String stderr) {
        String out = trim(stdout);
        String err = trim(stderr);
        if (out.isEmpty()) {
            return err;
        }
        return err.isEmpty() ? out : out + "\n" + err;
    }

    /**
     * Shell 单引号包裹。
     *
     * @param value 原始值
     * @return 字面量
     */
    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 取父目录（字符串截取：配置根都是规范路径，不需要 pwd 规范化）。
     *
     * @param path 路径
     * @return 父目录；无斜杠时返回 {@code /}
     */
    private static String parentOf(String path) {
        int index = path.lastIndexOf('/');
        return index > 0 ? path.substring(0, index) : "/";
    }

    /**
     * 取文件名。
     *
     * @param path 路径
     * @return 文件名
     */
    private static String fileNameOf(String path) {
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    /**
     * 去尾部斜杠。
     *
     * @param value 文本
     * @return 去尾斜杠文本
     */
    private static String stripTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    /**
     * 宽松取长整数。
     *
     * @param value 文本
     * @return 长整数，取不到为 null
     */
    private static Long parseLong(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 空转空串。
     *
     * @param value 文本
     * @return 非 null 文本
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 去空白。
     *
     * @param value 文本
     * @return 去空白文本，null 转空串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 是否空白。
     *
     * @param value 文本
     * @return 空白返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
