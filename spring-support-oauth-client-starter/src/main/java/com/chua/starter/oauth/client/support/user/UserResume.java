package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.google.common.base.Strings;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户信息
 *
 * @author CH
 * @since 2022/7/23 8:48
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class UserResume implements Serializable {

    public UserResume(String message) {
        this.message = message;
    }

    /**
     * unionId
     */
    private String unionId;
    /**
     * openId
     */
    private String openId;
    /**
     * 索引
     */
    private String uid;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 登录方式
     */
    private String loginType;
    /**
     * 租户id
     */
    private String tenantId;

    /**
     * 地址
     */
    private String address;
    /**
     * 机构id
     */
    private String deptId;
    /**
     * 名称
     */
    private String username;

    /**
     * 登录次数
     */
    private int loginCnt;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 电话号码
     */
    private String phone;
    /**
     * 身份证号
     */
    private String card;
    /**
     * 姓名
     */
    private String name;
    /**
     * 性别
     */
    private String sex;
    /**
     * d
     * /**
     * 角色
     */
    private Set<String> roles;
    /**
     * 权限
     */
    private Set<String> permission;
    /**
     * 角色
     */
    private Set<RoleInfo> rolesByRole;

    /**
     * 数据权限(部门)
     */
    private DataFilterTypeEnum dataPermission;
    /**
     * 数据权限规则
     */
    private String dataPermissionRule;
    /**
     * 最后一次登录地址
     */
    private String lastIp;

    /**
     * 扩展信息
     */
    private Map<String, Object> ext;

    /**
     * 扩展信息
     */
    private static final String ANY = "*";

    /**
     * 登录信息
     */
    private String message;

    /**
     * 是否具备某个权限
     *
     * @param permission 权限
     * @return 是否具备某个权限
     */
    public boolean hasPermission(String permission) {
        if (Strings.isNullOrEmpty(permission)) {
            return false;
        }

        if (permission.contains(ANY)) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            for (String s : this.permission) {
                if (antPathMatcher.match(permission, s)) {
                    return true;
                }
            }
        }

        for (String s : this.permission) {
            if (Objects.equals(permission, s)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 是否具备某个角色
     *
     * @param roles 角色
     * @return 是否具备某个角色
     */
    public boolean hasRole(String[] roles) {
        if (roles.length == 0) {
            return true;
        }

        if (CollectionUtils.isEmpty(this.roles)) {
            return false;
        }

        boolean hasRole = false;
        for (String role : roles) {
            hasRole |= hasRole(role);
        }

        return hasRole;
    }

    /**
     * 是否具备某个角色
     *
     * @param role 角色
     * @return 是否具备某个角色
     */
    public boolean hasRole(String role) {
        if (Strings.isNullOrEmpty(role)) {
            return false;
        }

        if (role.contains(ANY)) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            for (String s : roles) {
                if (antPathMatcher.match(role, s)) {
                    return true;
                }
            }
        }

        for (String s : roles) {
            if (Objects.equals(role, s)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPermission(String[] value) {
        if (value.length == 0) {
            return true;
        }

        if (CollectionUtils.isEmpty(this.permission)) {
            return false;
        }
        for (String s : value) {
            if (this.permission.contains(s)) {
                return true;
            }
        }

        return false;
    }

    public boolean isAdmin() {
        return null != this.roles && this.roles.contains("ADMIN");
    }

    /**
     * 获取扩展信息
     *
     * @param name 名称
     * @return 扩展信息
     */
    public String getOption(String name) {
        return MapUtils.getString(ext, name);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public String getOption(String name, String defaultValue) {
        return MapUtils.getString(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Integer getOption(String name, Integer defaultValue) {
        return MapUtils.getInteger(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Long getOption(String name, Long defaultValue) {
        return MapUtils.getLong(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Double getOption(String name, Double defaultValue) {
        return MapUtils.getDouble(ext, name, defaultValue);
    }

    /**
     * 获取扩展信息
     *
     * @param name         名称
     * @param defaultValue 默认值
     * @return 扩展信息
     */
    public Boolean getOption(String name, Boolean defaultValue) {
        return MapUtils.getBoolean(ext, name, defaultValue);
    }


}
