package com.chua.starter.pay.support.pojo;

import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.starter.pay.support.enums.PayTransfer;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 转账参数
 *
 * @author CH
 * @since 2025/10/15 10:13
 */
@Data
@Schema(name = "转账参数")
public class CreateTransferV2Request {

    /**
     * 转账用户OpenID
     */
    @Schema(name = "转账用户OpenID")
    @NotEmpty(message = "转账用户OpenID不能为空", groups = {AddGroup.class})
    private String toUserOpenId;

    /**
     * 商户ID
     */
    @Schema(name = "商户ID")
    @NotNull(message = "商户ID不能为空", groups = {AddGroup.class})
    private Integer merchantId;

    /**
     * 交易类型
     */
    @Schema(name = "转账类型")
    @NotNull(message = "转账类型不能为空", groups = {AddGroup.class})
    private PayTransfer payTransfer;
    /**
     * 转账金额
     */
    @Schema(name = "转账金额")
    @NotNull(message = "转账金额不能为空", groups = {AddGroup.class})
    private BigDecimal amount;

    /**
     * 请求ID
     */
    @Schema(name = "请求ID")
    @NotEmpty(message = "请求ID不能为空", groups = {AddGroup.class})
    private String requestId;

    /**
     * 转账真实姓名
     */
    @Schema(name = "转账真实姓名(非必填)")
    private String realName;

    /**
     * 转账手机号
     */
    @Schema(name = "转账手机号(非必填)")
    private String phone;

    /**
     * 转账描述
     */
    @Schema(name = "转账描述(非必填)")
    private String description;

    /**
     * 转账场景ID，用于标识转账的具体场景（必填）
     */
    @SerializedName("transfer_scene_id")
    private String transferSceneId;

    /**
     * 转账场景报告信息列表，包含与转账场景相关的报告信息（必填）
     */
    @SerializedName("transfer_scene_report_infos")
    @NotNull(message = "转账场景报告信息列表不能为空", groups = {AddGroup.class})
    private List<TransferSceneReportInfo> transferSceneReportInfo;

    /**
     * 转账场景报告信息实体类
     */
    @NoArgsConstructor
    @Data
    public static class TransferSceneReportInfo {
        /**
         * 信息类型，用于标识报告信息的类型（必填）
         */
        @SerializedName("info_type")
        private String infoType;

        /**
         * 信息内容，包含报告信息的具体内容（必填）
         */
        @SerializedName("info_content")
        private String infoContent;
    }
}
