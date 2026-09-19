package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.RowNormalizer;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
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
 * <p>面向安装了 monitor-agent 的主机（osType=AGENT），通过 Agent 的 19876 HTTP 端口获取信息。
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
     * 主机类型是否为 AGENT。
     *
     * @param host 目标主机
     * @return 类型为 AGENT 时返回 true
     */
    @Override
    public boolean supports(MsServerHost host) {
        return "AGENT".equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * Agent 主机的真实平台：osType 只表达接入方式，命令族必须看 Agent 自报的 os。
     *
     * @param host 目标主机
     * @return WINDOWS / LINUX / MAC，探测不到时返回空串
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
            log.warn("[agent] 平台探测失败 host={}: {}", id, e.getMessage());
            return "";
        }
        if (!platform.isEmpty()) {
            PLATFORM_CACHE.put(id, platform);
        }
        return platform;
    }

    /**
     * 发起 GET 请求并把响应反序列化为指定类型。
     *
     * @param url  请求地址
     * @param type 目标类型
     * @param <T>  返回类型
     * @return 反序列化结果
     */
    @SuppressWarnings("unchecked")
    private <T> T get(String url, Class<T> type) {
        String body = body(url);
        if (type == String.class) {
            return (T) body;
        }
        try {
            return (T) MAPPER.readValue(body, type);
        } catch (Exception e) {
            throw new IllegalStateException("Agent 响应无法解析为 " + type.getSimpleName() + ": " + e.getMessage(), e);
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
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                return r.body();
            }
            throw new IllegalStateException("Agent 返回 HTTP " + r.statusCode());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Agent 无响应 " + url + ": " + e.getMessage(), e);
        }
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
        return RowNormalizer.hostInfo(get(agentUrl(host) + "/info", Map.class));
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
            return r.statusCode() == 200;
        } catch (Exception e) {
            // 上传失败统一返回 false，由调用方决定是否重试
            return false;
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
        return get(agentUrl(h) + "/exec?cmd=" + urlEncode(cmd), String.class);
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
}
