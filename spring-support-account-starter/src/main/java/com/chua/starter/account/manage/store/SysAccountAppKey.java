package com.chua.starter.account.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 账号模块的外部访问密钥。
 *
 * <p>用于给外部系统 / 设备 / 脚本发放访问凭据，<b>与 AI 的
 * {@code sys_ai_app_key}（模型计费用）无关</b>，两张表互不读写。</p>
 *
 * <p>{@code appKey} 是可检索的标识，{@code secret} 只在校验时使用，
 * 落库为 {@code enc:} 前缀密文；接口只在创建 / 轮换时回显一次明文 secret。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@TableName("sys_account_app_key")
public class SysAccountAppKey implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 密钥标识（UUID）
     */
    @TableId(value = "sys_account_app_key_id", type = IdType.INPUT)
    private String appKeyId;

    /**
     * 密钥名称
     */
    @TableField("sys_account_app_key_name")
    private String name;

    /**
     * 访问 Key（标识，可明文查询）
     */
    @TableField("sys_account_app_key_app_key")
    private String appKey;

    /**
     * 访问 Secret（落库密文；接口不回显）
     */
    @TableField("sys_account_app_key_secret")
    private String secret;

    /**
     * 归属用户（空表示部署级共享）
     */
    @TableField("sys_account_app_key_owner")
    private String owner;

    /**
     * 状态（1 启用 / 0 停用）
     */
    @TableField("sys_account_app_key_status")
    private Integer status;

    /**
     * 过期时间（毫秒，空表示永不过期）
     */
    @TableField("sys_account_app_key_expire_at")
    private Long expireAt;

    /**
     * IP 白名单（逗号分隔）
     */
    @TableField("sys_account_app_key_ip_whitelist")
    private String ipWhitelist;

    /**
     * 备注
     */
    @TableField("sys_account_app_key_remark")
    private String remark;

    /**
     * 最近使用时间（毫秒）
     */
    @TableField("sys_account_app_key_last_used_at")
    private Long lastUsedAt;

    /**
     * 逻辑删除（0 正常 / 1 已删除）
     */
    @TableField("sys_account_app_key_deleted")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_app_key_created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_app_key_updated_at")
    private Long updatedAt;

    /**
     * 创建 / 轮换时回显一次的明文 Secret（非持久化字段，其余场景恒为 {@code null}）
     */
    @TableField(exist = false)
    private String plainSecret;
}
