package com.chua.common.starter.support.network.quality;

import com.ip2proxy.IP2Proxy;
import java.io.Serializable;

/**
 * IP2代理 LITE 查询结果模型
 *
 * @since 4.0.0.42
 * @author CH
 */
public class IP2ProxyResult implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /** Not_支持 */
    private static final String NOT_SUPPORTED = "NOT SUPPORTED";
    /** 是否为空 */
    private static final String EMPTY = "-";

    /** 代理 */
    private final IP2Proxy proxy;
    /** IP */
    private final String ip;

    /**
     * 创建 IP2代理结果 实例
     * @param proxy 代理
     * @param ip 字符串
     * @param ip ip
     */
    public IP2ProxyResult(IP2Proxy proxy, String ip) {
        this.proxy = proxy;
        this.ip = ip;
    }

    /**
     * 是否Valid
     *
     * @return 是否valid的结果
     */
    public boolean isValid() {
        try {
            return proxy != null && "".equals(getProxyType()) == false && NOT_SUPPORTED.equals(getProxyType()) == false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否代理
     *
     * @return 是否代理的结果
     */
    public boolean isProxy() {
        try {
            return proxy != null && proxy.IsProxy(ip) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取代理类型
     *
     * @return 获取代理类型的结果
     */
    public String getProxyType() {
        try {
            return proxy != null ? coalesce(proxy.GetProxyType(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取country编码
     *
     * @return 获取country编码的结果
     */
    public String getCountryCode() {
        try {
            return proxy != null ? coalesce(proxy.GetCountryShort(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取country名称
     *
     * @return 获取country名称的结果
     */
    public String getCountryName() {
        try {
            return proxy != null ? coalesce(proxy.GetCountryLong(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取region名称
     *
     * @return 获取region名称的结果
     */
    public String getRegionName() {
        try {
            return proxy != null ? coalesce(proxy.GetRegion(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取city名称
     *
     * @return 获取city名称的结果
     */
    public String getCityName() {
        try {
            return proxy != null ? coalesce(proxy.GetCity(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取Isp
     *
     * @return 获取isp的结果
     */
    public String getIsp() {
        try {
            return proxy != null ? coalesce(proxy.GetISP(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取usage类型
     *
     * @return 获取usage类型的结果
     */
    public String getUsageType() {
        try {
            return proxy != null ? coalesce(proxy.GetUsageType(ip)) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 是否VPN
     *
     * @return 是否vpn的结果
     */
    public boolean isVpn() {
        return "VPN".equalsIgnoreCase(getProxyType());
    }

    /**
     * 是否Tor
     *
     * @return 是否tor的结果
     */
    public boolean isTor() {
        return "TOR".equalsIgnoreCase(getProxyType());
    }

    /**
     * 是否数据center
     *
     * @return 是否数据center的结果
     */
    public boolean isDataCenter() {
        return "DCH".equalsIgnoreCase(getProxyType());
    }

    /**
     * 是否web代理
     *
     * @return 是否web代理的结果
     */
    public boolean isWebProxy() {
        return "WEB".equalsIgnoreCase(getProxyType());
    }

    /**
     * Coalesce
     *
     * @param s s
     * @return coalesce的结果
     */
    private static String coalesce(String s) {
        if (s == null || NOT_SUPPORTED.equals(s) || "".equals(s) || "-".equals(s)) {
            return "";
        }
        return s;
    }
}
