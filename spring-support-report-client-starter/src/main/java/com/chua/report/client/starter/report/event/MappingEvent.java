package com.chua.report.client.starter.report.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 映射事件
 * @author CH
 * @since 2024/9/20
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MappingEvent  extends TimestampEvent implements Serializable {

    /**
     * url
     */
    private String url;

    /**
     * 请求地址
     */
    private String address;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 耗时
     */
    private long cost;

    /**
     * 城市
     */
    private String city;
    /**
     * 纬度
     */
    private Double longitude;

    /**
     * 经度
     */
    private Double latitude;
}
