package com.chua.common.support.geo;

/**
* IP 定位信息
*
* @author CH
* @since 4.0.0
 */
public class IpPosition {

    /**
    * 国家名称
     */
    private String country;

    /**
    * 区域/省份名称
     */
    private String region;

    /**
    * 城市名称
     */
    private String city;

    /**
    * 互联网服务提供商（ISP）名称
     */
    private String isp;

    /**
    * 获取国家名称
    *
    * @return 国家名称
     */
    public String getCountry() {
        return country;
    }

    /**
    * 设置国家名称
    *
    * @param country 国家名称
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
    * 获取区域/省份名称
    *
    * @return 区域/省份名称
     */
    public String getRegion() {
        return region;
    }

    /**
    * 设置区域/省份名称
    *
    * @param region 区域/省份名称
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
    * 获取城市名称
    *
    * @return 城市名称
     */
    public String getCity() {
        return city;
    }

    /**
    * 根据指定 IP 地址查询对应的城市地理信息
    *
    * @param ip 要查询的 IP 地址
    * @return GeoCity 对象，当前实现返回 空
     */
    public GeoCity getCity(String ip) {
        return null;
    }

    /**
    * 设置城市名称
    *
    * @param city 城市名称
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
    * 获取互联网服务提供商（ISP）名称
    *
    * @return ISP 名称
     */
    public String getIsp() {
        return isp;
    }

    /**
    * 设置互联网服务提供商（ISP）名称
    *
    * @param isp ISP 名称
     */
    public void setIsp(String isp) {
        this.isp = isp;
    }

    /**
    * 初始化回调方法，在属性设置完成后执行自定义初始化逻辑
    * <p>当前实现为空，供子类覆写使用。</p>
     */
    public void afterPropertiesSet() {
    }
}
