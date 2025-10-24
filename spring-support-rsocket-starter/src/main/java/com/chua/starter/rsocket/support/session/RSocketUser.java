package com.chua.starter.rsocket.support.session;

import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * RSocket用户信息
 * <p>
 * 封装用户的基本信息和权限信息
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Data
public class RSocketUser {
    
    /**
     * 用户ID
     */
    private String id;

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 登录次数
     */
    private Integer loginCnt;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * OpenID
     */
    private String openId;

    /**
     * 联合ID
     */
    private String unionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 索引唯一ID（由系统生成）
     */
    private String uid;

    /**
     * 加密密码
     */
    private String password;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 角色列表
     */
    private Set<String> roles;

    /**
     * 部门ID
     */
    private String deptId;

    /**
     * 权限列表（按钮）
     */
    private Set<String> permission;

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
     * 地址
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
     * 登录方式
     */
    private String authType;
}

