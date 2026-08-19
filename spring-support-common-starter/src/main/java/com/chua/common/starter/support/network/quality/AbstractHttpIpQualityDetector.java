package com.chua.common.starter.support.network.quality;

import com.chua.common.support.constant.CommonConstant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 HTTP API 的 IP 质量检测器抽象基类
 *
 * @since 4.0.0.42
 */
public abstract class AbstractHttpIpQualityDetector implements IpQualityDetector {

    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(AbstractHttpIpQualityDetector.class);

    /** Objectmapper */
    protected final ObjectMapper objectMapper;
    /** HTTP客户端 */
    protected final OkHttpClient httpClient;

    /** 创建 AbstractHttpIpQualityDetector 实例 */
    protected AbstractHttpIpQualityDetector() {
        this(Duration.ofSeconds(10));
    }

    /**
     * 创建 AbstractHttpIpQualityDetector 实例
     * @param timeout timeout
     */
    protected AbstractHttpIpQualityDetector(Duration timeout) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
    }

    @Override
    /** Detect */
    public IpQuality detect(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        try {
            String url = buildUrl(ip);
            if (logger.isDebugEnabled()) {
                logger.debug("IP 质量检测: ip={}, url={}", ip, url);
            }
            Request request = buildRequest(url, ip);
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected response: " + response);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body");
                }
                String json = body.string();
                return parseResult(json);
            }
        } catch (Exception e) {
            logger.error("IP 质量检测失败: ip={}", ip, e);
            IpQuality error = new IpQuality();
            error.setIp(ip);
            return error;
        }
    }

    @Override
    /** Detect */
    public IpQuality[] detect(String[] ips) {
        if (ips == null || ips.length == 0) {
            return new IpQuality[0];
        }
        List<IpQuality> result = new ArrayList<>(ips.length);
        for (String ip : ips) {
            result.add(detect(ip));
        }
        return result.toArray(new IpQuality[0]);
    }

    /**
     * 构建请求 URL
     *
     * @param ip IP 地址
     * @return URL
     */
    protected abstract String buildUrl(String ip);

    /**
     * 构建请求
     *
     * @param url URL
     * @param ip  IP
     * @return 请求
     */
    protected Request buildRequest(String url, String ip) {
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .get()
                .build();
    }

    /**
     * 解析响应结果
     *
     * @param json 响应 JSON
     * @return IP 质量信息
     * @throws IOException 解析异常
     */
    protected abstract IpQuality parseResult(String json) throws IOException;

    /**
     * 安全获取字符串
     */
    protected String optString(JsonNode node, String field) {
        if (node == null) {
            return CommonConstant.EMPTY_STRING;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? CommonConstant.EMPTY_STRING : value.asText();
    }
}