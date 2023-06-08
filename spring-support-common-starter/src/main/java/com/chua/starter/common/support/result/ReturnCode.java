package com.chua.starter.common.support.result;

import lombok.AllArgsConstructor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * @author Administrator
 */
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public enum ReturnCode implements ResultCode {
    /**
     * 操作成功
     **/
    OK("00000", "操作成功"),
    /**
     * 操作成功
     **/
    SUCCESS("00000", "操作成功"),
    //*******************************Client*********************************************
    USERNAME_OR_PASSWORD_ERROR("A0200", "用户名或密码错误"),
    USER_NOT_EXIST("A0201", "用户不存在"),
    USER_ACCOUNT_LOCKED("A0202", "用户账户被冻结"),
    USER_ACCOUNT_INVALID("A0203", "用户账户已作废"),
    USER_PASSWORD_ENTER_EXCEED_LIMIT("A0204", "用户输入密码次数超限"),


    PARAM_IS_NULL("A0400", "请求必填参数为空"),
    PARAM_ERROR("A0400", "请求参数错误"),
    RESOURCE_NOT_FOUND("A0401", "请求资源不存在"),
    RESOURCE_OAUTH_DENIED("A0403", "无访问权限,请联系管理员授予权限"),

    FILE_UPLOAD_FILE_ERROR("A0700", "用户上传文件异常"),
    FILE_UPLOAD_FILE_TYPE_NOT_MATCH("A0701", "用户上传文件类型不匹配"),
    FILE_UPLOAD_FILE_SIZE_EXCEEDS("A0702", "用户上传文件太大"),
    FILE_UPLOAD_IMAGE_SIZE_EXCEEDS("A0703", "用户上传图片太大"),

    RESULT_ACCESS_UNAUTHORIZED("A0403", "无权限访问"),
    SERVER_ERROR("A9999", "系统错误"),

    //*******************************Server*********************************************

    SYSTEM_EXECUTION_ERROR("B0001", "系统执行出错"),
    SYSTEM_EXECUTION_TIMEOUT("B0100", "系统执行超时"),
    SYSTEM_FLOW_LIMITING("B0210", "系统限流"),
    SYSTEM_DEGRADATION("B0220", "系统功能降级"),

    SYSTEM_RESOURCE_ERROR("B0300", "系统资源异常"),
    SYSTEM_RESOURCE_EXHAUSTION("B0310", "系统资源耗尽"),
    SYSTEM_RESOURCE_ACCESS_ERROR("B0320", "系统资源访问异常"),
    SYSTEM_READ_DISK_FILE_ERROR("B0321", "系统读取磁盘文件失败"),
    /**
     * 接口不存在
     */
    SYSTEM_INTERFACE_NOT_EXIST("B0113", "接口不存在"),
    /**
     * 服务繁忙
     **/
    SYSTEM_SERVER_BUSINESS("B0103", "服务繁忙,请稍后再试!"),
    /**
     * 系统规则不满足
     **/
    SYSTEM_SERVER_RULES_NO_MATCH("B0106", "系统规则不满足要求,请稍后再试!"),
    /**
     * 请求类型不支持
     **/
    SYSTEM_REQUEST_METHOD_NO_MATCH("B0405", "请求类型不支持"),

    /**
     * 鉴权服务器异常
     **/
    SYSTEM_AUTH_SERVER_ERROR("B3401", "鉴权服务器异常"),

    /**
     * 鉴权服务器异常
     **/
    SYSTEM_AUTH_NO_PASS("B0403", "无权限访问"),
    /**
     * 鉴权服务器不存在
     **/
    SYSTEM_AUTH_SERVER_NO_EXIST("B3404", "鉴权服务器不存在"),
    SYSTEM_UNSUPPORTED_GRANT_TYPE("B1103", "不支持的认证模式"),

    //*******************************Part*********************************************
    SYSTEM_CALL_THIRD_PARTY_SERVICE_ERROR("C0001", "调用第三方服务出错"),
    SYSTEM_MIDDLEWARE_SERVICE_ERROR("C0100", "中间件服务出错"),
    INTERFACE_NOT_EXIST("C0113", "接口不存在"),
    MESSAGE_SERVICE_ERROR("C0120", "消息服务出错"),
    MESSAGE_DELIVERY_ERROR("C0121", "消息投递出错"),
    MESSAGE_CONSUMPTION_ERROR("C0122", "消息消费出错"),
    MESSAGE_SUBSCRIPTION_ERROR("C0123", "消息订阅出错"),
    MESSAGE_GROUP_NOT_FOUND("C0124", "消息分组未查到"),

    DATABASE_ERROR("C0300", "数据库服务出错"),
    DATABASE_TABLE_NOT_EXIST("C0311", "表不存在"),
    DATABASE_COLUMN_NOT_EXIST("C0312", "列不存在"),
    DATABASE_DUPLICATE_COLUMN_NAME("C0321", "多表关联中存在多个相同名称的列"),
    DATABASE_DEADLOCK("C0331", "数据库死锁"),
    DATABASE_PRIMARY_KEY_CONFLICT("C0341", "主键冲突"),


    /**
     * 其它错误
     **/
    OTHER("Z0000", "其它错误");


    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    private String code;

    private String msg;


    public static ReturnCode getValue(String code) {
        for (ReturnCode value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return ReturnCode.SYSTEM_SERVER_BUSINESS;
    }
}