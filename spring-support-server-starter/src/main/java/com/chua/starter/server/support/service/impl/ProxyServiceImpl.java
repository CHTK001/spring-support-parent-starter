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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    private final MsProxyMapper proxyMapper; // 代理映射器

    @Override
    public List<MsProxy> fetchFreeProxies() {
        List<MsProxy> proxies = fetchFromSource();
        // 每次获取：清空旧数据 → 录入新列表
        proxyMapper.delete(null);
        if (!proxies.isEmpty()) {
            proxies.forEach(p -> p.setSource(SOURCE_NAME));
            proxies.forEach(p -> proxyMapper.insert(p));
        }
        log.info("[ProxyService] 免费代理获取完成: {} 条（清空重录）", proxies.size());
        return proxies;
    }

    @Override
    public List<MsProxy> listProxies() {
        // 从数据表读取（快）——限制 200 条
        return proxyMapper.selectList(new LambdaQueryWrapper<MsProxy>().last("limit 200"));
    }

    @Override
    public String currentProxy() {
        return proxyMapper.selectList(new LambdaQueryWrapper<MsProxy>().last("limit 1"))
                .stream().findFirst()
                .map(p -> p.getHost() + ":" + p.getPort())
                .orElse(null);
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
