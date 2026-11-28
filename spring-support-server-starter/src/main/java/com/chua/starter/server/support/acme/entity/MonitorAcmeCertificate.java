package com.chua.starter.server.support.acme.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 * ACME 证书信息，保存证书主域名、SAN、挑战类型、状态、有效期、PEM 内容及部署模式等。
 *
 * @since 2025-10-17
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_acme_certificate")
@ApiModel(description = "ACME 证书信息")
@Schema(description = "ACME 证书信息")
public class MonitorAcmeCertificate extends SysBase {

    /**
     * 主键标识
    */
    @TableId(value = "acme_cert_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    /**
     * Acme证书标识
    */
    private Integer acmeCertId;

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
     * 主域名
    */
    @TableField("acme_cert_primary_domain")
    @ApiModelProperty("主域名")
    @Schema(description = "主域名")
    /**
     * Acme证书primarydomain
    */
    private String acmeCertPrimaryDomain;

    /**
     * 备用域名(SAN)，逗号分隔
    */
    @TableField("acme_cert_san")
    @ApiModelProperty("备用域名(SAN)，逗号分隔")
    @Schema(description = "备用域名(SAN)，逗号分隔")
    /**
     * Acme证书SAN
    */
    private String acmeCertSan;

    /**
     * 挑战类型（HTTP-01/DNS-01）
    */
    @TableField("acme_cert_challenge_type")
    @ApiModelProperty("挑战类型（HTTP-01/DNS-01）")
    @Schema(description = "挑战类型（HTTP-01/DNS-01）")
    /**
     * Acme证书challenge类型
    */
    private String acmeCertChallengeType;

    /**
     * 证书状态（pending, valid, expired, revoked, 失败）
    */
    @TableField("acme_cert_status")
    @ApiModelProperty("证书状态")
    @Schema(description = "证书状态")
    /**
     * Acme证书状态
    */
    private String acmeCertStatus;

    /**
     * 有效期起
    */
    @TableField("acme_cert_not_before")
    @ApiModelProperty("有效期起")
    @Schema(description = "有效期起")
    /**
     * Acme证书notbefore
    */
    private String acmeCertNotBefore;

    /**
     * 有效期止
    */
    @TableField("acme_cert_not_after")
    @ApiModelProperty("有效期止")
    @Schema(description = "有效期止")
    /**
     * Acme证书notafter
    */
    private String acmeCertNotAfter;

    /**
     * 计划续签日期（到期前N天）
    */
    @TableField("acme_cert_renew_at")
    @ApiModelProperty("计划续签日期")
    @Schema(description = "计划续签日期")
    /**
     * Acme证书renewat
    */
    private String acmeCertRenewAt;

    /**
     * 证书PEM（含链）
    */
    @TableField("acme_cert_chain_pem")
    @ApiModelProperty("证书PEM（含链）")
    @Schema(description = "证书PEM（含链）")
    /**
     * Acme证书chainpem
    */
    private String acmeCertChainPem;

    /**
     * 私钥PEM
    */
    @TableField("acme_cert_key_pem")
    @ApiModelProperty("私钥PEM")
    @Schema(description = "私钥PEM")
    /**
     * Acme证书密钥PEM
    */
    private String acmeCertKeyPem;

    /**
     * 最近错误
    */
    @TableField(value = "acme_cert_last_error", updateStrategy = FieldStrategy.ALWAYS)
    @ApiModelProperty("最近错误")
    @Schema(description = "最近错误")
    /**
     * Acme证书最后错误
    */
    private String acmeCertLastError;

    /**
     * 部署模式：本地(本机自动)/manual(手动)
    */
    @TableField("acme_cert_deploy_mode")
    @ApiModelProperty("部署模式: local(本机自动)/manual(手动)")
    @Schema(description = "部署模式: local(本机自动)/manual(手动)")
    /**
     * Acme证书deploy模式
    */
    private String acmeCertDeployMode;

    /**
     * 本机模式 webroot 路径
    */
    @TableField("acme_cert_webroot_path")
    @ApiModelProperty("本机模式webroot路径")
    @Schema(description = "本机模式webroot路径")
    /**
     * Acme证书webroot路径
    */
    private String acmeCertWebrootPath;

    /**
     * 证书状态：待验证
     */
    private static final String STATUS_PENDING = "pending";

    /**
     * 证书状态：校验中
     */
    private static final String STATUS_VALIDATING = "validating";

    /**
     * 证书状态：已签发
     */
    private static final String STATUS_VALID = "valid";

    /**
     * 证书状态：已过期
     */
    private static final String STATUS_EXPIRED = "expired";

    /**
     * 证书状态：签发失败
     */
    private static final String STATUS_FAILED = "failed";

    /**
     * 证书状态：已吊销
     */
    private static final String STATUS_REVOKED = "revoked";

    /**
     * 前端标签样式：警告（黄色）
     */
    private static final String TYPE_WARNING = "warning";

    /**
     * 前端标签样式：成功（绿色）
     */
    private static final String TYPE_SUCCESS = "success";

    /**
     * 前端标签样式：危险（红色）
     */
    private static final String TYPE_DANGER = "danger";

    /**
     * 前端标签样式：信息（灰色）
     */
    private static final String TYPE_INFO = "info";

    /**
     * 状态样式类型（前端 标签 组件使用）。
     *
     * @return 状态对应的标签样式类型，无匹配时返回空字符串
     */
    @ApiModelProperty("状态样式类型")
    @Schema(description = "状态样式类型: success/warning/danger/info")
    public String getStatusType() {
        if (acmeCertStatus == null) {
            return "";
        }
        return switch (acmeCertStatus) {
            case STATUS_PENDING, STATUS_VALIDATING -> TYPE_WARNING;
            case STATUS_VALID -> TYPE_SUCCESS;
            case STATUS_EXPIRED, STATUS_FAILED -> TYPE_DANGER;
            case STATUS_REVOKED -> TYPE_INFO;
            default -> "";
        };
    }
}
