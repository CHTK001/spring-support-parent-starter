package com.chua.starter.panel.service;

import com.chua.starter.panel.model.*;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Nginx 面板服务（通过 SSH 管理远程 Nginx）。
 *
 * @author CH
 */
public interface NginxPanelService {

    // ── 连接管理 ──────────────────────────────────────────────────────────────

    /**
     * 打开 SSH 连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param privateKey 私钥文本
     * @return 连接标识
     */
    String open(String host, int port, String username, String password, String privateKey);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    // ── 服务控制 ──────────────────────────────────────────────────────────────

    /**
     * 获取 Nginx 状态。
     *
     * @param connectionId 连接标识
     * @return 状态信息
     */
    Map<String, Object> status(String connectionId);

    /**
     * 重新加载配置。
     *
     * @param connectionId 连接标识
     * @return 执行结果
     */
    Map<String, Object> reload(String connectionId);

    /**
     * 重启 Nginx。
     *
     * @param connectionId 连接标识
     * @return 执行结果
     */
    Map<String, Object> restart(String connectionId);

    /**
     * 停止 Nginx。
     *
     * @param connectionId 连接标识
     * @return 执行结果
     */
    Map<String, Object> stop(String connectionId);

    // ── 配置文件管理 ──────────────────────────────────────────────────────────

    /**
     * 读取配置文件内容。
     *
     * @param connectionId 连接标识
     * @param path 配置文件路径
     * @return 配置内容
     */
    String readConfig(String connectionId, String path);

    /**
     * 写入配置文件内容。
     *
     * @param connectionId 连接标识
     * @param path 配置文件路径
     * @param content 配置内容
     */
    void writeConfig(String connectionId, String path, String content);

    /**
     * 列出配置文件列表。
     * 扫描 /etc/nginx/nginx.conf 的 include 指令 + sites-enabled/ + conf.d/
     *
     * @param connectionId 连接标识
     * @return 配置文件列表
     */
    List<NginxConfFile> listConfFiles(String connectionId);

    /**
     * 创建新配置文件。
     *
     * @param connectionId 连接标识
     * @param path 配置文件路径
     * @param content 配置内容
     * @return 配置文件信息
     */
    NginxConfFile createConfFile(String connectionId, String path, String content);

    /**
     * 删除配置文件。
     *
     * @param connectionId 连接标识
     * @param path 配置文件路径
     */
    void deleteConfFile(String connectionId, String path);

    // ── Server/Location 结构化解析 ────────────────────────────────────────────

    /**
     * 解析指定配置文件中的 server 块列表。
     * 若 configPath 为 null，默认解析 /etc/nginx/nginx.conf。
     *
     * @param connectionId 连接标识
     * @param configPath 配置文件路径
     * @return server 块列表
     */
    List<NginxServerBlock> parseServers(String connectionId, String configPath);

    /**
     * 将 server 块列表写回配置文件（替换原有 server 块）。
     * 若 configPath 为 null，默认写入 /etc/nginx/nginx.conf。
     *
     * @param connectionId 连接标识
     * @param configPath 配置文件路径
     * @param servers server 块列表
     */
    void saveServers(String connectionId, String configPath, List<NginxServerBlock> servers);

    // ── 日志 ──────────────────────────────────────────────────────────────────

    /**
     * 读取日志尾部内容。
     *
     * @param connectionId 连接标识
     * @param logPath 日志路径
     * @param lines 行数
     * @return 日志内容
     */
    String tailLog(String connectionId, String logPath, int lines);

    // ── 大屏统计 ──────────────────────────────────────────────────────────────

    /**
     * 获取大屏统计数据：
     * - stub_status（若已配置）
     * - access.log Top IP 分析
     * - 服务运行状态
     *
     * @param connectionId 连接标识
     * @param accessLogPath access.log 路径，null 时使用默认路径
     * @param topN          Top IP 数量
     * @return 大屏统计数据
     */
    NginxDashboardStats dashboardStats(String connectionId, String accessLogPath, int topN);

    /**
     * 查询站点流量排行。
     *
     * @param connectionId 连接标识
     * @param topN Top N 数量
     * @return 站点流量列表
     */
    List<NginxSiteTraffic> siteTraffic(String connectionId, int topN);

    /**
     * 查询指定站点与 IP 的流量趋势。
     *
     * @param connectionId 连接标识
     * @param siteKey 站点标识
     * @param ip 客户端 IP
     * @param granularity 粒度
     * @return 流量趋势
     */
    NginxTrafficTrend trafficTrend(String connectionId, String siteKey, String ip, String granularity);

    // ── 脚本执行 ──────────────────────────────────────────────────────────────

    /**
     * 在远程服务器上执行 shell 脚本。
     *
     * @param connectionId 连接标识
     * @param script shell 脚本内容（多行）
     * @return 执行输出（stdout + stderr）
     */
    String executeScript(String connectionId, String script);

    /**
     * 在远程服务器上执行 shell 脚本（流式输出）。
     *
     * @param connectionId 连接标识
     * @param script shell 脚本内容（多行）
     * @param lineConsumer 输出行回调（每产生一行立即回调，用于实时日志推送）
     * @return 执行输出（stdout + stderr）
     */
    String executeScript(String connectionId, String script, Consumer<String> lineConsumer);
}
