package com.chua.starter.account.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 账号模块的 Cookie 池。
 *
 * <p>存放免登录渠道 / 站点会话的 Cookie，按 {@code domain + source} 归属，
 * 供渠道实现按需读取；与 AI 的提供商账号无任何关联。</p>
 *
 * <p>{@code value} 落库为 {@code enc:} 前缀密文，接口只回传
 * {@code hasValue}，不回显内容。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@TableName("sys_account_cookie")
public class SysAccountCookie implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Cookie 标识（UUID）
     */
    @TableId(value = "sys_account_cookie_id", type = IdType.INPUT)
    private String cookieId;

    /**
     * 名称（展示用）
     */
    @TableField("sys_account_cookie_name")
    private String name;

    /**
     * 作用域域名
     */
    @TableField("sys_account_cookie_domain")
    private String domain;

    /**
     * 作用路径
     */
    @TableField("sys_account_cookie_path")
    private String path;

    /**
     * Cookie 内容（落库密文；接口不回显）
     */
    @TableField("sys_account_cookie_value")
    private String cookieValue;

    /**
     * 归属用户（空表示部署级共享）
     */
    @TableField("sys_account_cookie_owner")
    private String owner;

    /**
     * 状态（1 启用 / 0 停用）
     */
    @TableField("sys_account_cookie_status")
    private Integer status;

    /**
     * 过期时间（毫秒，空表示会话级）
     */
    @TableField("sys_account_cookie_expire_at")
    private Long expireAt;

    /**
     * 来源（如 trae / duckduckgo / manual）
     */
    @TableField("sys_account_cookie_source")
    private String source;

    /**
     * 备注
     */
    @TableField("sys_account_cookie_remark")
    private String remark;

    /**
     * 逻辑删除（0 正常 / 1 已删除）
     */
    @TableField("sys_account_cookie_deleted")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_cookie_created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_cookie_updated_at")
    private Long updatedAt;

    /**
     * 是否已配置内容（非持久化字段，列表 / 详情接口回显用）
     */
    @TableField(exist = false)
    private Boolean hasValue;
}
