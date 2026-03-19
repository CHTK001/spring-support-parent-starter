package com.chua.starter.common.support.utils;

import com.chua.common.support.core.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * IP 工具类
 * <p>
 * 统一提供客户端 IP 获取、内网 IP 判断、IP 匹配（通配符/CIDR）等能力，
 * 避免在 ApiControlInterceptor、ApiGrayEvaluator 等多处重复实现。
 * </p>
 *
 * @author CH
 * @since 2025/3/16
 */
public final class IpUtils {

    private IpUtils() {}

    /**
     * 内网 IP 正则（预编译）
     * 覆盖范围：127.x、10.x、172.16-31.x、192.168.x、IPv6 回环/本地
     */
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "^(127\\.)|(10\\.)|(172\\.(1[6-9]|2[0-9]|3[0-1])\\.)|(192\\.168\\.)|(::1$)|(fc00:)|(fe80:)"
    );

    /**
     * 从请求中获取客户端真实 IP
     * <p>支持多层代理，优先取 X-Forwarded-For 第一个非 unknown 地址</p>
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlankOrUnknown(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 判断是否为内网 IP
     */
    public static boolean isPrivateIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(ip)) {
            return true;
        }
        return PRIVATE_IP_PATTERN.matcher(ip).find();
    }

    /**
     * IP 匹配，支持精确、通配符（192.168.1.*）、CIDR（192.168.1.0/24）三种格式
     */
    public static boolean matchIp(String clientIp, String pattern) {
        if (StringUtils.isBlank(clientIp) || StringUtils.isBlank(pattern)) {
            return false;
        }
        if (clientIp.equals(pattern)) {
            return true;
        }
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return clientIp.matches(regex);
        }
        if (pattern.contains("/")) {
            return matchCidr(clientIp, pattern);
        }
        return false;
    }

    /**
     * 根据 User-Agent 字符串生成浏览器指纹（SHA-256 前 32 位十六进制）
     * <p>
     * 当客户端未上报真实指纹时，可用 UA 作为 fallback 生成一个稳定的伪指纹。
     * 同一 UA 每次生成结果相同，可用于日志关联，但安全性低于真实指纹。
     * </p>
     *
     * @param userAgent User-Agent 字符串
     * @return 32 位十六进制指纹，UA 为空时返回 null
     */
    public static String fingerprintFromUserAgent(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---- private helpers ----

    private static boolean isBlankOrUnknown(String ip) {
        return StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip);
    }

    private static boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            int prefixLength = Integer.parseInt(parts[1].trim());
            long ipLong = ipToLong(ip);
            long baseIpLong = ipToLong(parts[0].trim());
            long mask = prefixLength == 0 ? 0L : (-1L << (32 - prefixLength));
            return (ipLong & mask) == (baseIpLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        long result = 0;
        for (String part : parts) {
            result = result * 256 + Integer.parseInt(part.trim());
        }
        return result;
    }
}
