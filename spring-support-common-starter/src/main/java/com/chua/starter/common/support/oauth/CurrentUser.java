package com.chua.starter.common.support.oauth;

import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.google.common.base.Strings;
import lombok.experimental.Accessors;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 当前用户
 *
 * @author CH
 */
@Accessors(chain = true)
public class CurrentUser {
    /**
     * 无参构造函数
     */
    public CurrentUser() {
    }


    private static final String ANY = "*";
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
     * 租户id
     */
    private String tenantId;
    /**
     * 名称
     */
    private String username;
    /**
     * 机构id
     */
    private String deptId;
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
     * 权限
     */
    private Set<String> permission;

    /**
     * 数据权限(部门)
     */
    private DataFilterTypeEnum dataPermission;
    /**
     * 数据权限规则
     */
    private String dataPermissionRule;
    /**
     * d
     * /**
     * 角色
     */
    private Set<String> roles;

    private Map<String, Object> ext;
    /**
     * 最后一次登录地址
     */
    private String lastIp;

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

    /**
     * 是否管理员
     *
     * @return 是否管理员
     */
    public boolean isAdmin() {
        return null != this.roles && this.roles.contains("ADMIN");
    }

    /**
     * 是否需要应用数据权限
     * <p>当数据权限类型不是 ALL 时，需要应用数据权限过滤</p>
     *
     * @return 是否需要应用数据权限
     */
    public boolean isNeedDataPermission() {
        return null != this.dataPermission && this.dataPermission != DataFilterTypeEnum.ALL;
    }

    /**
     * 是否部门权限（保留兼容性，建议使用 isNeedDataPermission）
     *
     * @return 是否需要应用数据权限
     * @deprecated 使用 {@link #isNeedDataPermission()} 代替
     */
    @Deprecated
    public boolean isDept() {
        return isNeedDataPermission();
    }
    /**
     * 获取 unionId
     *
     * @return unionId
     */
    public String getUnionId() {
        return unionId;
    }

    /**
     * 设置 unionId
     *
     * @param unionId unionId
     */
    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }

    /**
     * 获取 openId
     *
     * @return openId
     */
    public String getOpenId() {
        return openId;
    }

    /**
     * 设置 openId
     *
     * @param openId openId
     */
    public void setOpenId(String openId) {
        this.openId = openId;
    }

    /**
     * 获取 uid
     *
     * @return uid
     */
    public String getUid() {
        return uid;
    }

    /**
     * 设置 uid
     *
     * @param uid uid
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * 获取 userId
     *
     * @return userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置 userId
     *
     * @param userId userId
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 获取 tenantId
     *
     * @return tenantId
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置 tenantId
     *
     * @param tenantId tenantId
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 获取 username
     *
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置 username
     *
     * @param username username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取 deptId
     *
     * @return deptId
     */
    public String getDeptId() {
        return deptId;
    }

    /**
     * 设置 deptId
     *
     * @param deptId deptId
     */
    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    /**
     * 获取 phone
     *
     * @return phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置 phone
     *
     * @param phone phone
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取 card
     *
     * @return card
     */
    public String getCard() {
        return card;
    }

    /**
     * 设置 card
     *
     * @param card card
     */
    public void setCard(String card) {
        this.card = card;
    }

    /**
     * 获取 name
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 设置 name
     *
     * @param name name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取 sex
     *
     * @return sex
     */
    public String getSex() {
        return sex;
    }

    /**
     * 设置 sex
     *
     * @param sex sex
     */
    public void setSex(String sex) {
        this.sex = sex;
    }

    /**
     * 获取 permission
     *
     * @return permission
     */
    public Set<String> getPermission() {
        return permission;
    }

    /**
     * 设置 permission
     *
     * @param permission permission
     */
    public void setPermission(Set<String> permission) {
        this.permission = permission;
    }

    /**
     * 获取 dataPermission
     *
     * @return dataPermission
     */
    public DataFilterTypeEnum getDataPermission() {
        return dataPermission;
    }

    /**
     * 设置 dataPermission
     *
     * @param dataPermission dataPermission
     */
    public void setDataPermission(DataFilterTypeEnum dataPermission) {
        this.dataPermission = dataPermission;
    }

    /**
     * 获取 dataPermissionRule
     *
     * @return dataPermissionRule
     */
    public String getDataPermissionRule() {
        return dataPermissionRule;
    }

    /**
     * 设置 dataPermissionRule
     *
     * @param dataPermissionRule dataPermissionRule
     */
    public void setDataPermissionRule(String dataPermissionRule) {
        this.dataPermissionRule = dataPermissionRule;
    }

    /**
     * 获取 roles
     *
     * @return roles
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * 设置 roles
     *
     * @param roles roles
     */
    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /**
     * 获取 ext
     *
     * @return ext
     */
    public Map<String, Object> getExt() {
        return ext;
    }

    /**
     * 设置 ext
     *
     * @param ext ext
     */
    public void setExt(Map<String, Object> ext) {
        this.ext = ext;
    }

    /**
     * 获取 lastIp
     *
     * @return lastIp
     */
    public String getLastIp() {
        return lastIp;
    }

    /**
     * 设置 lastIp
     *
     * @param lastIp lastIp
     */
    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

}

