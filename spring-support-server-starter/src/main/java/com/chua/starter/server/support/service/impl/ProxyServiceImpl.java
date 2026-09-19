package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.server.support.entity.MsProxy;
import com.chua.starter.server.support.mapper.MsProxyMapper;
import com.chua.starter.server.support.service.ProxyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
* 免费代理服务实现：自研抓取可用源（databay——JSON）→ 清空代理表 → 录入。
* 每次获取均为全量刷新（每次获取清空录入）；页面从表读取（快）。
* @author CH
* @since 4.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyServiceImpl implements ProxyService {

    /**
     * 免费代理源（databay——JSON：数据[].ip/端口/协议）
     */
    private static final String SOURCE_URL = "https://databay.com/api/v1/proxy-list";
    private static final String SOURCE_NAME = "databay"; // 源名称
    private static final ObjectMapper MAPPER = new ObjectMapper(); // 映射器

    /**
     * 代理探测目标
     * <p>
     * 不能用 www.google.com：本网络里它被 hosts 直接指向 127.0.0.1，隧道对端变成本机回环，
     * 任何代理都会被误判为不可达。选一个解析正常、公网可达且不需要境外出口的地址。
     * </p>
     */
    private static final String PROBE_HOST = "www.baidu.com";

    /**
     * 代理探测端口（443 同时覆盖 CONNECT 隧道能力）
     */
    private static final int PROBE_PORT = 443;

    /**
     * 代理探测 URL（HTTP 通道用）
     */
    private static final String PROBE_URL = "https://" + PROBE_HOST;

    /**
     * 单个代理探测超时（秒）
     */
    private static final int PROBE_TIMEOUT_SECONDS = 10;

    private final MsProxyMapper proxyMapper; // 代理映射器

    @Override
    public List<MsProxy> fetchFreeProxies() {
        List<MsProxy> proxies = fetchFromSource();
        if (proxies.isEmpty()) {
            // 源故障不能连带冲掉表里已录入的代理，否则一次上游抖动就把可用资产清零
            log.warn("[ProxyService] 免费代理源无数据，保留代理表现有数据");
            return proxies;
        }
        // 每次获取：清空旧数据 → 录入新列表
        proxyMapper.delete(null);
        proxies.forEach(p -> p.setSource(SOURCE_NAME));
        proxies.forEach(p -> proxyMapper.insert(p));
        log.info("[ProxyService] 免费代理获取完成: {} 条（清空重录）", proxies.size());
        return proxies;
    }

    @Override
    public List<MsProxy> listProxies() {
        // 从数据表读取（快）——限制 200 条；不排序时同一张表每次可能给出不同切片，页面会“丢行”
        return proxyMapper.selectList(new LambdaQueryWrapper<MsProxy>()
                .orderByAsc(MsProxy::getId)
                .last("limit 200"));
    }

    @Override
    public String currentProxy() {
        return proxyMapper.selectList(new LambdaQueryWrapper<MsProxy>()
                        .orderByAsc(MsProxy::getId)
                        .last("limit 1"))
                .stream().findFirst()
                .map(p -> p.getHost() + ":" + p.getPort())
                .orElse(null);
    }

    @Override
    public int checkProxies() {
        // 与页面同一窗口：检测列表能看到的那批，避免跑完全表却展示不出结果
        List<MsProxy> proxies = listProxies();
        if (proxies.isEmpty()) {
            return 0;
        }
        int reachableCount = 0;
        // 虚拟线程批量检测；executor.close() 会等全部任务收敛，因此本方法是同步跑完才返回
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>(proxies.size());
            for (MsProxy proxy : proxies) {
                futures.add(executor.submit(() -> checkProxyInternal(proxy)));
            }
            for (Future<Boolean> future : futures) {
                try {
                    if (Boolean.TRUE.equals(future.get())) {
                        reachableCount++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 单条检测内部已兜底，这里只防调度层异常影响整体计数
                    log.debug("[ProxyService] 代理检测任务异常: {}", e.getMessage());
                }
            }
        }
        log.info("[ProxyService] 批量检测代理完成: {} 条，可达 {} 条", proxies.size(), reachableCount);
        return proxies.size();
    }

    @Override
    public boolean checkProxy(Long id) {
        MsProxy proxy = proxyMapper.selectById(id);
        if (proxy == null) {
            return false;
        }
        checkProxyInternal(proxy);
        return true;
    }

    /**
     * 检测单个代理并按协议回写结果
     *
     * @param proxy 代理
     * @return 是否可达
     */
    private boolean checkProxyInternal(MsProxy proxy) {
        long start = System.currentTimeMillis();
        boolean reachable = isSocks(proxy.getProtocol()) ? probeSocks(proxy) : probeHttp(proxy);
        long delay = System.currentTimeMillis() - start;
        proxy.setDelay(delay);
        proxy.setReachable(reachable ? 1 : 0);
        proxy.setCheckTime(System.currentTimeMillis());
        proxyMapper.updateById(proxy);
        return reachable;
    }

    /**
     * 是否 SOCKS 系代理
     *
     * @param protocol 协议字面量
     * @return true 表示必须走 SOCKS 通道探测
     */
    private boolean isSocks(String protocol) {
        return protocol != null && protocol.trim().toLowerCase(Locale.ROOT).startsWith("socks");
    }

    /**
     * HTTP 代理探测：经代理访问探测目标
     *
     * @param proxy 代理
     * @return 是否可达
     */
    private boolean probeHttp(MsProxy proxy) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS))
                    .proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(PROBE_URL))
                    .timeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 400;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * SOCKS 代理探测
     * <p>
     * JDK 的 HttpClient 只认 HTTP 代理，把 Socks4/Socks5 塞进 ProxySelector 会让所有
     * SOCKS 代理恒定判为不可达。这里改用 {@code Socket(Proxy)} 由 JDK 完成 SOCKS 握手，
     * 能建到探测端点的隧道即视为代理可用。
     * </p>
     *
     * @param proxy 代理
     * @return 是否可达
     */
    private boolean probeSocks(MsProxy proxy) {
        InetSocketAddress via = new InetSocketAddress(proxy.getHost(), proxy.getPort());
        try (Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, via))) {
            socket.connect(InetSocketAddress.createUnresolved(PROBE_HOST, PROBE_PORT),
                    (int) Duration.ofSeconds(PROBE_TIMEOUT_SECONDS).toMillis());
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 自研抓取（databay JSON——数据[].ip/端口/协议）
     *
     * @return 获取从源的结果
     */
    private List<MsProxy> fetchFromSource() {
        List<MsProxy> result = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(SOURCE_URL))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[ProxyService] 免费代理源响应异常: {}", resp.statusCode());
                return result;
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode data = root.path("data");
            for (JsonNode node : data) {
                String ip = node.path("ip").asText(null);
                int port = node.path("port").asInt(0);
                String proto = node.path("protocol").asText("Http");
                if (ip == null || port <= 0) {
                    continue;
                }
                MsProxy p = new MsProxy();
                p.setHost(ip);
                p.setPort(port);
                p.setProtocol(proto);
                result.add(p);
            }
        } catch (Exception e) {
            log.warn("[ProxyService] 免费代理抓取失败: {}", e.getMessage());
        }
        return result;
    }
}