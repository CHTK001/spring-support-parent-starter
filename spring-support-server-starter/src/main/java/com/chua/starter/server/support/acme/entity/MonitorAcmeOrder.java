package com.chua.starter.server.support.acme.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ACME 订单信息，记录 ACME 订单地址、授权地址、状态及最终证书地址。
 *
 * @since 2025-10-17
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_acme_order")
@ApiModel(description = "ACME 订单信息")
@Schema(description = "ACME 订单信息")
public class MonitorAcmeOrder extends SysBase {

    /**
     * 主键标识
    */
    @TableId(value = "acme_order_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    /**
     * Acme排序标识
    */
    private Integer acmeOrderId;

    /**
     * 账户标识
    */
    @TableField("acme_account_id")
    @ApiModelProperty("账户ID")
    @Schema(description = "账户ID")
    /**
     * acmeaccountid
    */
    private Integer acmeAccountId;

    /**
     * 证书标识
    */
    @TableField("acme_cert_id")
    @ApiModelProperty("证书ID")
    @Schema(description = "证书ID")
    /**
     * Acme证书标识
    */
    private Integer acmeCertId;

    /**
     * 订单URL
    */
    @TableField("acme_order_url")
    @ApiModelProperty("订单URL")
    @Schema(description = "订单URL")
    /**
     * Acme排序URL
    */
    private String acmeOrderUrl;

    /**
     * 授权URL列表(JSON)
    */
    @TableField("acme_authz_urls")
    @ApiModelProperty("授权URL列表(JSON)")
    @Schema(description = "授权URL列表(JSON)")
    /**
     * Acmeauthzurls
    */
    private String acmeAuthzUrls;

    /**
     * 状态
    */
    @TableField("acme_order_status")
    @ApiModelProperty("状态")
    @Schema(description = "状态")
    /**
     * Acme排序状态
    */
    private String acmeOrderStatus;

    /**
     * 最终证书URL
    */
    @TableField("acme_certificate_url")
    @ApiModelProperty("最终证书URL")
    @Schema(description = "最终证书URL")
    /**
     * Acme证书URL
    */
    private String acmeCertificateUrl;
}
