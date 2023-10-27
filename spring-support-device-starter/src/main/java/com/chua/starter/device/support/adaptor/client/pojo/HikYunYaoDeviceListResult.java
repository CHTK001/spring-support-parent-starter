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
public class HikYunYaoDeviceListResult {

    /**
     * pageNo
     */
    @JsonProperty("pageNo")
    private Integer pageNo;
    /**
     * pageSize
     */
    @JsonProperty("pageSize")
    private Integer pageSize;
    /**
     * total
     */
    @JsonProperty("total")
    private Integer total;
    /**
     * totalPage
     */
    @JsonProperty("totalPage")
    private Integer totalPage;
    /**
     * list
     */
    @JsonProperty("list")
    private List<ListDTO> list;

    /**
     * ListDTO
     */
    @NoArgsConstructor
    @Data
    public static class ListDTO {
        /**
         * createTime
         */
        @JsonProperty("createTime")
        private String createTime;
        /**
         * updateTime
         */
        @JsonProperty("updateTime")
        private String updateTime;
        /**
         * createrId
         */
        @JsonProperty("createrId")
        private String createrId;
        /**
         * operatorId
         */
        @JsonProperty("operatorId")
        private String operatorId;
        /**
         * id
         */
        @JsonProperty("id")
        private String id;
        /**
         * deviceSerial
         */
        @JsonProperty("deviceSerial")
        private String deviceSerial;
        /**
         * deviceOrgId
         */
        @JsonProperty("deviceOrgId")
        private String deviceOrgId;
        /**
         * deviceName
         */
        @JsonProperty("deviceName")
        private String deviceName;
        /**
         * validateCode
         */
        @JsonProperty("validateCode")
        private String validateCode;
        /**
         * model
         */
        @JsonProperty("model")
        private String model;
        /**
         * modelType
         */
        @JsonProperty("modelType")
        private String modelType;
        /**
         * deviceVersion
         */
        @JsonProperty("deviceVersion")
        private String deviceVersion;
        /**
         * channumCount
         */
        @JsonProperty("channumCount")
        private Integer channumCount;
        /**
         * projectId
         */
        @JsonProperty("projectId")
        private String projectId;
        /**
         * deviceOrgName
         */
        @JsonProperty("deviceOrgName")
        private String deviceOrgName;
        /**
         * modelTypeStr
         */
        @JsonProperty("modelTypeStr")
        private String modelTypeStr;
        /**
         * onlineStatus
         */
        @JsonProperty("onlineStatus")
        private Integer onlineStatus;
        /**
         * onlineStatusStr
         */
        @JsonProperty("onlineStatusStr")
        private String onlineStatusStr;
        /**
         * openStatus
         */
        @JsonProperty("openStatus")
        private Integer openStatus;
        /**
         * openStatusStr
         */
        @JsonProperty("openStatusStr")
        private String openStatusStr;
        /**
         * intelliVal
         */
        @JsonProperty("intelliVal")
        private Integer intelliVal;
        /**
         * intelliValStr
         */
        @JsonProperty("intelliValStr")
        private String intelliValStr;
        /**
         * defence
         */
        @JsonProperty("defence")
        private Integer defence;
        /**
         * defenceStr
         */
        @JsonProperty("defenceStr")
        private String defenceStr;
        /**
         * serverId
         */
        @JsonProperty("serverId")
        private String serverId;
        /**
         * netAddress
         */
        @JsonProperty("netAddress")
        private String netAddress;
    }
}
