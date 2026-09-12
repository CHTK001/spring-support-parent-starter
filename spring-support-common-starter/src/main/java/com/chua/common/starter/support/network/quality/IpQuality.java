package com.chua.common.starter.support.network.quality;

import java.io.Serializable;

/**
* IP 质量信息
*
* @since 4.0.0.42
* @author CH
 */
public class IpQuality implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** IP 地址 */
    /** IP */
    private String ip;

    /** 是否高危代理 */
    /** Highriskproxy */
    private boolean highRiskProxy;

    /** 是否 VPN */
    /** VPN */
    private boolean vpn;

    /** 是否为代理 */
    /** 代理 */
    private boolean proxy;

    /** 是否为 Tor 节点 */
    /** TOR */
    private boolean tor;

    /** 是否云服务 */
    /** Cloud服务 */
    private boolean cloudService;

    /** 是否数据中 */
    /** 数据center */
    private boolean dataCenter;

    /** 风险评分 (0-100) */
    /** Risk分数 */
    private int riskScore;

    /** 风险等级 (low/medium/high) */
    /** Risk级别 */
    private String riskLevel;

    /** 运营商信息 */
    /** ISP */
    private String isp;

    /** 国家/地区 */
    /** 国家 */
    private String country;

    /** 省份 */
    /** 区域 */
    private String region;

    /** 城市 */
    /** City */
    private String city;

    /** 经度 */
    /** Longitude */
    private Double longitude;

    /** 纬度 */
    /** Latitude */
    private Double latitude;

    /** 使用类型 */
    /** Usage类型 */
    private String usageType;

    /** ASN 编号 */
    /** ASN */
    private String asn;

    /**  organisation */
    /** Organisation */
    private String organisation;

    /**
    * 获取ip。
    * @return 获取ip的结果
     */
    public String getIp() {
        return ip;
    }

    /**
    * 设置ip。
    * @param ip ip
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
    * 是否highrisk代理。
    * @return 是否highrisk代理的结果
     */
    public boolean isHighRiskProxy() {
        return highRiskProxy;
    }

    /**
    * 设置highrisk代理。
    * @param highRiskProxy highrisk代理
     */
    public void setHighRiskProxy(boolean highRiskProxy) {
        this.highRiskProxy = highRiskProxy;
    }

    /**
    * 是否VPN。
    * @return 是否vpn的结果
     */
    public boolean isVpn() {
        return vpn;
    }

    /**
    * 设置VPN。
    * @param vpn VPN
     */
    public void setVpn(boolean vpn) {
        this.vpn = vpn;
    }

    /**
    * 是否代理。
    * @return 是否代理的结果
     */
    public boolean isProxy() {
        return proxy;
    }

    /**
    * 设置代理。
    * @param proxy 代理
     */
    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    /**
    * 是否tor。
    * @return 是否tor的结果
     */
    public boolean isTor() {
        return tor;
    }

    /**
    * 设置tor。
    * @param tor tor
     */
    public void setTor(boolean tor) {
        this.tor = tor;
    }

    /**
    * 是否cloud服务。
    * @return 是否cloud服务的结果
     */
    public boolean isCloudService() {
        return cloudService;
    }

    /**
    * 设置cloud服务。
    * @param cloudService cloud服务
     */
    public void setCloudService(boolean cloudService) {
        this.cloudService = cloudService;
    }

    /**
    * 是否数据center。
    * @return 是否数据center的结果
     */
    public boolean isDataCenter() {
        return dataCenter;
    }

    /**
    * 设置数据center。
    * @param dataCenter 数据center
     */
    public void setDataCenter(boolean dataCenter) {
        this.dataCenter = dataCenter;
    }

    /**
    * 获取riskscore。
    * @return 获取riskscore的结果
     */
    public int getRiskScore() {
        return riskScore;
    }

    /**
    * 设置riskscore。
    * @param riskScore riskscore
     */
    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    /**
    * 获取risk水平仪。
    * @return 获取risk水平仪的结果
     */
    public String getRiskLevel() {
        return riskLevel;
    }

    /**
    * 设置risk水平仪。
    * @param riskLevel risk水平仪
     */
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    /**
    * 获取isp。
    * @return 获取isp的结果
     */
    public String getIsp() {
        return isp;
    }

    /**
    * 设置isp。
    * @param isp isp
     */
    public void setIsp(String isp) {
        this.isp = isp;
    }

    /**
    * 获取country。
    * @return 获取country的结果
     */
    public String getCountry() {
        return country;
    }

    /**
    * 设置country。
    * @param country country
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
    * 获取region。
    * @return 获取region的结果
     */
    public String getRegion() {
        return region;
    }

    /**
    * 设置region。
    * @param region region
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
    * 获取city。
    * @return 获取city的结果
     */
    public String getCity() {
        return city;
    }

    /**
    * 设置city。
    * @param city city
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
    * 获取longitude。
    * @return 获取longitude的结果
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
    * 设置longitude。
    * @param longitude longitude
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /**
    * 获取latitude。
    * @return 获取latitude的结果
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
    * 设置latitude。
    * @param latitude latitude
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /**
    * 获取usage类型。
    * @return 获取usage类型的结果
     */
    public String getUsageType() {
        return usageType;
    }

    /**
    * 设置usage类型。
    * @param usageType usage类型
     */
    public void setUsageType(String usageType) {
        this.usageType = usageType;
    }

    /**
    * 获取asn。
    * @return 获取asn的结果
     */
    public String getAsn() {
        return asn;
    }

    /**
    * 设置asn。
    * @param asn asn
     */
    public void setAsn(String asn) {
        this.asn = asn;
    }

    /**
    * 获取organisation。
    * @return 获取organisation的结果
     */
    public String getOrganisation() {
        return organisation;
    }

    /**
    * 设置organisation。
    * @param organisation organisation
     */
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