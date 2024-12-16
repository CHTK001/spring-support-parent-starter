package com.chua.socketio.support.session;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import lombok.Data;
import org.springframework.util.ReflectionUtils;

import java.util.Map;
import java.util.Set;

/**
 * socket用户
 * @author CH
 * @since 2024/12/13
 */
@Data
public class SocketUser {
    private String id;

    private String roomId;

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
     * 登陆方式
     */
    private String authType;


}
