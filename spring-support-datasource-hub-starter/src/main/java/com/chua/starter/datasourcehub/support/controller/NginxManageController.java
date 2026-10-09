package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.NginxApplyResultVO;
import com.chua.starter.datasourcehub.support.model.NginxBackupVO;
import com.chua.starter.datasourcehub.support.model.NginxBackupDiffVO;
import com.chua.starter.datasourcehub.support.model.NginxCommandResultVO;
import com.chua.starter.datasourcehub.support.model.NginxConfigFileVO;
import com.chua.starter.datasourcehub.support.model.NginxConfigParseVO;
import com.chua.starter.datasourcehub.support.model.NginxDashboardVO;
import com.chua.starter.datasourcehub.support.model.NginxDirectiveMetaVO;
import com.chua.starter.datasourcehub.support.model.NginxLogChunkVO;
import com.chua.starter.datasourcehub.support.model.NginxLogFileVO;
import com.chua.starter.datasourcehub.support.model.NginxOverviewVO;
import com.chua.starter.datasourcehub.support.model.NginxSiteVO;
import com.chua.starter.datasourcehub.support.model.NginxAccessTopVO;
import com.chua.starter.datasourcehub.support.model.NginxStatusVO;
import com.chua.starter.datasourcehub.support.model.NginxTrendVO;
import com.chua.starter.datasourcehub.support.model.NginxTemplateVO;
import com.chua.starter.datasourcehub.support.model.NginxUrlTrendVO;
import com.chua.starter.datasourcehub.support.model.NginxVersionVO;
import com.chua.starter.datasourcehub.support.model.dto.NginxApplyDTO;
import com.chua.starter.datasourcehub.support.model.dto.NginxDashboardDTO;
import com.chua.starter.datasourcehub.support.model.dto.NginxSaveConfigDTO;
import com.chua.starter.datasourcehub.support.nginx.NginxMetricStreamRegistry;
import com.chua.starter.datasourcehub.support.service.NginxManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Nginx 管理 REST 接口。
 *
 * <p>全部经 SSH 在目标主机执行：总览、配置文件浏览 / 解析 / 编辑 / 保存、语法检查、
 * 重载、重启、停止、启动、版本信息、stub_status、日志查看与备份恢复。
 * datasourceId 可缺省，服务端退化为第一个 nginx 数据源。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "Nginx 管理")
@RestController
@RequestMapping("/ms/monitor/nginx")
@RequiredArgsConstructor
public class NginxManageController {

    /**
     * Nginx 管理服务
     */
    private final NginxManageService nginxManageService;

    /**
     * SSE 订阅者注册表
     */
    private final NginxMetricStreamRegistry streamRegistry;

    // ==================== 总览 ====================

    /**
     * 读取总览。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 总览
     */
    @Operation(summary = "Nginx 总览")
    @GetMapping("/overview")
    public ReturnResult<NginxOverviewVO> overview(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nginx 总览", () -> nginxManageService.overview(datasourceId));
    }

    // ==================== 配置文件 ====================

    /**
     * 列出配置文件。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 配置文件列表
     */
    @Operation(summary = "列出 Nginx 配置文件")
    @GetMapping("/config/files")
    public ReturnResult<List<NginxConfigFileVO>> files(@RequestParam(required = false) Long datasourceId) {
        return call("列出 Nginx 配置文件", () -> nginxManageService.listFiles(datasourceId));
    }

    /**
     * 读取配置文件内容。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path        配置文件路径
     * @return 文件内容
     */
    @Operation(summary = "读取 Nginx 配置文件")
    @GetMapping("/config")
    public ReturnResult<String> readConfig(@RequestParam(required = false) Long datasourceId,
                                           @RequestParam String path) {
        return call("读取 Nginx 配置", () -> nginxManageService.readConfig(datasourceId, path));
    }

    /**
     * 解析配置文件为结构化语法树（UI 模式数据源）。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path        配置文件路径
     * @return 解析结果
     */
    @Operation(summary = "解析 Nginx 配置为语法树")
    @GetMapping("/config/tree")
    public ReturnResult<NginxConfigParseVO> configTree(@RequestParam(required = false) Long datasourceId,
                                                       @RequestParam String path) {
        return call("解析 Nginx 配置", () -> nginxManageService.parseConfig(datasourceId, path));
    }

    /**
     * 保存配置文件（文本整篇覆盖）。
     *
     * @param request 保存请求
     * @return 执行结果
     */
    @Operation(summary = "保存 Nginx 配置文件")
    @PutMapping("/config")
    public ReturnResult<NginxApplyResultVO> saveConfig(@RequestBody NginxSaveConfigDTO request) {
        return call("保存 Nginx 配置", () -> nginxManageService.saveConfig(request));
    }

    /**
     * 保存配置文件：文件模式整篇覆盖，UI 模式按补丁增量应用。
     *
     * @param request 保存请求
     * @return 执行结果，含备份、语法检查与重载的每一步结果
     */
    @Operation(summary = "应用 Nginx 配置改动（文件模式 / UI 模式）")
    @PostMapping("/config/apply")
    public ReturnResult<NginxApplyResultVO> applyConfig(@RequestBody NginxApplyDTO request) {
        return call("应用 Nginx 配置", () -> nginxManageService.applyConfig(request));
    }

    /**
     * 取指令元数据，供 UI 模式决定表单控件。
     *
     * @param scope 作用域标识，为空返回全部
     * @return 指令元数据列表
     */
    @Operation(summary = "Nginx 指令元数据")
    @GetMapping("/directives")
    public ReturnResult<List<NginxDirectiveMetaVO>> directives(@RequestParam(required = false) String scope) {
        return call("读取指令元数据", () -> nginxManageService.directives(scope));
    }

    /**
     * 按站点维度汇总所有 server 块。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 站点清单
     */
    @Operation(summary = "Nginx 站点清单")
    @GetMapping("/sites")
    public ReturnResult<List<NginxSiteVO>> sites(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nginx 站点清单", () -> nginxManageService.sites(datasourceId));
    }

    /**
     * 取配置模板库（新人模式起步模板）。
     *
     * @return 模板列表
     */
    @Operation(summary = "Nginx 配置模板库")
    @GetMapping("/templates")
    public ReturnResult<List<NginxTemplateVO>> templates() {
        return call("读取配置模板", nginxManageService::templates);
    }

    // ==================== 生命周期 ====================

    /**
     * 语法检查（nginx -t）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 检查结果
     */
    @Operation(summary = "Nginx 语法检查")
    @PostMapping("/test")
    public ReturnResult<NginxCommandResultVO> test(@RequestParam(required = false) Long datasourceId) {
        return call("Nginx 语法检查", () -> nginxManageService.test(datasourceId));
    }

    /**
     * 重载 Nginx（先语法检查，通过才 reload）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 执行结果
     */
    @Operation(summary = "重载 Nginx")
    @PostMapping("/reload")
    public ReturnResult<NginxCommandResultVO> reload(@RequestParam(required = false) Long datasourceId) {
        return call("重载 Nginx", () -> nginxManageService.reload(datasourceId));
    }

    /**
     * 重启 Nginx（优雅退出后重新拉起）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 执行结果
     */
    @Operation(summary = "重启 Nginx")
    @PostMapping("/restart")
    public ReturnResult<NginxCommandResultVO> restart(@RequestParam(required = false) Long datasourceId) {
        return call("重启 Nginx", () -> nginxManageService.restart(datasourceId));
    }

    /**
     * 启动 Nginx。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 执行结果
     */
    @Operation(summary = "启动 Nginx")
    @PostMapping("/start")
    public ReturnResult<NginxCommandResultVO> start(@RequestParam(required = false) Long datasourceId) {
        return call("启动 Nginx", () -> nginxManageService.start(datasourceId));
    }

    /**
     * 停止 Nginx。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 执行结果
     */
    @Operation(summary = "停止 Nginx")
    @PostMapping("/stop")
    public ReturnResult<NginxCommandResultVO> stop(@RequestParam(required = false) Long datasourceId) {
        return call("停止 Nginx", () -> nginxManageService.stop(datasourceId));
    }

    // ==================== 备份 ====================

    /**
     * 读取备份文件内容。
     *
     * @param datasourceId 数据源 ID，可空
     * @param name         备份文件名，只允许纯文件名
     * @return 备份文件全文
     */
    @Operation(summary = "读取备份内容")
    @GetMapping("/backup/content")
    public ReturnResult<String> backupContent(@RequestParam(required = false) Long datasourceId,
            @RequestParam String name) {
        return call("读取备份内容", () -> nginxManageService.readBackupContent(datasourceId, name));
    }

    /**
     * 对比备份与当前配置的差异。
     *
     * @param datasourceId 数据源 ID，可空
     * @param name         备份文件名，只允许纯文件名
     * @param target       对比目标（当前配置文件）绝对路径
     * @return 差异结果
     */
    @Operation(summary = "对比备份与当前配置")
    @GetMapping("/backup/diff")
    public ReturnResult<NginxBackupDiffVO> backupDiff(@RequestParam(required = false) Long datasourceId,
            @RequestParam String name, @RequestParam String target) {
        return call("对比备份差异", () -> nginxManageService.diffBackup(datasourceId, name, target));
    }

    /**
     * 按 URL 维度统计访问趋势。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         日志文件路径，空则取配置里的 access_log
     * @param lines        扫描的尾部行数
     * @param topN         取前几个 URL
     * @return URL 趋势
     */
    @Operation(summary = "URL 访问趋势")
    @GetMapping("/log/url-trend")
    public ReturnResult<NginxUrlTrendVO> urlTrend(@RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer lines,
            @RequestParam(required = false) Integer topN) {
        return call("统计 URL 访问趋势", () -> nginxManageService.urlTrend(datasourceId, path, lines, topN));
    }

    // ==================== 状态 ====================

    /**
     * 读取 Nginx 版本与编译参数。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 版本信息
     */
    @Operation(summary = "Nginx 版本信息")
    @GetMapping("/version")
    public ReturnResult<NginxVersionVO> version(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nginx 版本", () -> nginxManageService.version(datasourceId));
    }

    /**
     * 读取 stub_status 实时状态。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 状态信息
     */
    @Operation(summary = "Nginx 实时状态")
    @GetMapping("/status")
    public ReturnResult<NginxStatusVO> status(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nginx 状态", () -> nginxManageService.status(datasourceId));
    }

    /**
     * 查询指标趋势。
     *
     * <p>数据来自后台定时采集的 {@code ms_monitor_nginx_metric}，保留窗口由
     * {@code monitor.data.retention.days} 控制（默认 1，即滚动 24 小时）。</p>
     *
     * @param datasourceId 数据源 ID
     * @param hours       回溯小时数，缺省 24
     * @param interval    采样间隔（分钟），缺省 30；点数超上限时后端自动放大
     * @return 趋势数据
     */
    @Operation(summary = "Nginx 指标趋势")
    @GetMapping("/metric/trend")
    public ReturnResult<NginxTrendVO> trend(@RequestParam(required = false) Long datasourceId,
                                            @RequestParam(required = false) Integer hours,
                                            @RequestParam(required = false) Integer interval) {
        return call("读取 Nginx 指标趋势", () -> nginxManageService.trend(datasourceId, hours, interval));
    }

    /**
     * 访问日志 Top 统计（路径 / 客户端 IP / 状态码分布）。
     *
     * <p>SSH 拉 access log 尾部后在应用内解析，不入库。</p>
     *
     * @param datasourceId 数据源 ID
     * @param path         日志路径，缺省 access 日志
     * @param lines        扫描尾部行数，缺省 2000，最大 20000
     * @param minutes      只统计最近 N 分钟，缺省 0（不限）
     * @param topN         每维度取前几，缺省 10
     * @return Top 统计
     */
    @Operation(summary = "Nginx 访问日志 Top 统计")
    @GetMapping("/log/top")
    public ReturnResult<NginxAccessTopVO> accessTop(@RequestParam(required = false) Long datasourceId,
                                                    @RequestParam(required = false) String path,
                                                    @RequestParam(required = false) Integer lines,
                                                    @RequestParam(required = false) Integer minutes,
                                                    @RequestParam(required = false) Integer topN) {
        return call("读取 Nginx 访问日志 Top 统计",
                () -> nginxManageService.accessTop(datasourceId, path, lines, minutes, topN));
    }

    /**
     * 订阅指标 / 运行态实时推送（SSE）。
     *
     * <p>建连即回一个 {@code ready} 事件，之后每轮采集（默认 30s）推两个事件：</p>
     * <ul>
     *   <li>{@code metric}：该数据源每个指标的最后一个点（已换算速率）</li>
     *   <li>{@code runtime}：实时运行态（运行中 / 已停止 / PID / 已运行时长 / 活跃连接）</li>
     * </ul>
     * <p>另有 20s 一次的注释帧保活，前端不会把它当事件。</p>
     *
     * <p><strong>首屏历史不在这里给</strong>：SSE 只做增量。首屏仍走
     * {@code GET /metric/trend}，否则建连那一刻就要推 288 个点，
     * 而页面往往在历史拉到之前就断开了。</p>
     *
     * @param datasourceId 数据源 ID
     * @return SSE 连接
     */
    @Operation(summary = "Nginx 指标与运行态实时推送（SSE）")
    @GetMapping("/metric/stream")
    public SseEmitter metricStream(@RequestParam(required = false) Long datasourceId) {
        Long id = datasourceId;
        if (id == null) {
            id = defaultDatasourceId();
        }
        return streamRegistry.subscribe(id);
    }

    /**
     * 解析缺省数据源：第一个 nginx 类型数据源。
     *
     * <p>SSE 建连是长连接，参数缺失时若直接返回空推送，前端会一直连着一个
     * 永远不说话的连接且不报错——比直接报错更难排查。这里退化成「第一个 nginx
     * 数据源」，取不到就抛出，由统一异常处理转成业务错误。</p>
     *
     * @return 数据源 id
     */
    private Long defaultDatasourceId() {
        List<Long> ids = nginxManageService.listNginxDatasourceIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("没有可用的 nginx 数据源");
        }
        return ids.get(0);
    }

    // ==================== 日志 ====================

    /**
     * 列出日志文件。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 日志文件列表
     */
    @Operation(summary = "列出 Nginx 日志文件")
    @GetMapping("/log/files")
    public ReturnResult<List<NginxLogFileVO>> logFiles(@RequestParam(required = false) Long datasourceId) {
        return call("列出 Nginx 日志文件", () -> nginxManageService.listLogFiles(datasourceId));
    }

    /**
     * 取日志尾部 N 行。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path        日志文件路径，可空
     * @param lines       尾部行数，缺省 200
     * @return 日志文本
     */
    @Operation(summary = "查看 Nginx 日志")
    @GetMapping("/log")
    public ReturnResult<String> log(@RequestParam(required = false) Long datasourceId,
                                    @RequestParam(required = false) String path,
                                    @RequestParam(required = false) Integer lines) {
        return call("读取 Nginx 日志", () -> nginxManageService.log(datasourceId, path, lines));
    }

    /**
     * 增量读取日志：只返回游标之后的新内容。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         日志文件路径，可空
     * @param lines        首次读取的尾部行数
     * @param cursor       上次读到的字节位置，首次不传
     * @return 增量结果
     */
    @Operation(summary = "增量读取 Nginx 日志")
    @GetMapping("/log/chunk")
    public ReturnResult<NginxLogChunkVO> logChunk(@RequestParam(required = false) Long datasourceId,
                                                  @RequestParam(required = false) String path,
                                                  @RequestParam(required = false) Integer lines,
                                                  @RequestParam(required = false) Long cursor) {
        return call("增量读取 Nginx 日志", () -> nginxManageService.logChunk(datasourceId, path, lines, cursor));
    }

    // ==================== 备份 ====================

    /**
     * 立即备份指定配置文件。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path        配置文件路径
     * @param reason      备份原因
     * @return 备份条目
     */
    @Operation(summary = "备份 Nginx 配置文件")
    @PostMapping("/backup")
    public ReturnResult<NginxBackupVO> backup(@RequestParam(required = false) Long datasourceId,
                                              @RequestParam String path,
                                              @RequestParam(required = false) String reason) {
        return call("备份 Nginx 配置", () -> nginxManageService.backup(datasourceId, path, reason));
    }

    /**
     * 列出备份。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 备份列表
     */
    @Operation(summary = "列出 Nginx 配置备份")
    @GetMapping("/backup")
    public ReturnResult<List<NginxBackupVO>> backups(@RequestParam(required = false) Long datasourceId) {
        return call("列出 Nginx 配置备份", () -> nginxManageService.listBackups(datasourceId));
    }

    /**
     * 从备份恢复。
     *
     * @param datasourceId 数据源 ID，可空
     * @param backupName   备份文件名
     * @param targetPath   还原到的目标路径，可空；原始路径含下划线时反推不可逆，需显式指定
     * @return 执行结果
     */
    @Operation(summary = "从备份恢复 Nginx 配置")
    @PostMapping("/backup/{backupName}/restore")
    public ReturnResult<NginxCommandResultVO> restore(@RequestParam(required = false) Long datasourceId,
                                                      @PathVariable String backupName,
                                                      @RequestParam(required = false) String targetPath) {
        return call("从备份恢复", () -> nginxManageService.restore(datasourceId, backupName, targetPath));
    }

    // ==================== 自定义看板 ====================

    /**
     * 读取自定义看板。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 看板
     */
    @Operation(summary = "读取 Nginx 自定义看板")
    @GetMapping("/dashboard")
    public ReturnResult<NginxDashboardVO> dashboard(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nginx 看板", () -> nginxManageService.getDashboard(datasourceId));
    }

    /**
     * 保存自定义看板（实时保存）。
     *
     * @param request 保存请求
     * @return 保存后的看板
     */
    @Operation(summary = "保存 Nginx 自定义看板")
    @PutMapping("/dashboard")
    public ReturnResult<NginxDashboardVO> saveDashboard(@RequestBody NginxDashboardDTO request) {
        return call("保存 Nginx 看板", () -> nginxManageService.saveDashboard(request));
    }

    /**
     * 重新生成嵌入令牌。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 看板
     */
    @Operation(summary = "生成 Nginx 看板嵌入令牌")
    @PostMapping("/dashboard/token")
    public ReturnResult<NginxDashboardVO> token(@RequestParam(required = false) Long datasourceId) {
        return call("生成嵌入令牌", () -> nginxManageService.regenerateToken(datasourceId));
    }

    /**
     * 按令牌读取看板（公开只读，需部署侧放行 OAuth 白名单）。
     *
     * @param token 嵌入令牌
     * @return 看板
     */
    @Operation(summary = "读取嵌入看板（公开）")
    @GetMapping("/dashboard/embed/{token}")
    public ReturnResult<NginxDashboardVO> embedDashboard(@PathVariable String token) {
        return call("读取嵌入看板", () -> nginxManageService.embedDashboard(token));
    }

    /**
     * 嵌入场景读取实时状态（公开只读）。
     *
     * @param token 嵌入令牌
     * @return 状态信息
     */
    @Operation(summary = "嵌入看板实时状态（公开）")
    @GetMapping("/dashboard/embed/{token}/status")
    public ReturnResult<NginxStatusVO> embedStatus(@PathVariable String token) {
        return call("嵌入看板实时状态", () -> nginxManageService.embedStatus(token));
    }

    /**
     * 嵌入场景读取指标趋势（公开只读）。
     *
     * @param token    嵌入令牌
     * @param hours    回溯小时数，缺省 24
     * @param interval 采样间隔（分钟），缺省 30
     * @return 趋势数据
     */
    @Operation(summary = "嵌入看板指标趋势（公开）")
    @GetMapping("/dashboard/embed/{token}/metric/trend")
    public ReturnResult<NginxTrendVO> embedTrend(@PathVariable String token,
                                                 @RequestParam(required = false) Integer hours,
                                                 @RequestParam(required = false) Integer interval) {
        return call("嵌入看板指标趋势", () -> nginxManageService.embedTrend(token, hours, interval));
    }

    /**
     * 嵌入场景读取访问日志 Top 统计（公开只读）。
     *
     * @param token   嵌入令牌
     * @param path    日志文件路径，缺省 access 日志
     * @param lines   扫描的尾部行数
     * @param minutes 只统计最近 N 分钟，缺省不限
     * @param topN    每个维度取前几
     * @return Top 统计
     */
    @Operation(summary = "嵌入看板访问日志 Top 统计（公开）")
    @GetMapping("/dashboard/embed/{token}/log/top")
    public ReturnResult<NginxAccessTopVO> embedAccessTop(@PathVariable String token,
                                                         @RequestParam(required = false) String path,
                                                         @RequestParam(required = false) Integer lines,
                                                         @RequestParam(required = false) Integer minutes,
                                                         @RequestParam(required = false) Integer topN) {
        return call("嵌入看板访问日志 Top 统计", () -> nginxManageService.embedAccessTop(token, path, lines, minutes, topN));
    }

    /**
     * 嵌入场景读取 URL 访问趋势（公开只读）。
     *
     * @param token 嵌入令牌
     * @param path  日志文件路径，缺省 access 日志
     * @param lines 扫描的尾部行数
     * @param topN  取前几个 URL
     * @return URL 趋势
     */
    @Operation(summary = "嵌入看板 URL 访问趋势（公开）")
    @GetMapping("/dashboard/embed/{token}/log/url-trend")
    public ReturnResult<NginxUrlTrendVO> embedUrlTrend(@PathVariable String token,
                                                       @RequestParam(required = false) String path,
                                                       @RequestParam(required = false) Integer lines,
                                                       @RequestParam(required = false) Integer topN) {
        return call("嵌入看板 URL 访问趋势", () -> nginxManageService.embedUrlTrend(token, path, lines, topN));
    }

    /**
     * 执行一次管理操作，把远端失败换成可读原因而不是 500 堆栈。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[nginx] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
