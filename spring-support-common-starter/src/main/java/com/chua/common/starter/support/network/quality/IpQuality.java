package com.chua.common.starter.support.network.quality;

import java.io.Serializable;

/**
 * IP 质量信息
 *
 * @since 4.0.0.42
 * @author CH
 */
public class IpQuality implements Serializable {

    /** IP 地址 */
    private String ip;

    /** 是否高危代理 */
    private boolean highRiskProxy;

    /** 是否 VPN */
    private boolean vpn;

    /** 是否为代理 */
    private boolean proxy;

    /** 是否为 Tor 节点 */
    private boolean tor;

    /** 是否云服务 */
    private boolean cloudService;

    /** 是否数据中 */
    private boolean dataCenter;

    /** 风险评分 (0-100) */
    private int riskScore;

    /** 风险等级 (low/medium/high) */
    private String riskLevel;

    /** 运营商信息 */
    private String isp;

    /** 国家/地区 */
    private String country;

    /** 省份 */
    private String region;

    /** 城市 */
    private String city;

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 使用类型 */
    private String usageType;

    /** ASN 编号 */
    private String asn;

    /**  organisation */
    private String organisation;

    /** 获取Ip */
    public String getIp() {
        return ip;
    }

    /** 设置Ip */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /** 是否HighRiskProxy */
    public boolean isHighRiskProxy() {
        return highRiskProxy;
    }

    /** 设置HighRiskProxy */
    public void setHighRiskProxy(boolean highRiskProxy) {
        this.highRiskProxy = highRiskProxy;
    }

    /** 是否Vpn */
    public boolean isVpn() {
        return vpn;
    }

    /** 设置Vpn */
    public void setVpn(boolean vpn) {
        this.vpn = vpn;
    }

    /** 是否Proxy */
    public boolean isProxy() {
        return proxy;
    }

    /** 设置Proxy */
    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    /** 是否Tor */
    public boolean isTor() {
        return tor;
    }

    /** 设置Tor */
    public void setTor(boolean tor) {
        this.tor = tor;
    }

    /** 是否CloudService */
    public boolean isCloudService() {
        return cloudService;
    }

    /** 设置CloudService */
    public void setCloudService(boolean cloudService) {
        this.cloudService = cloudService;
    }

    /** 是否DataCenter */
    public boolean isDataCenter() {
        return dataCenter;
    }

    /** 设置DataCenter */
    public void setDataCenter(boolean dataCenter) {
        this.dataCenter = dataCenter;
    }

    /** 获取RiskScore */
    public int getRiskScore() {
        return riskScore;
    }

    /** 设置RiskScore */
    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    /** 获取RiskLevel */
    public String getRiskLevel() {
        return riskLevel;
    }

    /** 设置RiskLevel */
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    /** 获取Isp */
    public String getIsp() {
        return isp;
    }

    /** 设置Isp */
    public void setIsp(String isp) {
        this.isp = isp;
    }

    /** 获取Country */
    public String getCountry() {
        return country;
    }

    /** 设置Country */
    public void setCountry(String country) {
        this.country = country;
    }

    /** 获取Region */
    public String getRegion() {
        return region;
    }

    /** 设置Region */
    public void setRegion(String region) {
        this.region = region;
    }

    /** 获取City */
    public String getCity() {
        return city;
    }

    /** 设置City */
    public void setCity(String city) {
        this.city = city;
    }

    /** 获取Longitude */
    public Double getLongitude() {
        return longitude;
    }

    /** 设置Longitude */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /** 获取Latitude */
    public Double getLatitude() {
        return latitude;
    }

    /** 设置Latitude */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /** 获取UsageType */
    public String getUsageType() {
        return usageType;
    }

    /** 设置UsageType */
    public void setUsageType(String usageType) {
        this.usageType = usageType;
    }

    /** 获取Asn */
    public String getAsn() {
        return asn;
    }

    /** 设置Asn */
    public void setAsn(String asn) {
        this.asn = asn;
    }

    /** 获取Organisation */
    public String getOrganisation() {
        return organisation;
    }

    /** 设置Organisation */
    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    /**
     * 判断是否为风险 IP
     *
     * @return 是否风险 IP
     */
    public boolean isRisky() {
        return highRiskProxy || proxy || vpn || tor || riskScore > 50;
    }
}