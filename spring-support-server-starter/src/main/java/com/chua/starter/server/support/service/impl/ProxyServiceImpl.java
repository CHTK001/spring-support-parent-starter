// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsProxy;
import com.chua.starter.server.support.mapper.MsProxyMapper;
import com.chua.starter.server.support.model.vo.ProxyCheckSummaryVO;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
* 免费代理服务实现：自研抓取可用源（databay——JSON）→ 清空代理表 → 录入。
* 每次获取均为全量刷新（每次获取清空录入）；页面从表读取（快）。
* 台账维护（新增/编辑/删除/清空）与探测并行存在，使代理表可脱离外部源独立运营。
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
    private static final String MANUAL_SOURCE = "manual"; // 手工录入来源
    private static final ObjectMapper MAPPER = new ObjectMapper(); // 映射器

    /**
     * 协议白名单（落库统一用下列字面量，避免同一协议多种写法把筛选拆散）
     */
    private static final List<String> PROTOCOLS = List.of("Http", "Https", "Socks4", "Socks5");

    /**
     * 列表默认窗口
     */
    private static final int DEFAULT_LIST_LIMIT = 500;

    /**
     * 列表最大窗口（探测目标上限，防止一次拉全表拖死请求）
     */
    private static final int MAX_LIST_LIMIT = 2000;

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

    /**
     * 批量检测并发上限
     * <p>
     * 实测同一张表 200 条并发探测约 25 秒，放到 500 条并发反而劣化到 300 秒以上——
     * 每个 {@code HttpClient} 都带独立选择器与连接池，叠加同库并发写，瓶颈是并发不是条数。
     * </p>
     */
    private static final int CHECK_CONCURRENCY = 64;

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
    public List<MsProxy> listProxies(String protocol, String status, String keyword, Integer limit) {
        // 不排序时同一张表每次可能给出不同切片，页面会“丢行”
        LambdaQueryWrapper<MsProxy> query = new LambdaQueryWrapper<MsProxy>()
                .orderByAsc(MsProxy::getId)
                .last("limit " + windowOf(limit));
        if (notBlank(protocol)) {
            query.eq(MsProxy::getProtocol, protocol.trim());
        }
        if (STATUS_UNCHECKED.equals(status)) {
            query.isNull(MsProxy::getCheckTime);
        } else if (STATUS_REACHABLE.equals(status)) {
            query.eq(MsProxy::getReachable, 1);
        } else if (STATUS_UNREACHABLE.equals(status)) {
            query.isNotNull(MsProxy::getCheckTime).eq(MsProxy::getReachable, 0);
        }
        if (notBlank(keyword)) {
            query.like(MsProxy::getHost, keyword.trim());
        }
        return proxyMapper.selectList(query);
    }

    @Override
    public ReturnResult<MsProxy> get(Long id) {
        MsProxy proxy = proxyMapper.selectById(id);
        return proxy == null ? ReturnResult.error("代理不存在: " + id) : ReturnResult.ok(proxy);
    }

    @Override
    public ReturnResult<MsProxy> create(MsProxy proxy) {
        String error = validate(proxy);
        if (error != null) {
            return ReturnResult.error(error);
        }
        if (existsAt(proxy.getHost(), proxy.getPort(), null)) {
            return ReturnResult.error("代理已存在: " + proxy.getHost() + ":" + proxy.getPort());
        }
        apply(proxy);
        proxyMapper.insert(proxy);
        log.info("[ProxyService] 新增代理: {}:{}", proxy.getHost(), proxy.getPort());
        return ReturnResult.ok(proxyMapper.selectById(proxy.getId()));
    }

    @Override
    public ReturnResult<MsProxy> update(Long id, MsProxy proxy) {
        MsProxy exists = proxyMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("代理不存在: " + id);
        }
        String error = validate(proxy);
        if (error != null) {
            return ReturnResult.error(error);
        }
        if (existsAt(proxy.getHost(), proxy.getPort(), id)) {
            return ReturnResult.error("代理已存在: " + proxy.getHost() + ":" + proxy.getPort());
        }
        apply(proxy);
        proxy.setId(id);
        // updateById 会跳过 null 字段，探测字段必须显式置空，否则改完地址仍挂着旧结论
        proxyMapper.update(proxy, new LambdaUpdateWrapper<MsProxy>()
                .eq(MsProxy::getId, id)
                .set(MsProxy::getDelay, null)
                .set(MsProxy::getReachable, null)
                .set(MsProxy::getCheckTime, null));
        log.info("[ProxyService] 更新代理: {}（探测结果已重置为未检测）", id);
        return ReturnResult.ok(proxyMapper.selectById(id));
    }

    @Override
    public ReturnResult<Void> delete(Long id) {
        if (proxyMapper.selectById(id) == null) {
            return ReturnResult.error("代理不存在: " + id);
        }
        proxyMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<Integer> clear() {
        Long total = proxyMapper.selectCount(null);
        proxyMapper.delete(null);
        int removed = total == null ? 0 : total.intValue();
        log.info("[ProxyService] 清空代理表: {} 条", removed);
        return ReturnResult.ok(removed);
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
    public ProxyCheckSummaryVO checkProxies() {
        // 探测覆盖整表窗口上限，避免"检测完了"却永远碰不到列表外的行
        List<MsProxy> proxies = listProxies(null, null, null, MAX_LIST_LIMIT);
        if (proxies.isEmpty()) {
            return new ProxyCheckSummaryVO(0, 0);
        }
        int reachableCount = 0;
        // 虚拟线程批量检测，但用信号量压在飞探测数；executor.close() 会等全部任务收敛，因此本方法是同步跑完才返回
        Semaphore permits = new Semaphore(CHECK_CONCURRENCY);
// [P3C C1 豁免] 场景化创建线程，需文档化
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>(proxies.size());
            for (MsProxy proxy : proxies) {
                futures.add(executor.submit(() -> {
                    try {
                        permits.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    try {
                        return checkProxyInternal(proxy);
                    } finally {
                        permits.release();
                    }
                }));
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
        return new ProxyCheckSummaryVO(proxies.size(), reachableCount);
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
     * 校验并归一化台账入参
     *
     * @param proxy 代理
     * @return 错误说明，通过校验时返回 {@code null}
     */
    private String validate(MsProxy proxy) {
        if (proxy == null) {
            return "代理内容不能为空";
        }
        if (!notBlank(proxy.getHost())) {
            return "代理主机不能为空";
        }
        proxy.setHost(proxy.getHost().trim());
        if (proxy.getHost().length() > 64) {
            return "代理主机长度不能超过 64";
        }
        Integer port = proxy.getPort();
        if (port == null || port < 1 || port > 65535) {
            return "代理端口必须是 1~65535 之间的整数";
        }
        String protocol = normalizeProtocol(proxy.getProtocol());
        if (protocol == null) {
            return "代理协议仅支持 " + String.join("/", PROTOCOLS) + "，收到: " + proxy.getProtocol();
        }
        proxy.setProtocol(protocol);
        return null;
    }

    /**
     * 归一化落库字段：来源缺省、探测结论重置为未检测
     *
     * <p>地址或协议一旦变更，旧的探测结论就不再描述当前这条代理，因此总是清空，由页面重新探测。</p>
     *
     * @param proxy 代理
     */
    private void apply(MsProxy proxy) {
        if (!notBlank(proxy.getSource())) {
            proxy.setSource(MANUAL_SOURCE);
        } else {
            proxy.setSource(proxy.getSource().trim());
        }
        proxy.setDelay(null);
        proxy.setReachable(null);
        proxy.setCheckTime(null);
    }

    /**
     * 协议归一化（大小写与分隔符宽容）
     *
     * @param protocol 原始协议字面量，可为空（取白名单首项）
     * @return 白名单内的协议字面量；白名单外返回 {@code null}
     */
    private String normalizeProtocol(String protocol) {
        if (!notBlank(protocol)) {
            return PROTOCOLS.get(0);
        }
        String key = protocol.trim().toLowerCase(Locale.ROOT).replace("-", "");
        for (String candidate : PROTOCOLS) {
            if (candidate.toLowerCase(Locale.ROOT).equals(key)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 判断地址是否已被其他代理占用
     *
     * @param host      代理主机
     * @param port      代理端口
     * @param exceptId  编辑时排除自身主键，新增时传 {@code null}
     * @return true 表示已存在
     */
    private boolean existsAt(String host, Integer port, Long exceptId) {
        LambdaQueryWrapper<MsProxy> query = new LambdaQueryWrapper<MsProxy>()
                .eq(MsProxy::getHost, host)
                .eq(MsProxy::getPort, port);
        if (exceptId != null) {
            query.ne(MsProxy::getId, exceptId);
        }
        Long hit = proxyMapper.selectCount(query);
        return hit != null && hit > 0;
    }

    /**
     * 计算本次读取窗口条数
     *
     * @param limit 业务指定条数，可为空
     * @return 落在 1~{@link #MAX_LIST_LIMIT} 的条数
     */
    private static int windowOf(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    /**
     * 字符串非空白判定
     *
     * @param value 值
     * @return true 表示有内容
     */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
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
     * <p>
     * 必须用 {@code sendAsync(...).get(deadline)} 收口：实测 {@code connectTimeout} 与
     * {@code HttpRequest.timeout} 双双设成 10 秒时，仍有代理在建链成功后让同步 {@code send} 永久挂起，
     * 一次批量检测被三条这样的记录拖到十分钟以上。
     * </p>
     *
     * @param proxy 代理
     * @return 是否可达
     */
    private boolean probeHttp(MsProxy proxy) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS))
                .proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(PROBE_URL))
                .timeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS)).GET().build();
        CompletableFuture<Integer> future =
                client.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
        try {
            Integer status = future.get(PROBE_TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS);
            return status != null && status < 400;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return false;
        } catch (Exception e) {
            // 超时/连接失败/隧道被拒都归为不可达，探测本身不允许把异常抛给调用方
            future.cancel(true);
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
    }}