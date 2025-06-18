package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
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
@AllArgsConstructor
@JsonIgnoreProperties({"beanType", "accessSecret", "expire", "password", "salt", "password", "salt", "userEnable", "address", "lastArea", "lastLatitude", "lastIp", "lastLongitude"})
public class UserResult {
    private String id;

    /**
     * 登录次数
     */
    private Integer loginCnt;

    /**
     * 客户端id
     */
    private String clientId;
    /**
     * openId
     */
    private String openId;
    /**
     * 联合id
     */
    private String unionId;

    /**
     * 用户id
     */
    private String userId;
    /**
     * 索引唯一由系统生成
     */
    private String uid;

    /**
     * 刷新token
     */
    private String refreshToken;
    /**
     * 加密密码
     */
    private String password;
    /**
     * 名称
     */
    private String username;

    /**
     * 租户id
     */
    private String tenantId;
    /**
     * 角色
     */
    private Set<String> roles;


    /**
     * 部门id
     */
    private String deptId;
    /**
     * 权限(按钮)
     */
    private Set<String> permission;
    /**
     * 角色
     */
    private Set<RoleInfo> rolesByRole;
    /**
     * 用户自定义数据
     */
    private String userDashboardGrid;
    /**
     * 用户自定义数据
     */
    private String userDashboard;

    /**
     * 数据权限(部门)
     */
    private DataFilterTypeEnum dataPermission;
    /**
     * 数据权限规则
     */
    private String dataPermissionRule;

    /**
     * 额外信息
     */
    private Map<String, Object> ext;
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
     * address
     */
    private String address;
    /**
     * 登录超时时间
     */
    private Long expire;

    /**
     * 刷新token超时时间
     */
    private Long refreshExpire;
    /**
     * 错误信息
     */
    private String message;

    /**
     * ak/sk
     */
    private AccessSecret accessSecret;
    /**
     * 登陆方式
     */
    private String authType;

    /**
     * 密码
     *
     * @param password 密码
     * @return this
     */
    public UserResult setPassword(String password) {
        this.password = password;
        if (password.length() > 4) {
            this.password = password.substring(0, 4).concat("****").concat(password.substring(4));
        } else {
            this.password = password.concat("****");
        }
        return this;
    }

    /**
     * 转化
     *
     * @param target 目标类型
     * @param <T>    类型
     * @return 结果
     */
    public <T> T toBean(Class<T> target) {
        T newInstance = null;
        try {
            newInstance = target.newInstance();
        } catch (Exception ignored) {
        }

        if (null == newInstance) {
            return null;
        }

        Map<String, Object> beanMap = this.ext;
        T finalNewInstance = newInstance;
        ReflectionUtils.doWithFields(target, field -> {
            ClassUtils.setAccessible(field);
            Object o = beanMap.get(field.getName());
            if (null == o) {
                return;
            }

            try {
                field.set(finalNewInstance, o);
            } catch (Exception ignore) {
            }
        });

        return finalNewInstance;
    }

    public boolean hasRootRole() {
        return this.getRoles().contains("admin");
    }

    public String getExtValue(String key) {
        return MapUtils.getString(ext, key);
    }


    @Data
    public static class RoleInfo {

        /**
         * 角色名称
         */
        private String roleName;
        /**
         * 角色id
         */
        private String roleId;

        /**
         * 角色编码
         */
        private String roleCode;
        /**
         * 角色描述
         */
        private String roleDesc;

        /**
         * 是否可读
         */
        private boolean readable;

        /**
         * 是否可写
         */
        private boolean writeable;

        /**
         * 是否可执行
         */
        private boolean executable;
    }

}
