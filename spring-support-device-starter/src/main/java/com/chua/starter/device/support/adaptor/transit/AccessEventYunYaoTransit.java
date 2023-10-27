package com.chua.starter.device.support.adaptor.transit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 访问事件传输
 *
 * @author CH
 * @since 2023/10/27
 */
@NoArgsConstructor
@Data
public class AccessEventYunYaoTransit {


    /**
     * code
     */
    @JsonProperty("code")
    private String code;
    /**
     * msg
     */
    @JsonProperty("msg")
    private String msg;
    /**
     * data
     */
    @JsonProperty("data")
    private DataDTO data;

    /**
     * DataDTO
     */
    @NoArgsConstructor
    @Data
    public static class DataDTO {
        /**
         * total
         */
        @JsonProperty("total")
        private Integer total;
        /**
         * pageSize
         */
        @JsonProperty("pageSize")
        private Integer pageSize;
        /**
         * pageNo
         */
        @JsonProperty("pageNo")
        private Integer pageNo;
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
             * projectId
             */
            @JsonProperty("projectId")
            private String projectId;
            /**
             * productCode
             */
            @JsonProperty("productCode")
            private String productCode;
            /**
             * eventId
             */
            @JsonProperty("eventId")
            private String eventId;
            /**
             * eventTime
             */
            @JsonProperty("eventTime")
            private String eventTime;
            /**
             * eventType
             */
            @JsonProperty("eventType")
            private Integer eventType;
            /**
             * eventCode
             */
            @JsonProperty("eventCode")
            private String eventCode;
            /**
             * eventCodeStr
             */
            @JsonProperty("eventCodeStr")
            private String eventCodeStr;
            /**
             * inOrOut
             */
            @JsonProperty("inOrOut")
            private Integer inOrOut;
            /**
             * cardNo
             */
            @JsonProperty("cardNo")
            private String cardNo;
            /**
             * personId
             */
            @JsonProperty("personId")
            private String personId;
            /**
             * personId
             */
            @JsonProperty("phone")
            private String phone;
            /**
             * personNum
             */
            @JsonProperty("personNum")
            private String personNum;
            /**
             * personName
             */
            @JsonProperty("personName")
            private String personName;
            /**
             * personType
             */
            @JsonProperty("personType")
            private String personType;
            /**
             * certNum
             */
            @JsonProperty("certNum")
            private String certNum;
            /**
             * orgId
             */
            @JsonProperty("orgId")
            private String orgId;
            /**
             * personGroupIds
             */
            @JsonProperty("personGroupIds")
            private String personGroupIds;
            /**
             * personGroupNames
             */
            @JsonProperty("personGroupNames")
            private String personGroupNames;
            /**
             * faceUrl
             */
            @JsonProperty("faceUrl")
            private String faceUrl;
            /**
             * picUrl
             */
            @JsonProperty("picUrl")
            private String picUrl;
            /**
             * temperatureStr
             */
            @JsonProperty("temperatureStr")
            private String temperatureStr;
            /**
             * mask
             */
            @JsonProperty("mask")
            private Integer mask;
            /**
             * regionPathName
             */
            @JsonProperty("regionPathName")
            private String regionPathName;
            /**
             * orgPathId
             */
            @JsonProperty("orgPathId")
            private String orgPathId;
            /**
             * orgPathName
             */
            @JsonProperty("orgPathName")
            private String orgPathName;
            /**
             * devId
             */
            @JsonProperty("devId")
            private String devId;
            /**
             * devName
             */
            @JsonProperty("devName")
            private String devName;
        }
    }
}
