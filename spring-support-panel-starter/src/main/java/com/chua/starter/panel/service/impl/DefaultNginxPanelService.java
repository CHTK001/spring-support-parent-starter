package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.cache.PanelSshSessionRegistry;
import com.chua.starter.panel.model.*;
import com.chua.starter.panel.service.NginxPanelService;
import com.jcraft.jsch.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 JSch SSH 的 Nginx 面板服务默认实现。
 *
 * @author CH
 */
public class DefaultNginxPanelService implements NginxPanelService {

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final String DEFAULT_NGINX_CONF = "/etc/nginx/nginx.conf";
    private static final String CONF_D_DIR = "/etc/nginx/conf.d/";
    private static final String SITES_ENABLED_DIR = "/etc/nginx/sites-enabled/";
    private static final DateTimeFormatter ACCESS_LOG_TIME =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    /** connectionId -> JSch Session（共享注册表） */
    private final ConcurrentHashMap<String, Session> sessions;

    public DefaultNginxPanelService(PanelSshSessionRegistry registry) {
        this.sessions = registry.getSessions();
    }

    /** 兼容无参构造（单独使用时） */
    public DefaultNginxPanelService() {
        this.sessions = new ConcurrentHashMap<>();
    }

    // ── 连接管理 ──────────────────────────────────────────────────────────────

    @Override
    public String open(String host, int port, String username, String password, String privateKey) {
        try {
            JSch jsch = new JSch();
            if (privateKey != null && !privateKey.isBlank()) {
                byte[] keyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
                jsch.addIdentity("key", keyBytes, null, null);
            } else if ((password == null || password.isBlank())) {
                // 仅当没有密码时才尝试默认私钥文件（JSch 不支持 OpenSSH 新格式）
                String defaultKey = System.getProperty("user.home") + "/.ssh/id_rsa";
                java.io.File keyFile = new java.io.File(defaultKey);
                if (keyFile.isFile()) {
                    jsch.addIdentity(defaultKey);
                }
            }
            Session session = jsch.getSession(username, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            if (password != null && !password.isBlank()) {
                session.setPassword(password);
            }
            session.connect(CONNECT_TIMEOUT);
            String connectionId = UUID.randomUUID().toString();
            sessions.put(connectionId, session);
            return connectionId;
        } catch (JSchException e) {
            throw new RuntimeException("SSH 连接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void close(String connectionId) {
        Session session = sessions.remove(connectionId);
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    // ── 服务控制 ──────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> status(String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configTest", exec(connectionId, "nginx -t 2>&1"));
        result.put("systemctlStatus", exec(connectionId, "systemctl status nginx 2>&1 || service nginx status 2>&1"));
        result.put("version", exec(connectionId, "nginx -v 2>&1").trim());
        String pid = exec(connectionId, "cat /var/run/nginx.pid 2>/dev/null || pgrep -o nginx 2>/dev/null").trim();
        result.put("pid", pid);
        return result;
    }

    @Override
    public Map<String, Object> reload(String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", exec(connectionId, "nginx -s reload 2>&1"));
        return result;
    }

    @Override
    public Map<String, Object> restart(String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", exec(connectionId, "systemctl restart nginx 2>&1 || service nginx restart 2>&1"));
        return result;
    }

    @Override
    public Map<String, Object> stop(String connectionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", exec(connectionId, "systemctl stop nginx 2>&1 || service nginx stop 2>&1"));
        return result;
    }

    // ── 配置文件管理 ──────────────────────────────────────────────────────────

    @Override
    public String readConfig(String connectionId, String path) {
        return readFileViaSftp(connectionId, path);
    }

    @Override
    public void writeConfig(String connectionId, String path, String content) {
        writeFileViaSftp(connectionId, path, content);
    }

    @Override
    public List<NginxConfFile> listConfFiles(String connectionId) {
        List<NginxConfFile> files = new ArrayList<>();

        // 主配置文件
        files.add(buildConfFile(connectionId, DEFAULT_NGINX_CONF, "nginx.conf", "main"));

        // conf.d/
        for (String path : listRemoteConfFiles(connectionId, CONF_D_DIR)) {
            String label = path.substring(path.lastIndexOf('/') + 1);
            files.add(buildConfFile(connectionId, path, label, "site"));
        }

        // sites-enabled/
        for (String path : listRemoteConfFiles(connectionId, SITES_ENABLED_DIR)) {
            String label = path.substring(path.lastIndexOf('/') + 1);
            files.add(buildConfFile(connectionId, path, label, "site"));
        }

        return files;
    }

    @Override
    public NginxConfFile createConfFile(String connectionId, String path, String content) {
        writeFileViaSftp(connectionId, path, content);
        String label = path.substring(path.lastIndexOf('/') + 1);
        List<NginxServerBlock> servers = parseServerBlocks(content, path);
        return NginxConfFile.builder()
                .path(path)
                .label(label)
                .type("site")
                .servers(servers)
                .exists(true)
                .build();
    }

    @Override
    public void deleteConfFile(String connectionId, String path) {
        deleteFileViaSftp(connectionId, path);
    }

    // ── Server/Location 结构化解析 ────────────────────────────────────────────

    @Override
    public List<NginxServerBlock> parseServers(String connectionId, String configPath) {
        String path = configPath != null ? configPath : DEFAULT_NGINX_CONF;
        String content = readFileViaSftp(connectionId, path);
        return parseServerBlocks(content, path);
    }

    @Override
    public void saveServers(String connectionId, String configPath, List<NginxServerBlock> servers) {
        String path = configPath != null ? configPath : DEFAULT_NGINX_CONF;
        String original = readFileViaSftp(connectionId, path);
        String rendered = renderServers(servers);
        String updated = replaceServerBlocksInConfig(original, rendered);
        writeFileViaSftp(connectionId, path, updated);
    }

    // ── 日志 ──────────────────────────────────────────────────────────────────

    @Override
    public String tailLog(String connectionId, String logPath, int lines) {
        return exec(connectionId, "tail -n " + lines + " " + logPath + " 2>&1");
    }

    // ── 大屏统计 ──────────────────────────────────────────────────────────────

    @Override
    public NginxDashboardStats dashboardStats(String connectionId, String accessLogPath, int topN) {
        String logPath = accessLogPath != null
                ? resolveAccessLogPath(connectionId, DEFAULT_NGINX_CONF, accessLogPath)
                : resolveConfiguredAccessLogPath(connectionId, DEFAULT_NGINX_CONF);
        if (logPath == null || logPath.isBlank()) {
            logPath = "/var/log/nginx/access.log";
        }
        NginxDashboardStats.NginxDashboardStatsBuilder builder = NginxDashboardStats.builder();

        // stub_status
        String stubOut = exec(connectionId,
                "curl -sf http://127.0.0.1/nginx_status 2>/dev/null || curl -sf http://127.0.0.1:80/nginx_status 2>/dev/null");
        parseStubStatus(stubOut, builder);

        String accessLogTail = exec(connectionId, "tail -n 20000 " + shellQuote(logPath) + " 2>/dev/null");
        List<AccessLogEntry> accessLogEntries = parseAccessLogEntries(accessLogTail);
        applyAccessLogStats(builder, accessLogEntries, topN);

        if (builder.build().getRequests() <= 0 && !accessLogEntries.isEmpty()) {
            builder.requests(accessLogEntries.size());
        }
        if (builder.build().getAccepts() <= 0 && !accessLogEntries.isEmpty()) {
            builder.accepts(accessLogEntries.size());
        }
        if (builder.build().getHandled() <= 0 && !accessLogEntries.isEmpty()) {
            builder.handled(accessLogEntries.size());
        }
        if (builder.build().getActiveConnections() <= 0) {
            applySocketFallback(connectionId, builder);
        }

        // 版本 & PID
        builder.version(exec(connectionId, "nginx -v 2>&1").trim());
        builder.pid(exec(connectionId, "cat /var/run/nginx.pid 2>/dev/null || pgrep -o nginx 2>/dev/null").trim());

        // 运行状态
        String running = exec(connectionId, "pgrep -x nginx > /dev/null 2>&1 && echo running || echo stopped");
        builder.running("running".equals(running.trim()));

        // 配置检查
        String configTest = exec(connectionId, "nginx -t 2>&1");
        builder.configOk(configTest != null && configTest.contains("successful"));

        return builder.build();
    }

    @Override
    public List<NginxSiteTraffic> siteTraffic(String connectionId, int topN) {
        List<SiteLogSource> sources = collectSiteLogSources(connectionId);
        if (sources.isEmpty()) {
            return Collections.emptyList();
        }
        List<NginxSiteTraffic> result = new ArrayList<>();
        for (SiteLogSource source : sources) {
            String out = exec(connectionId, "tail -n 20000 " + shellQuote(source.logPath()) + " 2>/dev/null");
            List<AccessLogEntry> entries = parseAccessLogEntries(out);
            if (entries.isEmpty()) {
                continue;
            }
            result.add(buildSiteTraffic(source, entries, topN));
        }
        return result;
    }

    @Override
    public NginxTrafficTrend trafficTrend(String connectionId, String siteKey, String ip, String granularity) {
        SiteLogSource source = collectSiteLogSources(connectionId).stream()
                .filter(item -> Objects.equals(item.siteKey(), siteKey))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return NginxTrafficTrend.builder()
                    .siteKey(siteKey)
                    .siteName(siteKey)
                    .ip(ip)
                    .granularity(defaultGranularity(granularity))
                    .points(Collections.emptyList())
                    .build();
        }
        String out = exec(connectionId, "tail -n 50000 " + shellQuote(source.logPath()) + " 2>/dev/null");
        List<AccessLogEntry> entries = parseAccessLogEntries(out);
        if (ip != null && !ip.isBlank()) {
            entries = entries.stream().filter(item -> ip.equals(item.ip())).toList();
        }
        return buildTrafficTrend(source, entries, ip, granularity);
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    /**
     * 通过 SSH exec channel 执行命令，返回 stdout+stderr 合并输出。
     */
    private String exec(String connectionId, String command) {
        Session session = getSession(connectionId);
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(System.err);
            InputStream in = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    /**
     * 通过 SSH exec channel 执行命令，逐行回调（流式输出）。
     */
    private String execLines(String connectionId, String command, Consumer<String> lineConsumer) {
        Session session = getSession(connectionId);
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                baos.write(line.getBytes(StandardCharsets.UTF_8));
                baos.write('\n');
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        } finally {
            if (channel != null) channel.disconnect();
        }
    }

    private String readFileViaSftp(String connectionId, String remotePath) {
        Session session = getSession(connectionId);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);
            InputStream is = sftp.get(remotePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("SFTP 读取失败 [" + remotePath + "]: " + e.getMessage(), e);
        } finally {
            if (sftp != null) sftp.disconnect();
        }
    }

    private void writeFileViaSftp(String connectionId, String remotePath, String content) {
        Session session = getSession(connectionId);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            sftp.put(new ByteArrayInputStream(bytes), remotePath, ChannelSftp.OVERWRITE);
        } catch (Exception e) {
            throw new RuntimeException("SFTP 写入失败 [" + remotePath + "]: " + e.getMessage(), e);
        } finally {
            if (sftp != null) sftp.disconnect();
        }
    }

    private void deleteFileViaSftp(String connectionId, String remotePath) {
        Session session = getSession(connectionId);
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT);
            sftp.rm(remotePath);
        } catch (Exception e) {
            throw new RuntimeException("SFTP 删除失败 [" + remotePath + "]: " + e.getMessage(), e);
        } finally {
            if (sftp != null) sftp.disconnect();
        }
    }

    /** 列出远程目录下所有 .conf 文件路径 */
    private List<String> listRemoteConfFiles(String connectionId, String dir) {
        String out = exec(connectionId, "ls " + dir + "*.conf 2>/dev/null");
        List<String> paths = new ArrayList<>();
        if (out == null || out.startsWith("ERROR") || out.isBlank()) return paths;
        for (String line : out.split("\n")) {
            String p = line.trim();
            if (!p.isEmpty()) paths.add(p);
        }
        return paths;
    }

    private NginxConfFile buildConfFile(String connectionId, String path, String label, String type) {
        try {
            String content = readFileViaSftp(connectionId, path);
            List<NginxServerBlock> servers = parseServerBlocks(content, path);
            return NginxConfFile.builder()
                    .path(path).label(label).type(type)
                    .servers(servers).exists(true).build();
        } catch (Exception e) {
            return NginxConfFile.builder()
                    .path(path).label(label).type(type)
                    .servers(Collections.emptyList()).exists(false).build();
        }
    }

    // ── 解析 ──────────────────────────────────────────────────────────────────

    /**
     * 字符扫描法解析配置文件中所有 server{} 块。
     */
    private List<NginxServerBlock> parseServerBlocks(String content, String configPath) {
        List<NginxServerBlock> result = new ArrayList<>();
        if (content == null || content.isBlank()) return result;

        int i = 0;
        int len = content.length();
        while (i < len) {
            // 找 "server" 关键字
            int idx = content.indexOf("server", i);
            if (idx < 0) break;
            // 确认后面跟着空白或 {
            int after = idx + 6;
            while (after < len && Character.isWhitespace(content.charAt(after))) after++;
            if (after >= len || content.charAt(after) != '{') {
                i = idx + 6;
                continue;
            }
            // 提取 server{} 块内容
            int braceStart = after;
            int braceEnd = findMatchingBrace(content, braceStart);
            if (braceEnd < 0) break;
            String blockContent = content.substring(braceStart + 1, braceEnd);
            result.add(parseServerBlock(blockContent, configPath));
            i = braceEnd + 1;
        }
        return result;
    }

    private NginxServerBlock parseServerBlock(String blockContent, String configPath) {
        NginxServerBlock block = new NginxServerBlock();
        block.setConfigPath(configPath);
        block.setListen(extractDirective(blockContent, "listen"));
        block.setServerName(extractDirective(blockContent, "server_name"));
        block.setRoot(extractDirective(blockContent, "root"));
        block.setIndex(extractDirective(blockContent, "index"));
        block.setSslCertificate(extractDirective(blockContent, "ssl_certificate"));
        block.setSslCertificateKey(extractDirective(blockContent, "ssl_certificate_key"));
        block.setAccessLog(extractDirective(blockContent, "access_log"));
        block.setLocations(parseLocationBlocks(blockContent));
        return block;
    }

    private List<NginxLocationBlock> parseLocationBlocks(String serverContent) {
        List<NginxLocationBlock> locations = new ArrayList<>();
        int i = 0;
        int len = serverContent.length();
        while (i < len) {
            int idx = serverContent.indexOf("location", i);
            if (idx < 0) break;
            // 确认前面是行首或空白（避免匹配 proxy_location 等）
            if (idx > 0 && !Character.isWhitespace(serverContent.charAt(idx - 1))) {
                i = idx + 8;
                continue;
            }
            int after = idx + 8;
            // 跳过空白，读取 path（可能有修饰符 = ~ ~* ^~）
            while (after < len && Character.isWhitespace(serverContent.charAt(after))) after++;
            int pathStart = after;
            // 找 {
            int braceIdx = serverContent.indexOf('{', after);
            if (braceIdx < 0) break;
            String pathPart = serverContent.substring(pathStart, braceIdx).trim();
            int braceEnd = findMatchingBrace(serverContent, braceIdx);
            if (braceEnd < 0) break;
            String locContent = serverContent.substring(braceIdx + 1, braceEnd);
            locations.add(parseLocationBlock(pathPart, locContent));
            i = braceEnd + 1;
        }
        return locations;
    }

    private NginxLocationBlock parseLocationBlock(String pathPart, String locContent) {
        NginxLocationBlock loc = new NginxLocationBlock();
        loc.setPath(pathPart);

        String proxyPass = extractDirective(locContent, "proxy_pass");
        String root = extractDirective(locContent, "root");
        String alias = extractDirective(locContent, "alias");
        String returnVal = extractDirective(locContent, "return");

        if (proxyPass != null) {
            loc.setMode("proxy");
            loc.setProxyPass(proxyPass);
            loc.setProxyReadTimeout(extractDirective(locContent, "proxy_read_timeout"));
            loc.setProxyConnectTimeout(extractDirective(locContent, "proxy_connect_timeout"));
            loc.setProxySetHeader(extractAllDirectives(locContent, "proxy_set_header"));
            String buffering = extractDirective(locContent, "proxy_buffering");
            if (buffering != null) loc.setProxyBuffering("on".equalsIgnoreCase(buffering));
        } else if (returnVal != null) {
            loc.setMode("redirect");
            String[] parts = returnVal.split("\\s+", 2);
            loc.setReturnCode(parts[0]);
            loc.setReturnUrl(parts.length > 1 ? parts[1] : null);
        } else if (root != null || alias != null) {
            loc.setMode("static");
            loc.setRoot(root);
            loc.setAlias(alias);
            loc.setTryFiles(extractDirective(locContent, "try_files"));
            loc.setExpires(extractDirective(locContent, "expires"));
            String autoindex = extractDirective(locContent, "autoindex");
            if (autoindex != null) loc.setAutoindex("on".equalsIgnoreCase(autoindex));
            loc.setIndex(extractDirective(locContent, "index"));
        } else {
            loc.setMode("custom");
            loc.setRawDirectives(locContent.trim());
        }

        String limitReq = extractDirective(locContent, "limit_req");
        if (limitReq != null) {
            String zone = extractDirectiveValue(limitReq, "zone");
            String burst = extractDirectiveValue(limitReq, "burst");
            if (zone != null) {
                loc.setLimitReqZone(zone);
            }
            if (burst != null) {
                loc.setLimitReqBurst(burst);
            }
            loc.setLimitReqNoDelay(limitReq.contains("nodelay"));
        }
        loc.setAllowList(extractAllDirectives(locContent, "allow"));
        loc.setDenyList(extractAllDirectives(locContent, "deny"));
        loc.setAddHeader(extractAllDirectives(locContent, "add_header"));
        return loc;
    }

    /** 正则提取单个指令值，如 "listen 80;" -> "80" */
    private String extractDirective(String content, String directive) {
        Pattern p = Pattern.compile("(?m)^\\s*" + Pattern.quote(directive) + "\\s+([^;{]+);");
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    /** 提取所有同名指令值列表 */
    private List<String> extractAllDirectives(String content, String directive) {
        List<String> list = new ArrayList<>();
        Pattern p = Pattern.compile("(?m)^\\s*" + Pattern.quote(directive) + "\\s+([^;{]+);");
        Matcher m = p.matcher(content);
        while (m.find()) list.add(m.group(1).trim());
        return list.isEmpty() ? null : list;
    }

    // ── 序列化 ────────────────────────────────────────────────────────────────

    private String renderServers(List<NginxServerBlock> servers) {
        StringBuilder sb = new StringBuilder();
        for (NginxServerBlock s : servers) {
            sb.append("server {\n");
            appendDirective(sb, "    ", "listen", s.getListen());
            appendDirective(sb, "    ", "server_name", s.getServerName());
            appendDirective(sb, "    ", "root", s.getRoot());
            appendDirective(sb, "    ", "index", s.getIndex());
            appendDirective(sb, "    ", "ssl_certificate", s.getSslCertificate());
            appendDirective(sb, "    ", "ssl_certificate_key", s.getSslCertificateKey());
            appendDirective(sb, "    ", "access_log", s.getAccessLog());
            if (s.getLocations() != null) {
                for (NginxLocationBlock loc : s.getLocations()) {
                    sb.append(renderLocation(loc));
                }
            }
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    private String renderLocation(NginxLocationBlock loc) {
        StringBuilder sb = new StringBuilder();
        sb.append("    location ").append(loc.getPath()).append(" {\n");
        switch (loc.getMode() == null ? "custom" : loc.getMode()) {
            case "proxy":
                appendDirective(sb, "        ", "proxy_pass", loc.getProxyPass());
                appendDirective(sb, "        ", "proxy_read_timeout", loc.getProxyReadTimeout());
                appendDirective(sb, "        ", "proxy_connect_timeout", loc.getProxyConnectTimeout());
                if (loc.getProxySetHeader() != null) {
                    for (String h : loc.getProxySetHeader())
                        sb.append("        proxy_set_header ").append(h).append(";\n");
                }
                if (loc.getProxyBuffering() != null)
                    sb.append("        proxy_buffering ").append(loc.getProxyBuffering() ? "on" : "off").append(";\n");
                break;
            case "redirect":
                String ret = loc.getReturnCode() != null ? loc.getReturnCode() : "301";
                if (loc.getReturnUrl() != null) ret += " " + loc.getReturnUrl();
                sb.append("        return ").append(ret).append(";\n");
                break;
            case "static":
                appendDirective(sb, "        ", "root", loc.getRoot());
                appendDirective(sb, "        ", "alias", loc.getAlias());
                appendDirective(sb, "        ", "try_files", loc.getTryFiles());
                appendDirective(sb, "        ", "expires", loc.getExpires());
                appendDirective(sb, "        ", "index", loc.getIndex());
                if (loc.getAutoindex() != null)
                    sb.append("        autoindex ").append(loc.getAutoindex() ? "on" : "off").append(";\n");
                break;
            default:
                if (loc.getRawDirectives() != null) sb.append(loc.getRawDirectives()).append("\n");
        }
        appendLimitReqDirective(sb, loc);
        appendRepeatedDirective(sb, "        ", "allow", loc.getAllowList());
        appendRepeatedDirective(sb, "        ", "deny", loc.getDenyList());
        if (loc.getAddHeader() != null) {
            for (String h : loc.getAddHeader())
                sb.append("        add_header ").append(h).append(";\n");
        }
        sb.append("    }\n");
        return sb.toString();
    }

    /**
     * 将原配置文件中所有 server{} 块替换为新渲染内容。
     * 保留 server{} 块之外的内容（events、http 外层等）。
     */
    private String replaceServerBlocksInConfig(String original, String renderedServers) {
        if (original == null || original.isBlank()) return renderedServers;
        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = original.length();
        boolean appended = false;
        while (i < len) {
            int idx = original.indexOf("server", i);
            if (idx < 0) {
                result.append(original, i, len);
                break;
            }
            int after = idx + 6;
            while (after < len && Character.isWhitespace(original.charAt(after))) after++;
            if (after >= len || original.charAt(after) != '{') {
                result.append(original, i, idx + 6);
                i = idx + 6;
                continue;
            }
            // 保留 server 块之前的内容
            result.append(original, i, idx);
            // 跳过整个 server{} 块
            int braceEnd = findMatchingBrace(original, after);
            if (braceEnd < 0) {
                result.append(original, idx, len);
                i = len;
                break;
            }
            if (!appended) {
                result.append(renderedServers);
                appended = true;
            }
            i = braceEnd + 1;
            // 跳过紧跟的换行
            while (i < len && original.charAt(i) == '\n') i++;
        }
        if (!appended) result.append(renderedServers);
        return result.toString();
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────────

    /** 找到与 pos 处 '{' 匹配的 '}' 的位置 */
    private int findMatchingBrace(String s, int pos) {
        int depth = 0;
        boolean inString = false;
        for (int i = pos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' && !inString) {
                // 跳过注释行
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private void appendDirective(StringBuilder sb, String indent, String name, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(indent).append(name).append(" ").append(value).append(";\n");
        }
    }

    private void appendRepeatedDirective(StringBuilder sb, String indent, String name, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                sb.append(indent).append(name).append(" ").append(value).append(";\n");
            }
        }
    }

    private void appendLimitReqDirective(StringBuilder sb, NginxLocationBlock loc) {
        if (loc == null || loc.getLimitReqZone() == null || loc.getLimitReqZone().isBlank()) {
            return;
        }
        StringBuilder value = new StringBuilder("zone=").append(loc.getLimitReqZone().trim());
        if (loc.getLimitReqBurst() != null && !loc.getLimitReqBurst().isBlank()) {
            value.append(" burst=").append(loc.getLimitReqBurst().trim());
        }
        if (Boolean.TRUE.equals(loc.getLimitReqNoDelay())) {
            value.append(" nodelay");
        }
        sb.append("        limit_req ").append(value).append(";\n");
    }

    private String extractDirectiveValue(String directive, String key) {
        if (directive == null || directive.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile("(?<!\\S)" + Pattern.quote(key) + "=([^\\s]+)");
        Matcher m = p.matcher(directive);
        return m.find() ? m.group(1).trim() : null;
    }

    private Session getSession(String connectionId) {
        Session session = sessions.get(connectionId);
        if (session == null || !session.isConnected()) {
            throw new RuntimeException("连接不存在或已断开: " + connectionId);
        }
        return session;
    }

    // ── 脚本执行 ──────────────────────────────────────────────────────────────

    @Override
    public String executeScript(String connectionId, String script) {
        String tmpFile = "/tmp/panel_script_" + UUID.randomUUID().toString().replace("-", "") + ".sh";
        try {
            writeFileViaSftp(connectionId, tmpFile, script);
            String output = exec(connectionId, "chmod +x " + tmpFile + " && bash " + tmpFile + " 2>&1");
            exec(connectionId, "rm -f " + tmpFile);
            return output;
        } catch (Exception e) {
            exec(connectionId, "rm -f " + tmpFile);
            throw new RuntimeException("脚本执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeScript(String connectionId, String script, Consumer<String> lineConsumer) {
        String tmpFile = "/tmp/panel_script_" + UUID.randomUUID().toString().replace("-", "") + ".sh";
        try {
            writeFileViaSftp(connectionId, tmpFile, script);
            String output = execLines(connectionId, "chmod +x " + tmpFile + " && bash " + tmpFile + " 2>&1", lineConsumer);
            exec(connectionId, "rm -f " + tmpFile);
            return output;
        } catch (Exception e) {
            exec(connectionId, "rm -f " + tmpFile);
            throw new RuntimeException("脚本执行失败: " + e.getMessage(), e);
        }
    }

    private void parseStubStatus(String out, NginxDashboardStats.NginxDashboardStatsBuilder builder) {
        if (out == null || out.isBlank() || out.startsWith("ERROR")) {
            builder.stubStatusAvailable(false);
            return;
        }
        try {
            // Active connections: 1
            Matcher m1 = Pattern.compile("Active connections:\\s*(\\d+)").matcher(out);
            if (m1.find()) builder.activeConnections(Long.parseLong(m1.group(1)));
            // server accepts handled requests
            Matcher m2 = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+)").matcher(out);
            if (m2.find()) {
                builder.accepts(Long.parseLong(m2.group(1)));
                builder.handled(Long.parseLong(m2.group(2)));
                builder.requests(Long.parseLong(m2.group(3)));
            }
            // Reading: 0 Writing: 1 Waiting: 0
            Matcher m3 = Pattern.compile("Reading:\\s*(\\d+)\\s+Writing:\\s*(\\d+)\\s+Waiting:\\s*(\\d+)").matcher(out);
            if (m3.find()) {
                builder.reading(Long.parseLong(m3.group(1)));
                builder.writing(Long.parseLong(m3.group(2)));
                builder.waiting(Long.parseLong(m3.group(3)));
            }
            builder.stubStatusAvailable(true);
        } catch (Exception ignored) {
            builder.stubStatusAvailable(false);
        }
    }

    private Map<String, Long> parseTopIps(String out) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (out == null || out.isBlank()) return map;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) {
                try {
                    map.put(parts[1].trim(), Long.parseLong(parts[0].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    private void applyAccessLogStats(
            NginxDashboardStats.NginxDashboardStatsBuilder builder,
            List<AccessLogEntry> entries,
            int topN) {
        if (entries.isEmpty()) {
            builder.topIps(Collections.emptyMap());
            builder.recentRequests(Collections.emptyList());
            builder.analyzedLines(0);
            builder.uniqueIps(0);
            builder.requestsPerMinute(0.0);
            builder.dailyRequests(0);
            builder.dailyUniqueIps(0);
            builder.weeklyRequests(0);
            builder.weeklyUniqueIps(0);
            builder.monthlyRequests(0);
            builder.monthlyUniqueIps(0);
            return;
        }

        Map<String, Long> ipCounter = new HashMap<>();
        Set<String> uniqueIps = new HashSet<>();
        List<String> recentRequests = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dailyThreshold = now.minusDays(1);
        OffsetDateTime weeklyThreshold = now.minusDays(7);
        OffsetDateTime monthlyThreshold = now.minusDays(30);
        OffsetDateTime minuteThreshold = now.minusMinutes(1);
        Set<String> dailyIps = new HashSet<>();
        Set<String> weeklyIps = new HashSet<>();
        Set<String> monthlyIps = new HashSet<>();
        long dailyRequests = 0;
        long weeklyRequests = 0;
        long monthlyRequests = 0;
        long minuteRequests = 0;

        for (AccessLogEntry entry : entries) {
            uniqueIps.add(entry.ip());
            ipCounter.merge(entry.ip(), 1L, Long::sum);
            if (recentRequests.size() >= 20) {
                recentRequests.remove(0);
            }
            recentRequests.add(entry.rawLine());
            if (entry.timestamp() == null) {
                continue;
            }
            if (!entry.timestamp().isBefore(dailyThreshold)) {
                dailyRequests++;
                dailyIps.add(entry.ip());
            }
            if (!entry.timestamp().isBefore(weeklyThreshold)) {
                weeklyRequests++;
                weeklyIps.add(entry.ip());
            }
            if (!entry.timestamp().isBefore(monthlyThreshold)) {
                monthlyRequests++;
                monthlyIps.add(entry.ip());
            }
            if (!entry.timestamp().isBefore(minuteThreshold)) {
                minuteRequests++;
            }
        }

        Map<String, Long> sortedTopIps = ipCounter.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(Math.max(1, topN))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);

        builder.topIps(sortedTopIps);
        builder.recentRequests(recentRequests);
        builder.analyzedLines(entries.size());
        builder.uniqueIps(uniqueIps.size());
        builder.requestsPerMinute(minuteRequests);
        builder.dailyRequests(dailyRequests);
        builder.dailyUniqueIps(dailyIps.size());
        builder.weeklyRequests(weeklyRequests);
        builder.weeklyUniqueIps(weeklyIps.size());
        builder.monthlyRequests(monthlyRequests);
        builder.monthlyUniqueIps(monthlyIps.size());
    }

    private List<AccessLogEntry> parseAccessLogEntries(String out) {
        if (out == null || out.isBlank() || out.startsWith("ERROR")) {
            return Collections.emptyList();
        }
        List<AccessLogEntry> entries = new ArrayList<>();
        Pattern pattern = Pattern.compile("^(\\S+)\\s+\\S+\\s+\\S+\\s+\\[([^\\]]+)]\\s+\"([^\"]*)\"\\s+(\\d{3})\\b.*$");
        for (String line : out.split("\n")) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = pattern.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String ip = matcher.group(1);
            OffsetDateTime timestamp = null;
            try {
                timestamp = OffsetDateTime.parse(matcher.group(2), ACCESS_LOG_TIME);
            } catch (DateTimeParseException ignored) {
                // ignore malformed timestamps
            }
            int statusCode = 0;
            try {
                statusCode = Integer.parseInt(matcher.group(4));
            } catch (Exception ignored) {
                // ignore malformed statuses
            }
            entries.add(new AccessLogEntry(ip, timestamp, statusCode, trimmed, matcher.group(3)));
        }
        return entries;
    }

    private List<SiteLogSource> collectSiteLogSources(String connectionId) {
        Map<String, SiteLogSourceBuilder> sourceMap = new LinkedHashMap<>();
        String globalAccessLogPath = resolveConfiguredAccessLogPath(connectionId, DEFAULT_NGINX_CONF);
        for (NginxConfFile file : listConfFiles(connectionId)) {
            if (file.getServers() == null || file.getServers().isEmpty()) {
                continue;
            }
            String inheritedAccessLogPath = resolveConfiguredAccessLogPath(connectionId, file.getPath());
            for (NginxServerBlock server : file.getServers()) {
                String logPath = resolveAccessLogPath(connectionId, file.getPath(), server.getAccessLog());
                if (logPath == null) {
                    logPath = inheritedAccessLogPath != null ? inheritedAccessLogPath : globalAccessLogPath;
                }
                if (logPath == null) {
                    continue;
                }
                String key = buildSiteKey(logPath);
                SiteLogSourceBuilder builder = sourceMap.computeIfAbsent(key, item -> new SiteLogSourceBuilder());
                builder.siteKey(key);
                builder.logPath(logPath);
                builder.serverNames(serverNames(server));
                if (builder.siteName() == null || builder.siteName().isBlank()) {
                    builder.siteName(siteDisplayName(server, file, logPath));
                }
            }
        }
        return sourceMap.values().stream()
                .map(SiteLogSourceBuilder::build)
                .sorted(Comparator.comparingLong(SiteLogSource::order).reversed())
                .toList();
    }

    private NginxSiteTraffic buildSiteTraffic(SiteLogSource source, List<AccessLogEntry> entries, int topN) {
        Map<String, Long> ipCounter = new HashMap<>();
        long minuteRequests = 0;
        long minuteSuccessRequests = 0;
        Set<String> uniqueIps = new HashSet<>();
        OffsetDateTime minuteThreshold = OffsetDateTime.now().minusMinutes(1);
        for (AccessLogEntry entry : entries) {
            uniqueIps.add(entry.ip());
            ipCounter.merge(entry.ip(), 1L, Long::sum);
            if (entry.timestamp() != null && !entry.timestamp().isBefore(minuteThreshold)) {
                minuteRequests++;
                if (entry.statusCode() >= 200 && entry.statusCode() < 500) {
                    minuteSuccessRequests++;
                }
            }
        }
        long totalRequests = entries.size();
        List<NginxSiteIpTraffic> topIps = ipCounter.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(Math.max(1, topN))
                .map(entry -> NginxSiteIpTraffic.builder()
                        .ip(entry.getKey())
                        .requests(entry.getValue())
                        .percent(totalRequests > 0 ? entry.getValue() * 100.0 / totalRequests : 0.0)
                        .build())
                .toList();
        return NginxSiteTraffic.builder()
                .siteKey(source.siteKey())
                .siteName(source.siteName())
                .logPath(source.logPath())
                .requests(totalRequests)
                .uniqueIps(uniqueIps.size())
                .qps(minuteRequests / 60.0)
                .tps(minuteSuccessRequests / 60.0)
                .topIps(topIps)
                .build();
    }

    private NginxTrafficTrend buildTrafficTrend(
            SiteLogSource source,
            List<AccessLogEntry> entries,
            String ip,
            String granularity) {
        String normalized = defaultGranularity(granularity);
        Map<OffsetDateTime, Long> buckets = new TreeMap<>();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = switch (normalized) {
            case "hour" -> now.minusHours(24);
            case "day" -> now.minusDays(30);
            default -> now.minusHours(1);
        };
        for (AccessLogEntry entry : entries) {
            if (entry.timestamp() == null || entry.timestamp().isBefore(threshold)) {
                continue;
            }
            OffsetDateTime bucket = truncateTimestamp(entry.timestamp(), normalized);
            buckets.merge(bucket, 1L, Long::sum);
        }
        List<NginxTrafficPoint> points = new ArrayList<>();
        OffsetDateTime cursor = truncateTimestamp(threshold, normalized);
        OffsetDateTime end = truncateTimestamp(now, normalized);
        while (!cursor.isAfter(end)) {
            points.add(NginxTrafficPoint.builder()
                    .label(formatTrafficLabel(cursor, normalized))
                    .requests(buckets.getOrDefault(cursor, 0L))
                    .build());
            cursor = advanceTimestamp(cursor, normalized);
        }
        return NginxTrafficTrend.builder()
                .siteKey(source.siteKey())
                .siteName(source.siteName())
                .ip(ip)
                .granularity(normalized)
                .points(points)
                .build();
    }

    private String normalizeAccessLogPath(String directive) {
        if (directive == null) {
            return null;
        }
        String value = directive.trim();
        if (value.isEmpty() || "off".equalsIgnoreCase(value)) {
            return null;
        }
        String[] parts = value.split("\\s+");
        String path = parts.length > 0 ? parts[0].trim() : value;
        return path.isBlank() ? null : path;
    }

    private String resolveConfiguredAccessLogPath(String connectionId, String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return null;
        }
        try {
            String content = readFileViaSftp(connectionId, configPath);
            return resolveAccessLogPath(connectionId, configPath, extractOuterDirective(content, "access_log"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveAccessLogPath(String connectionId, String configPath, String directive) {
        String normalized = normalizeAccessLogPath(directive);
        if (normalized == null || normalized.contains("$") || normalized.startsWith("syslog:")) {
            return null;
        }
        if (normalized.startsWith("/")) {
            return normalized;
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);
        String prefix = resolveNginxPrefix(connectionId);
        if (prefix != null && !prefix.isBlank()) {
            candidates.add(joinUnixPath(prefix, normalized));
        }
        String configDir = resolveConfigDirectory(configPath);
        if (configDir != null) {
            candidates.add(joinUnixPath(configDir, normalized));
        }
        candidates.add(joinUnixPath("/var/log/nginx", normalized));
        for (String candidate : candidates) {
            if (remoteFileExists(connectionId, candidate)) {
                return candidate;
            }
        }
        return candidates.stream().findFirst().orElse(null);
    }

    private String extractOuterDirective(String content, String directive) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(directive) + "\\s+([^;{]+);");
        int braceDepth = 0;
        for (String rawLine : content.split("\n")) {
            String line = rawLine.replaceFirst("#.*$", "");
            Matcher matcher = pattern.matcher(line);
            if (braceDepth <= 1 && matcher.find()) {
                return matcher.group(1).trim();
            }
            braceDepth += countChar(line, '{');
            braceDepth -= countChar(line, '}');
            if (braceDepth < 0) {
                braceDepth = 0;
            }
        }
        return null;
    }

    private int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private String resolveNginxPrefix(String connectionId) {
        String out = exec(connectionId, "nginx -V 2>&1");
        Matcher matcher = Pattern.compile("--prefix=([^\\s]+)").matcher(out == null ? "" : out);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String resolveConfigDirectory(String configPath) {
        if (configPath == null) {
            return null;
        }
        int idx = configPath.lastIndexOf('/');
        return idx > 0 ? configPath.substring(0, idx) : null;
    }

    private String joinUnixPath(String base, String path) {
        if (base == null || base.isBlank()) {
            return path;
        }
        if (path == null || path.isBlank()) {
            return base;
        }
        return base.endsWith("/") ? base + path : base + "/" + path;
    }

    private boolean remoteFileExists(String connectionId, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String out = exec(connectionId, "test -f " + shellQuote(path) + " && echo 1 || echo 0");
        return out != null && out.trim().endsWith("1");
    }

    private String shellQuote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\"'\"'")) + "'";
    }

    private String buildSiteKey(String logPath) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(logPath.getBytes(StandardCharsets.UTF_8));
    }

    private String siteDisplayName(NginxServerBlock server, NginxConfFile file, String logPath) {
        String serverName = serverNames(server);
        if (serverName != null && !serverName.isBlank()) {
            return serverName;
        }
        if (file != null && file.getLabel() != null && !file.getLabel().isBlank()) {
            return file.getLabel();
        }
        return logPath.substring(logPath.lastIndexOf('/') + 1);
    }

    private String serverNames(NginxServerBlock server) {
        if (server == null || server.getServerName() == null) {
            return null;
        }
        String[] names = server.getServerName().trim().split("\\s+");
        if (names.length == 0) {
            return null;
        }
        return Arrays.stream(names)
                .filter(name -> !name.isBlank() && !"_".equals(name))
                .distinct()
                .limit(3)
                .collect(java.util.stream.Collectors.joining(" / "));
    }

    private String defaultGranularity(String granularity) {
        String value = granularity == null ? "" : granularity.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "hour", "day" -> value;
            default -> "minute";
        };
    }

    private OffsetDateTime truncateTimestamp(OffsetDateTime timestamp, String granularity) {
        return switch (granularity) {
            case "hour" -> timestamp.withMinute(0).withSecond(0).withNano(0);
            case "day" -> timestamp.withHour(0).withMinute(0).withSecond(0).withNano(0);
            default -> timestamp.withSecond(0).withNano(0);
        };
    }

    private OffsetDateTime advanceTimestamp(OffsetDateTime timestamp, String granularity) {
        return switch (granularity) {
            case "hour" -> timestamp.plusHours(1);
            case "day" -> timestamp.plusDays(1);
            default -> timestamp.plusMinutes(1);
        };
    }

    private String formatTrafficLabel(OffsetDateTime timestamp, String granularity) {
        DateTimeFormatter formatter = switch (granularity) {
            case "hour" -> DateTimeFormatter.ofPattern("MM-dd HH:00");
            case "day" -> DateTimeFormatter.ofPattern("MM-dd");
            default -> DateTimeFormatter.ofPattern("HH:mm");
        };
        return timestamp.format(formatter);
    }

    private void applySocketFallback(String connectionId, NginxDashboardStats.NginxDashboardStatsBuilder builder) {
        String stateOut = exec(connectionId,
                "sh -c \"netstat -ant 2>/dev/null | awk '$4 ~ /:(80|443)$/ {print $6}'\"");
        if (stateOut == null || stateOut.isBlank() || stateOut.startsWith("ERROR")) {
            return;
        }
        long activeConnections = 0;
        long reading = 0;
        long writing = 0;
        long waiting = 0;
        for (String line : stateOut.split("\n")) {
            String state = line == null ? "" : line.trim().toUpperCase(Locale.ROOT);
            if (state.isEmpty()) {
                continue;
            }
            switch (state) {
                case "ESTABLISHED" -> activeConnections++;
                case "SYN_RECV" -> reading++;
                case "FIN_WAIT1", "FIN_WAIT2", "CLOSING", "LAST_ACK" -> writing++;
                case "TIME_WAIT", "CLOSE_WAIT" -> waiting++;
                default -> {
                    // ignore
                }
            }
        }
        if (activeConnections > 0) {
            builder.activeConnections(activeConnections);
        }
        if (reading > 0) {
            builder.reading(reading);
        }
        if (writing > 0) {
            builder.writing(writing);
        }
        if (waiting > 0) {
            builder.waiting(waiting);
        }
    }

    private record AccessLogEntry(String ip, OffsetDateTime timestamp, int statusCode, String rawLine, String request) {
    }

    private record SiteLogSource(String siteKey, String siteName, String logPath, int order) {
    }

    private static final class SiteLogSourceBuilder {
        private String siteKey;
        private String siteName;
        private String logPath;
        private int order;

        SiteLogSourceBuilder siteKey(String siteKey) {
            this.siteKey = siteKey;
            return this;
        }

        SiteLogSourceBuilder siteName(String siteName) {
            this.siteName = siteName;
            return this;
        }

        String siteName() {
            return siteName;
        }

        SiteLogSourceBuilder logPath(String logPath) {
            this.logPath = logPath;
            return this;
        }

        SiteLogSourceBuilder serverNames(String serverNames) {
            this.order = (serverNames == null ? 0 : serverNames.length()) + (logPath == null ? 0 : logPath.length());
            if (this.siteName == null || this.siteName.isBlank()) {
                this.siteName = serverNames;
            }
            return this;
        }

        SiteLogSource build() {
            return new SiteLogSource(siteKey, siteName == null ? logPath : siteName, logPath, order);
        }
    }
}
