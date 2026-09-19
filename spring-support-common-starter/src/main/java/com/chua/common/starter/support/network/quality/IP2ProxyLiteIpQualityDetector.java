package com.chua.common.starter.support.network.quality;

import com.ip2proxy.IP2Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * IP2Proxy LITE 离线 IP 质量检测器（基于 IP2Proxy Java 库）。
 * <p>
 * 该检测器使用本地的 IP2PROXY-LITE-PX2.BIN 离线数据库判断 IP 的代理 / VPN /
 * 数据中心等特征。BIN 文件从 classpath 下读取，若不存在则直接降级返回空
 * 检测结果（不抛异常），保证在没有 BIN 的环境下系统仍可正常启动。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class IP2ProxyLiteIpQualityDetector implements IpQualityDetector {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(IP2ProxyLiteIpQualityDetector.class);

    /** 默认 BIN 文件名（从 classpath 读取） */
    private static final String DEFAULT_BIN = "IP2PROXY-LITE-PX2.BIN";

    /** IP2Proxy 实例；BIN 缺失时为 null，表示不可用 */
    private final IP2Proxy ip2Proxy;

    /**
    * 从默认 classpath 资源创建实例。BIN 缺失时降级为不可用（不抛异常）。
    */
    public IP2ProxyLiteIpQualityDetector() {
        Path bin = findDefaultResource();
        if (bin == null) {
            // 缺少 BIN：降级为不可用，detect 将返回空结果
            this.ip2Proxy = null;
            return;
        }
        this.ip2Proxy = open(bin, null);
    }

    /**
     * 从指定 BIN 文件创建实例。
     *
     * @param binPath BIN 文件路径
     */
    public IP2ProxyLiteIpQualityDetector(Path binPath) {
        this.ip2Proxy = open(binPath, null);
    }

    /**
     * 从 BIN 输入流创建实例。
     *
     * @param binStream BIN 输入流
     * @throws IOException 读取流失败时抛出
     */
    public IP2ProxyLiteIpQualityDetector(InputStream binStream) throws IOException {
        try {
            byte[] bytes = binStream.readAllBytes();
            this.ip2Proxy = open(null, bytes);
        } catch (IOException e) {
            throw new IOException("初始化 IP2Proxy LITE 失败", e);
        }
    }

    /**
     * 打开 BIN（文件或字节），失败时返回 null 并记录日志（降级）。
     *
     * @param path     BIN 文件路径（与 bytes 二选一）
     * @param bytes    BIN 字节（与 path 二选一）
     * @return 打开成功返回 IP2Proxy，失败返回 null
     */
    private static IP2Proxy open(Path path, byte[] bytes) {
        try {
            IP2Proxy proxy = new IP2Proxy();
            int result = (bytes != null) ? proxy.Open(bytes) : proxy.Open(path.toString());
            if (result != 0) {
                logger.warn("IP2Proxy LITE 打开失败，错误码={}，路径={}", result,
                        bytes != null ? "InputStream" : path);
                return null;
            }
            return proxy;
        } catch (IOException e) {
            logger.warn("IP2Proxy LITE 打开异常: {}", path, e);
            return null;
        }
    }

    /**
     * 查找默认 classpath BIN 资源，缺失时返回 null（不抛异常）。
     *
     * @return BIN 路径；不存在返回 null
     */
    private static Path findDefaultResource() {
        URL resource = IP2ProxyLiteIpQualityDetector.class.getClassLoader().getResource(DEFAULT_BIN);
        if (resource == null) {
            return null;
        }
        try {
            return Path.of(resource.toURI());
        } catch (Exception e) {
            logger.warn("解析 IP2Proxy LITE BIN 路径失败", e);
            return null;
        }
    }

    @Override
    public IpQuality detect(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        if (ip2Proxy == null) {
            // BIN 缺失降级：返回空检测结果
            return emptyQuality(ip);
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

    /**
     * 构造空检测结果。
     *
     * @param ip IP
     * @return 空结果
     */
    private static IpQuality emptyQuality(String ip) {
        IpQuality q = new IpQuality();
        q.setIp(ip);
        return q;
    }

    /**
     * 将 IP2Proxy 结果映射为 IpQuality。
     *
     * @param ip 原始 IP
     * @param r  IP2Proxy 结果
     * @return IpQuality
     */
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
