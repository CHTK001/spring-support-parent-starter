package com.chua.starter.oauth.client.support.user;

import com.chua.starter.common.support.constant.DataFilterTypeEnum;
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
* @since 4.0.0
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
    * 用户标识
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
    * 打开标识
    */
    private String openId;

    /**
    * unionid
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
    * 租户标识
    */
    private String tenantId;

    /**
    * 部门标识
    */
    private String deptId;

    /**
    * 是否管理员
    */
    private boolean admin;

    /**
    * 是否只读账号
    * <p>由 OAuth 服务端数据库查询返回（用户表只读字段），非配置文件配置；
    * 只读账号禁止写操作（POST/放入/删除/PATCH），优先于所有权限（含超级管理员）</p>
    */
    private boolean readonly;

    /**
    * 错误消息
    */
    private String message;

    /**
    * 用户编码
    */
    private String userCode;

    /**
    * 应用标识
    */
    private String appId;

    /**
    * 数据权限类型
    */
    private DataFilterTypeEnum dataPermission;

    /**
    * 数据权限规则（部门标识集合）
    */
    private String dataPermissionRule;

    /**
    * 创建 用户resume 实例
    * @param message 消息
    */
    public UserResume(String message) {
        this.message = message;
    }

    /**
    * 获取期权
    *
    * @param key 钥匙
    * @return 获取期权的结果
    */
    public Object getOption(String key) {
        if (ext == null) {
            return null;
        }
        return ext.get(key);
    }

    /**
    * 是否拥有角色
    *
    * @param role 角色
    * @return 是否包含角色的结果
    */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
    * 是否拥有角色
    *
    * @param roles 角色
    * @return 是否包含角色的结果
    */
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

    /**
    * 是否拥有权限
    *
    * @param permission 权限
    * @return 是否包含权限的结果
    */
    public boolean hasPermission(String permission) {
        return this.permission != null && this.permission.contains(permission);
    }

    /**
    * 是否拥有权限
    *
    * @param permissions 权限
    * @return 是否包含权限的结果
    */
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
