package com.chua.starter.account.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 上游余额查询配置。
 *
 * <p>网关（magpie）对部分上游没有余额接口，额度卡上只剩一句「读不到」。
 * 这类上游可以在这里配一条「去哪查、余额在哪个字段」：地址、方法、令牌、
 * 字段表达式都由本表给出，真正的查询交给
 * {@code com.chua.starter.datasearch.credit.spi.CreditQueryProvider}。</p>
 *
 * <p>{@code token} 落库为 {@code enc:} 前缀密文，读接口只回 {@code hasToken}；
 * 主键就是网关里的 provider id，一个上游最多一条配置。</p>
 *
 * @author CH
 * @since 4.0.0.82
 */
@Data
@TableName("sys_account_credit_config")
public class SysAccountCreditConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上游 provider id（网关里的标识，如 moxuna）
     */
    @TableId(value = "sys_account_credit_config_provider", type = IdType.INPUT)
    private String providerId;

    /**
     * 查询实现（expression / new-api / sub2api / openai / host），空表示按表达式回落
     */
    @TableField("sys_account_credit_config_kind")
    private String kind;

    /**
     * 余额接口地址
     */
    @TableField("sys_account_credit_config_url")
    private String url;

    /**
     * HTTP 方法（GET / POST / PUT），空按 GET
     */
    @TableField("sys_account_credit_config_method")
    private String method;

    /**
     * 令牌请求头名，空按实现默认值（一般为 Authorization）
     */
    @TableField("sys_account_credit_config_header")
    private String tokenHeader;

    /**
     * 令牌（落库密文；接口不回显）
     */
    @TableField("sys_account_credit_config_token")
    private String token;

    /**
     * Access Key（AK/SK 签名类余额接口用；落库密文，接口不回显）
     */
    @TableField("sys_account_credit_config_ak")
    private String accessKey;

    /**
     * Secret Key（AK/SK 签名类余额接口用；落库密文，接口不回显）
     */
    @TableField("sys_account_credit_config_sk")
    private String secretKey;

    /**
     * 字段表达式，多段用分号分隔（如 {@code $data.total_available / 500000}）
     */
    @TableField("sys_account_credit_config_expr")
    private String expression;

    /**
     * 计费币种口径（usd / cny），空时由表达式币种前缀决定
     */
    @TableField("sys_account_credit_config_billing")
    private String billing;

    /**
     * 状态（1 启用 / 0 停用）
     */
    @TableField("sys_account_credit_config_status")
    private Integer status;

    /**
     * 备注
     */
    @TableField("sys_account_credit_config_remark")
    private String remark;

    /**
     * 逻辑删除（0 正常 / 1 已删除）
     */
    @TableField("sys_account_credit_config_deleted")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_credit_config_created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_credit_config_updated_at")
    private Long updatedAt;

    /**
     * 是否已配置令牌（非持久化字段，列表 / 详情接口回显用）
     */
    @TableField(exist = false)
    private Boolean hasToken;

    /**
     * 是否已配置 Access Key（非持久化字段，列表 / 详情接口回显用）
     */
    @TableField(exist = false)
    private Boolean hasAccessKey;

    /**
     * 是否已配置 Secret Key（非持久化字段，列表 / 详情接口回显用）
     */
    @TableField(exist = false)
    private Boolean hasSecretKey;
}
