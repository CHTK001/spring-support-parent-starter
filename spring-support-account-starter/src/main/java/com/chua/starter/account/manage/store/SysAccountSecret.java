package com.chua.starter.account.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 账号模块的密钥库。
 *
 * <p>把厂商访问密钥（AccessKeyId / AccessKeySecret）从账号里拆出来单独管理：
 * 账号只存 {@code config.secretId} 引用，换密钥时所有引用它的账号一起生效。
 * 后续 {@code server} 等底层模块也可以按同一份密钥复用。</p>
 *
 * <p>{@code secret} 落库为 {@code enc:} 前缀密文，读接口只回 {@code hasSecret}；
 * {@code accessKey} 是可检索标识，保留明文。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("sys_account_secret")
public class SysAccountSecret implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 密钥标识（UUID）
     */
    @TableId(value = "sys_account_secret_id", type = IdType.INPUT)
    private String secretId;

    /**
     * 密钥名称（展示用）
     */
    @TableField("sys_account_secret_name")
    private String name;

    /**
     * 密钥用途 / 厂商（aliyun / tencent / huawei / custom）
     */
    @TableField("sys_account_secret_provider")
    private String provider;

    /**
     * AccessKeyId / 账号标识（可明文查询）
     */
    @TableField("sys_account_secret_access_key")
    private String accessKey;

    /**
     * AccessKeySecret（落库密文；接口不回显）
     */
    @TableField("sys_account_secret_secret")
    private String secret;

    /**
     * 区域（如 cn-hangzhou）
     */
    @TableField("sys_account_secret_region")
    private String region;

    /**
     * 服务地址（可空，按厂商默认）
     */
    @TableField("sys_account_secret_endpoint")
    private String endpoint;

    /**
     * 归属用户（空表示部署级共享）
     */
    @TableField("sys_account_secret_owner")
    private String owner;

    /**
     * 状态（1 启用 / 0 停用）
     */
    @TableField("sys_account_secret_status")
    private Integer status;

    /**
     * 备注
     */
    @TableField("sys_account_secret_remark")
    private String remark;

    /**
     * 逻辑删除（0 正常 / 1 已删除）
     */
    @TableField("sys_account_secret_deleted")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_secret_created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_secret_updated_at")
    private Long updatedAt;

    /**
     * 是否已配置 Secret（非持久化字段，列表 / 详情接口回显用）
     */
    @TableField(exist = false)
    private Boolean hasSecret;
}
