package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.model.NginxConfFile;
import com.chua.starter.panel.model.NginxDashboardStats;
import com.chua.starter.panel.model.NginxSiteTraffic;
import com.chua.starter.panel.model.NginxServerBlock;
import com.chua.starter.panel.model.NginxTrafficTrend;
import com.chua.starter.panel.service.NginxPanelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Nginx 面板控制器（通过 SSH 管理远程 Nginx）。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/nginx")
@Tag(name = "Panel Nginx", description = "Panel Nginx 远程管理接口")
public class PanelNginxController {

    private final NginxPanelService nginxPanelService;
    private final ObjectMapper objectMapper;

    // ── 连接管理 ──────────────────────────────────────────────────────────────

    /**
     * 打开 Nginx SSH 连接并返回连接标识。
     *
     * @param body 连接参数
     * @return 连接标识
     */
    @Operation(summary = "打开 SSH 连接")
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 22)));
        String username = String.valueOf(body.getOrDefault("username", "root"));
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        String privateKey = body.containsKey("privateKey") ? String.valueOf(body.get("privateKey")) : null;
        try {
            return ReturnResult.ok(nginxPanelService.open(host, port, username, password, privateKey));
        } catch (Exception e) {
            return ReturnResult.error("SSH 连接失败: " + e.getMessage());
        }
    }

    /**
     * 关闭指定连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @Operation(summary = "关闭连接")
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        nginxPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    // ── 服务控制 ──────────────────────────────────────────────────────────────

    /**
     * 查询 Nginx 当前状态。
     *
     * @param connectionId 连接标识
     * @return 状态信息
     */
    @Operation(summary = "查询服务状态")
    @GetMapping("/{connectionId}/status")
    public ReturnResult<Map<String, Object>> status(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(nginxPanelService.status(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取状态失败: " + e.getMessage());
        }
    }

    /**
     * 重新加载 Nginx 配置。
     *
     * @param connectionId 连接标识
     * @return 执行结果
     */
    @Operation(summary = "重载服务")
    @PostMapping("/{connectionId}/reload")
    public ReturnResult<Map<String, Object>> reload(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(nginxPanelService.reload(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Reload 失败: " + e.getMessage());
        }
    }

    /**
     * 重启 Nginx 服务。
     *
     * @param connectionId 连接标识
     * @return 执行结果
     */
    @Operation(summary = "重启服务")
    @PostMapping("/{connectionId}/restart")
    public ReturnResult<Map<String, Object>> restart(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(nginxPanelService.restart(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Restart 失败: " + e.getMessage());
        }
    }

    /**
     * 停止 Nginx 服务。
     *
     * @param connectionId 连接标识
     * @return 执行结果
     */
    @Operation(summary = "停止服务")
    @PostMapping("/{connectionId}/stop")
    public ReturnResult<Map<String, Object>> stop(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(nginxPanelService.stop(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("Stop 失败: " + e.getMessage());
        }
    }

    // ── 配置文件管理 ──────────────────────────────────────────────────────────

    /**
     * 读取 Nginx 配置文件内容。
     *
     * @param connectionId 连接标识
     * @param path 配置文件路径
     * @return 配置内容
     */
    @Operation(summary = "读取配置")
    @GetMapping("/{connectionId}/config")
    public ReturnResult<String> readConfig(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "配置文件路径") @RequestParam(required = false) String path) {
        try {
            return ReturnResult.ok(nginxPanelService.readConfig(connectionId, path));
        } catch (Exception e) {
            return ReturnResult.error("读取配置失败: " + e.getMessage());
        }
    }

    /**
     * 写入 Nginx 配置文件内容。
     *
     * @param connectionId 连接标识
     * @param body 配置写入参数
     * @return 写入结果
     */
    @Operation(summary = "写入配置")
    @PutMapping("/{connectionId}/config")
    public ReturnResult<Void> writeConfig(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        try {
            nginxPanelService.writeConfig(connectionId, body.get("path"), body.get("content"));
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("写入配置失败: " + e.getMessage());
        }
    }

    /**
     * 列出所有配置文件及解析出的 server 块。
     *
     * @param connectionId 连接标识
     * @return 配置文件列表
     */
    @Operation(summary = "列出配置文件")
    @GetMapping("/{connectionId}/conf-files")
    public ReturnResult<List<NginxConfFile>> listConfFiles(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(nginxPanelService.listConfFiles(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取配置文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建新的 Nginx 配置文件。
     *
     * @param connectionId 连接标识
     * @param body 文件参数
     * @return 新建后的配置文件
     */
    @Operation(summary = "创建配置文件")
    @PostMapping("/{connectionId}/conf-files")
    public ReturnResult<NginxConfFile> createConfFile(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(nginxPanelService.createConfFile(
                    connectionId, body.get("path"), body.getOrDefault("content", "")));
        } catch (Exception e) {
            return ReturnResult.error("创建配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 删除指定配置文件。
     *
     * @param connectionId 连接标识
     * @param path 配置文件路径
     * @return 删除结果
     */
    @Operation(summary = "删除配置文件")
    @DeleteMapping("/{connectionId}/conf-files")
    public ReturnResult<Void> deleteConfFile(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "配置文件路径") @RequestParam String path) {
        try {
            nginxPanelService.deleteConfFile(connectionId, path);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("删除配置文件失败: " + e.getMessage());
        }
    }

    // ── Server/Location 结构化管理 ────────────────────────────────────────────

    /**
     * 解析指定配置文件中的 server 块。
     *
     * @param connectionId 连接标识
     * @param configPath 配置文件路径
     * @return server 块列表
     */
    @Operation(summary = "解析 server 块")
    @GetMapping("/{connectionId}/servers")
    public ReturnResult<List<NginxServerBlock>> parseServers(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "配置文件路径") @RequestParam(required = false) String configPath) {
        try {
            return ReturnResult.ok(nginxPanelService.parseServers(connectionId, configPath));
        } catch (Exception e) {
            return ReturnResult.error("解析 server 块失败: " + e.getMessage());
        }
    }

    /**
     * 保存 server 块列表并写回配置文件。
     *
     * @param connectionId 连接标识
     * @param body server 块参数
     * @return 保存结果
     */
    @Operation(summary = "保存 server 块")
    @PutMapping("/{connectionId}/servers")
    public ReturnResult<Void> saveServers(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        try {
            String configPath = (String) body.get("configPath");
            List<NginxServerBlock> servers = objectMapper.convertValue(
                    body.get("servers"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, NginxServerBlock.class));
            nginxPanelService.saveServers(connectionId, configPath, servers);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("保存 server 块失败: " + e.getMessage());
        }
    }

    // ── 日志 ──────────────────────────────────────────────────────────────────

    /**
     * 读取 Nginx 日志尾部内容。
     *
     * @param connectionId 连接标识
     * @param logPath 日志路径
     * @param lines 行数
     * @return 日志内容
     */
    @Operation(summary = "读取日志")
    @GetMapping("/{connectionId}/log")
    public ReturnResult<String> tailLog(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "日志路径") @RequestParam(required = false) String logPath,
            @Parameter(description = "返回行数") @RequestParam(defaultValue = "100") int lines) {
        try {
            return ReturnResult.ok(nginxPanelService.tailLog(connectionId, logPath, lines));
        } catch (Exception e) {
            return ReturnResult.error("读取日志失败: " + e.getMessage());
        }
    }

    // ── 大屏统计 ──────────────────────────────────────────────────────────────

    /**
     * 获取 Nginx 大屏统计信息。
     *
     * @param connectionId 连接标识
     * @param accessLogPath 访问日志路径
     * @param topN Top N 数量
     * @return 大屏统计数据
     */
    @Operation(summary = "获取大屏统计")
    @GetMapping("/{connectionId}/dashboard")
    public ReturnResult<NginxDashboardStats> dashboardStats(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "访问日志路径") @RequestParam(required = false) String accessLogPath,
            @Parameter(description = "Top N 数量") @RequestParam(defaultValue = "20") int topN) {
        try {
            return ReturnResult.ok(nginxPanelService.dashboardStats(connectionId, accessLogPath, topN));
        } catch (Exception e) {
            return ReturnResult.error("获取大屏数据失败: " + e.getMessage());
        }
    }

    /**
     * 查询站点流量排行。
     *
     * @param connectionId 连接标识
     * @param topN Top N 数量
     * @return 站点流量列表
     */
    @Operation(summary = "查询站点流量")
    @GetMapping("/{connectionId}/traffic/sites")
    public ReturnResult<List<NginxSiteTraffic>> siteTraffic(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "Top N 数量") @RequestParam(defaultValue = "10") int topN) {
        try {
            return ReturnResult.ok(nginxPanelService.siteTraffic(connectionId, topN));
        } catch (Exception e) {
            return ReturnResult.error("获取站点流量失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定站点和 IP 的流量趋势。
     *
     * @param connectionId 连接标识
     * @param siteKey 站点标识
     * @param ip 客户端 IP
     * @param granularity 粒度
     * @return 流量趋势
     */
    @Operation(summary = "查询流量趋势")
    @GetMapping("/{connectionId}/traffic/trend")
    public ReturnResult<NginxTrafficTrend> trafficTrend(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "站点标识") @RequestParam String siteKey,
            @Parameter(description = "客户端 IP") @RequestParam String ip,
            @Parameter(description = "时间粒度") @RequestParam(defaultValue = "minute") String granularity) {
        try {
            return ReturnResult.ok(nginxPanelService.trafficTrend(connectionId, siteKey, ip, granularity));
        } catch (Exception e) {
            return ReturnResult.error("获取流量趋势失败: " + e.getMessage());
        }
    }

    // ── 兼容旧接口 ────────────────────────────────────────────────────────────

    /**
     * 兼容旧版站点列表接口。
     *
     * @param connectionId 连接标识
     * @return 站点名称列表
     */
    @Operation(summary = "兼容旧版站点接口")
    @GetMapping("/{connectionId}/sites")
    public ReturnResult<List<String>> listSites(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            // 从 conf-files 里提取 sites-enabled 下的文件名
            List<NginxConfFile> files = nginxPanelService.listConfFiles(connectionId);
            List<String> sites = files.stream()
                    .filter(f -> f.getPath() != null && f.getPath().contains("sites-enabled"))
                    .map(NginxConfFile::getLabel)
                    .collect(java.util.stream.Collectors.toList());
            return ReturnResult.ok(sites);
        } catch (Exception e) {
            return ReturnResult.error("获取站点列表失败: " + e.getMessage());
        }
    }
}
