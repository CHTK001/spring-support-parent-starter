package com.chua.starter.mybatis.oauth;

import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;
import java.util.Set;

/**
 * 当前用户
 *
 * @author CH
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {
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
    private String dataPermission;
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
     * 错误信息
     */
    private String message;

    /**
     * 实现类
     */
    private String beanType;
    /**
     * 登陆方式
     */
    private String authType;

    public String getExtValue(String key) {
        return MapUtils.getString(ext, key);
    }

    public DataFilterTypeEnum getDataPermissionEnum() {
        for (DataFilterTypeEnum value : DataFilterTypeEnum.values()) {
            if (value.getCode().equalsIgnoreCase(getDataPermission())) {
                return value;
            }
        }
        return null;
    }
}
