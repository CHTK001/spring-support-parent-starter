package com.chua.starter.common.support.logger;

import org.springframework.context.ApplicationEvent;

import java.util.Date;

/**
 * 系统日志
 *
 * @author CH
 * @since 2023-04-01
 */
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
     * <p>示例: "用户张三查询了ID为23的用户信息"</p>
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
     * 日志状态 0-失败,1-成功
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
    /**
     * 获取 methodName
     *
     * @return methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 设置 methodName
     *
     * @param methodName methodName
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 获取 createTime
     *
     * @return createTime
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * 设置 createTime
     *
     * @param createTime createTime
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取 logName
     *
     * @return logName
     */
    public String getLogName() {
        return logName;
    }

    /**
     * 设置 logName
     *
     * @param logName logName
     */
    public void setLogName(String logName) {
        this.logName = logName;
    }

    /**
     * 获取 fingerprint
     *
     * @return fingerprint
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * 设置 fingerprint
     *
     * @param fingerprint fingerprint
     */
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    /**
     * 获取 loginType
     *
     * @return loginType
     */
    public String getLoginType() {
        return loginType;
    }

    /**
     * 设置 loginType
     *
     * @param loginType loginType
     */
    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    /**
     * 获取 system
     *
     * @return system
     */
    public String getSystem() {
        return system;
    }

    /**
     * 设置 system
     *
     * @param system system
     */
    public void setSystem(String system) {
        this.system = system;
    }

    /**
     * 获取 ua
     *
     * @return ua
     */
    public String getUa() {
        return ua;
    }

    /**
     * 设置 ua
     *
     * @param ua ua
     */
    public void setUa(String ua) {
        this.ua = ua;
    }

    /**
     * 获取 browser
     *
     * @return browser
     */
    public String getBrowser() {
        return browser;
    }

    /**
     * 设置 browser
     *
     * @param browser browser
     */
    public void setBrowser(String browser) {
        this.browser = browser;
    }

    /**
     * 获取 logMapping
     *
     * @return logMapping
     */
    public String getLogMapping() {
        return logMapping;
    }

    /**
     * 设置 logMapping
     *
     * @param logMapping logMapping
     */
    public void setLogMapping(String logMapping) {
        this.logMapping = logMapping;
    }

    /**
     * 获取 logCode
     *
     * @return logCode
     */
    public String getLogCode() {
        return logCode;
    }

    /**
     * 设置 logCode
     *
     * @param logCode logCode
     */
    public void setLogCode(String logCode) {
        this.logCode = logCode;
    }

    /**
     * 获取 logParam
     *
     * @return logParam
     */
    public String getLogParam() {
        return logParam;
    }

    /**
     * 设置 logParam
     *
     * @param logParam logParam
     */
    public void setLogParam(String logParam) {
        this.logParam = logParam;
    }

    /**
     * 获取 createName
     *
     * @return createName
     */
    public String getCreateName() {
        return createName;
    }

    /**
     * 设置 createName
     *
     * @param createName createName
     */
    public void setCreateName(String createName) {
        this.createName = createName;
    }

    /**
     * 获取 createBy
     *
     * @return createBy
     */
    public String getCreateBy() {
        return createBy;
    }

    /**
     * 设置 createBy
     *
     * @param createBy createBy
     */
    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    /**
     * 获取 result
     *
     * @return result
     */
    public Object getResult() {
        return result;
    }

    /**
     * 设置 result
     *
     * @param result result
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * 获取 logContent
     *
     * @return logContent
     */
    public String getLogContent() {
        return logContent;
    }

    /**
     * 设置 logContent
     *
     * @param logContent logContent
     */
    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    /**
     * 获取 logWatch
     *
     * @return logWatch
     */
    public String getLogWatch() {
        return logWatch;
    }

    /**
     * 设置 logWatch
     *
     * @param logWatch logWatch
     */
    public void setLogWatch(String logWatch) {
        this.logWatch = logWatch;
    }

    /**
     * 获取 logCost
     *
     * @return logCost
     */
    public Long getLogCost() {
        return logCost;
    }

    /**
     * 设置 logCost
     *
     * @param logCost logCost
     */
    public void setLogCost(Long logCost) {
        this.logCost = logCost;
    }

    /**
     * 获取 sysDeptId
     *
     * @return sysDeptId
     */
    public String getSysDeptId() {
        return sysDeptId;
    }

    /**
     * 设置 sysDeptId
     *
     * @param sysDeptId sysDeptId
     */
    public void setSysDeptId(String sysDeptId) {
        this.sysDeptId = sysDeptId;
    }

    /**
     * 获取 clientIp
     *
     * @return clientIp
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * 设置 clientIp
     *
     * @param clientIp clientIp
     */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    /**
     * 获取 logModule
     *
     * @return logModule
     */
    public String getLogModule() {
        return logModule;
    }

    /**
     * 设置 logModule
     *
     * @param logModule logModule
     */
    public void setLogModule(String logModule) {
        this.logModule = logModule;
    }

    /**
     * 获取 logStatus
     *
     * @return logStatus
     */
    public Integer getLogStatus() {
        return logStatus;
    }

    /**
     * 设置 logStatus
     *
     * @param logStatus logStatus
     */
    public void setLogStatus(Integer logStatus) {
        this.logStatus = logStatus;
    }


}

