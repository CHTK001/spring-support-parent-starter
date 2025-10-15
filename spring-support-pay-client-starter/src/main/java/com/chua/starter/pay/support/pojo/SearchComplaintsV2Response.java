package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.dto.SearchComplaintsResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索投诉响应数据
 * @author CH
 * @since 2025/10/15 15:50
 */
@Data
@Schema(description = "搜索投诉响应数据")
public class SearchComplaintsV2Response {
    /**
     * 投诉单号
     */
    @Schema(description = "投诉单号")
    private String complaintId;

    /**
     * 投诉时间
     */
    @Schema(description = "投诉时间")
    private String complaintTime;

    /**
     * 投诉详情
     */
    @Schema(description = "投诉详情")
    private String complaintDetail;

    /**
     * 投诉单状态
     */
    @Schema(description = "投诉单状态")
    private String complaintState;

    /**
     * 投诉人联系方式
     */
    @Schema(description = "投诉人联系方式")
    private String payerPhone;

    /**
     * 投诉订单信息
     */
    @Schema(description = "投诉订单信息")
    private List<SearchComplaintsResponse.DataDTO.ComplaintOrderInfoDTO> complaintOrderInfo;

    /**
     * 投诉订单是否已全额退款
     */
    @Schema(description = "投诉订单是否已全额退款")
    private Boolean complaintFullRefunded;

    /**
     * 是否有待回复的用户留言
     */
    @Schema(description = "是否有待回复的用户留言")
    private Boolean incomingUserResponse;

    /**
     * 用户投诉次数
     */
    @Schema(description = "用户投诉次数")
    private Integer userComplaintTimes;

    /**
     * 投诉相关媒体信息列表
     */
    @Schema(description = "投诉相关媒体信息列表")
    private List<SearchComplaintsResponse.DataDTO.ComplaintMediaListDTO> complaintMediaList;

    /**
     * 问题描述
     */
    @Schema(description = "问题描述")
    private String problemDescription;

    /**
     * 问题类型
     */
    @Schema(description = "问题类型")
    private String problemType;

    /**
     * 申请退款金额
     */
    @Schema(description = "申请退款金额")
    private Integer applyRefundAmount;

    /**
     * 用户标签列表
     */
    @Schema(description = "用户标签列表")
    private List<String> userTagList;

    /**
     * 服务订单信息列表
     */
    @Schema(description = "服务订单信息列表")
    private List<SearchComplaintsResponse.DataDTO.ServiceOrderInfoDTO> serviceOrderInfo;

    /**
     * 额外信息
     */
    @Schema(description = "额外信息")
    private SearchComplaintsResponse.DataDTO.AdditionalInfoDTO additionalInfo;

    /**
     * 是否平台介入处理
     */
    @Schema(description = "是否平台介入处理")
    private Boolean inPlatformService;

    /**
     * 是否需要紧急处理
     */
    @Schema(description = "是否需要紧急处理")
    private Boolean needImmediateService;

    /**
     * 额外信息数据传输对象
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "额外信息数据传输对象")
    public static class AdditionalInfoDTO {
        /**
         * 类型
         */
        @Schema(description = "类型")
        private String type;

        /**
         * 共享电源信息
         */
        @Schema(description = "共享电源信息")
        private SearchComplaintsResponse.DataDTO.AdditionalInfoDTO.SharePowerInfoDTO sharePowerInfo;

        /**
         * 共享电源信息数据传输对象
         */
        @NoArgsConstructor
        @Data
        @Schema(description = "共享电源信息数据传输对象")
        public static class SharePowerInfoDTO {
            /**
             * 归还时间
             */
            @Schema(description = "归还时间")
            private String returnTime;

            /**
             * 归还地址信息
             */
            @Schema(description = "归还地址信息")
            private SearchComplaintsResponse.DataDTO.AdditionalInfoDTO.SharePowerInfoDTO.ReturnAddressInfoDTO returnAddressInfo;

            /**
             * 是否归还到同一设备
             */
            @Schema(description = "是否归还到同一设备")
            private Boolean isReturnedToSameMachine;

            /**
             * 归还地址信息数据传输对象
             */
            @NoArgsConstructor
            @Data
            @Schema(description = "归还地址信息数据传输对象")
            public static class ReturnAddressInfoDTO {
                /**
                 * 归还地址
                 */
                @Schema(description = "归还地址")
                private String returnAddress;

                /**
                 * 经度
                 */
                @Schema(description = "经度")
                private String longitude;

                /**
                 * 纬度
                 */
                @Schema(description = "纬度")
                private String latitude;
            }
        }
    }

    /**
     * 投诉订单信息数据传输对象
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "投诉订单信息数据传输对象")
    public static class ComplaintOrderInfoDTO {
        /**
         * 微信支付订单号
         */
        @Schema(description = "微信支付订单号")
        private String transactionId;

        /**
         * 商户订单号
         */
        @Schema(description = "商户订单号")
        private String outTradeNo;

        /**
         * 订单金额(分)
         */
        @Schema(description = "订单金额(分)")
        private Integer amount;
    }

    /**
     * 投诉媒体信息数据传输对象
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "投诉媒体信息数据传输对象")
    public static class ComplaintMediaListDTO {
        /**
         * 媒体类型
         */
        @Schema(description = "媒体类型")
        private String mediaType;

        /**
         * 媒体URL列表
         */
        @Schema(description = "媒体URL列表")
        private List<String> mediaUrl;
    }

    /**
     * 服务订单信息数据传输对象
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "服务订单信息数据传输对象")
    public static class ServiceOrderInfoDTO {
        /**
         * 服务订单号
         */
        @Schema(description = "服务订单号")
        private String orderId;

        /**
         * 商户服务订单号
         */
        @Schema(description = "商户服务订单号")
        private String outOrderNo;

        /**
         * 服务订单状态
         */
        @Schema(description = "服务订单状态")
        private String state;
    }
}
