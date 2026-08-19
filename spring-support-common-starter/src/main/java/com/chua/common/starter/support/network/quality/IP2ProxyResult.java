package com.chua.common.starter.support.network.quality;

import com.ip2proxy.IP2Proxy;
import java.io.Serializable;

/**
 * IP2Proxy LITE 查询结果模型
 *
 * @since 4.0.0.42
 * @author CH
 */
public class IP2ProxyResult implements Serializable {

    /** Not_supported */
    private static final String NOT_SUPPORTED = "NOT SUPPORTED";
    /** 是否为空 */
    private static final String EMPTY = "-";

    /** Proxy */
    private final IP2Proxy proxy;
    /** IP */
    private final String ip;

    /**
     * 创建 IP2ProxyResult 实例
     * @param proxy proxy
     * @param String String
     */
    public IP2ProxyResult(IP2Proxy proxy, String ip) {
        this.proxy = proxy;
        this.ip = ip;
    }

    /** 是否Valid */
    public boolean isValid() {
        try {
            return proxy != null && "".equals(getProxyType()) == false && NOT_SUPPORTED.equals(getProxyType()) == false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否Proxy */
    public boolean isProxy() {
        try {
            return proxy != null && proxy.IsProxy(ip) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取ProxyType */
    public String getProxyType() {
        try {
            return proxy != null ? coalesce(proxy.GetProxyType(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取CountryCode */
    public String getCountryCode() {
        try {
            return proxy != null ? coalesce(proxy.GetCountryShort(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取CountryName */
    public String getCountryName() {
        try {
            return proxy != null ? coalesce(proxy.GetCountryLong(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取RegionName */
    public String getRegionName() {
        try {
            return proxy != null ? coalesce(proxy.GetRegion(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取CityName */
    public String getCityName() {
        try {
            return proxy != null ? coalesce(proxy.GetCity(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取Isp */
    public String getIsp() {
        try {
            return proxy != null ? coalesce(proxy.GetISP(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取UsageType */
    public String getUsageType() {
        try {
            return proxy != null ? coalesce(proxy.GetUsageType(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 是否Vpn */
    public boolean isVpn() {
        return "VPN".equalsIgnoreCase(getProxyType());
    }

    /** 是否Tor */
    public boolean isTor() {
        return "TOR".equalsIgnoreCase(getProxyType());
    }

    /** 是否DataCenter */
    public boolean isDataCenter() {
        return "DCH".equalsIgnoreCase(getProxyType());
    }

    /** 是否WebProxy */
    public boolean isWebProxy() {
        return "WEB".equalsIgnoreCase(getProxyType());
    }

    /** Coalesce */
    private static String coalesce(String s) {
        if (s == null || NOT_SUPPORTED.equals(s) || "".equals(s) || "-".equals(s)) {
            return "";
        }
        return s;
    }
}