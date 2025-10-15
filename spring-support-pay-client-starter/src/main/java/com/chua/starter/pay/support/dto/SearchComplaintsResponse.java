package com.chua.starter.pay.support.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索投诉响应数据
 *
 * @author CH
 * @since 2025/10/15 15:38
 */
@NoArgsConstructor
@Data
public class SearchComplaintsResponse {

    /**
     * 投诉信息列表
     */
    @JsonProperty("data")
    private List<DataDTO> data;
    
    /**
     * 设置该次请求返回条数
     */
    @JsonProperty("limit")
    private Integer limit;
    
    /**
     * 设置该次请求的偏移量
     */
    @JsonProperty("offset")
    private Integer offset;
    
    /**
     * 投诉总条数
     */
    @JsonProperty("total_count")
    private Integer totalCount;

    /**
     * 投诉信息数据传输对象
     */
    @NoArgsConstructor
    @Data
    public static class DataDTO {
        /**
         * 投诉单号
         */
        @JsonProperty("complaint_id")
        private String complaintId;
        
        /**
         * 投诉时间
         */
        @JsonProperty("complaint_time")
        private String complaintTime;
        
        /**
         * 投诉详情
         */
        @JsonProperty("complaint_detail")
        private String complaintDetail;
        
        /**
         * 投诉单状态
         */
        @JsonProperty("complaint_state")
        private String complaintState;
        
        /**
         * 投诉人联系方式
         */
        @JsonProperty("payer_phone")
        private String payerPhone;
        
        /**
         * 投诉订单信息
         */
        @JsonProperty("complaint_order_info")
        private List<ComplaintOrderInfoDTO> complaintOrderInfo;
        
        /**
         * 投诉订单是否已全额退款
         */
        @JsonProperty("complaint_full_refunded")
        private Boolean complaintFullRefunded;
        
        /**
         * 是否有待回复的用户留言
         */
        @JsonProperty("incoming_user_response")
        private Boolean incomingUserResponse;
        
        /**
         * 用户投诉次数
         */
        @JsonProperty("user_complaint_times")
        private Integer userComplaintTimes;
        
        /**
         * 投诉相关媒体信息列表
         */
        @JsonProperty("complaint_media_list")
        private List<ComplaintMediaListDTO> complaintMediaList;
        
        /**
         * 问题描述
         */
        @JsonProperty("problem_description")
        private String problemDescription;
        
        /**
         * 问题类型
         */
        @JsonProperty("problem_type")
        private String problemType;
        
        /**
         * 申请退款金额
         */
        @JsonProperty("apply_refund_amount")
        private Integer applyRefundAmount;
        
        /**
         * 用户标签列表
         */
        @JsonProperty("user_tag_list")
        private List<String> userTagList;
        
        /**
         * 服务订单信息列表
         */
        @JsonProperty("service_order_info")
        private List<ServiceOrderInfoDTO> serviceOrderInfo;
        
        /**
         * 额外信息
         */
        @JsonProperty("additional_info")
        private AdditionalInfoDTO additionalInfo;
        
        /**
         * 是否平台介入处理
         */
        @JsonProperty("in_platform_service")
        private Boolean inPlatformService;
        
        /**
         * 是否需要紧急处理
         */
        @JsonProperty("need_immediate_service")
        private Boolean needImmediateService;

        /**
         * 额外信息数据传输对象
         */
        @NoArgsConstructor
        @Data
        public static class AdditionalInfoDTO {
            /**
             * 类型
             */
            @JsonProperty("type")
            private String type;
            
            /**
             * 共享电源信息
             */
            @JsonProperty("share_power_info")
            private SharePowerInfoDTO sharePowerInfo;

            /**
             * 共享电源信息数据传输对象
             */
            @NoArgsConstructor
            @Data
            public static class SharePowerInfoDTO {
                /**
                 * 归还时间
                 */
                @JsonProperty("return_time")
                private String returnTime;
                
                /**
                 * 归还地址信息
                 */
                @JsonProperty("return_address_info")
                private ReturnAddressInfoDTO returnAddressInfo;
                
                /**
                 * 是否归还到同一设备
                 */
                @JsonProperty("is_returned_to_same_machine")
                private Boolean isReturnedToSameMachine;

                /**
                 * 归还地址信息数据传输对象
                 */
                @NoArgsConstructor
                @Data
                public static class ReturnAddressInfoDTO {
                    /**
                     * 归还地址
                     */
                    @JsonProperty("return_address")
                    private String returnAddress;
                    
                    /**
                     * 经度
                     */
                    @JsonProperty("longitude")
                    private String longitude;
                    
                    /**
                     * 纬度
                     */
                    @JsonProperty("latitude")
                    private String latitude;
                }
            }
        }

        /**
         * 投诉订单信息数据传输对象
         */
        @NoArgsConstructor
        @Data
        public static class ComplaintOrderInfoDTO {
            /**
             * 微信支付订单号
             */
            @JsonProperty("transaction_id")
            private String transactionId;
            
            /**
             * 商户订单号
             */
            @JsonProperty("out_trade_no")
            private String outTradeNo;
            
            /**
             * 订单金额(分)
             */
            @JsonProperty("amount")
            private Integer amount;
        }

        /**
         * 投诉媒体信息数据传输对象
         */
        @NoArgsConstructor
        @Data
        public static class ComplaintMediaListDTO {
            /**
             * 媒体类型
             */
            @JsonProperty("media_type")
            private String mediaType;
            
            /**
             * 媒体URL列表
             */
            @JsonProperty("media_url")
            private List<String> mediaUrl;
        }

        /**
         * 服务订单信息数据传输对象
         */
        @NoArgsConstructor
        @Data
        public static class ServiceOrderInfoDTO {
            /**
             * 服务订单号
             */
            @JsonProperty("order_id")
            private String orderId;
            
            /**
             * 商户服务订单号
             */
            @JsonProperty("out_order_no")
            private String outOrderNo;
            
            /**
             * 服务订单状态
             */
            @JsonProperty("state")
            private String state;
        }
    }
}
