package com.chua.starter.common.support.logger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

/**
 * 系统日志
 *
 * @author CH
 * @since 2023-04-01
 */
@Getter
@Setter
public class SysLoggerInfo extends ApplicationEvent {
    /**
     * 方法名称
     * <p>示例: "getUserInfo"</p>
     */
    private String methodName;

    /**
     * 创建时间
     * <p>示例: new Date()</p>
     */
    private Date createTime;

    /**
     * 操作名称
     * <p>示例: "查询用户信息"</p>
     */
    private String logName;

    /**
     * 浏览器指纹
     * <p>示例: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"</p>
     */
    private String fingerprint;

    /**
     * 登录方式
     * <p>示例: "PASSWORD", "SMS", "WECHAT"</p>
     */
    private String loginType;

    /**
     * 系统信息
     * <p>示例: "Windows 10", "macOS Big Sur"</p>
     */
    private String system;

    /**
     * User-Agent信息
     * <p>示例: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"</p>
     */
    private String ua;

    /**
     * 浏览器信息
     * <p>示例: "Chrome 91.0.4472.124", "Firefox 89.0"</p>
     */
    private String browser;

    /**
     * 访问地址映射
     * <p>示例: "/api/login/info", "/login"</p>
     */
    private String logMapping;

    /**
     * 日志编码
     * <p>示例: "USER_QUERY_001", "LOGIN_SUCCESS"</p>
     */
    private String logCode;

    /**
     * 查询参数
     * <p>示例: "?userId=123&name=John", "username=admin&password=****"</p>
     */
    private String logParam;

    /**
     * 操作人姓名
     * <p>示例: "张三", "admin"</p>
     */
    private String createName;

    /**
     * 操作人ID
     * <p>示例: "12345", "admin"</p>
     */
    private String createBy;

    /**
     * 操作结果
     * <p>示例: {"code": 200, "data": {...}}, "操作成功"</p>
     */
    private Object result;

    /**
     * 日志内容
     * <p>示例: "用户张三查询了ID为123的用户信息"</p>
     */
    private String logContent;

    /**
     * 日志观察点
     * <p>示例: "CONTROLLER_ENTER", "SERVICE_EXIT", "DAO_EXCEPTION"</p>
     */
    private String logWatch;

    /**
     * 操作耗时(秒)
     * <p>示例: 1L, 5L</p>
     */
    private Long logCost;

    /**
     * 部门ID
     * <p>示例: "DEPT_001", "1001"</p>
     */
    private String sysDeptId;

    /**
     * 客户端IP地址
     * <p>示例: "192.168.1.100", "2001:0db8:85a3:0000:0000:8a2e:0370:7334"</p>
     */
    private String clientIp;

    /**
     * 操作模块
     * <p>示例: "用户管理", "系统设置", "权限管理"</p>
     */
    private String logModule;

    /**
     * 日志状态;0-失败,1-成功
     * <p>示例: 1, 0</p>
     */
    private Integer logStatus;

    /**
     * 构造函数
     *
     * @param source 事件源对象，通常为触发该日志事件的组件或服务
     *               <p>示例: this, userService, request</p>
     */
    public SysLoggerInfo(Object source) {
        super(source);
    }
}
