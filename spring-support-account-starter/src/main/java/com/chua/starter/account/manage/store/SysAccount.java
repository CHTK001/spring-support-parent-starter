package com.chua.starter.account.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一账号持久化实体。
 *
 * <p>一个账号是一种「可被其它业务引用的凭据」：</p>
 * <ul>
 *   <li>{@code API_KEY}：设备厂商 / 第三方 API 的 AccessKey、Secret、Token；</li>
 *   <li>{@code EMAIL}：POP3 / SMTP 邮箱账号，主机与端口放 {@code config}；</li>
 *   <li>{@code SMS}：短信服务商的签名、模板与密钥。</li>
 * </ul>
 *
 * <p>设备管理、模板管理等必选依赖模块直接注入
 * {@code ManageAccountService} 按 {@code accountId} 取数，不需要经过 SPI；
 * AI 等非必选模块不依赖本模块，也不会读写本表。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@TableName(value = "sys_account", autoResultMap = true)
public class SysAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号标识（UUID）
     */
    @TableId(value = "sys_account_id", type = IdType.INPUT)
    private String accountId;

    /**
     * 账号编码（业务唯一键，设备 / 模板按它引用）
     */
    @TableField("sys_account_code")
    private String code;

    /**
     * 账号名称（展示用）
     */
    @TableField("sys_account_name")
    private String name;

    /**
     * 账号种类（API_KEY / EMAIL / SMS）
     */
    @TableField("sys_account_kind")
    private String kind;

    /**
     * 厂商 / 服务商（如 aliyun / smtp / huawei）
     */
    @TableField("sys_account_vendor")
    private String vendor;

    /**
     * 所属渠道标识（可空）
     */
    @TableField("sys_account_channel_key")
    private String channelKey;

    /**
     * 登录名 / 发件邮箱 / 手机号
     */
    @TableField("sys_account_username")
    private String username;

    /**
     * 敏感凭据（落库为 {@code enc:} 前缀密文；接口永不回显）
     */
    @TableField("sys_account_secret")
    private String secret;

    /**
     * 服务地址（SMTP/POP 主机、API baseUrl）
     */
    @TableField("sys_account_endpoint")
    private String endpoint;

    /**
     * 类型专属配置（端口 / SSL / 默认发件人等）
     */
    @TableField(value = "sys_account_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    /**
     * 归属用户（空表示部署级共享）
     */
    @TableField("sys_account_owner")
    private String owner;

    /**
     * 状态（1 启用 / 0 停用）
     */
    @TableField("sys_account_status")
    private Integer status;

    /**
     * 是否同种类默认账号
     */
    @TableField("sys_account_default")
    private Boolean defaultAccount;

    /**
     * 校验状态（UNKNOWN / VALID / INVALID）
     */
    @TableField("sys_account_verify_status")
    private String verifyStatus;

    /**
     * 最近校验时间（毫秒）
     */
    @TableField("sys_account_verify_at")
    private Long verifyAt;

    /**
     * 最近校验结果说明
     */
    @TableField("sys_account_verify_message")
    private String verifyMessage;

    /**
     * 逻辑删除（0 正常 / 1 已删除）
     */
    @TableField("sys_account_deleted")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_updated_at")
    private Long updatedAt;

    /**
     * 是否已配置敏感凭据（非持久化字段，列表 / 详情接口回显用）
     */
    @TableField(exist = false)
    private Boolean hasSecret;
}
