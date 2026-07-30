package com.chua.starter.oauth.client.support.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;
import java.util.Set;

/**
 * 用户简历
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class UserResume {

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 角色
     */
    private Set<String> roles;

    /**
     * 权限
     */
    private Set<String> permission;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 地址
     */
    private String address;

    /**
     * 性别
     */
    private String sex;

    /**
     * 登录类型
     */
    private String loginType;

    /**
     * openId
     */
    private String openId;

    /**
     * unionId
     */
    private String unionId;

    /**
     * 过期时间（秒）
     */
    private Long expireTime;

    /**
     * UID
     */
    private String uid;

    /**
     * 扩展信息
     */
    private Map<String, Object> ext;

    /**
     * 浏览器指纹
     */
    private String fingerprint;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 部门ID
     */
    private String deptId;

    /**
     * 是否管理员
     */
    private boolean admin;

    public UserResume(String message) {
        this.username = message;
    }

    public Object getOption(String key) {
        if (ext == null) {
            return null;
        }
        return ext.get(key);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasRole(String... roles) {
        if (roles == null || this.roles == null) {
            return false;
        }
        for (String role : roles) {
            if (this.roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(String permission) {
        return this.permission != null && this.permission.contains(permission);
    }

    public boolean hasPermission(String... permissions) {
        if (permissions == null || this.permission == null) {
            return false;
        }
        for (String permission : permissions) {
            if (this.permission.contains(permission)) {
                return true;
            }
        }
        return false;
    }
}
