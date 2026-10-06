package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.DiskUsageNodeVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.RowNormalizer;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Agent HTTP 系统信息 Provider。
 *
 * <p>面向安装了 monitor-agent 的主机（采集方式 collect_mode=AGENT），通过 Agent 的 19876 HTTP 端口获取信息。
 * Agent 的 JSON 报文只在本类内部流转，出参一律是 SPI 的强类型视图。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "agent", order = 300)
public class AgentProvider implements SystemInfoProvider {

    /**
     * HTTP 客户端（连接超时 5s）
    */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /**
     * 复用的 JSON 解析器
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 主机 ID 到真实平台的缓存，避免每次拼命令都向 Agent 取一次 /info
     */
    private static final Map<Long, String> PLATFORM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Agent 侧命令执行超时（秒），由 Agent 到点自行终止命令并回原因
     */
    private static final int AGENT_EXEC_TIMEOUT_SECONDS = 25;

    /**
     * /exec 的 HTTP 读超时（秒）：须大于 Agent 侧超时，否则拿到的是本地读超时而非 Agent 的原因
     */
    private static final Duration AGENT_EXEC_READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 拼接 Agent 基础地址。
     *
     * @param host 目标主机
     * @return Agent 地址
     */
    private String agentUrl(MsServerHost host) {
        Integer port = host.getServerHostAgentPort();
        return "http://" + host.getServerHostAddress() + ":" + (port == null || port <= 0 ? 19876 : port);
    }

    /**
     * 采集方式是否为 AGENT。
     *
     * @param host 目标主机
     * @return 采集方式为 AGENT 时返回 true
     */
    @Override
    public boolean supports(MsServerHost host) {
        return host != null
                && "AGENT".equalsIgnoreCase(host.getServerHostCollectMode())
                && host.getServerHostAgentInstalled() != null
                && host.getServerHostAgentInstalled() == 1;
    }

    /**
     * Agent 主机的真实平台：优先取 Agent 自报的 os，探测不到再回退台账记录。
     *
     * @param host 目标主机
     * @return WINDOWS / LINUX / MACOS，无法判定时返回空串
     */
    @Override
    public String platform(MsServerHost host) {
        Long id = host == null ? null : host.getId();
        if (id == null) {
            return "";
        }
        String cached = PLATFORM_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        String platform = "";
        try {
            HostInfoVO info = hostInfo(host);
            platform = Platforms.normalize(info == null ? null : info.getOs());
        } catch (Exception e) {
            log.warn("[agent] 平台探测失败 host={}, 回退台账 osType: {}", id, e.getMessage());
        }
        if (!platform.isEmpty()) {
            PLATFORM_CACHE.put(id, platform);
            return platform;
        }
        return SystemInfoProvider.super.platform(host);
    }

    /**
     * 发起 GET 请求并把 JSON 对象响应反序列化为 Map。
     *
     * @param url 请求地址
     * @return 反序列化结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String url) {
        try {
            return MAPPER.readValue(body(url), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Agent 响应无法解析为对象: " + e.getMessage(), e);
        }
    }

    /**
     * 发起 GET 并取回响应正文。
     *
     * <p>失败时抛异常而非返回 null：调用方吞掉 null 会让前端把"取不到"误显示成"没有数据"。</p>
     *
     * @param url 请求地址
     * @return 响应正文
     */
    private String body(String url) {
        return body(url, Duration.ofSeconds(15));
    }

    /**
     * 发起 GET 并按给定读超时取回响应正文。
     *
     * @param url     请求地址
     * @param timeout 读超时
     * @return 响应正文
     */
    private String body(String url, Duration timeout) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                return r.body();
            }
            throw new IllegalStateException("Agent 返回 HTTP " + r.statusCode() + brief(r.body()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Agent 无响应 " + url + ": "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    /**
     * 把 Agent 的错误正文缀到异常文案里，长文本截断。
     *
     * @param body 响应正文，可为 null
     * @return 形如「：原因」的后缀，正文为空时返回空串
     */
    private static String brief(String body) {
        if (body == null) {
            return "";
        }
        String one = body.replaceAll("\\s+", " ").trim();
        if (one.isEmpty()) {
            return "";
        }
        return "：" + (one.length() > 200 ? one.substring(0, 200) + "..." : one);
    }

    /**
     * 取列表型响应。
     *
     * <p>Agent 的 {@code /processes}、{@code /services} 是分页对象，其余是裸数组，这里统一抽出行。</p>
     *
     * @param url 请求地址
     * @return 行列表，永不为 null
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(String url) {
        Object parsed;
        try {
            parsed = MAPPER.readValue(body(url), Object.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Agent 响应解析失败: " + e.getMessage(), e);
        }
        if (parsed instanceof List) {
            return (List<Map<String, Object>>) parsed;
        }
        if (parsed instanceof Map) {
            Object list = ((Map<String, Object>) parsed).get("list");
            if (list instanceof List) {
                return (List<Map<String, Object>>) list;
            }
        }
        throw new IllegalStateException("Agent 返回结构不是列表: " + String.valueOf(parsed).substring(0, Math.min(80, String.valueOf(parsed).length())));
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        Map<String, Object> info = getMap(agentUrl(host) + "/info");
        // /info 只含静态台账字段（无 CPU% / 已用内存），合并 /metrics 的实时用量，
        // 否则画像顶部 CPU、内存会显示 0（磁盘/进程数因另走 /disks、/processes 看似正常，更具迷惑性）
        try {
            info.putAll(getMap(agentUrl(host) + "/metrics"));
        } catch (Exception e) {
            log.warn("[agent] 合并 /metrics 失败 hostId={}: {}", host.getId(), e.getMessage());
        }
        return RowNormalizer.hostInfo(info);
    }

    @Override
    public List<ProcessRowVO> processes(MsServerHost h, int p, int ps) {
        return RowNormalizer.processes(getList(agentUrl(h) + "/processes?page=" + p + "&pageSize=" + ps));
    }

    @Override
    public List<ServiceRowVO> services(MsServerHost h, int p, int ps) {
        return RowNormalizer.services(getList(agentUrl(h) + "/services?page=" + p + "&pageSize=" + ps));
    }

    @Override
    public List<DiskRowVO> disks(MsServerHost h) {
        return RowNormalizer.disks(getList(agentUrl(h) + "/disks"));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Agent 在被监控主机本地递归，一次 HTTP 返回整棵占用树，避免平台侧逐目录经 HTTP 递归
     * 放大成数千次串行往返；根节点的卷总容量 / 可用容量由 Agent 直接回填。</p>
     */
    @Override
    public DiskUsageNodeVO diskUsage(MsServerHost h, String path, int depth) {
        int d = depth <= 0 ? 4 : Math.min(depth, 8);
        String url = agentUrl(h) + "/files/usage?path=" + urlEncode(path) + "&depth=" + d;
        try {
            return MAPPER.readValue(body(url, Duration.ofSeconds(120)), DiskUsageNodeVO.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Agent 磁盘占用解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost h) {
        return RowNormalizer.interfaces(getList(agentUrl(h) + "/network"));
    }

    @Override
    public List<NetProcessRowVO> netProcesses(MsServerHost h) {
        return RowNormalizer.netProcs(getList(agentUrl(h) + "/netproc"));
    }

    @Override
    public List<FileEntryVO> files(MsServerHost h, String path) {
        return RowNormalizer.files(getList(agentUrl(h) + "/files?path=" + urlEncode(path)));
    }

    @Override
    public boolean uploadFile(MsServerHost h, String remotePath, InputStream d, long s) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl(h) + "/upload?path=" + urlEncode(remotePath)))
                    .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> d))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                // Agent 已把失败原因写在响应体里，折成 false 就只剩「上传失败: 路径」
                throw new IllegalStateException("Agent 上传失败(HTTP " + r.statusCode() + ")" + brief(r.body()));
            }
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Agent 上传无响应: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost h, String remotePath) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl(h) + "/download?path=" + urlEncode(remotePath)))
                    .GET().build();
            HttpResponse<java.io.InputStream> r = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            // 下载失败返回 null，由调用方判断流是否可用
            return null;
        }
    }

    @Override
    public String exec(MsServerHost h, String cmd) {
        String url = agentUrl(h) + "/exec?cmd=" + urlEncode(cmd) + "&timeout=" + AGENT_EXEC_TIMEOUT_SECONDS;
        return unwrapExec(body(url, AGENT_EXEC_READ_TIMEOUT));
    }

    /**
     * Agent 的 /exec 把命令输出包在 {@code {"exitCode":0,"output":"..."}} 信封里，
     * 这里取出 output，使 exec 的返回与本机 / SSH 通道一致（均为命令 stdout）。
     *
     * <p>退出码非 0 时必须补出失败探针行：命令自己把 shell 退出掉（{@code exit 3}）时，
     * {@link ShellSupport} 拼在行尾的探针来不及执行，只回 stdout 就成了「什么都没做但成功」。</p>
     *
     * @param body Agent 响应正文
     * @return 命令 stdout；非信封结构时原样返回
     */
    @SuppressWarnings("unchecked")
    private static String unwrapExec(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            try {
                Object parsed = MAPPER.readValue(trimmed, Object.class);
                if (parsed instanceof Map) {
                    Map<String, Object> envelope = (Map<String, Object>) parsed;
                    Object output = envelope.get("output");
                    String out = output == null ? "" : output.toString();
                    Object code = envelope.get("exitCode");
                    if (code instanceof Number && ((Number) code).intValue() != 0) {
                        String mark = ShellSupport.FAIL_MARK + ":Agent 退出码 " + ((Number) code).intValue();
                        return out.isBlank() ? mark : out + "\r\n" + mark;
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // 不是信封则按原始 stdout 处理
            }
        }
        return body;
    }

    /**
     * URL 查询参数编码。
     *
     * <p>Agent 端用 {@code URI.getRawQuery()} 手工解析，因此必须预先把空格、&amp;、中文等编成百分号形式。</p>
     *
     * @param text 原始值，可为 null
     * @return 编码结果；null 返回空串
     */
    private static String urlEncode(String text) {
        if (text == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }

    @Override
    public List<EnvVarVO> env(MsServerHost h) {
        return RowNormalizer.envVars(getList(agentUrl(h) + "/env"));
    }

    @Override
    public boolean mkdir(MsServerHost h, String path) {
        // 走 Agent 专用文件端点，跨平台由 Agent 用 File 实现
        return fileOk(h, "/files/mkdir?path=" + urlEncode(path));
    }

    @Override
    public boolean delete(MsServerHost h, String path) {
        return fileOk(h, "/files/delete?path=" + urlEncode(path));
    }

    @Override
    public boolean rename(MsServerHost h, String from, String to) {
        return fileOk(h, "/files/rename?from=" + urlEncode(from) + "&to=" + urlEncode(to));
    }

    /**
     * 调用 Agent 文件操作端点并判定是否成功（端点回传 {@code {"ok":true}}）。
     *
     * @param h        目标主机
     * @param endpoint 端点路径（含查询串）
     * @return 成功返回 true，Agent 无响应或返回失败均为 false
     */
    private boolean fileOk(MsServerHost h, String endpoint) {
        try {
            String b = body(agentUrl(h) + endpoint);
            return b != null && b.contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        }
    }
}
