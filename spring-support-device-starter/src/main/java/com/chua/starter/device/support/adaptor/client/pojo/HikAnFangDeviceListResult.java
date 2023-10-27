package com.chua.starter.device.support.adaptor.client.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author CH
 */
@NoArgsConstructor
@Data
public class HikAnFangDeviceListResult {

    @JsonProperty("total")
    private int total;
    @JsonProperty("pageNo")
    private int pageNo;
    @JsonProperty("pageSize")
    private int pageSize;
    @JsonProperty("list")
    private List<ListDTO> list;

    @NoArgsConstructor
    @Data
    public static class ListDTO {
        @JsonProperty("belongIndexCode")
        private String belongIndexCode;
        @JsonProperty("capability")
        private String capability;
        @JsonProperty("deviceKey")
        private String deviceKey;
        @JsonProperty("deviceType")
        private String deviceType;
        @JsonProperty("devSerialNum")
        private String devSerialNum;
        @JsonProperty("deviceCode")
        private String deviceCode;
        @JsonProperty("indexCode")
        private String indexCode;
        @JsonProperty("manufacturer")
        private String manufacturer;
        @JsonProperty("name")
        private String name;
        @JsonProperty("regionIndexCode")
        private String regionIndexCode;
        @JsonProperty("regionPath")
        private String regionPath;
        @JsonProperty("resourceType")
        private String resourceType;
        @JsonProperty("treatyType")
        private String treatyType;
        @JsonProperty("createTime")
        private String createTime;
        @JsonProperty("updateTime")
        private String updateTime;
    }
}
