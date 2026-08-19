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

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public boolean isHighRiskProxy() {
        return highRiskProxy;
    }

    public void setHighRiskProxy(boolean highRiskProxy) {
        this.highRiskProxy = highRiskProxy;
    }

    public boolean isVpn() {
        return vpn;
    }

    public void setVpn(boolean vpn) {
        this.vpn = vpn;
    }

    public boolean isProxy() {
        return proxy;
    }

    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    public boolean isTor() {
        return tor;
    }

    public void setTor(boolean tor) {
        this.tor = tor;
    }

    public boolean isCloudService() {
        return cloudService;
    }

    public void setCloudService(boolean cloudService) {
        this.cloudService = cloudService;
    }

    public boolean isDataCenter() {
        return dataCenter;
    }

    public void setDataCenter(boolean dataCenter) {
        this.dataCenter = dataCenter;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getIsp() {
        return isp;
    }

    public void setIsp(String isp) {
        this.isp = isp;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public String getUsageType() {
        return usageType;
    }

    public void setUsageType(String usageType) {
        this.usageType = usageType;
    }

    public String getAsn() {
        return asn;
    }

    public void setAsn(String asn) {
        this.asn = asn;
    }

    public String getOrganisation() {
        return organisation;
    }

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