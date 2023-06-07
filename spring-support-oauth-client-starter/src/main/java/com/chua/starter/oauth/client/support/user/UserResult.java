package com.chua.starter.oauth.client.support.user;

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
     * 索引唯一由系统生成
     */
    private String uid;
    /**
     * 加密密码
     */
    private String password;
    /**
     * 名称
     */
    private String username;
    /**
     * 角色
     */
    private Set<String> roles;
    /**
     * 权限
     */
    private Set<String> permission;
    /**
     * 数据权限
     */
    private Set<String> dataPermission;

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
     * 错误信息
     */
    private String message;

    /**
     * ak/sk
     */
    private AccessSecret accessSecret;
    /**
     * 实现类
     */
    private String beanType;
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
            field.setAccessible(true);
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
}
