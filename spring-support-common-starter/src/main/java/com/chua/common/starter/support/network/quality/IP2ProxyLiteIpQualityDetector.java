package com.chua.common.starter.support.network.quality;

import com.ip2proxy.IP2Proxy;
import com.ip2proxy.ProxyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * IP2Proxy LITE 离线 IP 质量检测器（基于 IP2Proxy Java 库）
 *
 * @since 4.0.0.42
 * @author CH
 */
public class IP2ProxyLiteIpQualityDetector implements IpQualityDetector {

    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(IP2ProxyLiteIpQualityDetector.class);

    /** Default_bin */
    private static final String DEFAULT_BIN = "IP2PROXY-LITE-PX2.BIN";

    /** Ip2proxy */
    private final IP2Proxy ip2Proxy;

    /** 创建 IP2ProxyLiteIpQualityDetector 实例 */
    public IP2ProxyLiteIpQualityDetector() {
        this(findDefaultResource());
    }

    /**
     * 创建 IP2ProxyLiteIpQualityDetector 实例
     * @param binPath binPath
     */
    public IP2ProxyLiteIpQualityDetector(Path binPath) {
        try {
            this.ip2Proxy = new IP2Proxy();
            int result = ip2Proxy.Open(binPath.toString());
            if (result != 0) {
                throw new IllegalStateException("初始化 IP2Proxy LITE 失败，打开文件: " + binPath + "，错误码: " + result);
            }
        } catch (IOException e) {
            throw new IllegalStateException("初始化 IP2Proxy LITE 失败: " + binPath, e);
        }
    }

    /**
     * 创建 IP2ProxyLiteIpQualityDetector 实例
     * @param binStream binStream
     */
    public IP2ProxyLiteIpQualityDetector(InputStream binStream) throws IOException {
        try {
            this.ip2Proxy = new IP2Proxy();
            byte[] bytes = binStream.readAllBytes();
            int result = ip2Proxy.Open(bytes);
            if (result != 0) {
                throw new IllegalStateException("初始化 IP2Proxy LITE InputStream 失败，错误码: " + result);
            }
        } catch (IOException e) {
            throw new IOException("初始化 IP2Proxy LITE 失败", e);
        }
    }

    @Override
    /** Detect */
    public IpQuality detect(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        try {
            IP2ProxyResult result = new IP2ProxyResult(ip2Proxy, ip);
            if (!result.isValid()) {
                return emptyQuality(ip);
            }
            return mapResult(ip, result);
        } catch (Exception e) {
            logger.error("IP2Proxy LITE 检测失败: ip={}", ip, e);
            return emptyQuality(ip);
        }
    }

    @Override
    /** Detect */
    public IpQuality[] detect(String[] ips) {
        if (ips == null || ips.length == 0) {
            return new IpQuality[0];
        }
        IpQuality[] result = new IpQuality[ips.length];
        for (int i = 0; i < ips.length; i++) {
            result[i] = detect(ips[i]);
        }
        return result;
    }

    /** 查找DefaultResource */
    private static Path findDefaultResource() {
        URL resource = IP2ProxyLiteIpQualityDetector.class.getClassLoader()
                .getResource(DEFAULT_BIN);
        if (resource == null) {
            throw new IllegalStateException(
                    "未找到 IP2PROXY-LITE-PX2.BIN，请将 IP2PROXY-LITE-PX2.BIN 放置于 classpath 下");
        }
        try {
            return Path.of(resource.toURI());
        } catch (Exception e) {
            throw new IllegalStateException("解析 IP2Proxy LITE 数据库路径失败", e);
        }
    }

    /** EmptyQuality */
    private static IpQuality emptyQuality(String ip) {
        IpQuality q = new IpQuality();
        q.setIp(ip);
        return q;
    }

    /** MapResult */
    private static IpQuality mapResult(String ip, IP2ProxyResult r) {
        IpQuality q = new IpQuality();
        q.setIp(ip);
        q.setCountry(r.getCountryCode());
        q.setRegion(r.getRegionName());
        q.setCity(r.getCityName());
        q.setIsp(r.getIsp());
        q.setProxy(r.isProxy());
        q.setVpn(r.isVpn());
        q.setTor(r.isTor());
        q.setDataCenter(r.isDataCenter());
        q.setCloudService(r.isWebProxy());
        q.setUsageType(r.getUsageType());
        return q;
    }
}